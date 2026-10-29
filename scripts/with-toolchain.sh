#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOL_ROOT="${UNPLUGGED_TOOLCHAIN_DIR:-$ROOT/.tooling}"

if [[ -x "$TOOL_ROOT/jdk/bin/java" && -z "${JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$TOOL_ROOT/jdk"
fi
if [[ -d "$TOOL_ROOT/android-sdk" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_SDK_ROOT="$TOOL_ROOT/android-sdk"
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
fi

if [[ "$#" -eq 0 ]]; then
  echo "Usage: $0 command [args...]" >&2
  exit 2
fi
exec "$@"
