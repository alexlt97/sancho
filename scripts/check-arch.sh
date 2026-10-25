#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RULES="$ROOT/.harness/arch-rules.json"

python3 - "$RULES" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    rules = json.load(stream)
for rule in rules:
    for field in ("id", "description", "what", "why", "fix"):
        if not rule.get(field):
            raise SystemExit(f"Architecture rule missing {field}: {rule.get('id', '<unknown>')}")
print(f"[PASS] {len(rules)} architecture rule(s) are structurally valid")
PY
