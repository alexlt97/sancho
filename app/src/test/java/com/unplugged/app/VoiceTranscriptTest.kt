package com.unplugged.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTranscriptTest {
    @Test
    fun selectsFirstNonBlankRecognizedPhrase() {
        assertEquals("hello Sancho", firstRecognizedPhrase(listOf("  ", "hello Sancho", "second result")))
    }

    @Test
    fun returnsNullWhenRecognitionHasNoText() {
        assertNull(firstRecognizedPhrase(listOf("", "   ")))
        assertNull(firstRecognizedPhrase(null))
    }

    @Test
    fun recognizesOnlyShortVoiceControlPhrasesAsStop() {
        assertEquals(true, isVoiceStopCommand("Stop!"))
        assertEquals(true, isVoiceStopCommand("cancel Sancho"))
        assertEquals(true, isVoiceStopCommand("never   mind"))
        assertEquals(false, isVoiceStopCommand("Please stop explaining the first part"))
        assertEquals(false, isVoiceStopCommand("stop the timer"))
    }
}
