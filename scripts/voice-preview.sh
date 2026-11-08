#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${SANCHO_VOICE_PREVIEW_PORT:-8765}"

command -v python3 >/dev/null 2>&1 || {
  echo "[FAIL] python3 is required for the voice design preview." >&2
  exit 1
}

echo "Sancho voice design preview: http://127.0.0.1:${PORT}"
echo "Mock only: no microphone, model, phone, emulator, or network is used."
echo "Press Ctrl-C to stop the preview."
exec python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$ROOT/tools/voice-preview"
