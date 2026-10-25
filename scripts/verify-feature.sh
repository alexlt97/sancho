#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FEATURE_ID="${1:-}"
[[ -n "$FEATURE_ID" ]] || { echo "Usage: $0 FEATURE_ID" >&2; exit 2; }

python3 - "$ROOT/feature_list.json" "$FEATURE_ID" <<'PY'
import json
import sys

path, feature_id = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    features = json.load(stream)
for feature in features:
    if feature["id"] == feature_id:
        if feature["state"] != "active":
            raise SystemExit(f"{feature_id} must be active before verification; current state is {feature['state']}")
        break
else:
    raise SystemExit(f"Unknown feature: {feature_id}")
PY

cd "$ROOT"

while IFS=$'\t' read -r label command repair; do
  [[ -n "$label" ]] || continue
  echo "[RUN] layer=$label command=$command"
  if ! bash -c "$command"; then
    echo "[FAIL] layer=$label" >&2
    echo "[REPAIR] $repair" >&2
    exit 1
  fi
done < <(python3 - "$ROOT/feature_list.json" "$FEATURE_ID" <<'PY'
import json
import sys

path, feature_id = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    features = json.load(stream)
for feature in features:
    if feature["id"] == feature_id:
        for layer in feature.get("layers", []):
            for field in ("label", "cmd", "repair"):
                if not layer.get(field):
                    raise SystemExit(f"{feature_id} layer missing {field}")
            print(f"{layer['label']}\t{layer['cmd']}\t{layer['repair']}")
        break
PY
)

python3 - "$ROOT/feature_list.json" "$FEATURE_ID" <<'PY'
import json
import sys
from datetime import date

path, feature_id = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    features = json.load(stream)
for feature in features:
    if feature["id"] == feature_id:
        feature["state"] = "passing"
        feature["evidence"] = f"all declared verification layers passed; verified {date.today().isoformat()}"
        break
with open(path, "w", encoding="utf-8") as stream:
    json.dump(features, stream, indent=2)
    stream.write("\n")
PY
echo "Verified $FEATURE_ID and transitioned it to passing."
