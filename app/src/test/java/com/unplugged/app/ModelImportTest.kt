package com.unplugged.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelImportTest {
    @Test
    fun acceptsLiteRtModelAndSanitizesName() {
        assertEquals(
            "gemma-4-E2B-it.litertlm",
            supportedModelFileName("Downloads/gemma-4-E2B-it.litertlm"),
        )
        assertEquals(
            "gemma_4_E2B_it.litertlm",
            supportedModelFileName("gemma 4 E2B it.litertlm"),
        )
    }

    @Test
    fun rejectsOtherModelFormats() {
        assertNull(supportedModelFileName("gemma-4-E2B-it.gguf"))
        assertNull(supportedModelFileName("notes.txt"))
    }
}
