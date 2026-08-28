# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- PIN protection: mandatory 6-digit PIN, confirmed twice, required on first launch and via `Settings` gear → `Change PIN`. `AuthPinScreen` setup/unlock, `PinVerifyDialog` for change, `SettingsScreen`.
- Secure verifier: PBKDF2-HMAC-SHA256 120k + 16B salt, `PinManager` constant-time `MessageDigest.isEqual`, `PinStore` single-record `v1:salt:iter:algo:hash` (legacy `pin_hash`/`pin_salt` read for upgrade), refusal on empty/invalid, anti-downgrade, `ProtectedPrefsStore` `enc:iv:cipher:hmac` AAD-bound under Android Keystore `S4MasterKey`/`S4HmacKey` with `KeystoreKeyRecovery`, `MonotonicClock` (`elapsedRealtime` + anchor, survives reboot/rollback), `PinRepository` facade.
- Lockout: `PinLockoutPolicy` 5→30s doubling to 24h cap (overflow-safe), `PinStore` synchronized `lockoutLock`, `getPinLockoutRemainingMs()` monotonic, `LifecycleEventObserver` ON_STOP re-lock when PIN is set (always, since PIN is mandatory).
- Screenshots: `ScreenshotCaptureTest` parks `pin-setup`/`pin-unlock`/`settings`/`pin-manage` (light+dark) via `adb emu screenrecord` (FLAG_SECURE-safe), `Makefile` `screens`/`screens-dark` now 12 parks each.
- Tests: 111 new JVM tests (port of Airgate) — `PinManagerTest` 26, `PinStoreTest` 44, `MonotonicClockTest` 12, `PinLockoutPolicyTest` 11, `PinGateTest` 6, `AuthPinSubmitDecisionTest` 12 (total now 202) + `PinFlowInstrumentedTest` (setup/unlock/lockout/Settings).

### Changed
- `MainActivity` gates `NavHost` behind mandatory `AuthPinScreen` (setup if no PIN, unlock if PIN `isUnlocked=false` on launch), bottom bar hidden on `SETTINGS`/`PIN_MANAGE`, `FLAG_SECURE` comment restored.
- `S4HeaderBar` now exposes `settingsButton` gear → `Routes.SETTINGS`.
- `README.md` / `user-guide.md` / `about.md` refactored for PIN (threat model, What it does, Security model, Project layout, Testing, screenshots).

## [0.1.0] - 2026-08-13

### Added
- Split a 12/15/18/21/24-word BIP-39 mnemonic (or raw entropy hex) into 2–16 SLIP-39 shares with a chosen restore threshold (default 6 shares, any 3) and an optional BIP-39 passphrase.
- Restore the exact original seed from any T of N shares, with SLIP-39 word suggestions while typing, a SHA-256(seed) verification fingerprint, and clear errors for too few shares, unknown words, corrupted checksums, and mismatched wallets.
- A hand-written-copyable Recovery Guide that names only frozen standards and long-lived tools (iancoleman.io/slip39, iancoleman.io/bip39, python-mnemonic, a Trezor device), so a beneficiary can rebuild the wallet decades later with no app and no domain knowledge.
- Fingerprint verification at both split and restore time, so a wrong word or passphrase is caught before any funds are touched.
- Share lengths matched to the secret: 256-bit entropy produces 33-word shares, and smaller secrets produce 20/23/27/30-word shares.
- Full-screen results page that never auto-dismisses, with shares kept stable while it is open.
- Light and dark themes.

### Security
- Fully offline: no `INTERNET` permission, and nothing is uploaded, sent, or logged.
- Nothing persisted: secrets live in memory only (`plain remember`, not `rememberSaveable`), `android:allowBackup="false"` blocks cloud backups, and secrets never reach saved state or disk.
- `FLAG_SECURE` set on every screen, blocking screenshots, recents snapshots, and screen-mirroring capture.
- Copying the full share set or a session-backed guide requires an explicit risk-confirmation dialog, because the clipboard is readable by other apps and persists after S4 closes.
- The BIP-39 passphrase is deliberately never sharded: sharding a low-entropy passphrase would let T−1 shareholders brute-force it offline, so it stays a single secret documented in the Recovery Guide.
- Native crypto is vendored C from Blockchain Commons (bc-slip39, bc-shamir, bc-crypto-base, BSD-2-Clause-Patent) with no OpenSSL, exposed to Kotlin through thin, auditable JNI facades.
- 256-bit secrets sharded to match the SSKR round-trip: the BIP-39 checksum is recomputed on restore, never stored, and SLIP-39's checksum oracle bounds brute-force at 2⁻³² false positives.
