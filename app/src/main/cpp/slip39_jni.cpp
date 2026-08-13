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

// slip39_jni.cpp — JNI façade for the vendored bc-slip39 + bc-crypto-base.
//
// Exposes (to be consumed by the Kotlin `Slip39` facade, Phase 2):
//   nativeGenerate(threshold: Int, shareCount: Int, secret: ByteArray): Array<String>
//   nativeCombine(mnemonics: Array<String>, passphrase: String): ByteArray
//   nativePbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray
//
// Scheme constants (documented in plan.md, decisions log):
//   - Single SLIP-39 group: group_threshold = 1, groups = [{threshold=T, count=N}].
//   - SLIP-39 encryption passphrase = "" by default (the BIP-39 passphrase is a
//     separate, preserved secret and is never sharded). `nativeCombine` accepts a
//     passphrase parameter so tests can verify against the official trezor
//     vectors (which use b"TREZOR"); the app always passes "".
//   - iteration_exponent = 0 (the spec default, matching the official trezor
//     vectors: PBKDF2 round count 2500 * 2^0).
//
// Randomness: the bc-slip39 `random_generator` callback bridges to Android
// `java.security.SecureRandom` (per plan.md) via a cached global reference.
//
// Errors: negative bc-slip39 error codes (see slip39-errors.h) are surfaced as
// a typed com.s4.crypto.Slip39Exception (code + description); input
// validation errors (null/empty/invalid args) remain IllegalArgumentException.

#include <jni.h>

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <string>
#include <vector>

extern "C" {
#include "bc-slip39.h"
#include <bc-crypto-base/bc-crypto-base.h>
}

namespace {

// ---------------------------------------------------------------------------
// SecureRandom bridge (bc-slip39 random_generator callback)
// ---------------------------------------------------------------------------

struct RngContext {
    JNIEnv* env;
    jobject secure_random;  // global reference, created lazily
    jmethodID next_bytes;   // SecureRandom.nextBytes([B)V
};

void secure_random_fill(uint8_t* buf, size_t len, void* ctx) {
    if (buf != nullptr && len > 0) {
        memzero(buf, len);
    }
    RngContext* rng = static_cast<RngContext*>(ctx);
    JNIEnv* env = rng->env;
    if (env->ExceptionCheck()) return;

    if (rng->secure_random == nullptr) {
        jclass cls = env->FindClass("java/security/SecureRandom");
        if (cls == nullptr) return;
        jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
        if (ctor == nullptr) {
            env->DeleteLocalRef(cls);
            return;
        }
        rng->next_bytes = env->GetMethodID(cls, "nextBytes", "([B)V");
        jobject instance = env->NewObject(cls, ctor);
        if (instance != nullptr) {
            rng->secure_random = env->NewGlobalRef(instance);
            env->DeleteLocalRef(instance);
        }
        env->DeleteLocalRef(cls);
    }
    if (rng->secure_random == nullptr || rng->next_bytes == nullptr || env->ExceptionCheck()) return;

    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(len));
    if (bytes == nullptr || env->ExceptionCheck()) return;
    env->CallVoidMethod(rng->secure_random, rng->next_bytes, bytes);
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(bytes);
        return;
    }
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(len),
                            reinterpret_cast<jbyte*>(buf));
    env->DeleteLocalRef(bytes);
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

const char* error_description(int32_t code) {
    switch (code) {
        case ERROR_NOT_ENOUGH_MNEMONIC_WORDS: return "not enough mnemonic words";
        case ERROR_INVALID_MNEMONIC_CHECKSUM: return "invalid mnemonic checksum: one or more shares are incorrect";
        case ERROR_SECRET_TOO_SHORT:          return "secret too short";
        case ERROR_INVALID_GROUP_THRESHOLD:   return "invalid group threshold";
        case ERROR_INVALID_SINGLETON_MEMBER:  return "invalid singleton member";
        case ERROR_INSUFFICIENT_SPACE:        return "insufficient space";
        case ERROR_INVALID_SECRET_LENGTH:     return "invalid secret length";
        case ERROR_INVALID_PASSPHRASE:        return "invalid passphrase";
        case ERROR_INVALID_SHARD_SET:         return "invalid shard set: shares are not from the same wallet";
        case ERROR_EMPTY_MNEMONIC_SET:        return "empty mnemonic set";
        case ERROR_DUPLICATE_MEMBER_INDEX:    return "duplicate member index";
        case ERROR_NOT_ENOUGH_MEMBER_SHARDS:  return "not enough member shares";
        case ERROR_INVALID_MEMBER_THRESHOLD:  return "invalid member threshold";
        case ERROR_INVALID_PADDING:           return "invalid padding";
        case ERROR_NOT_ENOUGH_GROUPS:         return "not enough groups";
        case ERROR_INVALID_SHARD_BUFFER:      return "invalid shard buffer";
        default:                              return "unknown SLIP-39 error";
    }
}

void throw_slip39(JNIEnv* env, int32_t code) {
    // Typed Slip39Exception(code, message) so the Kotlin layer can map codes
    // to messages without parsing (see Slip39.kt / Slip39Exception.kt).
    jclass cls = env->FindClass("com/s4/crypto/Slip39Exception");
    if (cls == nullptr) {
        throw_illegal_arg(env, error_description(code));
        return;
    }
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;)V");
    if (ctor == nullptr) {
        env->DeleteLocalRef(cls);
        throw_illegal_arg(env, error_description(code));
        return;
    }
    jstring message = env->NewStringUTF(error_description(code));
    if (message == nullptr) {
        env->DeleteLocalRef(cls);
        return;
    }
    jobject ex = env->NewObject(cls, ctor, static_cast<jint>(code), message);
    env->DeleteLocalRef(message);
    env->DeleteLocalRef(cls);
    if (ex == nullptr) return;
    env->Throw(static_cast<jthrowable>(ex));
    env->DeleteLocalRef(ex);
}

// ---------------------------------------------------------------------------
// String conversion
// ---------------------------------------------------------------------------

// Parses a whitespace-delimited mnemonic into 10-bit words. Returns false on an
// unknown word (slip39_words_for_strings returns (uint32_t)-1).
bool words_for_string(const char* s, std::vector<uint16_t>& out) {
    uint16_t buf[64];
    const uint32_t n = slip39_words_for_strings(s, buf, 64);
    if (n == static_cast<uint32_t>(-1) || n == 0 || n > 64) return false;
    out.assign(buf, buf + n);
    return true;
}

}  // namespace

// ---------------------------------------------------------------------------
// JNI entry points
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_s4_crypto_Slip39_nativeGenerate(JNIEnv* env, jclass /*clazz*/,
                                                 jint threshold, jint shareCount,
                                                 jbyteArray secretArray) {
    if (secretArray == nullptr) {
        throw_illegal_arg(env, "secret is null");
        return nullptr;
    }
    if (threshold < 1 || shareCount < threshold || shareCount > 16) {
        throw_illegal_arg(env, "threshold/shareCount must satisfy 1 <= threshold <= shareCount <= 16");
        return nullptr;
    }
    const jsize secretLen = env->GetArrayLength(secretArray);
    if (secretLen < 16 || secretLen > 32 || (secretLen & 1) != 0) {
        throw_illegal_arg(env, "secret length must be 16..32 and even");
        return nullptr;
    }

    std::vector<uint8_t> secret(static_cast<size_t>(secretLen));
    env->GetByteArrayRegion(secretArray, 0, secretLen,
                            reinterpret_cast<jbyte*>(secret.data()));
    if (env->ExceptionCheck()) return nullptr;

    group_descriptor group;
    group.threshold = static_cast<uint8_t>(threshold);
    group.count = static_cast<uint8_t>(shareCount);
    group.passwords = nullptr;

    // bc-slip39 rejects threshold==1 with count>1 inside a single group (a
    // "singleton member" error). SLIP-39 represents 1-of-N as group_threshold=1
    // with N singleton groups, so the app never produces invalid share sets.
    const uint8_t groups_length = (threshold == 1 && shareCount > 1)
        ? static_cast<uint8_t>(shareCount) : 1;
    std::vector<group_descriptor> groups(groups_length, group);
    if (groups_length > 1) {
        for (auto& g : groups) {
            g.threshold = 1;
            g.count = 1;
        }
    }
    const group_descriptor* groups_ptr = groups.data();

    const uint32_t words_per_share =
        METADATA_LENGTH_WORDS + slip39_word_count_for_bytes(secretLen);
    const uint32_t buffer_words = words_per_share * static_cast<uint32_t>(shareCount);
    std::vector<uint16_t> mnemonics(buffer_words);
    uint32_t mnemonic_length = 0;

    // The share-word buffer is an encoding of the secret, so wipe it on every
    // exit path — matching the explicit zeroing of `secret` below.
    struct MnemonicsWipe {
        std::vector<uint16_t>* v;
        ~MnemonicsWipe() {
            if (v != nullptr && !v->empty()) {
                memset(v->data(), 0, v->size() * sizeof(uint16_t));
            }
        }
    } mnemonicsWipe{&mnemonics};

    RngContext rng{env, nullptr, nullptr};

    const int rv = slip39_generate(
        1, groups_ptr, groups_length, secret.data(), static_cast<uint32_t>(secretLen),
        "", 0, &mnemonic_length, mnemonics.data(), buffer_words, &rng,
        secure_random_fill);

    memset(secret.data(), 0, secret.size());
    if (rng.secure_random != nullptr) env->DeleteGlobalRef(rng.secure_random);

    if (env->ExceptionCheck()) {
        return nullptr;
    }

    if (rv != shareCount || mnemonic_length == 0) {
        throw_illegal_arg(env, "SLIP-39 generation failed");
        return nullptr;
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;
    jobjectArray out = env->NewObjectArray(shareCount, stringClass, nullptr);
    if (out == nullptr) return nullptr;

    for (jint i = 0; i < shareCount; ++i) {
        char* words = slip39_strings_for_words(
            mnemonics.data() + static_cast<size_t>(i) * mnemonic_length,
            mnemonic_length);
        if (words == nullptr) {
            env->DeleteLocalRef(out);
            throw_illegal_arg(env, "SLIP-39 generation failed");
            return nullptr;
        }
        jstring str = env->NewStringUTF(words);
        free(words);
        if (str == nullptr) {
            env->DeleteLocalRef(out);
            return nullptr;
        }
        env->SetObjectArrayElement(out, i, str);
        env->DeleteLocalRef(str);
    }

    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_s4_crypto_Slip39_nativeCombine(JNIEnv* env, jclass /*clazz*/,
                                                jobjectArray mnemonicArray,
                                                jstring passphraseArray) {
    if (mnemonicArray == nullptr) {
        throw_illegal_arg(env, "mnemonics is null");
        return nullptr;
    }
    const char* passphrase = (passphraseArray != nullptr)
        ? env->GetStringUTFChars(passphraseArray, nullptr) : "";
    if (passphraseArray != nullptr && passphrase == nullptr) return nullptr;
    struct PassphraseGuard {
        JNIEnv* env;
        jstring array;
        const char* cstr;
        ~PassphraseGuard() {
            if (array != nullptr) env->ReleaseStringUTFChars(array, cstr);
        }
    } passGuard{env, passphraseArray, passphrase};
    const jsize count = env->GetArrayLength(mnemonicArray);
    if (count == 0) {
        throw_illegal_arg(env, "at least one share is required");
        return nullptr;
    }

    std::vector<std::vector<uint16_t>> parsed(static_cast<size_t>(count));
    std::vector<const uint16_t*> wordPtrs(static_cast<size_t>(count));
    uint32_t wordsPerShare = 0;

    // The parsed word buffers are an encoding of the secret, so wipe them on
    // every exit path — matching the explicit zeroing of `secret` below.
    struct ParsedWipe {
        std::vector<std::vector<uint16_t>>* v;
        ~ParsedWipe() {
            if (v != nullptr) {
                for (auto& words : *v) {
                    if (!words.empty()) {
                        memset(words.data(), 0, words.size() * sizeof(uint16_t));
                    }
                }
            }
        }
    } parsedWipe{&parsed};

    for (jsize i = 0; i < count; ++i) {
        jstring str = static_cast<jstring>(env->GetObjectArrayElement(mnemonicArray, i));
        if (str == nullptr) {
            throw_illegal_arg(env, "share entry is null");
            return nullptr;
        }
        const char* utf = env->GetStringUTFChars(str, nullptr);
        if (utf == nullptr) {
            env->DeleteLocalRef(str);
            return nullptr;
        }
        std::vector<uint16_t> words;
        const bool ok = words_for_string(utf, words);
        env->ReleaseStringUTFChars(str, utf);
        env->DeleteLocalRef(str);
        if (!ok) {
            throw_illegal_arg(env, "share contains an unknown word");
            return nullptr;
        }
        if (wordsPerShare == 0) {
            wordsPerShare = static_cast<uint32_t>(words.size());
        } else if (words.size() != wordsPerShare) {
            if (!words.empty()) {
                memset(words.data(), 0, words.size() * sizeof(uint16_t));
            }
            throw_illegal_arg(env, "all shares must have the same number of words");
            return nullptr;
        }
        parsed[static_cast<size_t>(i)] = std::move(words);
        wordPtrs[static_cast<size_t>(i)] = parsed[static_cast<size_t>(i)].data();
    }

    std::vector<uint8_t> secret(32);
    const int rv = slip39_combine(
        wordPtrs.data(), wordsPerShare, static_cast<uint32_t>(count),
        passphrase, nullptr, secret.data(), static_cast<uint32_t>(secret.size()));
    if (rv < 0) {
        throw_slip39(env, rv);
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(rv);
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, rv,
                            reinterpret_cast<const jbyte*>(secret.data()));

    memset(secret.data(), 0, secret.size());
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_s4_crypto_Slip39_nativePbkdf2Sha512(JNIEnv* env, jclass /*clazz*/,
                                                     jbyteArray passwordArray,
                                                     jbyteArray saltArray,
                                                     jint iterations) {
    if (passwordArray == nullptr || saltArray == nullptr) {
        throw_illegal_arg(env, "password/salt must not be null");
        return nullptr;
    }
    if (iterations < 1) {
        throw_illegal_arg(env, "iterations must be >= 1");
        return nullptr;
    }
    const jsize passLen = env->GetArrayLength(passwordArray);
    const jsize saltLen = env->GetArrayLength(saltArray);
    if (passLen == 0 || saltLen == 0) {
        throw_illegal_arg(env, "password/salt must not be empty");
        return nullptr;
    }

    std::vector<uint8_t> pass(static_cast<size_t>(passLen));
    std::vector<uint8_t> salt(static_cast<size_t>(saltLen));
    env->GetByteArrayRegion(passwordArray, 0, passLen,
                            reinterpret_cast<jbyte*>(pass.data()));
    env->GetByteArrayRegion(saltArray, 0, saltLen,
                            reinterpret_cast<jbyte*>(salt.data()));
    if (env->ExceptionCheck()) return nullptr;

    uint8_t key[64];
    pbkdf2_hmac_sha512(pass.data(), passLen, salt.data(), saltLen,
                       static_cast<uint32_t>(iterations), key, 64);

    memset(pass.data(), 0, pass.size());
    memset(salt.data(), 0, salt.size());

    jbyteArray out = env->NewByteArray(64);
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, 64, reinterpret_cast<const jbyte*>(key));

    memset(key, 0, sizeof(key));
    return out;
}
