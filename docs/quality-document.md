# Quality document

Scores below describe the current terminal-first Android foundation. The first real inference check has now passed on the authorized Pixel 9a AI profile.

| Module | Correctness | Architecture | Tests | Verification | Notes |
| --- | --- | --- | --- | --- | --- |
| Repository harness | B | B | B | B | `make check` and `make clean-check` are the baseline; `make preview` provides a resource-limited managed visual emulator loop without targeting the Pixel 9a. |
| Android app | B | B | B | B | Compose shell, Sancho launcher icon, dark galaxy theme, compact icon composer, attachment menu, Markdown and inline-math response rendering, response timing, preview-only deterministic mock streaming responses, model-picker UI, native image picker/camera flow, initialized LiteRT-LM vision executor, JVM tests, APK safety preflight, and focused emulator UI tests pass; real text and image inference are manually verified. |
| Model import/storage | B | B | B | B | Validates `.litertlm` names, streams into private app storage, and replaces files through a temporary path; the imported Gemma model is loaded from AI-profile private storage. |
| Local LLM adapter | B | B | B | B | LiteRT-LM 0.18.0 CPU default plus verified GPU benchmark backend, CPU/GPU vision backend configuration, native image-file requests, background loading, single-thread native-resource ownership, text streaming, control-token cleanup, fake-LLM tests, real Pixel 9a text and image responses, and CPU/GPU timing output. |
