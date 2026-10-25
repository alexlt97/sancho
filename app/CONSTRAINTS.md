# App constraints

- UI communicates with chat state through `ChatViewModel` and `ChatUiState`.
- The local model must be introduced behind a `LocalLlm` interface; composables must not call LiteRT-LM directly.
- The chat-only milestone has no network permission and no phone-action permissions.
- Real model files and device inference belong to device/integration tests, not JVM unit tests.
