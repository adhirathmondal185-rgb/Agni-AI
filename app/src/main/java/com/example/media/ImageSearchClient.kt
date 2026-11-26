package com.example.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class SearchedImageResult(
    val id: String,
    val title: String,
    val thumbUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val author: String,
    val license: String
)

object ImageSearchClient {
    private const val USER_AGENT = "AgniAI-Android/1.0 (https://agni-ai.app)"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 18_000
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024 // 12 MB safety cap

    suspend fun searchImages(query: String, limit: Int = 24): Result<List<SearchedImageResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                require(trimmed.isNotEmpty()) { "Enter a keyword to search images." }

                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val endpoint =
                    "https://commons.wikimedia.org/w/api.php" +
                        "?action=query" +
                        "&generator=search" +
                        "&gsrnamespace=6" +
                        "&gsrlimit=${limit.coerceIn(6, 36)}" +
                        "&gsrsearch=$encoded" +
                        "&prop=imageinfo" +
                        "&iiprop=url|mime|size|extmetadata" +
                        "&iiurlwidth=640" +
                        "&format=json"

                val conn = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        throw IllegalStateException("Image search failed (HTTP $code). Please try again.")
                    }

                    val root = JSONObject(body)
                    val pages = root.optJSONObject("query")?.optJSONObject("pages")
                        ?: return@runCatching emptyList()

                    val results = mutableListOf<SearchedImageResult>()
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val page = pages.optJSONObject(key) ?: continue
                        val imageInfoArr = page.optJSONArray("imageinfo") ?: continue
                        if (imageInfoArr.length() == 0) continue
                        val info = imageInfoArr.optJSONObject(0) ?: continue

                        val mime = info.optString("mime", "").lowercase()
                        if (!mime.startsWith("image/") || mime.contains("svg") || mime.contains("tiff")) {
                            continue
                        }

                        val thumbUrl = info.optString("thumburl", "").trim()
                        val fullUrl = info.optString("url", "").trim()
                        val safeThumb = thumbUrl.ifBlank { fullUrl }
                        if (!safeThumb.startsWith("https://") || !fullUrl.startsWith("https://")) {
                            continue
                        }

                        val rawTitle = page.optString("title", "Image")
                            .removePrefix("File:")
                            .substringBeforeLast('.')
                            .replace('_', ' ')
                            .trim()
                            .ifBlank { "Image" }

                        val meta = info.optJSONObject("extmetadata")
                        val author = stripHtml(meta?.optJSONObject("Artist")?.optString("value", "").orEmpty())
                            .ifBlank { "Wikimedia Commons" }
                        val license = stripHtml(meta?.optJSONObject("LicenseShortName")?.optString("value", "").orEmpty())
                            .ifBlank { "Open License" }

                        results.add(
                            SearchedImageResult(
                                id = key,
                                title = rawTitle,
                                thumbUrl = safeThumb,
                                fullUrl = fullUrl,
                                width = info.optInt("width", 0),
                                height = info.optInt("height", 0),
                                author = author.take(60),
                                license = license.take(40)
                            )
                        )
                    }
                    results
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun downloadBitmap(imageUrl: String, maxDim: Int = 1440): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(imageUrl.trim())
                require(parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
                    "Only secure HTTPS image URLs are supported."
                }

                val conn = (URL(imageUrl.trim()).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                val bytes = try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Could not download image (HTTP $code).")
                    }
                    conn.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) {
                                throw IllegalStateException("Image exceeds 12 MB safety limit.")
                            }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    }
                } finally {
                    conn.disconnect()
                }

                decodeScaledBitmap(bytes, maxDim)
            }
        }

    private fun decodeScaledBitmap(bytes: ByteArray, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("Downloaded file is not a supported image format.")
    }

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
