package com.paul.trumptrade

import org.json.JSONArray
import org.json.JSONObject

enum class Cat(val label: String) { TRUMP("川普"), NEWS("財經"), POLICY("政策法案") }

data class NewsItem(
    val id: String,
    val title: String,
    val body: String,
    val link: String,
    val source: String,
    val time: Long,
    val cat: Cat,
    val themes: List<String>,
)

data class StockPick(
    val code: String,
    val name: String,
    val action: String,
    val confidence: Int,
    val reason: String,
)

data class Analysis(
    val summary: String,
    val importance: Int,
    val sentiment: String,
    val sectors: List<String>,
    val usImpact: String,
    val twImpact: String,
    val stocks: List<StockPick>,
    val risk: String,
    val time: Long,
)

data class Quote(val code: String, val name: String, val price: Double, val prev: Double, val time: String) {
    val change: Double get() = if (price > 0 && prev > 0) price - prev else 0.0
    val pct: Double get() = if (price > 0 && prev > 0) (price - prev) / prev * 100 else 0.0
}

data class MarketQuote(val symbol: String, val label: String, val price: Double, val prev: Double) {
    val pct: Double get() = if (price > 0 && prev > 0) (price - prev) / prev * 100 else 0.0
}

data class BriefEvent(val title: String, val impact: String, val sectors: List<String>)

data class Brief(
    val time: Long,
    val mood: String,
    val moodScore: Int,
    val headline: String,
    val events: List<BriefEvent>,
    val buy: List<StockPick>,
    val sell: List<StockPick>,
    val watch: List<StockPick>,
    val advice: String,
)

data class StockAnalysis(
    val code: String,
    val name: String,
    val action: String,
    val confidence: Int,
    val summary: String,
    val shortTerm: String,
    val midTerm: String,
    val reasons: List<String>,
    val risks: List<String>,
    val catalysts: List<String>,
    val time: Long,
)

// ---------- JSON helpers ----------

fun JSONObject.s(k: String, d: String = ""): String =
    if (has(k) && !isNull(k)) optString(k, d).trim() else d

fun JSONObject.n(k: String, d: Int = 0): Int {
    if (!has(k) || isNull(k)) return d
    return when (val v = opt(k)) {
        is Number -> v.toInt()
        is String -> v.filter { it.isDigit() || it == '-' }.toIntOrNull() ?: d
        else -> d
    }
}

fun JSONObject.strList(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        when (val v = a.opt(i)) {
            is JSONObject -> v.s("title").ifBlank { v.s("text").ifBlank { v.toString() } }
            null -> null
            else -> v.toString()
        }
    }.filter { it.isNotBlank() && it != "null" }
}

fun cleanCode(raw: String): String =
    Regex("\\d{4,6}[A-Z]?").find(raw.uppercase())?.value ?: raw.trim()

fun parsePicks(a: JSONArray?): List<StockPick> {
    if (a == null) return emptyList()
    val out = mutableListOf<StockPick>()
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        val code = cleanCode(o.s("code"))
        if (code.isBlank()) continue
        out.add(
            StockPick(
                code = code,
                name = o.s("name").ifBlank { StockMap.nameOf(code) },
                action = o.s("action", "觀望"),
                confidence = o.n("confidence", 50).coerceIn(0, 100),
                reason = o.s("reason"),
            )
        )
    }
    return out
}

fun parseAnalysis(o: JSONObject): Analysis = Analysis(
    summary = o.s("summary"),
    importance = o.n("importance", 1).coerceIn(1, 5),
    sentiment = o.s("sentiment", "中性"),
    sectors = o.strList("sectors"),
    usImpact = o.s("us_impact"),
    twImpact = o.s("tw_impact"),
    stocks = parsePicks(o.optJSONArray("stocks")),
    risk = o.s("risk"),
    time = o.optLong("_t", System.currentTimeMillis()),
)

fun parseBrief(o: JSONObject): Brief {
    val ev = mutableListOf<BriefEvent>()
    o.optJSONArray("events")?.let { a ->
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            ev.add(BriefEvent(e.s("title"), e.s("impact"), e.strList("sectors")))
        }
    }
    return Brief(
        time = o.optLong("_t", System.currentTimeMillis()),
        mood = o.s("mood", "中性"),
        moodScore = o.n("mood_score", 0).coerceIn(-5, 5),
        headline = o.s("headline"),
        events = ev,
        buy = parsePicks(o.optJSONArray("buy")),
        sell = parsePicks(o.optJSONArray("sell")),
        watch = parsePicks(o.optJSONArray("watch")),
        advice = o.s("advice"),
    )
}

fun parseStockAnalysis(o: JSONObject): StockAnalysis = StockAnalysis(
    code = o.s("_code"),
    name = o.s("_name"),
    action = o.s("action", "觀望"),
    confidence = o.n("confidence", 50).coerceIn(0, 100),
    summary = o.s("summary"),
    shortTerm = o.s("short_term"),
    midTerm = o.s("mid_term"),
    reasons = o.strList("reasons"),
    risks = o.strList("risks"),
    catalysts = o.strList("catalysts"),
    time = o.optLong("_t", System.currentTimeMillis()),
)
