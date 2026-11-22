package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_INLINE_BYTES = 20L * 1024L * 1024L // 20 MB limit
private const val MAX_IMAGE_DIM = 1600

object AttachmentLoader {

    suspend fun loadImage(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeRaw = resolver.getType(uri) ?: "image/jpeg"
                val mimeType = when (mimeRaw) {
                    "image/png", "image/webp", "image/jpeg" -> mimeRaw
                    else -> "image/jpeg"
                }
                val (fileName, reportedSize) = queryDisplayNameAndSize(context, uri, "image.jpg")
                if (reportedSize > MAX_INLINE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Image exceeds the 20 MB limit.")
                    )
                }

                // First decode bounds to downsample huge photos cleanly
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > MAX_IMAGE_DIM ||
                    boundsOpts.outHeight / sampleSize > MAX_IMAGE_DIM
                ) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap: Bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext Result.failure(
                    IllegalStateException("Could not decode the selected image.")
                )

                val effectiveSize = if (reportedSize > 0L) reportedSize else bitmap.byteCount.toLong().coerceAtMost(MAX_INLINE_BYTES)
                Result.success(
                    PendingAttachment(
                        fileName = fileName,
                        mimeType = mimeType,
                        sizeBytes = effectiveSize.coerceAtLeast(1L),
                        uri = uri,
                        bitmap = bitmap
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadPdf(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeRaw = resolver.getType(uri) ?: "application/pdf"
                if (mimeRaw != "application/pdf") {
                    return@withContext Result.failure(
                        IllegalArgumentException("Please select a valid PDF document (.pdf).")
                    )
                }
                val (fileName, reportedSize) = queryDisplayNameAndSize(context, uri, "document.pdf")
                if (reportedSize > MAX_INLINE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("PDF file exceeds the 20 MB inline analysis limit.")
                    )
                }

                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size.toLong() > MAX_INLINE_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException("PDF file exceeds the 20 MB inline analysis limit.")
                        )
                    }
                    data
                } ?: return@withContext Result.failure(
                    IllegalStateException("Could not read the selected PDF file.")
                )

                if (bytes.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Selected PDF file is empty.")
                    )
                }

                Result.success(
                    PendingAttachment(
                        fileName = fileName,
                        mimeType = "application/pdf",
                        sizeBytes = bytes.size.toLong(),
                        uri = uri,
                        pdfBytes = bytes
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun queryDisplayNameAndSize(
        context: Context,
        uri: Uri,
        fallbackName: String
    ): Pair<String, Long> {
        var name = fallbackName
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}
