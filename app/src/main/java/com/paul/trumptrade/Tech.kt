package com.paul.trumptrade

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** 日 K 線（舊→新） */
data class KLine(
    val d: List<Int>, val o: List<Double>, val h: List<Double>, val l: List<Double>, val c: List<Double>, val v: List<Double>,
) {
    val size get() = c.size
}

data class TechReason(val zh: String, val en: String, val dir: Int) {
    val text get() = if (Lang.en) en else zh
}

data class TechResult(
    val status: String, val score: Int, val reasons: List<TechReason>, val n: Int, val date: Int,
    val ma5: List<Double?> = emptyList(), val ma20: List<Double?> = emptyList(), val ma60: List<Double?> = emptyList(),
    val k: List<Double> = emptyList(), val dd: List<Double> = emptyList(), val osc: List<Double> = emptyList(), val rsi: List<Double?> = emptyList(),
) {
    val ok get() = n >= 26
    val label: String get() = if (!Lang.en) status else when (status) {
        "轉強" -> "Turning strong"; "偏多" -> "Bullish bias"; "盤整" -> "Sideways"; "偏弱" -> "Bearish bias"; "轉弱" -> "Turning weak"; else -> "Not enough data"
    }
    val bullish get() = status == "轉強" || status == "偏多"
    val bearish get() = status == "轉弱" || status == "偏弱"
}

object Tech {
    fun parse(o: JSONObject): KLine {
        fun nums(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0) } } ?: emptyList()
        val d = o.optJSONArray("d")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        return KLine(d, nums("o"), nums("h"), nums("l"), nums("c"), nums("v"))
    }

    /** 加上盤中今天的即時 K 棒 */
    fun withLive(k: KLine, q: Quote?): KLine {
        if (q == null || q.price <= 0 || q.open <= 0 || k.size == 0) return k
        val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
        val dow = now.get(java.util.Calendar.DAY_OF_WEEK)
        if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) return k
        val today = now.get(java.util.Calendar.YEAR) * 10000 + (now.get(java.util.Calendar.MONTH) + 1) * 100 + now.get(java.util.Calendar.DAY_OF_MONTH)
        if (k.d.last() >= today) return k
        return KLine(k.d + today, k.o + q.open, k.h + max(q.high, q.price), k.l + min(if (q.low > 0) q.low else q.price, q.price), k.c + q.price, k.v + q.vol.toDouble())
    }

    fun sma(a: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(a.size) { null }; var s = 0.0
        for (i in a.indices) { s += a[i]; if (i >= n) s -= a[i - n]; if (i >= n - 1) out[i] = s / n }
        return out
    }

    fun ema(a: List<Double>, n: Int): List<Double> {
        val k = 2.0 / (n + 1); var e: Double? = null
        return a.map { x -> val v = if (e == null) x else x * k + e!! * (1 - k); e = v; v }
    }

    fun analyze(k: KLine): TechResult {
        val n = k.size
        if (n < 26) return TechResult("資料不足", 0, emptyList(), n, k.d.lastOrNull() ?: 0)
        val (o, h, l, c, v) = listOf(k.o, k.h, k.l, k.c, k.v)
        val ma5 = sma(c, 5); val ma20 = sma(c, 20); val ma60 = sma(c, 60)
        // KD
        val kk = ArrayList<Double>(); val dd = ArrayList<Double>(); var kv = 50.0; var dv = 50.0
        for (i in c.indices) {
            val s = max(0, i - 8)
            val hh = h.subList(s, i + 1).max(); val ll = l.subList(s, i + 1).min()
            val rsv = if (hh > ll) (c[i] - ll) / (hh - ll) * 100 else 50.0
            kv = kv * 2 / 3 + rsv / 3; dv = dv * 2 / 3 + kv / 3; kk.add(kv); dd.add(dv)
        }
        // MACD
        val e12 = ema(c, 12); val e26 = ema(c, 26)
        val dif = c.indices.map { e12[it] - e26[it] }; val dea = ema(dif, 9)
        val osc = dif.indices.map { dif[it] - dea[it] }
        // RSI
        val rsi = MutableList<Double?>(n) { null }; var g = 0.0; var ls = 0.0
        for (i in 1 until n) {
            val ch = c[i] - c[i - 1]; val up = max(ch, 0.0); val dn = max(-ch, 0.0)
            if (i <= 14) { g += up / 14; ls += dn / 14; if (i == 14) rsi[i] = if (ls == 0.0) 100.0 else 100 - 100 / (1 + g / ls) }
            else { g = (g * 13 + up) / 14; ls = (ls * 13 + dn) / 14; rsi[i] = if (ls == 0.0) 100.0 else 100 - 100 / (1 + g / ls) }
        }
        val i = n - 1; val p = n - 2
        val rs = ArrayList<TechReason>(); var score = 0
        fun add(pts: Int, zh: String, en: String) { score += pts; rs.add(TechReason(zh, en, pts.sign)) }
        fun note(zh: String, en: String) = rs.add(TechReason(zh, en, 0))
        fun f0(x: Double) = "%.0f".format(x)

        val m20 = ma20[i]; val m20p = ma20[p]
        if (m20 != null && m20p != null) {
            when {
                c[i] > m20 && c[p] <= m20p -> add(2, "今天站上月線（20日均線）", "Closed above the 20-day MA today")
                c[i] < m20 && c[p] >= m20p -> add(-2, "今天跌破月線（20日均線）", "Closed below the 20-day MA today")
                c[i] > m20 -> add(1, "股價在月線之上", "Price above the 20-day MA")
                else -> add(-1, "股價在月線之下", "Price below the 20-day MA")
            }
        }
        fun crossUp(j: Int): Boolean { val a = ma5[j]; val b = ma20[j]; val a1 = ma5.getOrNull(j - 1); val b1 = ma20.getOrNull(j - 1); return a != null && b != null && a1 != null && b1 != null && a > b && a1 <= b1 }
        fun crossDn(j: Int): Boolean { val a = ma5[j]; val b = ma20[j]; val a1 = ma5.getOrNull(j - 1); val b1 = ma20.getOrNull(j - 1); return a != null && b != null && a1 != null && b1 != null && a < b && a1 >= b1 }
        if (listOf(i, i - 1, i - 2).any { crossUp(it) }) add(2, "5日線向上穿過20日線（黃金交叉）", "5-day MA crossed above 20-day (golden cross)")
        if (listOf(i, i - 1, i - 2).any { crossDn(it) }) add(-2, "5日線向下穿過20日線（死亡交叉）", "5-day MA crossed below 20-day (death cross)")
        val m5 = ma5[i]; val m60 = ma60[i]; val m60p = ma60[p]
        if (m5 != null && m20 != null && m60 != null) {
            if (m5 > m20 && m20 > m60) add(1, "均線多頭排列（5>20>60）", "Bullish MA alignment (5>20>60)")
            else if (m5 < m20 && m20 < m60) add(-1, "均線空頭排列（5<20<60）", "Bearish MA alignment (5<20<60)")
            if (m60p != null && c[i] > m60 && c[p] <= m60p) add(1, "站上季線（60日均線）", "Closed above the 60-day MA")
            if (m60p != null && c[i] < m60 && c[p] >= m60p) add(-1, "跌破季線（60日均線）", "Closed below the 60-day MA")
        }
        fun kUp(j: Int) = kk[j] > dd[j] && kk[j - 1] <= dd[j - 1]
        fun kDn(j: Int) = kk[j] < dd[j] && kk[j - 1] >= dd[j - 1]
        if (kUp(i) || kUp(p)) { if (kk[i] < 30) add(2, "KD 低檔黃金交叉（K=${f0(kk[i])}）", "KD golden cross at low level (K=${f0(kk[i])})") else add(1, "KD 黃金交叉", "KD golden cross") }
        if (kDn(i) || kDn(p)) { if (kk[i] > 70) add(-2, "KD 高檔死亡交叉（K=${f0(kk[i])}）", "KD death cross at high level (K=${f0(kk[i])})") else add(-1, "KD 死亡交叉", "KD death cross") }
        if (kk[i] > 80) note("KD 過熱（K=${f0(kk[i])}），留意追高風險", "KD overbought (K=${f0(kk[i])}), beware chasing")
        if (kk[i] < 20) note("KD 超賣（K=${f0(kk[i])}），可能醞釀反彈", "KD oversold (K=${f0(kk[i])}), rebound possible")
        when {
            osc[i] > 0 && osc[p] <= 0 -> add(2, "MACD 柱狀體翻紅", "MACD histogram turned positive")
            osc[i] < 0 && osc[p] >= 0 -> add(-2, "MACD 柱狀體翻綠", "MACD histogram turned negative")
            osc[i] > osc[p] && osc[p] > osc[i - 2] -> add(1, "MACD 動能連續增強", "MACD momentum rising")
            osc[i] < osc[p] && osc[p] < osc[i - 2] -> add(-1, "MACD 動能連續減弱", "MACD momentum fading")
        }
        rsi[i]?.let { r ->
            if (r > 75) note("RSI 偏高（${f0(r)}），短線過熱", "RSI high (${f0(r)}), short-term overheated")
            else if (r < 25) note("RSI 偏低（${f0(r)}），短線超跌", "RSI low (${f0(r)}), short-term oversold")
        }
        val cnt = min(5, i)
        val v5 = if (cnt > 0) v.subList(i - cnt, i).sum() / cnt else 0.0
        val chg = (c[i] - c[p]) / c[p] * 100
        if (v5 > 0 && v[i] > v5 * 1.5) {
            val x = "%.1f".format(v[i] / v5)
            if (chg > 0) add(1, "量增價漲（量為5日均量 $x 倍）", "Rising on heavy volume (${x}× 5-day avg)")
            else if (chg < 0) add(-1, "量增價跌（量為5日均量 $x 倍），留意賣壓", "Falling on heavy volume (${x}× 5-day avg)")
        }
        val body = (c[i] - o[i]) / o[i] * 100
        if (body > 3) add(1, "收長紅K（+${"%.1f".format(body)}%）", "Long bullish candle (+${"%.1f".format(body)}%)")
        if (body < -3) add(-1, "收長黑K（${"%.1f".format(body)}%）", "Long bearish candle (${"%.1f".format(body)}%)")
        if (c[i] > o[i] && c[p] < o[p] && c[i] >= o[p] && o[i] <= c[p]) add(1, "多頭吞噬（紅K包覆前一根黑K）", "Bullish engulfing")
        if (c[i] < o[i] && c[p] > o[p] && c[i] <= o[p] && o[i] >= c[p]) add(-1, "空頭吞噬（黑K包覆前一根紅K）", "Bearish engulfing")
        if (n > 21) {
            val hh = h.subList(i - 20, i).max(); val ll = l.subList(i - 20, i).min()
            if (c[i] > hh) add(1, "突破近20日高點", "Broke above 20-day high")
            if (c[i] < ll) add(-1, "跌破近20日低點", "Broke below 20-day low")
        }
        val status = when { score >= 4 -> "轉強"; score >= 2 -> "偏多"; score <= -4 -> "轉弱"; score <= -2 -> "偏弱"; else -> "盤整" }
        return TechResult(status, score, rs, n, k.d[i], ma5, ma20, ma60, kk, dd, osc, rsi)
    }

    fun summary(t: TechResult?, k: KLine?): String {
        if (t == null || !t.ok || k == null) return ""
        val i = k.size - 1
        fun f(x: Double?) = x?.let { "%.2f".format(it) } ?: "–"
        return "技術面（${t.date}）：${t.status}（分數 ${t.score}）；收盤 ${f(k.c[i])}，MA5 ${f(t.ma5[i])}、MA20 ${f(t.ma20[i])}、MA60 ${f(t.ma60[i])}；" +
                "K ${f(t.k[i])}、D ${f(t.dd[i])}；MACD柱 ${f(t.osc[i])}；RSI ${f(t.rsi[i])}。訊號：" + t.reasons.joinToString("；") { it.zh }
    }
}

/** K 線資料（雲端整理，依代號第一碼分檔） */
object KData {
    private val cache = HashMap<Char, Pair<Long, JSONObject>>()
    fun get(code: String): KLine? {
        val k = code.firstOrNull() ?: return null
        val now = System.currentTimeMillis()
        val hit = cache[k]
        val shard = if (hit != null && now - hit.first < 30 * 60_000L) hit.second else {
            val o = JSONObject(Net.get("https://paultsai1011.github.io/TrumpTrade/data/k/$k.json?t=${now / 600_000}", timeout = 30000))
            cache[k] = now to o
            o
        }
        return shard.optJSONObject(code)?.let { Tech.parse(it) }
    }
}
