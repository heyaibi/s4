#!/usr/bin/env bash
# STRICT ENFORCEMENT: This script requires Bash.
if [ -z "$BASH_VERSION" ]; then
    echo "❌ Error: This script must be run using Bash, not sh or zsh." >&2
    echo "   Please run it as: bash $0" >&2
    exit 1
fi

JSON_FILE="apps.json"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

# Defensive command runner
run_defensive() {
    echo "⚙️  Running: $*"
    if ! output=$("$@" </dev/null 2>&1); then
        echo "   ⚠️  Warning: Command failed or returned non-zero (continuing anyway):"
        echo "   $output"
    fi
}

# ---------------------------------------------------------
# 1. PRE-FLIGHT CHECKS
# ---------------------------------------------------------
echo "📱 Checking ADB connection..."
if ! adb get-state 1>/dev/null 2>&1; then
    echo "❌ Error: No ADB device detected or unauthorized." >&2
    exit 1
fi

echo "🔍 Validating JSON file..."
if ! jq empty "$JSON_FILE" 2>/dev/null; then
    echo "❌ Error: $JSON_FILE is invalid JSON or empty." >&2
    exit 1
fi

# ---------------------------------------------------------
# 2. DOWNLOAD & CHECKSUM VERIFICATION
# ---------------------------------------------------------
echo "📦 Processing APKs..."
RESULT_PKGS=()
RESULT_STATUSES=()
PROTECTED_PACKAGES=()
INDEX=0
ANY_FAILURE=0

while IFS=$'\t' read -r pkg url checksum; do
    [[ -z "$url" || "$pkg" == "null" ]] && continue

    PROTECTED_PACKAGES+=("$pkg")

    echo ""
    echo "🔍 Checking if $pkg is already installed..."
    if adb shell pm path "$pkg" </dev/null 2>/dev/null | grep -q "package:"; then
        echo "✅ $pkg is already installed. Skipping download."
        RESULT_PKGS+=("$pkg")
        RESULT_STATUSES+=("✅ already installed")
        continue
    fi

    INDEX=$((INDEX + 1))
    filename=$(printf "apk_%03d.apk" "$INDEX")
    filepath="$TMP_DIR/$filename"

    echo "⬇️  [$INDEX] Downloading $pkg..."
    if ! curl -fL --retry 3 --retry-all-errors --retry-delay 2 -o "$filepath" "$url" </dev/null; then
        echo "   ⚠️  Download failed. Continuing with next app."
        RESULT_PKGS+=("$pkg")
        RESULT_STATUSES+=("❌ download failed")
        ANY_FAILURE=1
        continue
    fi

    expected_hash="${checksum#*:}"
    actual_hash=$(shasum -a 256 "$filepath" | awk '{print $1}')

    if [ "$expected_hash" != "$actual_hash" ]; then
        echo "   ⚠️  CHECKSUM MISMATCH for $pkg!" >&2
        echo "   Expected: $expected_hash" >&2
        echo "   Actual:   $actual_hash" >&2
        RESULT_PKGS+=("$pkg")
        RESULT_STATUSES+=("❌ checksum mismatch")
        ANY_FAILURE=1
        continue
    fi

    echo "✅ Checksum verified."
    RESULT_PKGS+=("$pkg")
    RESULT_STATUSES+=("✅ installed")

    echo "🚀 Installing $pkg..."
    if ! INSTALL_OUTPUT=$(adb install -r -d -g -t "$filepath" </dev/null 2>&1); then
        echo "   ⚠️  Installation failed for $pkg:"
        echo "   $INSTALL_OUTPUT" | sed 's/^/   /'
        RESULT_STATUSES[${#RESULT_STATUSES[@]}-1]="❌ install failed"
        ANY_FAILURE=1
        continue
    fi
    echo "   ✅ Installed $pkg."

done < <(jq -r '.[] | "\(.pkg)\t\(.url)\t\(.checksum)"' "$JSON_FILE")

echo ""
echo "📋 INSTALL SUMMARY"
echo "   ---------------------"
for i in "${!RESULT_PKGS[@]}"; do
    echo "   ${RESULT_STATUSES[$i]} — ${RESULT_PKGS[$i]}"
done
echo "   ---------------------"

if [ ${#PROTECTED_PACKAGES[@]} -eq 0 ]; then
    echo "❌ Error: No valid packages found in $JSON_FILE." >&2
    exit 1
fi

# ---------------------------------------------------------
# 4. DHIZUKU DEVICE OWNER (IDEMPOTENT FIX)
# ---------------------------------------------------------
echo ""
echo "👑 Setting Dhizuku as Device Owner..."
DPM_OUTPUT=$(adb shell dpm set-device-owner com.rosan.dhizuku/.server.DhizukuDAReceiver 2>&1)
DPM_EXIT=$?

if [ $DPM_EXIT -ne 0 ]; then
    if echo "$DPM_OUTPUT" | grep -qi "already"; then
        echo "✅ Dhizuku is already the Device Owner. (Skipped)"
    else
        echo "❌ Failed to set Device Owner." >&2
        echo "$DPM_OUTPUT" >&2
        exit 1
    fi
else
    echo "✅ Dhizuku set as Device Owner successfully."
fi

# ---------------------------------------------------------
# 5. BULK FORCE-STOP & DISABLE PACKAGES
# ---------------------------------------------------------
echo ""
echo "🚫 Processing bulk package removal lists..."

DO_NOT_DISABLE=(
    "android"
    "com.android.systemui"
    "com.android.settings"
    "com.android.phone"
    "com.android.shell"
    "com.android.packageinstaller"
    "com.android.ext.services"
    "com.android.se"
    "com.android.providers.settings"
    "com.android.providers.telephony"
    "com.android.server.telecom"
    "com.android.providers.media"
    "com.android.providers.contacts"
    "com.android.providers.calendar"
    "com.android.providers.downloads"
    "com.android.externalstorage"
    "com.android.intentresolver"
)

BULK_RESULT_PKGS=()
BULK_RESULT_STATUSES=()
UNINSTALLED_COUNT=0
DISABLED_COUNT=0
SKIPPED_COUNT=0
ALREADY_COUNT=0
DUP_COUNT=0

# Snapshot current package state (cheap, single round-trip each)
echo "   📋 Snapshotting current package state..."
adb shell pm list packages --user 0 2>/dev/null | sed 's/^package://' | sort -u > "$TMP_DIR/user0_installed.txt"
adb shell pm list packages -d 2>/dev/null | sed 's/^package://' | sort -u > "$TMP_DIR/user0_disabled.txt"

# ---------------------------------------------------------
# process_pkg_list <file> <mode>
#   mode = "uninstall" -> try `pm uninstall --user 0` first, fall back to disable
#   mode = "disable"   -> force-stop + `pm disable-user --user 0` only
# ---------------------------------------------------------
process_pkg_list() {
    local list_file="$1"
    local mode="$2"

    if [ ! -f "$list_file" ]; then
        echo "   ⚠️  $list_file not found — skipping."
        return 0
    fi

    while IFS= read -r raw_pkg; do
        # Strip invisible Windows/Mac carriage returns
        raw_pkg="${raw_pkg%$'\r'}"

        # Strip "package:" prefix if you left them in the text file
        pkg="${raw_pkg#package:}"

        # Regex: If it's empty or contains spaces (corrupted line), skip it
        if [[ ! "$pkg" =~ ^[a-z0-9._]+$ ]]; then
            continue
        fi

        # Dedupe across all lists (uninstall.txt takes precedence over packages.txt)
        if grep -qxF "$pkg" "$TMP_DIR/seen_pkgs.txt" 2>/dev/null; then
            BULK_RESULT_PKGS+=("$pkg")
            if [ "$mode" = "disable" ] && grep -qxF "$pkg" "$TMP_DIR/uninstall_handled.txt" 2>/dev/null; then
                echo "   ✅ Already handled by uninstall.txt: $pkg"
                BULK_RESULT_STATUSES+=("✅ already handled (uninstall.txt)")
            else
                echo "   🛑 DUPLICATE (skip): $pkg"
                BULK_RESULT_STATUSES+=("🛑 duplicate entry")
                DUP_COUNT=$((DUP_COUNT + 1))
            fi
            continue
        fi
        echo "$pkg" >> "$TMP_DIR/seen_pkgs.txt"

        skip=false
        for forbidden in "${DO_NOT_DISABLE[@]}" "${PROTECTED_PACKAGES[@]}"; do
            if [[ "$pkg" == "$forbidden" ]]; then
                echo "   🛑 SKIPPED (Protected/Critical): $pkg"
                skip=true
                break
            fi
        done

        if [[ "$skip" == true ]]; then
            BULK_RESULT_PKGS+=("$pkg")
            BULK_RESULT_STATUSES+=("🛑 skipped (protected/critical)")
            SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
            continue
        fi

        # STATE CHECK 1: not installed for user 0 (already removed, or never present)
        if ! grep -qx "$pkg" "$TMP_DIR/user0_installed.txt"; then
            echo "   ✅ Already removed (not installed for user 0): $pkg"
            BULK_RESULT_PKGS+=("$pkg")
            BULK_RESULT_STATUSES+=("✅ already removed")
            ALREADY_COUNT=$((ALREADY_COUNT + 1))
            continue
        fi

        # STATE CHECK 2: already disabled for user 0
        if grep -qx "$pkg" "$TMP_DIR/user0_disabled.txt"; then
            echo "   ✅ Already disabled: $pkg"
            BULK_RESULT_PKGS+=("$pkg")
            BULK_RESULT_STATUSES+=("✅ already disabled")
            ALREADY_COUNT=$((ALREADY_COUNT + 1))
            continue
        fi

        if [ "$mode" = "uninstall" ]; then
            echo "   ⏱ [$mode] $pkg"

            # STEP 1: Attempt user-level uninstall first
            UNINSTALL_OUTPUT=$(adb shell pm uninstall --user 0 "$pkg" </dev/null 2>&1)
            UNINSTALL_EXIT=$?
            if [ $UNINSTALL_EXIT -eq 0 ] && echo "$UNINSTALL_OUTPUT" | grep -qi "success"; then
                echo "   ✅ Uninstalled (user 0): $pkg"
                BULK_RESULT_PKGS+=("$pkg")
                BULK_RESULT_STATUSES+=("✅ uninstalled (user 0)")
                UNINSTALLED_COUNT=$((UNINSTALLED_COUNT + 1))
                continue
            fi
            echo "   ⚠️  Uninstall failed — falling back to force-stop + disable: $pkg"
        else
            echo "   ⏱ [$mode] $pkg"
        fi

        # KILL IT FIRST
        adb shell am force-stop "$pkg" </dev/null 2>/dev/null

        # THEN DISABLE IT
        if DISABLE_OUTPUT=$(adb shell pm disable-user --user 0 "$pkg" </dev/null 2>&1) && echo "$DISABLE_OUTPUT" | grep -qi "new state"; then
            echo "   ✅ Disabled: $pkg"
            BULK_RESULT_PKGS+=("$pkg")
            BULK_RESULT_STATUSES+=("✅ disabled")
            DISABLED_COUNT=$((DISABLED_COUNT + 1))
        else
            echo "   ⚠️  Disable failed for $pkg:"
            echo "   $DISABLE_OUTPUT" | sed 's/^/   /'
            BULK_RESULT_PKGS+=("$pkg")
            BULK_RESULT_STATUSES+=("❌ disable failed")
        fi

    done < "$list_file"
}

echo "   ═══════════════════════════════════════════"
echo "   📦 uninstall.txt (attempt uninstall, else disable)"
echo "   ═══════════════════════════════════════════"
process_pkg_list "uninstall.txt" "uninstall"

echo "   ═══════════════════════════════════════════"
echo "   📦 packages.txt (force-stop + disable only)"
echo "   ═══════════════════════════════════════════"
process_pkg_list "packages.txt" "disable"

echo ""
echo "📋 PACKAGE REMOVAL SUMMARY"
echo "   ---------------------"
for i in "${!BULK_RESULT_PKGS[@]}"; do
    echo "   ${BULK_RESULT_STATUSES[$i]} — ${BULK_RESULT_PKGS[$i]}"
done
echo "   ---------------------"
echo "   Totals — Removed this run: $UNINSTALLED_COUNT | Disabled this run: $DISABLED_COUNT | Already handled: $ALREADY_COUNT | Duplicates: $DUP_COUNT | Skipped (protected): $SKIPPED_COUNT"

# ---------------------------------------------------------
# 6. KILL RADIOS & TWEAK SETTINGS
# ---------------------------------------------------------
echo ""
echo "📡 Disabling radios and adjusting settings..."
run_defensive adb shell svc wifi disable
run_defensive adb shell svc bluetooth disable
run_defensive adb shell svc data disable
run_defensive adb shell svc nfc disable

# Trigger actual Airplane Mode
run_defensive adb shell cmd connectivity airplane-mode enable

# Disable Background Scanning
run_defensive adb shell settings put global wifi_scan_always_enabled 0
run_defensive adb shell settings put global ble_scan_always_enabled 0

echo ""
if [ "$ANY_FAILURE" -eq 1 ]; then
    echo "⚠️  Completed with errors — see the install summary above."
    exit 1
fi
echo "🎉 All tasks completed successfully!"