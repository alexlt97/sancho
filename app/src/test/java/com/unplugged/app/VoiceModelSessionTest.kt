package com.unplugged.app

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceModelSessionTest {
    @Test
    fun loadsModelAndAggregatesStreamingResponse() = runTest {
        val fake = FakeLocalLlm()
        val session = VoiceModelSession(fake)
        val model = File("voice-model.litertlm")

        session.load(model)
        val response = session.generate("What did I say?")

        assertEquals(model, fake.loadedFile)
        assertEquals("Fake answer to: What did I say?", response)
    }
}
