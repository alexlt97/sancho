# Decisions

## 2026-10-22 — Offline Android target

- Decision: target a Pixel 9a running GrapheneOS.
- Why: the project prioritizes local inference, privacy, and operation without network access.

## 2026-10-22 — Initial model stack

- Decision: keep Kotlin, Jetpack Compose, LiteRT-LM, and Gemma 4 E2B as the planned stack.
- Why: this preserves the intended Android-native on-device path while keeping the model behind a `LocalLlm` abstraction.

## 2026-10-22 — Product scope

- Decision: build reliable offline multi-turn chat before phone automation or tool calling.
- Why: it creates a testable foundation and avoids granting phone-action permissions before the core inference loop is reliable.

## 2026-10-22 — Audit policy

- Decision: make the audit Android/Gradle-aware instead of manufacturing unrelated Node/Python files.
- Why: dependency locking, build verification, and device checks must reflect the actual project type.

## 2026-10-22 — Terminal-first build

- Decision: keep the JDK, Android command-line tools, SDK packages, and Gradle distribution in the ignored `.tooling/` directory, provisioned by `make setup`.
- Why: Codex must be able to compile, test, install, and launch the app without Android Studio.
- Detail: AGP 9 supplies Kotlin support, so the project uses the Compose compiler plugin without the rejected Kotlin Android plugin.

## 2026-10-25 — Explicit model import

- Decision: let the user select a `.litertlm` file with Android's document picker and copy it into the AI profile's private app storage.
- Why: model binaries are user-provided, must remain outside the APK and Git, and should not require broad storage permissions or network access.
- Detail: imports are streamed through a temporary file and moved into place only after copying succeeds.

## 2026-10-25 — CPU-first LiteRT-LM runtime

- Decision: use the LiteRT-LM CPU backend as the initial runtime and keep GPU acceleration as a measured follow-up.
- Why: CPU execution is the safer first device milestone on GrapheneOS and does not require GPU-specific native-library declarations.
- Detail: the adapter owns the engine and conversation on one background executor, extracts text content from streamed messages, and removes model control tokens before display.

## 2026-10-25 — GPU remains benchmark-only

- Decision: enable LiteRT-LM GPU support for explicit benchmarking, but keep CPU as the app's normal backend for now.
- Why: the Pixel 9a completed GPU inference faster after preparation, but the first GPU load took about 53 seconds versus about 1 second for CPU. A cached GPU load took about 5.4 seconds.
- Detail: GPU support is guarded by optional `libvndksupport.so` and `libOpenCL.so` manifest declarations; `make gpu-benchmark` exercises it without changing normal chat behavior.

## 2026-10-25 — Native image input

- Decision: pass user-selected or camera-captured images directly to the Gemma conversation through LiteRT-LM image content.
- Why: Gemma 4 E2B is multimodal, and the installed LiteRT-LM API exposes `Content.ImageFile`; an OCR-only preprocessing path would discard useful visual information.
- Detail: images are copied into the AI profile's app-private cache, selected through the system Photo Picker or captured through a `FileProvider` URI, and are never added to the APK or sent over a network.

## 2026-10-25 — Mocked visual preview runtime

- Decision: make `make preview` build a deterministic mock-model variant that starts ready and streams sample responses, while normal builds keep the real LiteRT-LM runtime.
- Why: UI iteration should not require importing a multi-gigabyte model or using the Pixel 9a, and the mock must not accidentally replace real on-device inference.
- Detail: the preview-only `mockPreview` Gradle property enables `BuildConfig.MOCK_PREVIEW`; the mock exercises text, image, Markdown, inline math, and response-timing presentation paths.
