#!/usr/bin/env python3
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    features = json.load(stream)
active = sum(feature["state"] == "active" for feature in features)
passing = sum(feature["state"] == "passing" for feature in features)
activated = active + passing
ratio = 1.0 if activated == 0 else passing / activated
print(f"VCR: {passing}/{activated} passing ({ratio:.1%})")
if active > 1:
    raise SystemExit("WIP=1 violation")
