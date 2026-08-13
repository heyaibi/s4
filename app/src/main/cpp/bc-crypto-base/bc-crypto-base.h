//
//  bc-crypto-base.h
//
//  Copyright © 2020 by Blockchain Commons, LLC
//  Licensed under the "BSD-2-Clause Plus Patent License"
//
//  Trimmed umbrella header for the subset of bc-crypto-base vendored by S4:
//  the primitives used by bc-shamir and bc-slip39 (memzero, SHA-256/512,
//  HMAC-SHA256, PBKDF2-HMAC-SHA256/SHA512).
//

#ifndef BC_CRYPTO_BASE_H
#define BC_CRYPTO_BASE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "memzero.h"
#include "sha2.h"
#include "hmac.h"
#include "pbkdf2.h"

#ifdef __cplusplus
}
#endif

#endif // BC_CRYPTO_BASE_H
