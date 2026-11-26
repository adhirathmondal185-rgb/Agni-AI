package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AgniPhotoFilter(val label: String) {
    ORIGINAL("Original"),
    AGNI_WARM("Agni Warm"),
    VIVID("Vivid"),
    COOL_CYAN("Cool Cyan"),
    BW("B&W"),
    VINTAGE("Vintage")
}

data class PhotoEditParams(
    val filter: AgniPhotoFilter = AgniPhotoFilter.ORIGINAL,
    val brightness: Float = 0f,      // -0.4f .. +0.4f
    val contrast: Float = 1f,        // 0.6f .. 1.6f
    val saturation: Float = 1f,      // 0.0f .. 1.8f
    val rotationDegrees: Int = 0,    // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val overlayText: String = ""
)

object AgniMediaProcessor {

    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDim: Int = 1440
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: throw IllegalStateException("Unable to open selected image.")

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalStateException("Could not decode image.")
        }
    }

    fun applyPhotoEdits(source: Bitmap, params: PhotoEditParams): Bitmap {
        val transform = Matrix().apply {
            if (params.rotationDegrees != 0) {
                postRotate(params.rotationDegrees.toFloat())
            }
            if (params.flipHorizontal) {
                postScale(-1f, 1f)
            }
        }
        val transformed = if (params.rotationDegrees != 0 || params.flipHorizontal) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
        } else {
            source
        }

        val out = Bitmap.createBitmap(
            transformed.width,
            transformed.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)

        val combinedMatrix = buildColorMatrix(params)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(combinedMatrix)
        }
        canvas.drawBitmap(transformed, 0f, 0f, paint)

        val caption = params.overlayText.trim()
        if (caption.isNotEmpty()) {
            drawStyledCaption(canvas, out.width, out.height, caption)
        }

        return out
    }

    private fun buildColorMatrix(params: PhotoEditParams): ColorMatrix {
        val matrix = ColorMatrix()

        // 1. Preset Filter
        val preset = ColorMatrix()
        when (params.filter) {
            AgniPhotoFilter.ORIGINAL -> Unit
            AgniPhotoFilter.AGNI_WARM -> preset.set(
                floatArrayOf(
                    1.18f, 0.04f, 0f, 0f, 14f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 0.86f, 0f, -8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            AgniPhotoFilter.VIVID -> {
                preset.setSaturation(1.45f)
            }
            AgniPhotoFilter.COOL_CYAN -> preset.set(
                floatArrayOf(
                    0.88f, 0f, 0f, 0f, -6f,
                    0f, 1.05f, 0.06f, 0f, 4f,
                    0f, 0.08f, 1.22f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            AgniPhotoFilter.BW -> {
                preset.setSaturation(0f)
            }
            AgniPhotoFilter.VINTAGE -> preset.set(
                floatArrayOf(
                    0.95f, 0.30f, 0.12f, 0f, 8f,
                    0.18f, 0.86f, 0.14f, 0f, 4f,
                    0.10f, 0.22f, 0.68f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        matrix.postConcat(preset)

        // 2. Saturation slider
        if (params.saturation != 1f) {
            val sat = ColorMatrix().apply { setSaturation(params.saturation.coerceIn(0f, 2f)) }
            matrix.postConcat(sat)
        }

        // 3. Contrast & Brightness
        val c = params.contrast.coerceIn(0.5f, 1.8f)
        val b = (params.brightness.coerceIn(-0.5f, 0.5f)) * 255f
        val translate = (-0.5f * c + 0.5f) * 255f + b
        val cb = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(cb)
        return matrix
    }

    private fun drawStyledCaption(canvas: Canvas, width: Int, height: Int, text: String) {
        val textSizePx = (min(width, height) * 0.055f).coerceIn(22f, 72f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 11, 18, 32)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 92, 26)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, textSizePx * 0.06f)
        }

        val maxCharsPerLine = 32
        val clipped = text.take(maxCharsPerLine)
        val bounds = Rect()
        textPaint.getTextBounds(clipped, 0, clipped.length, bounds)

        val padH = textSizePx * 0.75f
        val padV = textSizePx * 0.45f
        val centerX = width / 2f
        val bottomMargin = height * 0.06f
        val rectBottom = height - bottomMargin
        val rectTop = rectBottom - bounds.height() - padV * 2
        val pillWidth = min(width * 0.9f, bounds.width() + padH * 2)
        val rect = RectF(
            centerX - pillWidth / 2f,
            rectTop,
            centerX + pillWidth / 2f,
            rectBottom
        )
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(clipped, centerX, baseline, textPaint)
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filePrefix: String = "AgniAI_Photo"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${filePrefix}_${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AgniAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create MediaStore image entry.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) {
                            throw IllegalStateException("Failed to encode JPEG image.")
                        }
                    } ?: throw IllegalStateException("Could not open image output stream.")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AgniAI")
                dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
                }
                Uri.fromFile(file)
            }
        }
    }

    /**
     * Real on-device H.264 MP4 slideshow encoder using native MediaCodec (Surface input) + MediaMuxer.
     * Saves the resulting MP4 cleanly to Movies/AgniAI via MediaStore with zero storage permissions.
     */
    suspend fun createSlideshowVideo(
        context: Context,
        bitmaps: List<Bitmap>,
        secondsPerSlide: Int = 3,
        titleText: String = "",
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            require(bitmaps.isNotEmpty()) { "Select at least one photo to create a video." }

            val width = 720
            val height = 720
            val fps = 15
            val framesPerSlide = (secondsPerSlide.coerceIn(1, 6)) * fps
            val totalFrames = bitmaps.size * framesPerSlide

            val tempFile = File(context.cacheDir, "agni_video_${System.currentTimeMillis()}.mp4")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            var muxer: MediaMuxer? = null
            var muxerStarted = false
            var videoTrackIndex = -1

            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = encoder.createInputSurface()
                encoder.start()

                muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val bufferInfo = MediaCodec.BufferInfo()
                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                fun drainEncoder(endOfStream: Boolean) {
                    if (endOfStream) {
                        encoder.signalEndOfInputStream()
                    }
                    while (true) {
                        val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                        when {
                            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                if (!endOfStream) break
                            }
                            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (!muxerStarted) {
                                    videoTrackIndex = muxer!!.addTrack(encoder.outputFormat)
                                    muxer!!.start()
                                    muxerStarted = true
                                }
                            }
                            outputIndex >= 0 -> {
                                val encodedData = encoder.getOutputBuffer(outputIndex)
                                if (encodedData != null && bufferInfo.size > 0 &&
                                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                    muxerStarted
                                ) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer!!.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                }
                                encoder.releaseOutputBuffer(outputIndex, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    break
                                }
                            }
                        }
                    }
                }

                var frameIndex = 0
                val cleanTitle = titleText.trim()

                for (slideIdx in bitmaps.indices) {
                    val bmp = bitmaps[slideIdx]
                    for (localFrame in 0 until framesPerSlide) {
                        val progressInSlide = localFrame.toFloat() / framesPerSlide.toFloat()
                        val canvas = inputSurface.lockCanvas(null)
                        try {
                            canvas.drawColor(Color.rgb(11, 18, 32))
                            // Smooth gentle Ken Burns zoom (1.00x -> 1.07x)
                            val zoom = 1.0f + 0.07f * progressInSlide
                            drawCenterCropBitmap(canvas, bmp, width, height, zoom, drawPaint)

                            if (cleanTitle.isNotEmpty()) {
                                drawStyledCaption(canvas, width, height, cleanTitle)
                            }
                        } finally {
                            inputSurface.unlockCanvasAndPost(canvas)
                        }
                        drainEncoder(endOfStream = false)
                        frameIndex++
                        if (frameIndex % 3 == 0 || frameIndex == totalFrames) {
                            onProgress((frameIndex.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.95f))
                        }
                    }
                }

                drainEncoder(endOfStream = true)
                onProgress(1f)
            } finally {
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                if (muxerStarted) {
                    runCatching { muxer?.stop() }
                }
                runCatching { muxer?.release() }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Video encoding produced an empty file.")
            }

            val savedUri = publishVideoToGallery(context, tempFile, "AgniAI_Video")
            tempFile.delete()
            savedUri
        }
    }

    private fun drawCenterCropBitmap(
        canvas: Canvas,
        bmp: Bitmap,
        targetW: Int,
        targetH: Int,
        zoom: Float,
        paint: Paint
    ) {
        val scale = max(
            targetW.toFloat() / bmp.width.toFloat(),
            targetH.toFloat() / bmp.height.toFloat()
        ) * zoom
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (targetW - drawW) / 2f
        val top = (targetH - drawH) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), paint)
    }

    /**
     * Real on-device MP4 video trimmer using MediaExtractor + MediaMuxer (preserves video & audio).
     */
    suspend fun trimVideo(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            require(endMs > startMs + 300L) { "Selected trim clip must be at least 0.5 seconds long." }

            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            val clipDurationUs = (endUs - startUs).coerceAtLeast(1L)

            val tempFile = File(context.cacheDir, "agni_trim_${System.currentTimeMillis()}.mp4")
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            var muxerStarted = false

            try {
                extractor.setDataSource(context, sourceUri, null)
                muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                // Preserve original video rotation metadata
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, sourceUri)
                    val rotation = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                    )?.toIntOrNull()
                    if (rotation != null && (rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270)) {
                        muxer.setOrientationHint(rotation)
                    }
                } finally {
                    runCatching { retriever.release() }
                }

                val trackIndexMap = mutableMapOf<Int, Int>()
                var maxInputSize = 1024 * 1024
                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        extractor.selectTrack(i)
                        val muxerTrack = muxer.addTrack(trackFormat)
                        trackIndexMap[i] = muxerTrack
                        if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxInputSize = max(maxInputSize, trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                        }
                    }
                }

                if (trackIndexMap.isEmpty()) {
                    throw IllegalStateException("No supported video or audio tracks found in selected file.")
                }

                muxer.start()
                muxerStarted = true

                val buffer = ByteBuffer.allocate(maxInputSize)
                val info = MediaCodec.BufferInfo()
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var basePtsUs = -1L
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs > endUs) break

                    val sourceTrack = extractor.sampleTrackIndex
                    val targetTrack = trackIndexMap[sourceTrack]
                    if (targetTrack != null && sampleTimeUs >= startUs) {
                        if (basePtsUs < 0L) basePtsUs = sampleTimeUs
                        info.offset = 0
                        info.size = sampleSize
                        info.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                        info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        muxer.writeSampleData(targetTrack, buffer, info)
                        val pct = ((sampleTimeUs - startUs).toFloat() / clipDurationUs.toFloat()).coerceIn(0f, 0.95f)
                        onProgress(pct)
                    }
                    extractor.advance()
                }
                onProgress(1f)
            } finally {
                runCatching { extractor.release() }
                if (muxerStarted) {
                    runCatching { muxer?.stop() }
                }
                runCatching { muxer?.release() }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Trimmed video could not be exported.")
            }

            val savedUri = publishVideoToGallery(context, tempFile, "AgniAI_Trim")
            tempFile.delete()
            savedUri
        }
    }

    suspend fun getVideoDurationMs(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extractVideoFrame(
        context: Context,
        uri: Uri,
        timeMs: Long
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(
                    timeMs.coerceAtLeast(0L) * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: throw IllegalStateException("Could not extract frame from this video.")
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun publishVideoToGallery(context: Context, sourceFile: File, prefix: String): Uri {
        val fileName = "${prefix}_${System.currentTimeMillis()}.mp4"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AgniAI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore video entry.")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Could not write video to gallery.")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AgniAI")
            dir.mkdirs()
            val dest = File(dir, fileName)
            sourceFile.copyTo(dest, overwrite = true)
            Uri.fromFile(dest)
        }
    }
}
