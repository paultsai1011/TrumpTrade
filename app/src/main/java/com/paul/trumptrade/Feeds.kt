package com.paul.trumptrade

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object Rss {
    data class Raw(val title: String, val link: String, val desc: String, val date: Long, val source: String)

    fun parse(xmlIn: String): List<Raw> {
        val xml = xmlIn.trim().removePrefix("﻿")
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val out = mutableListOf<Raw>()
        var inItem = false
        var title = ""; var link = ""; var desc = ""; var date = ""; var source = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val n = p.name.lowercase()
                if (n == "item" || n == "entry") {
                    inItem = true
                    title = ""; link = ""; desc = ""; date = ""; source = ""
                } else if (inItem) {
                    when (n) {
                        "title" -> title = text(p)
                        "link" -> {
                            val href = p.getAttributeValue(null, "href")
                            if (href != null) {
                                if (link.isEmpty()) link = href
                            } else {
                                val t = text(p); if (t.isNotBlank()) link = t
                            }
                        }
                        "description", "summary", "content", "content:encoded" -> {
                            val t = text(p)
                            if (desc.isEmpty() || (n == "content:encoded" && t.isNotBlank())) desc = t
                        }
                        "pubdate", "published", "updated", "dc:date" -> {
                            val t = text(p); if (date.isEmpty()) date = t
                        }
                        "source" -> source = text(p)
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                val n = p.name.lowercase()
                if ((n == "item" || n == "entry") && inItem) {
                    inItem = false
                    out.add(Raw(clean(title), link.trim(), clean(desc), parseDate(date), clean(source)))
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun text(p: XmlPullParser): String = try { p.nextText() ?: "" } catch (e: Exception) { "" }

    fun clean(s: String): String {
        if (s.isBlank()) return ""
        val t = if (s.contains('<') || s.contains('&')) Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString() else s
        return t.replace(' ', ' ').replace(Regex("[\\uFFFC]"), "").replace(Regex("\\s+"), " ").trim()
    }

    private val fmts = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
    )

    fun parseDate(s: String): Long {
        val t = s.trim()
        if (t.isEmpty()) return 0L
        for (f in fmts) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                if (f.endsWith("'Z'") || !f.contains("Z") && !f.contains("X") && !f.contains("z")) {
                    sdf.timeZone = TimeZone.getTimeZone(if (f == "yyyy-MM-dd") "America/New_York" else "UTC")
                }
                val d = sdf.parse(t)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}

object Sources {
    const val RSS = 0
    const val FED_REG = 1
    const val CONGRESS = 2

    data class Src(val name: String, val cat: Cat, val url: String, val kind: Int = RSS)

    fun gnews(q: String) =
        "https://news.google.com/rss/search?q=${Net.enc(q)}&hl=en-US&gl=US&ceid=US:en"

    fun gnewsTw(q: String) =
        "https://news.google.com/rss/search?q=${Net.enc(q)}&hl=zh-TW&gl=TW&ceid=TW:zh-Hant"

    private const val FR = "https://www.federalregister.gov/api/v1/documents.json?per_page=25&order=newest"

    val list = listOf(
        Src("Truth Social", Cat.TRUMP, "https://www.trumpstruth.org/feed"),
        Src("Google News", Cat.TRUMP, gnews("Trump (tariff OR trade OR economy OR chips OR Fed OR tax OR China OR Taiwan OR stocks OR deal) when:1d")),
        Src("Google News", Cat.NEWS, "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-US&gl=US&ceid=US:en"),
        Src("Google News", Cat.NEWS, gnews("(stocks OR \"Wall Street\" OR Nasdaq OR \"S&P 500\" OR Nvidia OR semiconductor) when:1d")),
        Src("CNBC", Cat.NEWS, "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114"),
        Src("CNBC Tech", Cat.NEWS, "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=19854910"),
        Src("MarketWatch", Cat.NEWS, "https://feeds.content.dowjones.io/public/rss/mw_topstories"),
        Src("Yahoo Finance", Cat.NEWS, "https://finance.yahoo.com/news/rssindex"),
        Src("白宮總統文件", Cat.POLICY, "$FR&conditions%5Btype%5D%5B%5D=PRESDOCU", FED_REG),
        Src("商務部/USTR", Cat.POLICY, "$FR&conditions%5Bagencies%5D%5B%5D=industry-and-security-bureau&conditions%5Bagencies%5D%5B%5D=trade-representative-office-of-united-states", FED_REG),
        Src("Google News", Cat.POLICY, gnews("(Congress OR Senate OR House) (bill OR act OR legislation) (tech OR chips OR tariff OR tax OR AI OR crypto OR healthcare OR energy) when:3d")),
        Src("Google News", Cat.POLICY, gnews("(\"executive order\" OR \"export controls\" OR USTR OR \"Section 232\" OR \"Section 301\" OR \"Commerce Department\") when:3d")),
    )

    val alertSources = list.filter { it.cat == Cat.TRUMP || it.kind == FED_REG }
}

object Repo {
    data class Result(val items: List<NewsItem>, val errors: List<String>)

    private fun splitSource(title: String, src: String): Pair<String, String> {
        if (src.isNotBlank()) {
            val suffix = " - $src"
            return (if (title.endsWith(suffix)) title.removeSuffix(suffix) else title) to src
        }
        val i = title.lastIndexOf(" - ")
        return if (i > 20 && title.length - i < 40) title.substring(0, i) to title.substring(i + 3) else title to ""
    }

    private fun make(src: Sources.Src, title: String, body: String, link: String, source: String, time: Long): NewsItem {
        val themes = StockMap.match("$title $body").map { it.name }
        val id = link.ifBlank { title }.take(300)
        return NewsItem(id, title, body, link, source.ifBlank { src.name }, time, src.cat, themes)
    }

    fun fetchSource(src: Sources.Src, congressKey: String = ""): List<NewsItem> = when (src.kind) {
        Sources.FED_REG -> {
            val o = JSONObject(Net.get(src.url))
            val arr = o.optJSONArray("results")
            val out = mutableListOf<NewsItem>()
            if (arr != null) for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val title = r.s("title")
                if (title.isBlank()) continue
                out.add(make(src, title, r.s("abstract"), r.s("html_url"), src.name, Rss.parseDate(r.s("publication_date"))))
            }
            out
        }
        Sources.CONGRESS -> {
            val o = JSONObject(Net.get(src.url + "&api_key=" + Net.enc(congressKey)))
            val arr = o.optJSONArray("bills")
            val out = mutableListOf<NewsItem>()
            if (arr != null) for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val type = b.s("type").lowercase()
                val num = b.s("number")
                val congress = b.s("congress")
                val kind = when (type) {
                    "hr" -> "house-bill"; "s" -> "senate-bill"; "hjres" -> "house-joint-resolution"
                    "sjres" -> "senate-joint-resolution"; "hres" -> "house-resolution"; "sres" -> "senate-resolution"
                    else -> "house-bill"
                }
                val la = b.optJSONObject("latestAction")
                val action = la?.s("text") ?: ""
                val date = la?.s("actionDate") ?: b.s("updateDate")
                val link = "https://www.congress.gov/bill/${congress}th-congress/$kind/$num"
                out.add(make(src, "[${b.s("type")} $num] ${b.s("title")}", "最新進度：$action", link, "Congress.gov", Rss.parseDate(date)))
            }
            out
        }
        else -> {
            Rss.parse(Net.get(src.url)).take(40).map { r ->
                if (src.name == "Truth Social") {
                    val text = r.desc.ifBlank { r.title }
                    val title = if (text.length > 220) text.take(220) + "…" else text
                    make(src, title.ifBlank { "(圖片/影片貼文)" }, text, r.link, "Truth Social", r.date)
                } else {
                    val (t, s) = splitSource(r.title, r.source)
                    val body = if (r.desc.startsWith(t.take(30))) "" else r.desc
                    make(src, t, body, r.link, s.ifBlank { src.name }, r.date)
                }
            }
        }
    }

    suspend fun fetch(sources: List<Sources.Src>, congressKey: String): Result = coroutineScope {
        val all = sources.toMutableList()
        if (congressKey.isNotBlank()) {
            all.add(
                Sources.Src(
                    "Congress.gov", Cat.POLICY,
                    "https://api.congress.gov/v3/bill?format=json&limit=30&sort=updateDate+desc", Sources.CONGRESS
                )
            )
        }
        val results = all.map { src ->
            async(Dispatchers.IO) {
                try {
                    src to Result(fetchSource(src, congressKey), emptyList())
                } catch (e: Exception) {
                    src to Result(emptyList(), listOf("${src.name}(${src.cat.label}) 讀取失敗"))
                }
            }
        }.awaitAll()
        val seenTitles = HashSet<String>()
        val items = results.flatMap { it.second.items }
            .sortedByDescending { it.time }
            .filter { seenTitles.add(it.title.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fff]"), "").take(60)) }
            .distinctBy { it.id }
        Result(items, results.flatMap { it.second.errors })
    }

    suspend fun fetchAll(store: Store): Result = fetch(Sources.list, store.congressKey)

    data class Cloud(val tr: Map<String, JSONObject>, val brief: JSONObject?)

    /** 讀網頁版雲端已經翻好的資料，App 就不用重複翻譯、省 AI 額度 */
    fun cloud(): Cloud {
        val o = JSONObject(Net.get("https://paultsai1011.github.io/TrumpTrade/data/data.json?t=${System.currentTimeMillis()}", timeout = 20000))
        val tr = HashMap<String, JSONObject>()
        fun take(id: String, t: JSONObject?) {
            if (t == null || t.s("zh").isBlank()) return
            t.put("_t", t.optLong("t", System.currentTimeMillis()))
            tr[id] = t
        }
        o.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) {
                val it = a.optJSONObject(i) ?: continue
                take(it.s("id"), it.optJSONObject("tr"))
            }
        }
        o.optJSONObject("extraTr")?.let { m -> m.keys().forEach { k -> take(k, m.optJSONObject(k)) } }
        val b = o.optJSONObject("brief")?.also { it.put("_t", it.optLong("t", 0L)) }
        return Cloud(tr, b)
    }

    /** 個股中文新聞（Google News 台灣） */
    fun stockNews(code: String, name: String): List<NewsItem> {
        val q = if (name.isNotBlank()) "$name $code when:7d" else "$code 股票 when:7d"
        val src = Sources.Src("Google新聞", Cat.NEWS, Sources.gnewsTw(q))
        return Rss.parse(Net.get(src.url)).take(15).map { r ->
            val (t, s) = splitSource(r.title, r.source)
            NewsItem(r.link.ifBlank { t }, t, "", r.link, s.ifBlank { "Google新聞" }, r.date, Cat.NEWS, emptyList())
        }.distinctBy { it.id }
    }
}
