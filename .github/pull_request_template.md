## What does this PR do?

Briefly describe the change and why it is needed.

## Related issue(s)

Fixes #...

## Checklist

- [ ] `make unit` passes (91 JVM unit tests, including the official SLIP-39 vectors)
- [ ] `make lint` is clean
- [ ] `make build` assembles the debug APK
- [ ] `make android-test` passes on a device or emulator (35 instrumented tests)
- [ ] No real seed material, shares, passphrases, or keys are introduced anywhere
- [ ] No new network permissions or persistence of secret material
- [ ] Follows the repo's style and scope — see `CONTRIBUTING.md`

## Notes for reviewers

Anything the reviewer should test or watch out for.
