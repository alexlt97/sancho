package com.unplugged.app

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceModelRuntimeTest {
    @Test
    fun reusesLoadedModelForTheSameFile() = runTest {
        var factoryCalls = 0
        val fake = FakeLocalLlm()
        val runtime = VoiceModelRuntime(File("cache")) {
            factoryCalls += 1
            fake
        }
        val model = File("voice-model.litertlm")

        assertEquals("Fake answer to: first", runtime.generate(model, "first"))
        assertEquals("Fake answer to: second", runtime.generate(model, "second"))
        assertEquals(1, factoryCalls)
    }
}
