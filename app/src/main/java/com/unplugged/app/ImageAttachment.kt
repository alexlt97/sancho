package com.unplugged.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageAttachment(
    val file: File,
    val displayName: String,
)

sealed interface ImageAttachmentResult {
    data class Success(val attachment: ImageAttachment) : ImageAttachmentResult

    data class Failure(val message: String) : ImageAttachmentResult
}

class FileImageAttachmentStore(
    private val contentResolver: ContentResolver,
    private val imageDirectory: File,
) {
    fun newCameraFile(): File {
        prepareDirectory()
        return File(imageDirectory, "camera-${System.currentTimeMillis()}.jpg")
    }

    suspend fun copySelectedImage(source: Uri): ImageAttachmentResult = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(source).orEmpty()
        if (!mimeType.startsWith("image/")) {
            return@withContext ImageAttachmentResult.Failure("Choose an image file.")
        }
        if (!prepareDirectory()) {
            return@withContext ImageAttachmentResult.Failure("The app could not prepare private image storage.")
        }

        val displayName = queryDisplayName(source) ?: "selected-image"
        val extension = displayName.substringAfterLast('.', "jpg")
            .replace(Regex("[^A-Za-z0-9]"), "")
            .ifEmpty { "jpg" }
        val destination = File(imageDirectory, "selected-${System.currentTimeMillis()}.$extension")
        val temporary = File(imageDirectory, ".${destination.name}.tmp")

        try {
            val copiedBytes = contentResolver.openInputStream(source)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ImageAttachmentResult.Failure("The selected image could not be opened.")

            if (copiedBytes <= 0L) {
                return@withContext ImageAttachmentResult.Failure("The selected image is empty.")
            }

            moveIntoPlace(temporary, destination)
            ImageAttachmentResult.Success(ImageAttachment(destination, displayName))
        } catch (_: Exception) {
            ImageAttachmentResult.Failure("The image could not be copied into private storage.")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun prepareDirectory(): Boolean =
        imageDirectory.exists() || imageDirectory.mkdirs()

    private fun queryDisplayName(uri: Uri): String? = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        } else {
            null
        }
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
