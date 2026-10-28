#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOL_ROOT="${UNPLUGGED_TOOLCHAIN_DIR:-$ROOT/.tooling}"
SDK_ROOT="${ANDROID_SDK_ROOT:-$TOOL_ROOT/android-sdk}"
AVD_HOME="${ANDROID_AVD_HOME:-$TOOL_ROOT/avd}"
AVD_NAME="${UNPLUGGED_AVD_NAME:-unplugged-api35}"
GPU_MODE="${UNPLUGGED_GPU_MODE:-off}"
EMULATOR_CORES="${UNPLUGGED_EMULATOR_CORES:-1}"
EMULATOR_MEMORY="${UNPLUGGED_EMULATOR_MEMORY:-1536}"
EMULATOR_SKIN="${UNPLUGGED_EMULATOR_SKIN:-720x1280}"
EMULATOR_SCALE="${UNPLUGGED_EMULATOR_SCALE:-0.75}"
EMULATOR_VSYNC_RATE="${UNPLUGGED_EMULATOR_VSYNC_RATE:-30}"
EMULATOR_QT_PLATFORM="${UNPLUGGED_QT_PLATFORM:-xcb}"
EMULATOR_NICE="${UNPLUGGED_EMULATOR_NICE:-15}"
PREVIEW_HEADLESS="${UNPLUGGED_PREVIEW_HEADLESS:-0}"
EMULATOR="$SDK_ROOT/emulator/emulator"
ADB="$SDK_ROOT/platform-tools/adb"
GRADLE="$ROOT/gradlew"
APP_PACKAGE="com.unplugged.app"
PID_FILE="$AVD_HOME/unplugged-preview.pid"
LOG_FILE="$AVD_HOME/unplugged-preview.log"
BOOT_TIMEOUT_SECONDS=180

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_AVD_HOME="$AVD_HOME"
export PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

repair_local_avd_path() {
  local avd_ini="$AVD_HOME/$AVD_NAME.ini"
  local avd_directory="$AVD_HOME/$AVD_NAME.avd"
  [[ -f "$avd_ini" && -d "$avd_directory" ]] || return 0

  local configured_path
  configured_path="$(sed -n 's/^path=//p' "$avd_ini" | head -n 1)"
  if [[ -n "$configured_path" && ! -d "$configured_path" && -f "$avd_directory/config.ini" ]]; then
    echo "[INFO] Repairing stale AVD path in $avd_ini."
    sed -i "s#^path=.*#path=$avd_directory#" "$avd_ini"
  fi
}

require_tools() {
  [[ -x "$EMULATOR" ]] || fail "Android emulator is missing at $EMULATOR; run make setup first."
  [[ -x "$ADB" ]] || fail "ADB is missing at $ADB; run make setup first."
  [[ -x "$GRADLE" ]] || fail "Gradle wrapper is missing at $GRADLE; run make setup first."
  repair_local_avd_path
  "$EMULATOR" -list-avds | grep -Fx "$AVD_NAME" >/dev/null \
    || fail "AVD '$AVD_NAME' is not installed. Run make setup first."
}

emulator_serial() {
  mapfile -t emulators < <("$ADB" devices | awk '$1 ~ /^emulator-[0-9]+$/ {print $1}')
  if [[ "${#emulators[@]}" -gt 1 ]]; then
    "$ADB" devices -l >&2
    fail "More than one emulator is connected; stop the extra emulator(s) first."
  fi
  if [[ "${#emulators[@]}" -eq 1 ]]; then
    printf '%s\n' "${emulators[0]}"
  fi
}

wait_for_boot() {
  local serial="$1"
  local started_at="$SECONDS"

  echo "Waiting for $serial to finish booting..."
  while [[ "$("$ADB" -s "$serial" get-state 2>/dev/null || true)" != "device" ]]; do
    (( SECONDS - started_at < BOOT_TIMEOUT_SECONDS )) \
      || fail "Emulator did not become ready. See $LOG_FILE."
    sleep 2
  done

  while [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    (( SECONDS - started_at < BOOT_TIMEOUT_SECONDS )) \
      || fail "Android did not finish booting. See $LOG_FILE."
    sleep 2
  done
}

start_preview() {
  require_tools
  "$ADB" start-server >/dev/null

  local serial
  serial="$(emulator_serial)"
  if [[ -z "$serial" ]]; then
    mkdir -p "$AVD_HOME"
    echo "Starting $AVD_NAME with $GPU_MODE graphics, $EMULATOR_CORES emulator core, ${EMULATOR_MEMORY}MB RAM, ${EMULATOR_SKIN} display at ${EMULATOR_VSYNC_RATE}Hz via Qt $EMULATOR_QT_PLATFORM..."
    export QT_QPA_PLATFORM="$EMULATOR_QT_PLATFORM"
    export QT_AUTO_SCREEN_SCALE_FACTOR=0
    export QT_ENABLE_HIGHDPI_SCALING=0
    local -a emulator_command=(nice -n "$EMULATOR_NICE")
    if command -v taskset >/dev/null 2>&1; then
      emulator_command+=(taskset -c 0)
    fi
    emulator_command+=(
      "$EMULATOR"
      -avd "$AVD_NAME"
      -gpu "$GPU_MODE"
      -cores "$EMULATOR_CORES"
      -memory "$EMULATOR_MEMORY"
      -skin "$EMULATOR_SKIN"
      -scale "$EMULATOR_SCALE"
      -fixed-scale
      -vsync-rate "$EMULATOR_VSYNC_RATE"
      -no-snapshot
      -no-boot-anim
      -no-audio
    )
    if [[ "$PREVIEW_HEADLESS" == "1" ]]; then
      emulator_command+=(-no-window)
    fi
    "${emulator_command[@]}" >"$LOG_FILE" 2>&1 &
    local emulator_pid=$!
    printf '%s\n' "$emulator_pid" >"$PID_FILE"
    serial=""
    for _ in {1..90}; do
      serial="$(emulator_serial)"
      [[ -n "$serial" ]] && break
      sleep 1
    done
    [[ -n "$serial" ]] || fail "Emulator did not appear in ADB. See $LOG_FILE."
  else
    echo "Using already-running emulator $serial."
  fi

  wait_for_boot "$serial"
  echo "Building and installing Sancho..."
  "$GRADLE" assembleDebug -PmockPreview=true >/dev/null
  "$ADB" -s "$serial" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
  "$ADB" -s "$serial" shell am force-stop "$APP_PACKAGE"
  "$ADB" -s "$serial" shell am start -n "$APP_PACKAGE/.MainActivity" >/dev/null
  echo "[PASS] Sancho is running on $serial."
  echo "Stop an emulator started by this helper with: make preview-stop"
  echo "Emulator log: $LOG_FILE"
}

stop_preview() {
  if [[ ! -f "$PID_FILE" ]]; then
    echo "No emulator was started by make preview."
    return 0
  fi

  local emulator_pid
  emulator_pid="$(<"$PID_FILE")"
  if [[ "$emulator_pid" =~ ^[0-9]+$ ]] && kill -0 "$emulator_pid" 2>/dev/null; then
    local command_line
    command_line="$(ps -p "$emulator_pid" -o command= 2>/dev/null || true)"
    if [[ "$command_line" == *"/emulator"* || "$command_line" == *"/emulator "* ]]; then
      local serial=""
      if [[ -x "$ADB" ]]; then
        serial="$("$ADB" devices | awk '$1 ~ /^emulator-[0-9]+$/ {print $1; exit}')"
      fi
      if [[ -n "$serial" ]]; then
        "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
      fi
      kill "$emulator_pid" 2>/dev/null || true
      echo "Stopped emulator process $emulator_pid."
    else
      echo "Refusing to stop unrelated process $emulator_pid; remove the stale PID file if needed." >&2
      exit 1
    fi
  else
    echo "Emulator process is no longer running."
  fi
  rm -f "$PID_FILE"
}

case "${1:-start}" in
  start)
    start_preview
    ;;
  stop)
    stop_preview
    ;;
  *)
    echo "Usage: $0 [start|stop]" >&2
    exit 2
    ;;
esac
