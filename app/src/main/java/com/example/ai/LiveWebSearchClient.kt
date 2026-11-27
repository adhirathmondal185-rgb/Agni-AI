package com.example.ai

import android.util.Log
import android.util.Xml
import com.example.model.AgniLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

private const val TAG = "AgniLiveWebSearch"

data class LiveWebItem(
    val title: String,
    val snippet: String,
    val source: String,
    val publishedDate: String,
    val url: String
)

data class LiveWebContext(
    val promptBlock: String,
    val citations: List<String>
)

/**
 * Real-time public HTTPS web search & news retriever for grounding Custom Open-Source LLMs
 * (such as Llama 3.3 / Groq / FastAPI) with live current-year facts and news.
 */
object LiveWebSearchClient {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 6_000
    private const val OVERALL_TIMEOUT_MS = 7_500L
    private const val USER_AGENT = "AgniAI-Android/1.0 (https://agni-ai.app)"

    suspend fun fetchLiveContext(
        query: String,
        language: AgniLanguage
    ): LiveWebContext = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().take(180)
        if (cleanQuery.length < 3 || isSimpleGreeting(cleanQuery)) {
            return@withContext LiveWebContext("", emptyList())
        }

        val items = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            coroutineScope {
                val newsDeferred = async { fetchGoogleNewsRss(cleanQuery, language) }
                val wikiDeferred = async { fetchWikipediaSearch(cleanQuery, language) }
                val newsItems = newsDeferred.await()
                val wikiItems = wikiDeferred.await()
                (newsItems.take(5) + wikiItems.take(3)).distinctBy { it.title.lowercase(Locale.ROOT) }
            }
        }.orEmpty()

        if (items.isEmpty()) {
            return@withContext LiveWebContext("", emptyList())
        }

        val todayStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("LIVE WEB SEARCH RESULTS (Fetched live on $todayStr):")
        items.forEachIndexed { idx, item ->
            sb.append("${idx + 1}. ${item.title}")
            if (item.source.isNotBlank()) sb.append(" [${item.source}]")
            if (item.publishedDate.isNotBlank()) sb.append(" (${item.publishedDate})")
            sb.appendLine()
            if (item.snippet.isNotBlank()) {
                sb.appendLine("   Summary: ${item.snippet}")
            }
        }
        sb.appendLine("Use these live web results when answering questions about current 2025/2026 events, news, scores, prices, leaders, or recent updates. Do NOT claim your knowledge ends in 2023 or 2024 if live results above provide up-to-date facts.")

        val citations = items.map { item ->
            val label = if (item.source.isNotBlank()) "${item.title} (${item.source})" else item.title
            if (item.url.startsWith("https://")) "$label — ${item.url}" else label
        }.take(8)

        LiveWebContext(promptBlock = sb.toString(), citations = citations)
    }

    private fun fetchGoogleNewsRss(query: String, language: AgniLanguage): List<LiveWebItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val hasBengali = query.any { it in '\u0980'..'\u09FF' }
        val hasDevanagari = query.any { it in '\u0900'..'\u097F' }

        val (hl, ceid) = when {
            hasBengali || language == AgniLanguage.BENGALI -> "bn" to "IN:bn"
            hasDevanagari || language == AgniLanguage.HINDI -> "hi" to "IN:hi"
            else -> "en-IN" to "IN:en"
        }

        val primaryUrl = "https://news.google.com/rss/search?q=$encoded&hl=$hl&gl=IN&ceid=$ceid"
        val primaryResults = parseNewsRss(httpGet(primaryUrl))
        if (primaryResults.isNotEmpty() || hl == "en-IN") {
            return primaryResults
        }
        val fallbackUrl = "https://news.google.com/rss/search?q=$encoded&hl=en-IN&gl=IN&ceid=IN:en"
        return parseNewsRss(httpGet(fallbackUrl))
    }

    private fun parseNewsRss(xmlBody: String?): List<LiveWebItem> {
        if (xmlBody.isNullOrBlank()) return emptyList()
        val results = mutableListOf<LiveWebItem>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlBody))

            var inItem = false
            var title = ""
            var link = ""
            var pubDate = ""
            var source = ""
            var currentTag = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && results.size < 6) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.orEmpty().lowercase(Locale.ROOT)
                        if (currentTag == "item") {
                            inItem = true
                            title = ""
                            link = ""
                            pubDate = ""
                            source = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val text = parser.text.orEmpty().trim()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "title" -> title += text
                                    "link" -> link += text
                                    "pubdate" -> pubDate += text
                                    "source" -> source += text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.orEmpty().lowercase(Locale.ROOT)
                        if (tag == "item" && inItem) {
                            inItem = false
                            if (title.isNotBlank()) {
                                results.add(
                                    LiveWebItem(
                                        title = title.trim(),
                                        snippet = "",
                                        source = source.trim().ifBlank { "Google News" },
                                        publishedDate = pubDate.trim().take(25),
                                        url = link.trim()
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Google News RSS", e)
        }
        return results
    }

    private fun fetchWikipediaSearch(query: String, language: AgniLanguage): List<LiveWebItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val hasBengali = query.any { it in '\u0980'..'\u09FF' }
        val wikiLang = when {
            hasBengali -> "bn"
            language == AgniLanguage.BENGALI && query.any { it.code > 127 } -> "bn"
            else -> "en"
        }
        val endpoint =
            "https://$wikiLang.wikipedia.org/w/api.php" +
                "?action=query&list=search&srsearch=$encoded&srlimit=3&format=json"

        val body = httpGet(endpoint) ?: return emptyList()
        val results = mutableListOf<LiveWebItem>()
        try {
            val root = JSONObject(body)
            val arr = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                val snippet = stripHtml(obj.optString("snippet", ""))
                val timestamp = obj.optString("timestamp", "").take(10)
                if (title.isNotBlank()) {
                    val pageUrl = "https://$wikiLang.wikipedia.org/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}"
                    results.add(
                        LiveWebItem(
                            title = title,
                            snippet = snippet.take(260),
                            source = "Wikipedia",
                            publishedDate = timestamp,
                            url = pageUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Wikipedia search JSON", e)
        }
        return results
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as? HttpsURLConnection) ?: return null
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live web fetch failed for $urlStr: ${e.message}")
            null
        }
    }

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isSimpleGreeting(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        return lower in setOf(
            "hi", "hello", "hey", "namaste", "হাই", "হ্যালো", "নমস্কার", "সালাম", "কেমন আছো", "কেমন আছেন"
        )
    }
}
