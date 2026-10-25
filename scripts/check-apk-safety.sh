#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APK="${APK_PATH:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE_NAME="com.unplugged.app"

if [[ -z "${APK_PATH:-}" ]]; then
  echo "[INFO] Assembling the current debug APK for inspection."
  bash scripts/with-toolchain.sh ./gradlew assembleDebug
elif [[ ! -f "$APK" ]]; then
  echo "[INFO] Configured APK is missing; assembling it for inspection."
  bash scripts/with-toolchain.sh ./gradlew assembleDebug
fi

if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_ROOT="$ANDROID_SDK_ROOT"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  SDK_ROOT="$ANDROID_HOME"
else
  SDK_ROOT="$ROOT/.tooling/android-sdk"
fi

AAPT=""
if command -v aapt >/dev/null 2>&1; then
  AAPT="$(command -v aapt)"
else
  while IFS= read -r candidate; do
    AAPT="$candidate"
  done < <(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt -perm -u+x | sort -V)
fi

if [[ -z "$AAPT" || ! -x "$AAPT" ]]; then
  echo "[FAIL] aapt is required to inspect the APK." >&2
  exit 1
fi

BADGING="$($AAPT dump badging "$APK")"
PERMISSIONS="$($AAPT dump permissions "$APK")"

if grep -Fq "package: name='$PACKAGE_NAME'" <<<"$BADGING"; then
  echo "[PASS] APK package is $PACKAGE_NAME"
else
  echo "[FAIL] APK package is not $PACKAGE_NAME" >&2
  exit 1
fi

if grep -Fq "application-label:'Sancho'" <<<"$BADGING"; then
  echo "[PASS] APK application label is Sancho"
else
  echo "[FAIL] APK application label is not Sancho" >&2
  exit 1
fi

# AndroidX may generate this app-private permission for non-exported receivers.
# No platform, network, phone, sensor, or storage permissions are allowed here.
UNEXPECTED="$(grep '^uses-permission:' <<<"$PERMISSIONS" | grep -v -F "${PACKAGE_NAME}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" || true)"
if [[ -n "$UNEXPECTED" ]]; then
  echo "[FAIL] APK declares unexpected permissions:" >&2
  echo "$UNEXPECTED" >&2
  exit 1
fi

echo "[PASS] APK declares no permissions outside its app-private AndroidX permission"
