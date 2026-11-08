package com.unplugged.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonshineModelImportTest {
    @Test
    fun modelDirectoryRequiresAllNonEmptyFiles() {
        val directory = Files.createTempDirectory("moonshine-model-").toFile()
        try {
            val modelDirectory = moonshineModelDirectory(
                directory,
                MoonshineModelVariant.TINY_STREAMING,
            ).apply { mkdirs() }
            (moonshineCommonModelFileNames + "frontend.ort").forEach { name ->
                File(modelDirectory, name).writeText("model")
            }
            assertTrue(isMoonshineModelInstalled(directory, MoonshineModelVariant.TINY_STREAMING))

            File(modelDirectory, "tokenizer.bin").delete()
            assertFalse(isMoonshineModelInstalled(directory, MoonshineModelVariant.TINY_STREAMING))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun variantsUseIndependentModelDirectories() {
        val directory = Files.createTempDirectory("moonshine-model-variants-").toFile()
        try {
            val tiny = moonshineModelDirectory(directory, MoonshineModelVariant.TINY_STREAMING)
            val small = moonshineModelDirectory(directory, MoonshineModelVariant.SMALL_STREAMING)
            assertTrue(tiny != small)
            assertTrue(tiny.name != small.name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unknownVariantFallsBackToTiny() {
        assertEquals(
            MoonshineModelVariant.TINY_STREAMING,
            MoonshineModelVariant.fromId("not-a-model"),
        )
        assertEquals(
            MoonshineModelVariant.SMALL_STREAMING,
            MoonshineModelVariant.fromId("small-streaming"),
        )
    }
}
