package com.paul.trumptrade

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UsInfo(val t: String, val zh: String, val en: String, val tw: List<String>, val etf: Boolean)

data class UsQuote(
    val t: String, val name: String, val price: Double, val prev: Double, val open: Double, val high: Double, val low: Double,
    val vol: Long, val time: String, val date: Int, val extPrice: Double = 0.0, val extPct: Double = 0.0,
) {
    val change get() = price - prev
    val pct get() = if (prev > 0) (price - prev) / prev * 100 else 0.0
}

/** 美股：清單與台股連動（與網頁 web/us.js 相同），報價用 CNBC、日K用 Nasdaq */
object Us {
    val list = listOf(
        UsInfo("NVDA", "輝達", "NVIDIA", listOf("2330", "2317", "2382", "3231", "6669", "2376", "3017"), false),
        UsInfo("AAPL", "蘋果", "Apple", listOf("2317", "3008", "4938", "2474", "3406"), false),
        UsInfo("MSFT", "微軟", "Microsoft", listOf("2382", "6669", "3231", "2317"), false),
        UsInfo("GOOGL", "Google", "Alphabet", listOf("2382", "6669", "3231", "2317"), false),
        UsInfo("AMZN", "亞馬遜", "Amazon", listOf("2382", "6669", "3231", "2317"), false),
        UsInfo("META", "Meta", "Meta", listOf("2382", "6669", "3231", "2376"), false),
        UsInfo("TSLA", "特斯拉", "Tesla", listOf("2308", "3665", "1536", "2201"), false),
        UsInfo("AVGO", "博通", "Broadcom", listOf("2330", "3443", "2345"), false),
        UsInfo("AMD", "超微", "AMD", listOf("2330", "3711", "2376", "3661"), false),
        UsInfo("TSM", "台積電ADR", "TSMC ADR", listOf("2330"), false),
        UsInfo("INTC", "英特爾", "Intel", listOf("2330", "3711"), false),
        UsInfo("QCOM", "高通", "Qualcomm", listOf("2454", "2330"), false),
        UsInfo("MU", "美光", "Micron", listOf("2408", "2344", "8299", "3260"), false),
        UsInfo("ARM", "安謀", "Arm", listOf("2454", "2330", "3443"), false),
        UsInfo("ASML", "艾司摩爾", "ASML", listOf("2330"), false),
        UsInfo("AMAT", "應用材料", "Applied Materials", listOf("2330"), false),
        UsInfo("LRCX", "科林研發", "Lam Research", listOf("2330"), false),
        UsInfo("MRVL", "邁威爾", "Marvell", listOf("2330", "3711", "3443"), false),
        UsInfo("SMCI", "美超微", "Supermicro", listOf("2376", "6669", "3017", "2382"), false),
        UsInfo("DELL", "戴爾", "Dell", listOf("2382", "3231", "2357"), false),
        UsInfo("ORCL", "甲骨文", "Oracle", listOf("2382", "6669", "3231"), false),
        UsInfo("ANET", "Arista", "Arista", listOf("2345", "6669"), false),
        UsInfo("CSCO", "思科", "Cisco", listOf("2345"), false),
        UsInfo("PLTR", "Palantir", "Palantir", emptyList(), false),
        UsInfo("NFLX", "網飛", "Netflix", emptyList(), false),
        UsInfo("COST", "好市多", "Costco", emptyList(), false),
        UsInfo("WMT", "沃爾瑪", "Walmart", emptyList(), false),
        UsInfo("JPM", "摩根大通", "JPMorgan", emptyList(), false),
        UsInfo("LLY", "禮來", "Eli Lilly", emptyList(), false),
        UsInfo("BA", "波音", "Boeing", listOf("2634"), false),
        UsInfo("XOM", "埃克森美孚", "Exxon Mobil", listOf("6505"), false),
        UsInfo("COIN", "Coinbase", "Coinbase", emptyList(), false),
        UsInfo("MSTR", "Strategy", "Strategy", emptyList(), false),
        UsInfo("SPY", "標普500 ETF", "S&P 500 ETF", listOf("0050"), true),
        UsInfo("VOO", "Vanguard 標普500", "Vanguard S&P 500", listOf("0050"), true),
        UsInfo("QQQ", "那斯達克100 ETF", "Nasdaq-100 ETF", listOf("0050", "2330"), true),
        UsInfo("QQQI", "NEOS 那斯達克100 高息", "NEOS Nasdaq-100 High Income", listOf("0050"), true),
        UsInfo("JEPQ", "JPM 那斯達克 高息", "JPMorgan Nasdaq Equity Premium", emptyList(), true),
        UsInfo("JEPI", "JPM 股票高息", "JPMorgan Equity Premium", emptyList(), true),
        UsInfo("SCHD", "嘉信美國高股息", "Schwab US Dividend", emptyList(), true),
        UsInfo("SOXX", "費城半導體 ETF", "iShares Semiconductor", listOf("2330", "2454", "2303"), true),
        UsInfo("SMH", "VanEck 半導體", "VanEck Semiconductor", listOf("2330", "2454", "2303"), true),
        UsInfo("DIA", "道瓊 ETF", "Dow Jones ETF", emptyList(), true),
        UsInfo("IWM", "羅素2000 ETF", "Russell 2000 ETF", emptyList(), true),
        UsInfo("TLT", "20年美債 ETF", "20+ Year Treasury", listOf("00679B"), true),
        UsInfo("GLD", "黃金 ETF", "Gold ETF", emptyList(), true),
    )
    val map = list.associateBy { it.t }
    val defaultWatch = listOf("NVDA", "AAPL", "TSM", "TSLA", "QQQ")
    fun isUs(c: String) = Regex("^[A-Z][A-Z.\\-]{0,5}$").matches(c)
    fun name(t: String, fallback: String = ""): String = map[t]?.let { if (Lang.en) it.en else it.zh } ?: fallback

    private fun num(s: String?): Double = s?.replace(Regex("[$,%+]"), "")?.trim()?.toDoubleOrNull() ?: 0.0

    fun quotes(tickers: List<String>): Map<String, UsQuote> {
        val out = HashMap<String, UsQuote>()
        for (chunk in tickers.distinct().chunked(20)) {
            try {
                val url = "https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol?symbols=" +
                        Net.enc(chunk.joinToString("|")) + "&requestMethod=itv&noform=1&partnerId=2&fund=1&exthrs=1&output=json"
                val root = JSONObject(Net.get(url)).optJSONObject("FormattedQuoteResult") ?: continue
                val arr = root.optJSONArray("FormattedQuote")
                val objs = if (arr != null) (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } else listOfNotNull(root.optJSONObject("FormattedQuote"))
                for (x in objs) {
                    val t = x.s("symbol"); val last = num(x.s("last"))
                    if (t.isBlank() || last <= 0) continue
                    val ext = x.optJSONObject("ExtendedMktQuote")
                    out[t] = UsQuote(
                        t, x.s("name"), last, num(x.s("previous_day_closing")).takeIf { it > 0 } ?: (last - num(x.s("change"))),
                        num(x.s("open")), num(x.s("high")), num(x.s("low")), num(x.s("volume")).toLong(), x.s("last_timedate"),
                        x.s("last_time").replace("-", "").take(8).toIntOrNull() ?: 0,
                        ext?.let { num(it.s("last")) } ?: 0.0, ext?.let { num(it.s("change_pct")) } ?: 0.0,
                    )
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private val kCache = HashMap<String, Pair<Long, KLine>>()
    fun k(t: String): KLine? {
        val now = System.currentTimeMillis()
        kCache[t]?.let { if (now - it.first < 60 * 60_000L) return it.second }
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now - 200L * 86400_000))
        val classes = if (map[t]?.etf == true) listOf("etf", "stocks") else listOf("stocks", "etf")
        for (cls in classes) {
            try {
                val o = JSONObject(Net.get("https://api.nasdaq.com/api/quote/${Net.enc(t)}/historical?assetclass=$cls&fromdate=$from&limit=200",
                    mapOf("Accept" to "application/json, text/plain, */*")))
                val rows = o.optJSONObject("data")?.optJSONObject("tradesTable")?.optJSONArray("rows") ?: continue
                if (rows.length() == 0) continue
                val d = ArrayList<Int>(); val op = ArrayList<Double>(); val hi = ArrayList<Double>(); val lo = ArrayList<Double>(); val cl = ArrayList<Double>(); val v = ArrayList<Double>()
                for (i in rows.length() - 1 downTo 0) {
                    val r = rows.optJSONObject(i) ?: continue
                    val c = num(r.s("close")); if (c <= 0) continue
                    val p = r.s("date").split("/"); if (p.size != 3) continue
                    d.add((p[2] + p[0].padStart(2, '0') + p[1].padStart(2, '0')).toInt())
                    cl.add(c); op.add(num(r.s("open")).takeIf { it > 0 } ?: c); hi.add(num(r.s("high")).takeIf { it > 0 } ?: c)
                    lo.add(num(r.s("low")).takeIf { it > 0 } ?: c); v.add(num(r.s("volume")))
                }
                val kl = KLine(d, op, hi, lo, cl, v)
                kCache[t] = now to kl
                return kl
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** 加上最新報價（若比歷史K新） */
    fun withLive(k: KLine, q: UsQuote?): KLine {
        if (q == null || q.open <= 0 || k.size == 0 || q.date <= k.d.last()) return k
        return KLine(k.d + q.date, k.o + q.open, k.h + maxOf(q.high, q.price), k.l + minOf(if (q.low > 0) q.low else q.price, q.price), k.c + q.price, k.v + q.vol.toDouble())
    }
}
