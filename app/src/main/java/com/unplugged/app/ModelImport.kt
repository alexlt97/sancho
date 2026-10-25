package com.unplugged.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ImportedModel(
    val fileName: String,
    val sizeBytes: Long,
    val file: File,
)

sealed interface ModelImportResult {
    data class Success(val model: ImportedModel) : ModelImportResult

    data class Failure(val message: String) : ModelImportResult
}

interface ModelImporter {
    suspend fun importModel(source: Uri): ModelImportResult

    fun existingModel(): ImportedModel? = null
}

internal fun supportedModelFileName(rawName: String): String? {
    val fileName = rawName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()

    if (!fileName.endsWith(".litertlm", ignoreCase = true)) return null

    return fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

class FileModelImporter(
    private val contentResolver: ContentResolver,
    private val modelsDirectory: File,
) : ModelImporter {
    override fun existingModel(): ImportedModel? = modelsDirectory
        .listFiles { file -> file.isFile && file.name.endsWith(".litertlm", ignoreCase = true) }
        ?.maxByOrNull { file -> file.lastModified() }
        ?.let { file -> ImportedModel(file.name, file.length(), file) }

    override suspend fun importModel(source: Uri): ModelImportResult = withContext(Dispatchers.IO) {
        val sourceName = queryDisplayName(source) ?: source.lastPathSegment.orEmpty()
        val fileName = supportedModelFileName(sourceName)
            ?: return@withContext ModelImportResult.Failure("Choose a .litertlm model file.")

        if (!modelsDirectory.exists() && !modelsDirectory.mkdirs()) {
            return@withContext ModelImportResult.Failure("The app could not prepare private model storage.")
        }

        val temporaryFile = File(modelsDirectory, ".$fileName.tmp")
        val destinationFile = File(modelsDirectory, fileName)

        try {
            val copiedBytes = contentResolver.openInputStream(source)?.use { input ->
                temporaryFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ModelImportResult.Failure("The selected file could not be opened.")

            if (copiedBytes <= 0L) {
                return@withContext ModelImportResult.Failure("The selected model file is empty.")
            }

            moveIntoPlace(temporaryFile, destinationFile)
            ModelImportResult.Success(
                ImportedModel(
                    fileName = fileName,
                    sizeBytes = destinationFile.length(),
                    file = destinationFile,
                ),
            )
        } catch (_: Exception) {
            ModelImportResult.Failure("The model could not be imported. Check storage and try again.")
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun moveIntoPlace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
