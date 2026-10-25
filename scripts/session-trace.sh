#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVENT="${1:-}"
[[ "$EVENT" == "start" || "$EVENT" == "end" ]] || { echo "Usage: $0 start|end" >&2; exit 2; }
mkdir -p "$ROOT/.harness/traces"
python3 - "$ROOT/.harness/traces/traces.jsonl" "$EVENT" <<'PY'
import json
import sys
from datetime import datetime, timezone

path, event = sys.argv[1:]
record = {"event": event, "timestamp": datetime.now(timezone.utc).isoformat()}
with open(path, "a", encoding="utf-8") as stream:
    stream.write(json.dumps(record) + "\n")
print(json.dumps(record))
PY
