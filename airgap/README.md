# Airgap provisioning

Files are already in this folder. From here:

```bash
adb devices     # confirm the phone is connected
./airgap.sh
```

Phone prep: Settings → About phone → Build number ×7 → enable USB debugging.

## After

- Dhizuku → Application Management → allow "Island - Mobile"
- Android AntiForensic Tools → wipe on duress password / wrong attempts
- Login password, not a PIN
- Set up the wallet in Airgap Vault
