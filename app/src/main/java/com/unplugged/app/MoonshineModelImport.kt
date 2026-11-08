package com.unplugged.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val moonshineCommonModelFileNames = setOf(
    "adapter.ort",
    "cross_kv.ort",
    "decoder_kv.ort",
    "encoder.ort",
    "streaming_config.json",
    "tokenizer.bin",
)

internal fun moonshineModelFiles(directory: File): Set<String>? {
    if (!directory.isDirectory || moonshineCommonModelFileNames.any { name ->
            val file = File(directory, name)
            !file.isFile || file.length() <= 0L
        }
    ) return null

    val hasCombinedFrontend = File(directory, "frontend.ort").isFile &&
        File(directory, "frontend.ort").length() > 0L
    val hasSplitFrontend = listOf("frontend.model.ort", "frontend.weights.ort").all { name ->
        val file = File(directory, name)
        file.isFile && file.length() > 0L
    }
    return when {
        hasCombinedFrontend -> moonshineCommonModelFileNames + "frontend.ort"
        hasSplitFrontend -> moonshineCommonModelFileNames +
            setOf("frontend.model.ort", "frontend.weights.ort")
        else -> null
    }
}

internal fun moonshineModelDirectory(
    modelsRoot: File,
    variant: MoonshineModelVariant,
): File = File(modelsRoot, variant.cacheDirectoryName)

internal fun isMoonshineModelInstalled(
    modelsRoot: File,
    variant: MoonshineModelVariant,
): Boolean = moonshineModelFiles(moonshineModelDirectory(modelsRoot, variant)) != null

class MoonshineModelImporter(
    context: Context,
    private val contentResolver: ContentResolver,
    private val modelsRoot: File,
) {
    private val applicationContext = context.applicationContext

    fun isInstalled(variant: MoonshineModelVariant): Boolean =
        isMoonshineModelInstalled(modelsRoot, variant)

    suspend fun importDirectory(
        treeUri: Uri,
        variant: MoonshineModelVariant,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val sourceDirectory = DocumentFile.fromTreeUri(applicationContext, treeUri)
            ?: return@withContext Result.failure(
                IllegalArgumentException("The selected folder could not be opened."),
            )
        val sourceFiles = sourceDirectory.listFiles().associateBy { it.name }
        val missingCommon = moonshineCommonModelFileNames.filter { name ->
            val file = sourceFiles[name]
            file == null || !file.isFile || file.length() <= 0L
        }
        val hasCombinedFrontend = sourceFiles["frontend.ort"]?.let { file ->
            file.isFile && file.length() > 0L
        } == true
        val hasSplitFrontend = listOf("frontend.model.ort", "frontend.weights.ort").all { name ->
            sourceFiles[name]?.let { file -> file.isFile && file.length() > 0L } == true
        }
        val frontendFiles = when {
            hasCombinedFrontend -> setOf("frontend.ort")
            hasSplitFrontend -> setOf("frontend.model.ort", "frontend.weights.ort")
            else -> emptySet()
        }
        if (missingCommon.isNotEmpty() || frontendFiles.isEmpty()) {
            val missing = missingCommon + if (frontendFiles.isEmpty()) {
                listOf("frontend.ort (or frontend.model.ort + frontend.weights.ort)")
            } else {
                emptyList()
            }
            return@withContext Result.failure(
                IllegalArgumentException(
                    "The folder must contain: ${missing.joinToString()}",
                ),
            )
        }

        val modelsDirectory = moonshineModelDirectory(modelsRoot, variant)
        if (!modelsDirectory.exists() && !modelsDirectory.mkdirs()) {
            return@withContext Result.failure(
                IllegalStateException("Sancho could not prepare private voice-model storage."),
            )
        }

        val temporaryDirectory = File(modelsDirectory, ".import-${System.nanoTime()}")
        if (!temporaryDirectory.mkdirs()) {
            return@withContext Result.failure(
                IllegalStateException("Sancho could not prepare temporary voice-model storage."),
            )
        }

        try {
            (moonshineCommonModelFileNames + frontendFiles).forEach { name ->
                val sourceFile = sourceFiles.getValue(name)
                val destinationFile = File(temporaryDirectory, name)
                contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                    destinationFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("The selected file could not be opened: $name")
                if (destinationFile.length() <= 0L) error("The selected file is empty: $name")
            }

            modelsDirectory.listFiles()?.forEach { existing ->
                if (existing.isFile) existing.delete()
            }
            temporaryDirectory.listFiles()?.forEach { imported ->
                imported.copyTo(File(modelsDirectory, imported.name), overwrite = true)
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }
}
