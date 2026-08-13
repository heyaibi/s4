cmake_minimum_required(VERSION 3.22.1)
project("s4crypto" C CXX)

# Vendored bc-shamir + the bc-crypto-base subset it needs (SHA-256/512,
# HMAC-SHA256, PBKDF2-HMAC-SHA256/SHA512, memzero). No external linkage; this is
# a self-contained static library shared by both JNI facades.
add_library(shamir STATIC
    bc-shamir/shamir.c
    bc-shamir/hazmat.c
    bc-shamir/interpolate.c
    bc-crypto-base/sha2.c
    bc-crypto-base/hmac.c
    bc-crypto-base/memzero.c
    bc-crypto-base/pbkdf2.c
)

target_include_directories(shamir PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/bc-crypto-base
)

set_target_properties(shamir PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED YES
)

# Vendored bc-slip39 (single-group scheme; see bc-slip39/PIN.txt for the pinned
# commit). Its sources include <bc-shamir/bc-shamir.h> and
# <bc-crypto-base/bc-crypto-base.h>, so the cpp root is on the include path.
add_library(slip39 STATIC
    bc-slip39/encoding.c
    bc-slip39/encrypt.c
    bc-slip39/mnemonics.c
    bc-slip39/rs1024.c
    bc-slip39/util.c
)

target_include_directories(slip39 PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/bc-slip39
)

set_target_properties(slip39 PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED YES
)

target_link_libraries(slip39 shamir)

# JNI facades. Built for arm64-v8a, armeabi-v7a, and x86_64
# (see app/build.gradle.kts abiFilters).
add_library(shamir_jni SHARED
    shamir_jni.cpp
)

target_include_directories(shamir_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/bc-shamir
    ${CMAKE_CURRENT_SOURCE_DIR}/bc-crypto-base
)

target_link_libraries(shamir_jni shamir)

add_library(slip39_jni SHARED
    slip39_jni.cpp
)

target_include_directories(slip39_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/bc-slip39
    ${CMAKE_CURRENT_SOURCE_DIR}/bc-crypto-base
)

target_link_libraries(slip39_jni slip39 shamir)
