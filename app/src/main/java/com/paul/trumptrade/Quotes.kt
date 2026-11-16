package com.paul.trumptrade

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** 台股報價：先用證交所即時 API，失敗再用 OpenAPI 每日收盤 */
object TwQuote {
    private fun d(s: String?): Double {
        if (s.isNullOrBlank() || s == "-") return 0.0
        return s.split("_").firstOrNull { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0
    }

    fun fetch(codes: Collection<String>): Map<String, Quote> {
        val list = codes.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        if (list.isEmpty()) return emptyMap()
        val out = HashMap<String, Quote>()
        for (chunk in list.chunked(20)) {
            try {
                val ex = chunk.flatMap { listOf("tse_$it.tw", "otc_$it.tw") }.joinToString("|")
                val url = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=${Net.enc(ex)}&json=1&delay=0&_=${System.currentTimeMillis()}"
                val o = JSONObject(Net.get(url, mapOf("Referer" to "https://mis.twse.com.tw/stock/index.jsp")))
                val arr = o.optJSONArray("msgArray") ?: continue
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val code = m.s("c")
                    if (code.isBlank()) continue
                    val prev = d(m.s("y"))
                    var price = d(m.s("z"))
                    if (price <= 0) price = d(m.optJSONObject("trade")?.s("z"))
                    if (price <= 0) price = d(m.s("b"))
                    if (price <= 0) price = d(m.s("a"))
                    if (price <= 0) price = d(m.s("o"))
                    if (price <= 0) price = prev
                    fun nums(k: String) = m.s(k).split("_").filter { it.isNotBlank() }.map { it.toDoubleOrNull() ?: 0.0 }
                    out[code] = Quote(
                        code, m.s("n").ifBlank { StockMap.nameOf(code) }, price, prev, m.s("t"),
                        vol = m.s("v").toIntOrNull() ?: 0, askP = nums("a"), askV = nums("f").map { it.toInt() },
                        bidP = nums("b"), bidV = nums("g").map { it.toInt() },
                    )
                }
            } catch (_: Exception) {
            }
        }
        val missing = list.filter { it !in out }
        if (missing.isNotEmpty()) out.putAll(fallback(missing))
        return out
    }

    private fun fallback(codes: List<String>): Map<String, Quote> {
        val out = HashMap<String, Quote>()
        val want = codes.toSet()
        try {
            val arr = JSONArray(Net.get("https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL", timeout = 25000))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val c = o.s("Code")
                if (c !in want) continue
                val close = o.s("ClosingPrice").replace(",", "").toDoubleOrNull() ?: 0.0
                val chg = o.s("Change").replace(",", "").replace("+", "").toDoubleOrNull() ?: 0.0
                out[c] = Quote(c, o.s("Name"), close, close - chg, "收盤")
            }
        } catch (_: Exception) {
        }
        val rest = want - out.keys
        if (rest.isNotEmpty()) try {
            val arr = JSONArray(Net.get("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes", timeout = 25000))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val c = o.s("SecuritiesCompanyCode")
                if (c !in rest) continue
                val close = o.s("Close").replace(",", "").toDoubleOrNull() ?: 0.0
                val chg = o.s("Change").replace(",", "").replace("+", "").toDoubleOrNull() ?: 0.0
                out[c] = Quote(c, o.s("CompanyName"), close, close - chg, "收盤")
            }
        } catch (_: Exception) {
        }
        return out
    }
}

/** 美股與大盤指數（Yahoo Finance 公開圖表 API） */
object Markets {
    val symbols = listOf(
        "^GSPC" to "S&P500", "^IXIC" to "那斯達克", "^SOX" to "費城半導體", "TSM" to "台積電ADR",
        "^TWII" to "台股加權", "^VIX" to "恐慌指數", "TWD=X" to "美元/台幣"
    )

    suspend fun fetch(): List<MarketQuote> = coroutineScope {
        symbols.map { (sym, label) ->
            async(Dispatchers.IO) {
                try {
                    val o = JSONObject(Net.get("https://query1.finance.yahoo.com/v8/finance/chart/${Net.enc(sym)}?range=1d&interval=1d"))
                    val meta = o.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    val price = meta.optDouble("regularMarketPrice", 0.0)
                    var prev = meta.optDouble("chartPreviousClose", 0.0)
                    if (prev.isNaN() || prev <= 0) prev = meta.optDouble("previousClose", 0.0)
                    MarketQuote(sym, label, price, prev)
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }
}

/** 籌碼資料（雲端每天整理好，依代號第一碼分檔） */
object Chips {
    private val cache = HashMap<Char, Pair<Long, org.json.JSONObject>>()
    fun get(code: String): ChipData? {
        val k = code.firstOrNull() ?: return null
        val now = System.currentTimeMillis()
        val hit = cache[k]
        val shard = if (hit != null && now - hit.first < 30 * 60_000L) hit.second else {
            val o = org.json.JSONObject(Net.get("https://paultsai1011.github.io/TrumpTrade/data/chips/$k.json?t=${now / 600_000}", timeout = 30000))
            cache[k] = now to o
            o
        }
        return shard.optJSONObject(code)?.let { parseChips(it) }
    }
}
