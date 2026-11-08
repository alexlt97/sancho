#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

require_file() {
  if [[ -f "$1" ]]; then echo "[PASS] $1"; else echo "[FAIL] missing $1" >&2; exit 1; fi
}

require_file AGENTS.md
require_file PROGRESS.md
require_file DECISIONS.md
require_file feature_list.json
require_file Makefile
require_file docs/voice-preview.md
require_file scripts/voice-preview.sh
require_file tools/voice-preview/index.html
require_file app/src/main/assets/prompts/common.md
require_file app/src/main/assets/prompts/chat_vision.md
require_file app/src/main/assets/prompts/voice_agent.md
require_file app/src/androidTest/java/com/unplugged/app/ModelHarnessDeviceTest.kt
require_file app/src/androidTest/java/com/unplugged/app/HarnessComparisonBenchmarkTest.kt
require_file app/src/androidTest/java/com/unplugged/app/LongPlainTextBenchmarkTest.kt
require_file docs/model-harness-testing.md
require_file docs/voice-testing.md
require_file app/src/main/java/com/unplugged/app/VoiceEntryPoint.kt

bash -n scripts/voice-preview.sh
echo "[PASS] voice preview script syntax"

python3 - feature_list.json <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    features = json.load(stream)
if not isinstance(features, list):
    raise SystemExit("feature_list.json must contain an array")
for feature in features:
    required = {"id", "behavior", "verification", "state", "evidence", "layers"}
    missing = required - set(feature)
    if missing:
        raise SystemExit(f"feature {feature.get('id', '<unknown>')} missing: {sorted(missing)}")
    if feature["state"] not in {"planned", "active", "passing"}:
        raise SystemExit(f"feature {feature['id']} has invalid state")
active = [feature["id"] for feature in features if feature["state"] == "active"]
if len(active) > 1:
    raise SystemExit(f"WIP=1 violation: active features are {active}")
PY
echo "[PASS] feature_list.json schema and WIP=1"

if [[ -x ./gradlew ]]; then
  bash scripts/with-toolchain.sh ./gradlew check lint test
else
  echo "[FAIL] Gradle wrapper is missing; run make setup first." >&2
  exit 1
fi

bash scripts/with-toolchain.sh bash scripts/check-apk-safety.sh
bash scripts/check-arch.sh
echo "Harness check passed."
