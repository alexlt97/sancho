#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for path in AGENTS.md PROGRESS.md DECISIONS.md feature_list.json Makefile scripts/check.sh; do
  [[ -f "$path" ]] || { echo "[FAIL] missing $path" >&2; exit 1; }
done

if find . \( -path './.git' -o -path './.tooling' \) -prune -o -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.gguf' -o -name '*.task' \) -print -quit | grep -q .; then
  echo "[FAIL] generated model or package artifact is tracked in the workspace" >&2
  exit 1
fi

python3 - feature_list.json <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    features = json.load(stream)
if any(feature["state"] == "active" for feature in features) and __import__("os").environ.get("ALLOW_ACTIVE") != "1":
    raise SystemExit("active feature remains at clean-state check")
PY
echo "Clean state passed."
