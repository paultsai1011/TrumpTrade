package com.paul.trumptrade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("tt", Context.MODE_PRIVATE)
    private val dir: File = ctx.applicationContext.filesDir

    private fun str(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var geminiKey: String
        get() = str("gemini_key")
        set(v) = put("gemini_key", v.trim())
    var congressKey: String
        get() = str("congress_key")
        set(v) = put("congress_key", v.trim())
    var model: String
        get() = str("model", "auto")
        set(v) = put("model", v.trim().ifBlank { "auto" })
    var lastModel: String
        get() = str("last_model")
        set(v) = put("last_model", v)
    var modelCache: String
        get() = str("model_cache")
        set(v) = put("model_cache", v)
    var modelCacheTime: Long
        get() = sp.getLong("model_cache_t", 0L)
        set(v) = sp.edit().putLong("model_cache_t", v).apply()
    var notifyOn: Boolean
        get() = sp.getBoolean("notify_on", true)
        set(v) = sp.edit().putBoolean("notify_on", v).apply()
    var notifyAi: Boolean
        get() = sp.getBoolean("notify_ai", true)
        set(v) = sp.edit().putBoolean("notify_ai", v).apply()
    var intervalMin: Int
        get() = sp.getInt("interval", 30)
        set(v) = sp.edit().putInt("interval", v.coerceAtLeast(15)).apply()
    var alertKeywords: String
        get() = str("alert_kw", DEFAULT_KW)
        set(v) = put("alert_kw", v)
    var autoTranslate: Boolean
        get() = sp.getBoolean("auto_tr", true)
        set(v) = sp.edit().putBoolean("auto_tr", v).apply()
    var watchlist: List<String>
        get() = str("watch", StockMap.defaultWatch.joinToString(",")).split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(v) = put("watch", v.distinct().joinToString(","))

    fun alertRegex(): Regex? {
        val kws = alertKeywords.split(",", "，", "\n").map { it.trim() }.filter { it.isNotBlank() }
        if (kws.isEmpty()) return null
        return Regex(kws.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    // ---------- files ----------
    private fun readFile(name: String): String? = try {
        val f = File(dir, name); if (f.exists()) f.readText() else null
    } catch (e: Exception) { null }

    private fun writeFile(name: String, text: String) {
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        } catch (_: Exception) {
        }
    }

    fun saveItems(items: List<NewsItem>) {
        val a = JSONArray()
        items.take(400).forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("body", it.body.take(4000)).put("link", it.link)
                    .put("source", it.source).put("time", it.time).put("cat", it.cat.name)
                    .put("themes", JSONArray(it.themes))
            )
        }
        writeFile("items.json", a.toString())
    }

    fun loadItems(): List<NewsItem> {
        val t = readFile("items.json") ?: return emptyList()
        return try {
            val a = JSONArray(t)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                NewsItem(
                    o.s("id"), o.s("title"), o.s("body"), o.s("link"), o.s("source"), o.optLong("time"),
                    runCatching { Cat.valueOf(o.s("cat")) }.getOrDefault(Cat.NEWS), o.strList("themes")
                )
            }.distinctBy { it.id }
        } catch (e: Exception) { emptyList() }
    }

    private fun loadMap(name: String): JSONObject = try { JSONObject(readFile(name) ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun saveInMap(name: String, key: String, value: JSONObject, limit: Int) {
        synchronized(LOCK) {
            val m = loadMap(name)
            m.put(key, value)
            if (m.length() > limit) {
                val keys = m.keys().asSequence().toList()
                    .sortedBy { m.optJSONObject(it)?.optLong("_t") ?: 0L }
                keys.take(m.length() - limit).forEach { m.remove(it) }
            }
            writeFile(name, m.toString())
        }
    }

    fun saveAnalysis(id: String, o: JSONObject) = saveInMap("analyses.json", id, o, 300)

    fun loadAnalyses(): Map<String, Analysis> {
        val m = synchronized(LOCK) { loadMap("analyses.json") }
        val out = HashMap<String, Analysis>()
        m.keys().forEach { k -> m.optJSONObject(k)?.let { runCatching { out[k] = parseAnalysis(it) } } }
        return out
    }

    fun saveStockAnalysis(code: String, o: JSONObject) = saveInMap("stocks.json", code, o, 200)

    fun loadStockAnalyses(): Map<String, StockAnalysis> {
        val m = synchronized(LOCK) { loadMap("stocks.json") }
        val out = HashMap<String, StockAnalysis>()
        m.keys().forEach { k -> m.optJSONObject(k)?.let { runCatching { out[k] = parseStockAnalysis(it) } } }
        return out
    }

    fun saveTranslations(items: Map<String, JSONObject>) {
        synchronized(LOCK) {
            val m = loadMap("trans.json")
            items.forEach { (k, v) -> m.put(k, v) }
            if (m.length() > 800) {
                val keys = m.keys().asSequence().toList().sortedBy { m.optJSONObject(it)?.optLong("_t") ?: 0L }
                keys.take(m.length() - 800).forEach { m.remove(it) }
            }
            writeFile("trans.json", m.toString())
        }
    }

    fun loadTranslations(): Map<String, Translation> {
        val m = synchronized(LOCK) { loadMap("trans.json") }
        val out = HashMap<String, Translation>()
        m.keys().forEach { k -> m.optJSONObject(k)?.let { runCatching { out[k] = parseTranslation(it) } } }
        return out
    }

    fun saveBrief(o: JSONObject) = writeFile("brief.json", o.toString())
    fun loadBrief(): Brief? = readFile("brief.json")?.let { runCatching { parseBrief(JSONObject(it)) }.getOrNull() }

    fun loadSeen(): MutableList<String> = try {
        val a = JSONArray(readFile("seen.json") ?: "[]"); (0 until a.length()).map { a.optString(it) }.toMutableList()
    } catch (e: Exception) { mutableListOf() }

    fun saveSeen(list: List<String>) = writeFile("seen.json", JSONArray(list.takeLast(800)).toString())

    companion object {
        private val LOCK = Any()
        const val DEFAULT_KW =
            "tariff,China,chip,semiconductor,Taiwan,TSMC,Nvidia,Apple,Intel,export,Fed,interest rate,tax,trade,AI,ban,sanction,executive order,Section 232,Section 301,stock market,oil,關稅,晶片,台灣"
    }
}
