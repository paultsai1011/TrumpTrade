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
    val reasonEn: String = "",
) {
    val reasonShown: String get() = if (Lang.en && reasonEn.isNotBlank()) reasonEn else reason
}

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

data class Translation(
    val zh: String,
    val importance: Int,
    val sentiment: String,
    val stocks: List<StockPick>,
    val time: Long,
)

data class Quote(
    val code: String, val name: String, val price: Double, val prev: Double, val time: String,
    val vol: Int = 0, val askP: List<Double> = emptyList(), val askV: List<Int> = emptyList(),
    val bidP: List<Double> = emptyList(), val bidV: List<Int> = emptyList(),
    val open: Double = 0.0, val high: Double = 0.0, val low: Double = 0.0,
) {
    val change: Double get() = if (price > 0 && prev > 0) price - prev else 0.0
    val pct: Double get() = if (price > 0 && prev > 0) (price - prev) / prev * 100 else 0.0
}

data class MarketQuote(val symbol: String, val label: String, val price: Double, val prev: Double) {
    val pct: Double get() = if (price > 0 && prev > 0) (price - prev) / prev * 100 else 0.0
}

data class BriefEvent(val title: String, val impact: String, val sectors: List<String>, val titleEn: String = "", val impactEn: String = "")

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
    val headlineEn: String = "",
    val adviceEn: String = "",
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
    val chips: String = "",
    val tech: String = "",
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
                reasonEn = o.s("reason_en"),
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
            ev.add(BriefEvent(e.s("title"), e.s("impact"), e.strList("sectors"), e.s("title_en"), e.s("impact_en")))
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
        headlineEn = o.s("headline_en"),
        adviceEn = o.s("advice_en"),
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
    chips = o.s("chips"),
    tech = o.s("tech"),
)

fun parseTranslation(o: JSONObject): Translation = Translation(
    zh = o.s("zh"),
    importance = o.n("importance", 1).coerceIn(1, 5),
    sentiment = o.s("sentiment", "中性"),
    stocks = parsePicks(o.optJSONArray("stocks")).map { it.copy(confidence = -1, action = it.action.ifBlank { "相關" }) },
    time = o.optLong("_t", System.currentTimeMillis()),
)

// ---------- 籌碼 ----------
data class ChipDay(
    val date: Int, val foreign: Int?, val trust: Int?, val dealer: Int?, val total: Int?,
    val margin: Int?, val short: Int?, val vol: Int?, val close: Double?,
)

data class ChipDist(val date: Int, val holders: Int, val retail: Double, val big400: Double, val big1000: Double, val lv: List<Double>)
data class ChipDistRow(val date: Int, val big1000: Double, val big400: Double, val retail: Double, val holders: Int)
data class ChipData(val days: List<ChipDay>, val dist: ChipDist?, val distHist: List<ChipDistRow>) {
    fun sum(k: Int, sel: (ChipDay) -> Int?): Int = days.takeLast(k).sumOf { sel(it) ?: 0 }
}

fun parseChips(o: JSONObject): ChipData {
    fun JSONArray.intAt(i: Int): Int? = if (i >= length() || isNull(i)) null else optDouble(i).let { if (it.isNaN()) null else it.toInt() }
    fun JSONArray.dblAt(i: Int): Double? = if (i >= length() || isNull(i)) null else optDouble(i).takeUnless { it.isNaN() }
    val days = mutableListOf<ChipDay>()
    o.optJSONArray("h")?.let { a ->
        for (i in 0 until a.length()) {
            val r = a.optJSONArray(i) ?: continue
            days.add(ChipDay(r.optInt(0), r.intAt(1), r.intAt(2), r.intAt(3), r.intAt(4), r.intAt(5), r.intAt(6), r.intAt(7), r.dblAt(8)))
        }
    }
    val dist = o.optJSONObject("dist")?.let { d ->
        val lv = d.optJSONArray("lv")
        ChipDist(
            d.optInt("d"), d.optInt("holders"), d.optDouble("retail", 0.0), d.optDouble("big400", 0.0), d.optDouble("big1000", 0.0),
            if (lv == null) emptyList() else (0 until lv.length()).map { lv.optDouble(it, 0.0) }
        )
    }
    val dh = mutableListOf<ChipDistRow>()
    o.optJSONArray("dh")?.let { a ->
        for (i in 0 until a.length()) {
            val r = a.optJSONArray(i) ?: continue
            dh.add(ChipDistRow(r.optInt(0), r.optDouble(1, 0.0), r.optDouble(2, 0.0), r.optDouble(3, 0.0), r.optInt(4)))
        }
    }
    return ChipData(days.sortedBy { it.date }, dist, dh.sortedBy { it.date })
}

fun chipSummary(c: ChipData?): String {
    if (c == null) return ""
    val parts = mutableListOf<String>()
    val h = c.days
    if (h.isNotEmpty()) {
        parts.add("近5日法人買賣超(張)：外資 ${c.sum(5) { it.foreign }}、投信 ${c.sum(5) { it.trust }}、自營 ${c.sum(5) { it.dealer }}；近20日合計 ${c.sum(20) { it.total }}")
        val m = h.filter { it.margin != null }
        if (m.size >= 2) {
            val l = m.last(); val p5 = m[maxOf(0, m.size - 6)]
            parts.add("融資餘額 ${l.margin} 張（5日 ${(l.margin ?: 0) - (p5.margin ?: 0)}），融券餘額 ${l.short} 張（5日 ${(l.short ?: 0) - (p5.short ?: 0)}）")
        }
        val v = h.filter { it.vol != null }
        if (v.isNotEmpty()) parts.add("5日均量 ${v.takeLast(5).sumOf { it.vol ?: 0 } / minOf(5, v.size)} 張、20日均量 ${v.takeLast(20).sumOf { it.vol ?: 0 } / minOf(20, v.size)} 張")
    }
    c.dist?.let { d ->
        val pv = c.distHist.getOrNull(c.distHist.size - 2)
        parts.add("集保(${d.date})：千張大戶 ${d.big1000}%" + (pv?.let { "（週變化 ${"%.2f".format(d.big1000 - it.big1000)}）" } ?: "") +
                "、400張以上 ${d.big400}%、50張以下散戶 ${d.retail}%、股東 ${d.holders} 人")
    }
    return parts.joinToString("\n")
}
