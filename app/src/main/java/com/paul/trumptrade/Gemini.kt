package com.paul.trumptrade

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val defaults = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash-lite")

    fun listModels(key: String): List<String> {
        val o = JSONObject(Net.get("$BASE/models?pageSize=200", mapOf("x-goog-api-key" to key)))
        val arr = o.optJSONArray("models") ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            if (methods.contains("generateContent")) out.add(m.s("name").removePrefix("models/"))
        }
        return out
    }

    private fun ver(n: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    /** 挑出可用的 Flash 文字模型，新版優先、非 lite 優先、正式版優先 */
    fun rank(names: List<String>): List<String> {
        val bad = listOf("image", "tts", "audio", "live", "embedding", "vision", "learnlm", "computer", "robotics", "native", "thinking", "8b")
        return names.filter { n -> n.contains("gemini") && n.contains("flash") && bad.none { n.contains(it) } }
            .sortedWith(
                compareByDescending<String> { ver(it) }
                    .thenBy { if (it.contains("lite")) 1 else 0 }
                    .thenBy { if (it.contains("preview") || it.contains("exp")) 1 else 0 }
                    .thenBy { it.length }
            )
    }

    private fun candidates(store: Store): List<String> {
        var ranked = store.modelCache.split(",").filter { it.isNotBlank() }
        if (ranked.isEmpty() || System.currentTimeMillis() - store.modelCacheTime > 12 * 3600_000L) {
            try {
                val r = rank(listModels(store.geminiKey))
                if (r.isNotEmpty()) {
                    ranked = r
                    store.modelCache = r.joinToString(",")
                    store.modelCacheTime = System.currentTimeMillis()
                }
            } catch (_: Exception) {
            }
        }
        if (ranked.isEmpty()) ranked = defaults
        val chosen = store.model
        return if (chosen.isNotBlank() && chosen != "auto") listOf(chosen) + ranked.filter { it != chosen } else ranked
    }

    /** 關閉/降低「深度思考」，回應速度可快 3~10 倍 */
    private fun thinkingConfig(model: String): JSONObject? = when {
        model.contains("2.5") && !model.contains("pro") -> JSONObject().put("thinkingBudget", 0)
        ver(model) >= 3.0 || model.contains("latest") -> JSONObject().put("thinkingLevel", "low")
        else -> null
    }

    private fun generate(key: String, model: String, prompt: String, useThinkingCfg: Boolean = true): String {
        val gc = JSONObject().put("temperature", 0.4).put("responseMimeType", "application/json")
        val tc = if (useThinkingCfg) thinkingConfig(model) else null
        if (tc != null) gc.put("thinkingConfig", tc)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", gc)
        val raw = try {
            Net.post("$BASE/models/$model:generateContent", body.toString(), mapOf("x-goog-api-key" to key), timeout = 45000)
        } catch (e: HttpException) {
            // 舊模型不支援 thinkingConfig → 拿掉再試一次
            if (tc != null && e.code == 400 && e.body.contains("think", true)) return generate(key, model, prompt, false)
            throw e
        }
        val resp = JSONObject(raw)
        val cands = resp.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw IllegalStateException("AI 沒有回應")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("AI 回應空白")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""))
        }
        return sb.toString()
    }

    fun extractJson(t: String): JSONObject {
        val s = t.replace("```json", "").replace("```", "")
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) throw IllegalStateException("AI 回傳格式錯誤")
        return JSONObject(s.substring(a, b + 1))
    }

    fun ask(store: Store, prompt: String, onStatus: (String) -> Unit = {}): JSONObject {
        val key = store.geminiKey
        if (key.isBlank()) throw IllegalStateException("請先到「設定」輸入 Gemini API Key")
        var last: Exception? = null
        var list = candidates(store)
        // 上次成功的模型優先，省掉失敗重試的時間
        val ok = store.lastModel
        if (store.model == "auto" && ok.isNotBlank() && ok in list) list = listOf(ok) + list.filter { it != ok }
        for ((i, m) in list.take(3).withIndex()) {
            onStatus(if (i == 0) "AI 分析中（$m）" else "換模型重試（$m）")
            try {
                val o = extractJson(generate(key, m, prompt))
                store.lastModel = m
                return o
            } catch (e: HttpException) {
                last = e
                if (e.code == 400 && e.body.contains("API key", true)) throw IllegalStateException("Gemini API Key 無效，請到設定檢查")
                if (e.code == 403) throw IllegalStateException("API Key 沒有權限 (403)")
            } catch (e: Exception) {
                last = e
            }
        }
        val msg = when {
            last is HttpException && (last as HttpException).code == 429 -> "免費額度暫時用完(429)，請稍後再試"
            last is java.net.SocketTimeoutException -> "AI 回應逾時，請再試一次"
            else -> last?.message ?: "未知錯誤"
        }
        throw IllegalStateException("AI 分析失敗：$msg")
    }
}

object Prompts {
    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).format(Date())
    fun fmt(t: Long): String = if (t <= 0) "未知" else SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(Date(t))

    private const val STOCK_RULE =
        "台股代號必須是真實存在的台灣上市櫃股票或ETF（4~6碼），name 用台灣常用簡稱。action 只能是「買進」「賣出」「觀望」其中之一。"

    fun news(item: NewsItem): String {
        val hint = item.themes.mapNotNull { StockMap.theme(it) }.joinToString("\n") { th ->
            "- ${th.name}：" + th.stocks.joinToString("、") { "${it.first} ${it.second}" }
        }.ifBlank { "（無）" }
        return """
你是資深的美股與台股產業分析師。請分析下面這則美國消息（新聞/川普發言/政策法案）對「美國科技業、美國民生經濟」以及「台灣股市」的可能影響。
現在時間：${now()}（台灣時間）
類別：${item.cat.label}
來源：${item.source}
發布時間：${fmt(item.time)}
內容：
${item.title}
${item.body.take(3500)}

系統依關鍵字初步比對到的題材與台股（僅供參考，可自行增減）：
$hint

只輸出 JSON，格式如下：
{"summary":"繁體中文 2~4 句重點摘要（英文請翻譯）","importance":1~5整數(5=對市場影響極大),"sentiment":"利多/利空/中性/多空交雜","sectors":["受影響產業"],"us_impact":"對美國科技與民生的影響","tw_impact":"對台股與台灣產業鏈的影響","stocks":[{"code":"2330","name":"台積電","action":"買進","confidence":0~100,"reason":"一句理由"}],"risk":"主要風險與不確定性"}
stocks 列出 2~6 檔最直接受影響的台股。$STOCK_RULE 若內容與市場無關，importance 給 1、stocks 給空陣列。
""".trimIndent()
    }

    fun brief(items: List<NewsItem>, markets: List<MarketQuote>): String {
        val lines = items.joinToString("\n") { "[${it.cat.label}][${it.source}][${fmt(it.time)}] ${it.title.take(200)}" }
        val mk = markets.joinToString("；") { "${it.label} ${"%.2f".format(it.price)} (${"%+.2f".format(it.pct)}%)" }.ifBlank { "（無資料）" }
        return """
你是資深的台股投資策略分析師。以下是最近 24~36 小時的美國財經新聞、川普發言與美國政策法案標題，以及最新市場指數。
請整理成給台灣投資人看的「今日美國消息 × 台股盤勢 AI 日報」。
現在時間：${now()}（台灣時間）
市場指數：$mk

消息列表：
$lines

只輸出 JSON：
{"mood":"偏多/偏空/中性/震盪","mood_score":-5~5整數,"headline":"一句話總結今天最重要的事","events":[{"title":"事件(繁中)","impact":"對台股影響說明","sectors":["產業"]}],"buy":[{"code":"","name":"","action":"買進","confidence":0~100,"reason":""}],"sell":[{"code":"","name":"","action":"賣出","confidence":0~100,"reason":""}],"watch":[{"code":"","name":"","action":"觀望","confidence":0~100,"reason":""}],"advice":"今日操作建議與風險提醒（3~5句）"}
events 挑 3~6 件最重要的；buy/sell/watch 各 0~5 檔。$STOCK_RULE
""".trimIndent()
    }

    fun stock(code: String, name: String, q: Quote?, themes: List<String>, us: List<NewsItem>, tw: List<NewsItem>): String {
        val price = if (q != null && q.price > 0) "現價 ${q.price}（${"%+.2f".format(q.pct)}%），昨收 ${q.prev}" else "報價暫無"
        val usLines = us.take(15).joinToString("\n") { "- [${fmt(it.time)}] ${it.title.take(180)}" }.ifBlank { "（無）" }
        val twLines = tw.take(12).joinToString("\n") { "- [${fmt(it.time)}] ${it.title.take(120)}" }.ifBlank { "（無）" }
        return """
你是資深台股分析師。請評估台股 $code $name 在目前美國政策與國際新聞環境下的操作建議。
現在時間：${now()}（台灣時間）
$price
所屬題材：${themes.joinToString("、").ifBlank { "未分類" }}

相關美國新聞／川普／政策：
$usLines

近期台灣個股新聞：
$twLines

只輸出 JSON：
{"action":"買進/賣出/觀望","confidence":0~100,"summary":"2~3句總結","short_term":"短線(1~2週)看法","mid_term":"中線(1~3個月)看法","reasons":["理由1","理由2","理由3"],"risks":["風險1","風險2"],"catalysts":["接下來要觀察的事件"]}
""".trimIndent()
    }
}
