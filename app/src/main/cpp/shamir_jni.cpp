/*
 * Copyright (C) 2026 The S4 project contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// shamir_jni.cpp — JNI façade for the vendored bc-shamir implementation.
//
// Exposes:
//   nativeSplit(threshold, shareCount, secret: ByteArray): Array<ByteArray>
//   nativeSplitDeterministic(...)          — test hook using a fixed PRNG so
//                                             output matches the upstream
//                                             bc-shamir test vectors
//   nativeRecover(threshold, x: IntArray, shares: Array<ByteArray>,
//                 shareLength): ByteArray
//   (x holds the raw bc-shamir coordinates for each share: position 0-based,
//    matching the reference implementation; the Kotlin facade converts the
//    app's 1-based display indexes via x = index - 1)
//
// Randomness comes from the kernel CSPRNG via getrandom(). getrandom() became
// a bionic libc wrapper in API 28; for API 26–27 the syscall itself is used
// directly (the kernel getrandom syscall exists on all kernels those builds
// ship). All inputs are validated here and in the C library; failures raise a
// Java exception.

#include <jni.h>

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <sys/random.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <vector>

extern "C" {
#include "shamir.h"
#include <bc-crypto-base/bc-crypto-base.h>
}

namespace {

// ---------------------------------------------------------------------------
// Random sources
// ---------------------------------------------------------------------------

int getrandom_bytes(uint8_t* buf, size_t len) {
    while (len > 0) {
        ssize_t r;
#if defined(__ANDROID__) && defined(__ANDROID_API__) && __ANDROID_API__ < 28
        r = syscall(__NR_getrandom, buf, len, 0);
#else
        r = getrandom(buf, len, 0);
#endif
        if (r < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        buf += r;
        len -= static_cast<size_t>(r);
    }
    return 0;
}

void random_generator(uint8_t* buf, size_t len, void* /*ctx*/) {
    if (getrandom_bytes(buf, len) != 0) {
        // Cannot obtain CSPRNG bytes; zero-fill and let the caller fail on
        // the checksum path rather than leaking stack data.
        memset(buf, 0, len);
    }
}

// Matches the upstream bc-shamir test suite's `fake_random`: deterministic,
// never use outside of tests.
void fake_random_generator(uint8_t* buf, size_t len, void* /*ctx*/) {
    uint8_t b = 0;
    for (size_t i = 0; i < len; ++i) {
        buf[i] = b;
        b = static_cast<uint8_t>(b + 17);
    }
}

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------

void throw_illegal_arg(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/IllegalArgumentException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

void throw_shamir(JNIEnv* env, int32_t code) {
    const char* message;
    switch (code) {
        case SHAMIR_ERROR_SECRET_TOO_LONG:    message = "secret too long"; break;
        case SHAMIR_ERROR_TOO_MANY_SHARES:    message = "too many shares"; break;
        case SHAMIR_ERROR_INTERPOLATION_FAILURE: message = "interpolation failure"; break;
        case SHAMIR_ERROR_CHECKSUM_FAILURE:   message = "checksum failure: one or more shares are incorrect"; break;
        case SHAMIR_ERROR_SECRET_TOO_SHORT:   message = "secret too short"; break;
        case SHAMIR_ERROR_SECRET_NOT_EVEN_LEN: message = "secret length must be even"; break;
        case SHAMIR_ERROR_INVALID_THRESHOLD:  message = "invalid threshold"; break;
        default:                              message = "unknown shamir error"; break;
    }

    jclass cls = env->FindClass("com/s4/crypto/ShamirException");
    if (cls == nullptr) return;
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;)V");
    if (ctor == nullptr) return;
    jstring jmsg = env->NewStringUTF(message);
    jobject ex = env->NewObject(cls, ctor, static_cast<jint>(code), jmsg);
    if (ex != nullptr) {
        env->Throw(static_cast<jthrowable>(ex));
        env->DeleteLocalRef(ex);
    }
    env->DeleteLocalRef(jmsg);
    env->DeleteLocalRef(cls);
}

// ---------------------------------------------------------------------------
// Shared split logic
// ---------------------------------------------------------------------------

jobjectArray do_split(JNIEnv* env, jint threshold, jint shareCount,
                      const uint8_t* secret, jsize secretLen,
                      void (*rng)(uint8_t*, size_t, void*)) {
    if (threshold < 1 || threshold > 16 || shareCount < threshold || shareCount > 16) {
        throw_illegal_arg(env, "threshold/shareCount must satisfy 1 <= threshold <= shareCount <= 16");
        return nullptr;
    }
    if (secretLen < 16 || secretLen > 32 || (secretLen & 1) != 0) {
        throw_illegal_arg(env, "secret length must be 16..32 and even");
        return nullptr;
    }

    const size_t resultLen = static_cast<size_t>(shareCount) * static_cast<size_t>(secretLen);
    std::vector<uint8_t> result(resultLen);

    int32_t rv = split_secret(static_cast<uint8_t>(threshold),
                              static_cast<uint8_t>(shareCount),
                              secret,
                              static_cast<uint32_t>(secretLen),
                              result.data(),
                              nullptr,
                              rng);
    if (rv < 0) {
        throw_shamir(env, rv);
        return nullptr;
    }

    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) return nullptr;
    jobjectArray out = env->NewObjectArray(shareCount, byteArrayClass, nullptr);
    if (out == nullptr) return nullptr;

    for (jint i = 0; i < shareCount; ++i) {
        jbyteArray arr = env->NewByteArray(secretLen);
        if (arr == nullptr) return nullptr;
        env->SetByteArrayRegion(arr, 0, secretLen,
                                reinterpret_cast<const jbyte*>(result.data() + i * secretLen));
        env->SetObjectArrayElement(out, i, arr);
        env->DeleteLocalRef(arr);
    }

    memset(result.data(), 0, result.size());
    return out;
}

}  // namespace

// ---------------------------------------------------------------------------
// JNI entry points
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_s4_crypto_Shamir_nativeSplit(JNIEnv* env, jclass /*clazz*/,
                                              jint threshold, jint shareCount,
                                              jbyteArray secretArray) {
    if (secretArray == nullptr) {
        throw_illegal_arg(env, "secret is null");
        return nullptr;
    }
    jsize len = env->GetArrayLength(secretArray);
    std::vector<uint8_t> secret(static_cast<size_t>(len));
    env->GetByteArrayRegion(secretArray, 0, len,
                            reinterpret_cast<jbyte*>(secret.data()));
    if (env->ExceptionCheck()) return nullptr;

    return do_split(env, threshold, shareCount, secret.data(), len, random_generator);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_s4_crypto_Shamir_nativeSplitDeterministic(JNIEnv* env, jclass /*clazz*/,
                                                           jint threshold, jint shareCount,
                                                           jbyteArray secretArray) {
    if (secretArray == nullptr) {
        throw_illegal_arg(env, "secret is null");
        return nullptr;
    }
    jsize len = env->GetArrayLength(secretArray);
    std::vector<uint8_t> secret(static_cast<size_t>(len));
    env->GetByteArrayRegion(secretArray, 0, len,
                            reinterpret_cast<jbyte*>(secret.data()));
    if (env->ExceptionCheck()) return nullptr;

    return do_split(env, threshold, shareCount, secret.data(), len, fake_random_generator);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_s4_crypto_Shamir_nativeRecover(JNIEnv* env, jclass /*clazz*/,
                                                jint threshold, jintArray xArray,
                                                jobjectArray sharesArray,
                                                jint shareLength) {
    if (xArray == nullptr || sharesArray == nullptr) {
        throw_illegal_arg(env, "x/shares must not be null");
        return nullptr;
    }
    const jsize t = env->GetArrayLength(xArray);
    const jsize n = env->GetArrayLength(sharesArray);

    if (threshold < 1 || threshold > 16) {
        throw_illegal_arg(env, "threshold must be in 1..16");
        return nullptr;
    }
    if (t != threshold || n != threshold) {
        throw_illegal_arg(env, "threshold must equal the number of shares and x values");
        return nullptr;
    }
    if (shareLength < 16 || shareLength > 32 || (shareLength & 1) != 0) {
        throw_illegal_arg(env, "share length must be 16..32 and even");
        return nullptr;
    }

    jint* xj = env->GetIntArrayElements(xArray, nullptr);
    if (xj == nullptr) return nullptr;
    std::vector<uint8_t> x(static_cast<size_t>(threshold));
    for (jsize i = 0; i < t; ++i) {
        const jint xi = xj[i];
        if (xi < 0 || xi > 255) {
            env->ReleaseIntArrayElements(xArray, xj, JNI_ABORT);
            throw_illegal_arg(env, "share index x must be in 0..255");
            return nullptr;
        }
        x[static_cast<size_t>(i)] = static_cast<uint8_t>(xi);
    }
    env->ReleaseIntArrayElements(xArray, xj, JNI_ABORT);

    std::vector<const uint8_t*> sharePtrs(static_cast<size_t>(threshold));
    std::vector<std::vector<uint8_t>> shareData(static_cast<size_t>(threshold));
    for (jsize i = 0; i < n; ++i) {
        jobject el = env->GetObjectArrayElement(sharesArray, i);
        jbyteArray arr = static_cast<jbyteArray>(el);
        if (arr == nullptr) {
            throw_illegal_arg(env, "share entry is null");
            return nullptr;
        }
        const jsize slen = env->GetArrayLength(arr);
        if (slen != shareLength) {
            env->DeleteLocalRef(arr);
            throw_illegal_arg(env, "all shares must have the same length");
            return nullptr;
        }
        const size_t idx = static_cast<size_t>(i);
        shareData[idx].resize(static_cast<size_t>(slen));
        env->GetByteArrayRegion(arr, 0, slen,
                                reinterpret_cast<jbyte*>(shareData[idx].data()));
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(arr);
            return nullptr;
        }
        sharePtrs[idx] = shareData[idx].data();
        env->DeleteLocalRef(arr);
    }

    std::vector<uint8_t> secret(static_cast<size_t>(shareLength));
    struct SecretWipe {
        std::vector<uint8_t>* v;
        ~SecretWipe() {
            if (v != nullptr && !v->empty()) {
                memzero(v->data(), v->size());
            }
        }
    } secretWipe{&secret};

    int32_t rv = recover_secret(static_cast<uint8_t>(threshold),
                                x.data(),
                                sharePtrs.data(),
                                static_cast<uint32_t>(shareLength),
                                secret.data());
    if (rv < 0) {
        throw_shamir(env, rv);
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(shareLength);
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, shareLength,
                            reinterpret_cast<const jbyte*>(secret.data()));

    return out;
}
