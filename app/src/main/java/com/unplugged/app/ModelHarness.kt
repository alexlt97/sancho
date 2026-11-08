package com.unplugged.app

enum class HarnessRole(
    val promptAsset: String,
    val speechFriendly: Boolean,
) {
    CHAT_VISION("prompts/chat_vision.md", speechFriendly = false),
    VOICE_AGENT("prompts/voice_agent.md", speechFriendly = true),
}

enum class HarnessCapability {
    TEXT,
    IMAGE,
    TOOLS,
}

data class ModelHarness(
    val role: HarnessRole,
    val systemInstruction: String,
    val capabilities: Set<HarnessCapability>,
    val toolsEnabled: Boolean = HarnessCapability.TOOLS in capabilities,
    val speechFriendly: Boolean = role.speechFriendly,
) {
    init {
        require(systemInstruction.isNotBlank()) { "A model harness needs a system instruction." }
        require(!toolsEnabled || HarnessCapability.TOOLS in capabilities) {
            "Tools cannot be enabled without the tools capability."
        }
    }
}

fun interface PromptLoader {
    fun load(path: String): String
}

object ModelHarnessFactory {
    private const val COMMON_PROMPT = "prompts/common.md"

    fun create(role: HarnessRole, promptLoader: PromptLoader): ModelHarness {
        val systemInstruction = listOf(
            promptLoader.load(COMMON_PROMPT),
            promptLoader.load(role.promptAsset),
        ).joinToString(separator = "\n\n") { it.trim() }

        val capabilities = when (role) {
            HarnessRole.CHAT_VISION -> setOf(HarnessCapability.TEXT, HarnessCapability.IMAGE)
            HarnessRole.VOICE_AGENT -> setOf(HarnessCapability.TEXT)
        }

        return ModelHarness(
            role = role,
            systemInstruction = systemInstruction,
            capabilities = capabilities,
        )
    }

    fun defaultChatVision(): ModelHarness = ModelHarness(
        role = HarnessRole.CHAT_VISION,
        systemInstruction = "You are Sancho, an offline assistant for normal text and image chat. Do not request tools.",
        capabilities = setOf(HarnessCapability.TEXT, HarnessCapability.IMAGE),
    )

    fun defaultVoiceAgent(): ModelHarness = ModelHarness(
        role = HarnessRole.VOICE_AGENT,
        systemInstruction = "You are Sancho in voice mode. Reply briefly in speech-friendly text. Do not request tools.",
        capabilities = setOf(HarnessCapability.TEXT),
    )
}
