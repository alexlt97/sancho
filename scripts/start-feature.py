#!/usr/bin/env python3
"""Safely move one planned feature into the active WIP slot."""

from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2 or not sys.argv[1].strip():
        print("Usage: start-feature.py FEATURE_ID", file=sys.stderr)
        return 2

    feature_id = sys.argv[1].strip()
    path = Path(__file__).resolve().parent.parent / "feature_list.json"
    with path.open(encoding="utf-8") as stream:
        features = json.load(stream)

    selected = next((feature for feature in features if feature["id"] == feature_id), None)
    if selected is None:
        print(f"Unknown feature: {feature_id}", file=sys.stderr)
        return 2

    active = [feature["id"] for feature in features if feature["state"] == "active"]
    if selected["state"] == "passing":
        print(f"{feature_id} is already passing; activate a planned feature instead.", file=sys.stderr)
        return 2
    if selected["state"] == "active":
        print(f"[PASS] {feature_id} is already active.")
        return 0
    if active:
        print(f"WIP=1 violation: {active[0]} is already active", file=sys.stderr)
        return 2
    if selected["state"] != "planned":
        print(f"{feature_id} cannot be activated from state {selected['state']}", file=sys.stderr)
        return 2

    selected["state"] = "active"
    with path.open("w", encoding="utf-8") as stream:
        json.dump(features, stream, indent=2)
        stream.write("\n")
    print(f"[PASS] Started {feature_id}; it is now the only active feature.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
