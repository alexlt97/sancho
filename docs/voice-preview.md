# Device and voice design preview

The repository includes a dependency-free host preview shaped like the target
Pixel 9a screen. It is intended for fast UX iteration without launching an
Android emulator, loading Gemma, or connecting the phone.

From the repository root:

```bash
make voice-preview
```

Open <http://127.0.0.1:8765> in a browser and press `Ctrl-C` in the terminal
to stop the server. Set `SANCHO_VOICE_PREVIEW_PORT` to use another local port.

The preview includes interactive Chat, a concise Settings section list with
Models, Voice settings, Reports, and Chat settings detail pages, and Reports.
It opens with a suggested adaptive dark theme: flatter neutral surfaces, no
decorative stars behind chat, and a calmer semantic accent palette. Use the
`Current galaxy` and `Suggested` controls to compare both directions. It uses
the current model status, import state, composer,
attachment menu, report export feedback, and voice controls. The voice controls simulate the
1×1 widget/Quick Settings entry flow, Moonshine transcript, Gemma thinking phase,
Android TextToSpeech phase, speed control, and cancellation. It uses only local
HTML, CSS, JavaScript, and Python's standard-library HTTP server.

This is a visual and interaction harness, not Android behavior: it cannot
verify GrapheneOS system bars, exact launcher/widget sizing, Quick Settings
tinting, Moonshine model availability, model latency, or TextToSpeech voices. Those still
require a Pixel 9a smoke test.
