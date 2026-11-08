package com.unplugged.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelHarnessTest {
    private val prompts = mapOf(
        "prompts/common.md" to "Common privacy policy.",
        "prompts/chat_vision.md" to "Use images and Markdown.",
        "prompts/voice_agent.md" to "Use concise speech-friendly text.",
    )
    private val loader = PromptLoader { path -> prompts.getValue(path) }

    @Test
    fun chatVisionProfileEnablesImagesButNotTools() {
        val harness = ModelHarnessFactory.create(HarnessRole.CHAT_VISION, loader)

        assertTrue(HarnessCapability.IMAGE in harness.capabilities)
        assertFalse(harness.toolsEnabled)
        assertTrue(harness.systemInstruction.contains("Use images and Markdown."))
        assertFalse(harness.speechFriendly)
    }

    @Test
    fun voiceProfileIsSpeechFriendlyAndTextOnly() {
        val harness = ModelHarnessFactory.create(HarnessRole.VOICE_AGENT, loader)

        assertTrue(harness.speechFriendly)
        assertTrue(HarnessCapability.TEXT in harness.capabilities)
        assertFalse(HarnessCapability.IMAGE in harness.capabilities)
        assertFalse(harness.toolsEnabled)
        assertTrue(harness.systemInstruction.contains("concise speech-friendly text."))
    }

    @Test
    fun commonAndRoleInstructionsAreCombinedInOrder() {
        val harness = ModelHarnessFactory.create(HarnessRole.CHAT_VISION, loader)

        assertTrue(
            harness.systemInstruction.indexOf("Common privacy policy.") <
                harness.systemInstruction.indexOf("Use images and Markdown."),
        )
    }
}
