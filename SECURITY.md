# Security Policy

S4 handles crypto seed material and is built to be air-gapped. Report vulnerabilities privately — never in a public issue.

## Reporting a vulnerability

Do **not** open a public issue for a security problem, and never include real seed phrases, shares, passphrases, fingerprints, or keys anywhere public. Report privately via the repository's **Security tab → "Report a vulnerability"** button (GitHub's private vulnerability reporting) and include:

- Affected component (`app/`, the vendored native crypto, or the `airgap/` scripts) and version.
- Android version and device model.
- A minimal reproduction, with any test data clearly marked as synthetic.
- Impact, and a suggested fix if you have one.

This is a personal, single-maintainer project: expect an acknowledgment within 5 working days, and a fix plus advisory for confirmed issues.

## Scope

- `app/` — the Android app (Kotlin/Compose) and its JNI facades, including `data/crypto/PinManager` + `KeystoreManager`/`PrefsCrypto`, `data/repository/PinStore`/`ProtectedPrefsStore`/`PinLockoutPolicy`/`MonotonicClock`, and `ui/pin/` (`AuthPinScreen`, `PinGate`, `PinVerifyDialog`, `PinManagementScreen`, `MainActivity` PIN gate with `LifecycleEventObserver` ON_STOP re-lock).
- `app/src/main/cpp/` — vendored bc-shamir, bc-slip39, and bc-crypto-base.
- `airgap/` — the phone provisioning script and its app list.
- `tools/` — the host-JNI test harness.

Out of scope are third-party dependencies and the operating system; report those to their owners. Biometric unlock and PIN recovery are intentionally out of scope (offline, no account).

## Reporting a non-security bug

Use the issue templates in `.github/ISSUE_TEMPLATE/`, and keep in mind the app's ground rules from `CONTRIBUTING.md`: no real secret material, no network, no persistence.
