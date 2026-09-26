package com.example.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String
)

data class WebSearchResponse(
    val query: String,
    val results: List<SearchResultItem>,
    val summaryText: String
)

object WebSearchEngine {
    private const val TAG = "WebSearchEngine"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun performSearch(query: String, maxResults: Int = 5): WebSearchResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext WebSearchResponse(
                query = query,
                results = emptyList(),
                summaryText = "Qidiruv so'rovi bo'sh."
            )
        }

        val results = mutableListOf<SearchResultItem>()

        // 1. DuckDuckGo JSON API Query
        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val apiUrl = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:110.0) Gecko/110.0 Firefox/110.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotEmpty()) {
                        val json = JSONObject(bodyStr)

                        // AbstractText
                        val abstractText = json.optString("AbstractText", "")
                        val abstractUrl = json.optString("AbstractURL", "")
                        val heading = json.optString("Heading", cleanQuery)

                        if (abstractText.isNotBlank()) {
                            results.add(SearchResultItem(heading, abstractText, abstractUrl))
                        }

                        // RelatedTopics
                        val relatedTopics = json.optJSONArray("RelatedTopics")
                        if (relatedTopics != null) {
                            for (i in 0 until relatedTopics.length()) {
                                if (results.size >= maxResults) break
                                val topic = relatedTopics.optJSONObject(i) ?: continue
                                val text = topic.optString("Text", "")
                                val firstUrl = topic.optString("FirstURL", "")
                                if (text.isNotBlank()) {
                                    val title = if (text.contains(" - ")) text.substringBefore(" - ") else text.take(50)
                                    results.add(SearchResultItem(title, text, firstUrl))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo API search error: ${e.message}")
        }

        // 2. DuckDuckGo HTML Web Search Engine Scraper (Fallback if API returns few results)
        if (results.size < 3) {
            try {
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val htmlUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"

                val request = Request.Builder()
                    .url(htmlUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9,uz;q=0.8,ru;q=0.7")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        parseHtmlSearchSnippets(html, results, maxResults)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HTML search scraper error: ${e.message}")
            }
        }

        // Format summary output text
        val summaryBuilder = StringBuilder()
        if (results.isNotEmpty()) {
            summaryBuilder.append("🌐 Internetdan qidiruv natijalari ($cleanQuery):\n\n")
            for ((idx, item) in results.withIndex()) {
                summaryBuilder.append("${idx + 1}. **${item.title}**\n")
                summaryBuilder.append("   ${item.snippet}\n")
                if (item.url.isNotBlank()) {
                    summaryBuilder.append("   🔗 Manba: ${item.url}\n")
                }
                summaryBuilder.append("\n")
            }
        } else {
            summaryBuilder.append("Internetdan '$cleanQuery' bo'yicha hech qanday ma'lumot topilmadi.")
        }

        WebSearchResponse(
            query = cleanQuery,
            results = results,
            summaryText = summaryBuilder.toString()
        )
    }

    private fun parseHtmlSearchSnippets(html: String, results: MutableList<SearchResultItem>, maxResults: Int) {
        val resultBlockPattern = Pattern.compile("<a class=\"result__snippet[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL)
        val titlePattern = Pattern.compile("<a class=\"result__url[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL)
        val urlPattern = Pattern.compile("href=\"([^\"]+)\"", Pattern.DOTALL)

        val snippets = mutableListOf<String>()
        val snippetMatcher = resultBlockPattern.matcher(html)
        while (snippetMatcher.find()) {
            val text = snippetMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: continue
            if (text.isNotBlank()) {
                snippets.add(text)
            }
        }

        val urls = mutableListOf<String>()
        val urlMatcher = titlePattern.matcher(html)
        while (urlMatcher.find()) {
            val urlText = urlMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: continue
            if (urlText.isNotBlank()) {
                urls.add(if (urlText.startsWith("http")) urlText else "https://$urlText")
            }
        }

        for (i in 0 until minOf(snippets.size, maxResults)) {
            if (results.size >= maxResults) break
            val snip = snippets[i]
            val url = urls.getOrNull(i) ?: "https://duckduckgo.com/?q=${URLEncoder.encode(snip.take(20), "UTF-8")}"
            val title = if (snip.length > 45) snip.take(45) + "..." else snip
            if (results.none { it.snippet == snip }) {
                results.add(SearchResultItem(title, snip, url))
            }
        }
    }
}
