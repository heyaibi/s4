# Contributing to S4

Thanks for your interest. S4 is a personal, security-critical tool: it handles crypto seed material and is built to run on an offline phone. Please read this before opening a PR, and follow the same discipline the app itself does.

## The ground rules

- **No secrets in the repo, ever.** No real seed phrases, shares, passphrases, fingerprints, or keys. Tests must use synthetic or publicly published vectors (like the official trezor SLIP-39 vectors already vendored in `app/src/test/resources/`).
- **No network.** The app must stay fully offline. Never add a permission that touches the network, and never persist secret material to disk or saved state.
- **Respect the threat model.** Anything that stores, copies, or displays secret material must preserve the existing guards: `FLAG_SECURE`, plain `remember` (not `rememberSaveable`) for secrets, `allowBackup="false"`, and the gated clipboard confirmation dialogs.
- **Markdown rule.** In docs, newlines are structural only — one paragraph per line, one bullet per line. Never hard-wrap prose.

## Prerequisites

- JDK 21+ (the Makefile defaults to Android Studio's bundled JBR on macOS).
- Android SDK with platform 37 and NDK `29.0.14206865` (set `ANDROID_HOME` or `local.properties`).
- macOS for `make unit` — the JVM unit tests run the real native code through a host dylib built by `tools/host-jni/build-host.sh`, which is macOS-only.

## Setup and build

```bash
./gradlew :app:assembleDebug
```

## Running the checks

`make help` lists every target. The gate that must be green before a PR merges:

| Command | What it checks |
|---|---|
| `make unit` | 91 JVM unit tests (BIP-39, SLIP-39 vectors, fingerprint, codecs, guide, word completion) against the real native code |
| `make lint` | Android lint, clean |
| `make build` | debug APK assembles (all three ABIs) |
| `make android-test` | 35 instrumented tests on a device or the `s4_dev` emulator |

`make verify` runs all four in sequence. CI mirrors them: the `checks` job (unit + lint + build) and the `instrumented` job (emulator) must both pass.

## Release build

The release APK is signed with a keystore whose credentials live in `~/.gradle/gradle.properties` (`S4_RELEASE_STORE_FILE`, `S4_RELEASE_STORE_PASSWORD`, `S4_RELEASE_KEY_ALIAS`, `S4_RELEASE_KEY_PASSWORD`) — never in the repo. Build it with `make release` (or `./gradlew :app:assembleRelease`), which produces a signed, unminified APK at `app/build/outputs/apk/release/app-release.apk` and prints its SHA-256 for the `airgap/apps.json` entry. Back the keystore up offline: Android apps are permanently bound to their signing key, so losing it makes future updates impossible.

## Code style

- Kotlin, following the existing conventions: Compose + Material 3, `MonoMeta`/`RobotoMono` for data and fingerprints, `s4TextFieldColors()` for fields, `SectionCard`/`ActionButton`/`ErrorBanner` for structure.
- Keep the JNI boundaries thin: `Slip39.kt`/`Shamir.kt` are facades, `slip39_jni.cpp`/`shamir_jni.cpp` do the native work, and typed exceptions carry native error codes.
- Name things as they are named in the domain (split, restore, share, threshold, fingerprint). No unrelated renames in the same PR.

## Testing

- Add or update unit tests for any core logic change; the suite must stay green and ideally grow.
- Native changes must pass the official SLIP-39 vectors (`app/src/test/java/com/s4/crypto/Slip39Test.kt`) on both the host dylib and the device.
- UI changes should extend `SplitRestoreFlowTest` or the instrumented crypto tests, and the error paths (too few shares, corrupted word, mismatched identifiers, wrong passphrase fingerprint) must be covered.
- Never disable or relax a test to make CI green.

## Submitting a PR

1. Work on a topic branch off `master` with a descriptive name.
2. Keep the change focused; one logical change per PR.
3. Run `make verify` locally (or the CI `checks` + `instrumented` jobs on your branch) and make sure everything passes.
4. Write a concise, imperative commit message that matches the repo style (e.g. `feat: ...`, `fix: ...`, `refactor: ...`).
5. Open the PR against `master` and summarize what changed and why, including how you verified it.
