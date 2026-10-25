#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOL_ROOT="${UNPLUGGED_TOOLCHAIN_DIR:-$ROOT/.tooling}"
CACHE="$TOOL_ROOT/cache"
TMP="$TOOL_ROOT/tmp"
SDK_ROOT="$TOOL_ROOT/android-sdk"
GRADLE_VERSION="9.6.0"
CMDLINE_VERSION="15859902"
CMDLINE_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

mkdir -p "$CACHE" "$TMP" "$SDK_ROOT"
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v unzip >/dev/null || { echo "unzip is required" >&2; exit 1; }
command -v tar >/dev/null || { echo "tar is required" >&2; exit 1; }

download() {
  local url="$1"
  local destination="$2"
  if [[ ! -s "$destination" ]]; then
    echo "Downloading $(basename "$destination")"
    curl --fail --location --retry 3 --output "$destination" "$url"
  fi
}

jdk_archive="$CACHE/jdk17.tar.gz"
download "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" "$jdk_archive"
if [[ ! -x "$TOOL_ROOT/jdk/bin/java" ]]; then
  rm -rf "$TMP/jdk" "$TOOL_ROOT/jdk"
  mkdir -p "$TMP/jdk"
  tar -xzf "$jdk_archive" -C "$TMP/jdk"
  jdk_source="$(find "$TMP/jdk" -mindepth 1 -maxdepth 1 -type d -print -quit)"
  [[ -n "$jdk_source" ]] || { echo "Could not find extracted JDK" >&2; exit 1; }
  mv "$jdk_source" "$TOOL_ROOT/jdk"
fi

cmdline_archive="$CACHE/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
download "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip" "$cmdline_archive"
if command -v sha256sum >/dev/null; then
  actual_sha256="$(sha256sum "$cmdline_archive" | awk '{print $1}')"
  [[ "$actual_sha256" == "$CMDLINE_SHA256" ]] || {
    echo "Android command-line tools checksum mismatch" >&2
    exit 1
  }
fi
if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  rm -rf "$TMP/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$TMP/cmdline-tools" "$SDK_ROOT/cmdline-tools"
  unzip -q "$cmdline_archive" -d "$TMP/cmdline-tools"
  mv "$TMP/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

export JAVA_HOME="$TOOL_ROOT/jdk"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$JAVA_HOME/bin:$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;36.0.0"

gradle_archive="$CACHE/gradle-${GRADLE_VERSION}-bin.zip"
download "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" "$gradle_archive"
gradle_root="$TOOL_ROOT/gradle/gradle-${GRADLE_VERSION}"
if [[ ! -x "$gradle_root/bin/gradle" ]]; then
  rm -rf "$TOOL_ROOT/gradle" "$TMP/gradle"
  mkdir -p "$TOOL_ROOT/gradle" "$TMP/gradle"
  unzip -q "$gradle_archive" -d "$TOOL_ROOT/gradle"
fi

"$gradle_root/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
echo "Toolchain ready. Use make check, make build, make test, and make install."
