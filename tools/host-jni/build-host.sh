#!/usr/bin/env bash
#
# Builds a host (macOS) dylib of slip39_jni from the vendored sources so that
# JVM unit tests (`testDebugUnitTest`) can exercise the real native SLIP-39
# code path. The dylib is compiled against a host JNI header set — the running
# JDK's own `include/jni.h` when available, otherwise the Android NDK's
# sysroot copy — and links no Android runtime.
#
# Usage: build-host.sh <output-dir>
#   Produces <output-dir>/libslip39_jni.dylib for the current host arch.

set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "host-jni dylib build is macOS-only; skipping." >&2
    exit 0
fi

OUT_DIR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CPP="$REPO_ROOT/app/src/main/cpp"
SRC="$CPP/bc-slip39"
BS="$CPP/bc-shamir"
BC="$CPP/bc-crypto-base"

ARCH="$(uname -m)"
JNI_INCLUDES=()

# 1) Prefer the running JDK's own JNI headers — the correct headers for a
#    JVM host dylib, present on every CI runner and dev machine.
for jh in "${JAVA_HOME:-}" "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [[ -n "$jh" && -f "$jh/include/jni.h" ]]; then
        JNI_INCLUDES=(-I"$jh/include" -I"$jh/include/darwin")
        break
    fi
done

# 2) Fall back to the Android NDK's sysroot copy of jni.h.
if [[ ${#JNI_INCLUDES[@]} -eq 0 ]]; then
    NDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/29.0.14206865"
    for pre in "darwin-${ARCH}" darwin-x86_64; do
        if [[ -f "$NDK/toolchains/llvm/prebuilt/$pre/sysroot/usr/include/jni.h" ]]; then
            JNI_INCLUDES=(-I"$NDK/toolchains/llvm/prebuilt/$pre/sysroot/usr/include")
            break
        fi
    done
fi

if [[ ${#JNI_INCLUDES[@]} -eq 0 ]]; then
    echo "error: no JNI header found (set JAVA_HOME, or install NDK 29.0.14206865 with ANDROID_HOME set)" >&2
    exit 1
fi

mkdir -p "$OUT_DIR/obj"

OBJ=()
for c in "$SRC"/encoding.c "$SRC"/encrypt.c "$SRC"/mnemonics.c "$SRC"/rs1024.c "$SRC"/util.c \
         "$BS"/shamir.c "$BS"/hazmat.c "$BS"/interpolate.c \
         "$BC"/sha2.c "$BC"/hmac.c "$BC"/memzero.c "$BC"/pbkdf2.c; do
    name="$(basename "$c" .c)"
    clang -O2 -arch "$ARCH" -std=c99 -fPIC \
        "${JNI_INCLUDES[@]}" -I"$CPP" -I"$SRC" -I"$BC" \
        -c "$c" -o "$OUT_DIR/obj/$name.o"
    OBJ+=("$OUT_DIR/obj/$name.o")
done

clang++ -O2 -arch "$ARCH" -std=c++17 -fPIC \
    "${JNI_INCLUDES[@]}" -I"$CPP" -I"$SRC" -I"$BC" \
    -c "$CPP/slip39_jni.cpp" -o "$OUT_DIR/obj/slip39_jni.o"

clang++ -arch "$ARCH" -dynamiclib -o "$OUT_DIR/libslip39_jni.dylib" \
    "${OBJ[@]}" "$OUT_DIR/obj/slip39_jni.o"

echo "built $OUT_DIR/libslip39_jni.dylib ($ARCH)"
