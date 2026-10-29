#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOL_ROOT="${UNPLUGGED_TOOLCHAIN_DIR:-$ROOT/.tooling}"
SDK_ROOT="${ANDROID_SDK_ROOT:-$TOOL_ROOT/android-sdk}"
JAVA_ROOT="${JAVA_HOME:-$TOOL_ROOT/jdk}"
ADB="$SDK_ROOT/platform-tools/adb"
GRADLE="$ROOT/gradlew"
APP_PACKAGE="com.unplugged.app"
TEST_PACKAGE="${APP_PACKAGE}.test"
TARGET_USER_NAME="AI"
TARGET_HARDWARE="tegu"

export JAVA_HOME="$JAVA_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$JAVA_ROOT/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

require_tools() {
  [[ -x "$ADB" ]] || fail "ADB is missing at $ADB; run make setup first."
  [[ -x "$GRADLE" ]] || fail "Gradle wrapper is missing at $GRADLE; run make setup first."
}

select_target() {
  require_tools

  mapfile -t devices < <("$ADB" devices | awk '$2 == "device" {print $1}')
  if [[ "${#devices[@]}" -ne 1 ]]; then
    echo "Connected authorized devices:" >&2
    "$ADB" devices -l >&2
    fail "AI-profile operations require exactly one authorized device."
  fi
  SERIAL="${devices[0]}"

  hardware="$("$ADB" -s "$SERIAL" shell getprop ro.boot.hardware | tr -d '\r')"
  [[ "$hardware" == "$TARGET_HARDWARE" ]] || fail "Connected device hardware is '$hardware', not Pixel 9a hardware '$TARGET_HARDWARE'."

  users="$("$ADB" -s "$SERIAL" shell pm list users | tr -d '\r')"
  mapfile -t target_ids < <(awk -F'[{}:]' -v name="$TARGET_USER_NAME" '$3 == name {print $2}' <<<"$users")
  if [[ "${#target_ids[@]}" -ne 1 ]]; then
    echo "$users" >&2
    fail "Could not resolve exactly one GrapheneOS user named $TARGET_USER_NAME."
  fi
  USER_ID="${target_ids[0]}"

  current_user="$("$ADB" -s "$SERIAL" shell am get-current-user | tr -d '\r[:space:]')"
  [[ "$current_user" == "$USER_ID" ]] || fail "The active user is $current_user, but $TARGET_USER_NAME is user $USER_ID. Switch to the AI profile first."

  echo "[PASS] Pixel 9a $SERIAL; active GrapheneOS user $TARGET_USER_NAME ($USER_ID)"
}

assemble_app() {
  "$GRADLE" assembleDebug
}

assemble_tests() {
  "$GRADLE" assembleDebugAndroidTest
}

install_apk() {
  local apk="$1"
  local test_only="${2:-false}"
  if [[ "$test_only" == "true" ]]; then
    "$ADB" -s "$SERIAL" install --user "$USER_ID" -r -t "$apk"
  else
    "$ADB" -s "$SERIAL" install --user "$USER_ID" -r "$apk"
  fi
}

verify_installed_for_ai() {
  "$ADB" -s "$SERIAL" shell pm list packages --user "$USER_ID" "$APP_PACKAGE" | grep -Fx "package:$APP_PACKAGE" >/dev/null \
    || fail "$APP_PACKAGE is not installed for AI user $USER_ID."
}

prepare_instrumentation() {
  assemble_app
  assemble_tests
  install_apk "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  install_apk "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" true
  verify_installed_for_ai
}

run_instrumentation() {
  local label="$1"
  local test_classes="$2"
  shift 2
  local required_metric

  set +e
  instrumentation_output=$("$ADB" -s "$SERIAL" shell am instrument --user "$USER_ID" -w -r \
    -e class "$test_classes" \
    "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" 2>&1)
  instrumentation_status=$?
  set -e

  printf '%s\n' "$instrumentation_output"
  (( instrumentation_status == 0 )) || fail "$label command failed with exit code $instrumentation_status."
  grep -q 'FAILURES!!!' <<<"$instrumentation_output" && fail "$label reported test failures."
  grep -q '^OK (' <<<"$instrumentation_output" || fail "$label did not report a successful test summary."
  for required_metric in "$@"; do
    grep -q "$required_metric" <<<"$instrumentation_output" || fail "$label did not report $required_metric."
  done
  echo "[PASS] $label completed only for $TARGET_USER_NAME user $USER_ID"
}

case "${1:-}" in
  install)
    select_target
    assemble_app
    install_apk "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
    verify_installed_for_ai
    echo "[PASS] Installed $APP_PACKAGE only for $TARGET_USER_NAME user $USER_ID"
    ;;
  launch)
    select_target
    verify_installed_for_ai
    "$ADB" -s "$SERIAL" shell am start --user "$USER_ID" -n "$APP_PACKAGE/.MainActivity"
    ;;
  uninstall)
    select_target
    "$ADB" -s "$SERIAL" uninstall --user "$USER_ID" "$TEST_PACKAGE" >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" uninstall --user "$USER_ID" "$APP_PACKAGE"
    echo "[PASS] Removed app packages only for $TARGET_USER_NAME user $USER_ID"
    ;;
  e2e)
    select_target
    prepare_instrumentation
    run_instrumentation "Instrumentation" "com.unplugged.app.MainActivityTest,com.unplugged.app.MainActivityComposerTest"
    ;;
  benchmark)
    select_target
    prepare_instrumentation
    run_instrumentation "CPU benchmark" "com.unplugged.app.CpuBenchmarkTest" "cpu_benchmark="
    ;;
  gpu-benchmark)
    select_target
    prepare_instrumentation
    run_instrumentation "GPU benchmark" "com.unplugged.app.GpuBenchmarkTest" "gpu_benchmark="
    ;;
  vision-benchmark)
    select_target
    prepare_instrumentation
    run_instrumentation "CPU/GPU vision benchmark" \
      "com.unplugged.app.VisionCpuBenchmarkTest,com.unplugged.app.VisionGpuBenchmarkTest" \
      "vision_cpu_benchmark=" "vision_gpu_benchmark="
    ;;
  *)
    echo "Usage: $0 {install|launch|uninstall|e2e|benchmark|gpu-benchmark|vision-benchmark}" >&2
    exit 2
    ;;
esac
