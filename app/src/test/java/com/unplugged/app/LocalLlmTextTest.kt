package com.unplugged.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLlmTextTest {
    @Test
    fun removesConversationControlTokens() {
        assertEquals(
            "Hello!",
            "<|turn|><|model|>Hello!<|end_of_turn|>".cleanModelText(),
        )
    }
}
