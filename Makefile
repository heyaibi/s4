# S4 — SLIP-39 mnemonic shares for BIP-39 seeds
#
# Helpful development commands. Requires the Android SDK and a JDK on PATH
# (or JAVA_HOME pointing at one, e.g. Android Studio's bundled JBR).

# --- Defaults ---
GRADLE      ?= ./gradlew
# Android Studio's bundled JBR is a reliable JDK on macOS; override with
# `make JAVA_HOME=/path/to/jdk <target>` if you use a different one.
JAVA_HOME   ?= $(shell test -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" && echo "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
SDK         ?= $(shell cat local.properties 2>/dev/null | sed -n 's/^sdk\.dir=//p' || echo "$$ANDROID_HOME")
ADB         ?= $(SDK)/platform-tools/adb
EMULATOR    ?= $(SDK)/emulator/emulator
# Newest installed build-tools (sorts last); aapt/apksigner are not on PATH.
BUILD_TOOLS ?= $(shell ls -d "$(SDK)"/build-tools/* 2>/dev/null | sort -V | tail -1)
AAPT        ?= $(BUILD_TOOLS)/aapt
APKSIGNER   ?= $(BUILD_TOOLS)/apksigner
AVD         ?= s4_dev
# Script that boots the emulator (and waits for it) when no device is connected.
EMULATOR_SCRIPT ?= tools/start-emulator.sh
APP_ID      ?= com.s4
APK         ?= app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE ?= app/build/outputs/apk/release/app-release.apk
# Target device for adb. Prefers a physical phone over a running emulator;
# override for a specific one with `make install DEVICE=<serial>`.
DEVICE      ?= $(or \
	$(shell $(ADB) devices | awk '$$2=="device" && $$1 !~ /^emulator-/ {print $$1; exit}'), \
	$(shell $(ADB) devices | awk '$$2=="device" {print $$1; exit}'))

export JAVA_HOME

.PHONY: help build release verify-release unit android-test ui-test lint install update launch verify \
        test-report clean screens screens-dark logcat emulator emulator-start emulator-stop

help:
	@echo "S4 development commands"
	@echo "  make build            assemble the debug APK"
	@echo "  make release          build the signed release APK and print its SHA-256"
	@echo "  make verify-release   build release APK and verify permissions + signature"
	@echo "  make unit             run JVM unit tests (real native code via host dylib)"
	@echo "  make android-test      run all instrumented tests on a connected device/emulator"
	@echo "  make ui-test           run only the Compose UI flow tests (SplitRestoreFlowTest)"
	@echo "  make lint              run Android lint on the debug variant"
	@echo "  make install           build + install (or update) the app on the connected phone"
	@echo "  make update            alias for install: rebuild and update the installed app"
	@echo "  make launch            install/update and launch the app"
	@echo "  make verify            full Phase-4 gate: unit + android-test + lint + build"
	@echo "  make test-report       print a compact summary of the latest unit/instrumented results"
	@echo "  make clean             delete all Gradle build outputs"
	@echo "  make screens           capture light-theme screenshots of the open app"
	@echo "  make screens-dark      capture dark-theme screenshots of the open app"
	@echo "  make logcat            stream the app's logcat output"
	@echo "  make emulator          boot the $(AVD) emulator (no-op if a device is connected)"
	@echo "  make emulator-stop     kill the running emulator"
	@echo
	@echo "Target a specific device with DEVICE=<serial> (default: physical phone)."
	@echo "Device-requiring commands boot the $(AVD) emulator automatically when none is connected."

build:
	$(GRADLE) :app:assembleDebug

release:
	$(GRADLE) :app:assembleRelease
	@echo "Release APK: $(APK_RELEASE)"
	@shasum -a 256 "$(APK_RELEASE)"

# Build the release APK, then gate it: no network permissions and a valid
# signature from the release keystore. Requires build-tools for aapt/apksigner.
verify-release: release
	@echo "== permissions =="
	@$(AAPT) dump permissions "$(APK_RELEASE)"
	@echo "== signature =="
	@$(APKSIGNER) verify --print-certs "$(APK_RELEASE)"

unit:
	$(GRADLE) :app:testDebugUnitTest

android-test: emulator
	$(GRADLE) :app:connectedDebugAndroidTest

ui-test: emulator
	$(GRADLE) :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.s4.ui.SplitRestoreFlowTest

lint:
	$(GRADLE) :app:lintDebug

# Build the debug APK and install it on the connected device. `-r` replaces an
# existing install, so this both installs a fresh app and updates an installed
# one. Prefers a physical phone over a running emulator (see DEVICE above).
install: emulator build
	@test -n "$(DEVICE)" || { echo "error: no device connected — enable USB debugging and plug in the phone"; exit 1; }
	$(ADB) -s "$(DEVICE)" install -r "$(APK)"

# Explicit alias for updating an already-installed app.
update: install

launch: install
	$(ADB) -s "$(DEVICE)" shell am start -n $(APP_ID)/.MainActivity

verify: emulator unit android-test lint build

test-report:
	@echo "== unit tests =="; grep -h "<testsuite" app/build/test-results/testDebugUnitTest/*.xml | \
	  sed -E 's/.*name="([^"]*)" tests="([0-9]*)" skipped="([0-9]*)" failures="([0-9]*)" errors="([0-9]*)".*/\1: tests=\2 failures=\4 errors=\5/'
	@echo "== instrumented tests (summary) =="; grep -h "<testsuites" app/build/outputs/androidTest-results/connected/debug/*.xml | \
	  sed -E 's/.*tests="([0-9]*)" failures="([0-9]*)" errors="([0-9]*)" skipped="([0-9]*)".*/tests=\1 failures=\2 errors=\3 skipped=\4/'

clean:
	$(GRADLE) clean

screens: emulator
	mkdir -p art/screens
	$(ADB) -s "$(DEVICE)" shell "cmd uimode night no" >/dev/null 2>&1
	$(ADB) -s "$(DEVICE)" exec-out screencap -p > art/screens/screen-light.png

screens-dark: emulator
	mkdir -p art/screens
	$(ADB) -s "$(DEVICE)" shell "cmd uimode night yes" >/dev/null 2>&1
	$(ADB) -s "$(DEVICE)" exec-out screencap -p > art/screens/screen-dark.png

logcat: emulator
	$(ADB) -s "$(DEVICE)" logcat -v time | grep --line-buffered "$(APP_ID)"

# Boot the AVD when no device is connected (physical phone or running emulator
# already takes precedence — see the DEVICE detection above and the script).
emulator:
	@test -x "$(EMULATOR_SCRIPT)" || { echo "error: $(EMULATOR_SCRIPT) missing or not executable"; exit 1; }
	@$(EMULATOR_SCRIPT) "$(ADB)" "$(EMULATOR)" "$(AVD)"

emulator-start: emulator

emulator-stop:
	$(ADB) emu kill
