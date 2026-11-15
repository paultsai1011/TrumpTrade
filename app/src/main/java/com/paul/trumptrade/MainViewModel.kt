package com.paul.trumptrade

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errors: List<String> = emptyList(),
    val lastUpdate: Long = 0L,
    val markets: List<MarketQuote> = emptyList(),
    val quotes: Map<String, Quote> = emptyMap(),
    val analyses: Map<String, Analysis> = emptyMap(),
    val analyzing: Set<String> = emptySet(),
    val batchRunning: Boolean = false,
    val brief: Brief? = null,
    val briefLoading: Boolean = false,
    val stockAnalyses: Map<String, StockAnalysis> = emptyMap(),
    val stockAnalyzing: Set<String> = emptySet(),
    val stockNews: Map<String, List<NewsItem>> = emptyMap(),
    val watchlist: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val message: String? = null,
    val aiStatus: String = "",
    val aiStart: Long = 0L,
    val update: Updater.Info? = null,
    val translations: Map<String, Translation> = emptyMap(),
    val translating: Boolean = false,
    val updateDismissed: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    private val _s = MutableStateFlow(
        UiState(
            items = store.loadItems(),
            analyses = store.loadAnalyses(),
            translations = store.loadTranslations(),
            stockAnalyses = store.loadStockAnalyses(),
            brief = store.loadBrief(),
            watchlist = store.watchlist,
        )
    )
    val state: StateFlow<UiState> = _s.asStateFlow()

    init {
        refresh()
        checkUpdate(false)
    }

    // ---------- App 內更新 ----------
    fun checkUpdate(manual: Boolean) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { Updater.check(getApplication()) }.getOrNull() }
            if (info != null) upd { it.copy(update = info, updateDismissed = false) }
            else if (manual) toast("目前已是最新版 v${Updater.currentName(getApplication())}")
        }
    }

    fun dismissUpdate() = upd { it.copy(updateDismissed = true) }

    fun startUpdate() {
        try { Updater.openDownload(getApplication()) } catch (e: Exception) { toast("無法開啟瀏覽器") }
        dismissUpdate()
    }

    private fun upd(f: (UiState) -> UiState) = _s.update(f)
    fun toast(m: String) = upd { it.copy(message = m) }
    fun clearMessage() = upd { it.copy(message = null) }

    private fun aiBegin() = upd { it.copy(aiStatus = "AI 準備中…", aiStart = System.currentTimeMillis()) }
    private val onAi: (String) -> Unit = { msg -> upd { it.copy(aiStatus = msg) } }
    private fun aiEnd() = upd { it.copy(aiStatus = "", aiStart = 0L) }

    fun nameOf(code: String): String =
        _s.value.quotes[code]?.name?.takeIf { it.isNotBlank() }
            ?: StockMap.nameOf(code).takeIf { it.isNotBlank() }
            ?: _s.value.analyses.values.flatMap { it.stocks }.firstOrNull { it.code == code }?.name
            ?: _s.value.stockAnalyses[code]?.name
            ?: ""

    fun refresh() {
        if (_s.value.loading) return
        upd { it.copy(loading = true) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { Repo.fetchAll(store) }
            val mk = withContext(Dispatchers.IO) { runCatching { Markets.fetch() }.getOrDefault(emptyList()) }
            if (res.items.isNotEmpty()) withContext(Dispatchers.IO) { store.saveItems(res.items) }
            mergeCloud()
            val an = withContext(Dispatchers.IO) { store.loadAnalyses() }
            upd {
                it.copy(
                    loading = false,
                    items = res.items.ifEmpty { it.items },
                    errors = res.errors,
                    lastUpdate = System.currentTimeMillis(),
                    markets = mk.ifEmpty { it.markets },
                    analyses = an,
                )
            }
            if (res.items.isEmpty()) toast("讀取失敗，請確認網路連線")
            refreshQuotes()
            if (store.autoTranslate) translateBatch(_s.value.items.filter { it.cat == Cat.TRUMP }, auto = true)
        }
    }

    /** 合併網頁版雲端翻譯與日報（雲端已翻的，App 就不再花額度翻） */
    private suspend fun mergeCloud() {
        val c = withContext(Dispatchers.IO) { runCatching { Repo.cloud() }.getOrNull() } ?: return
        val have = _s.value.translations
        val add = c.tr.filterKeys { it !in have }
        if (add.isNotEmpty()) {
            withContext(Dispatchers.IO) { store.saveTranslations(add) }
            val parsed = add.mapValues { parseTranslation(it.value) }
            upd { it.copy(translations = it.translations + parsed) }
        }
        val b = c.brief
        if (b != null && b.optLong("_t") > (_s.value.brief?.time ?: 0L) && b.s("mood").isNotBlank()) {
            withContext(Dispatchers.IO) { store.saveBrief(b) }
            upd { it.copy(brief = parseBrief(b)) }
        }
    }

    private var lastTrFail = 0L
    private val trAttempted = HashSet<String>()

    fun refreshQuotes(extra: Collection<String> = emptyList()) {
        val s = _s.value
        val codes = LinkedHashSet<String>()
        codes.addAll(extra)
        codes.addAll(s.watchlist)
        s.brief?.let { b -> (b.buy + b.sell + b.watch).forEach { codes.add(it.code) } }
        s.analyses.values.sortedByDescending { it.time }.take(30).forEach { a -> a.stocks.forEach { codes.add(it.code) } }
        val cutoff = System.currentTimeMillis() - 24 * 3600_000L
        s.items.filter { it.time > cutoff }.flatMap { it.themes }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(8)
            .forEach { e -> StockMap.theme(e.key)?.stocks?.forEach { codes.add(it.first) } }
        if (codes.isEmpty()) return
        viewModelScope.launch {
            val q = withContext(Dispatchers.IO) { TwQuote.fetch(codes.take(100)) }
            if (q.isNotEmpty()) upd { it.copy(quotes = it.quotes + q) }
        }
    }

    // ---------- AI：單則新聞 ----------
    private suspend fun analyzeNow(item: NewsItem): Analysis? {
        upd { it.copy(analyzing = it.analyzing + item.id) }
        aiBegin()
        return try {
            val o = withContext(Dispatchers.IO) { Gemini.ask(store, Prompts.news(item), onAi) }
            o.put("_t", System.currentTimeMillis())
            withContext(Dispatchers.IO) { store.saveAnalysis(item.id, o) }
            val a = parseAnalysis(o)
            upd { it.copy(analyses = it.analyses + (item.id to a), analyzing = it.analyzing - item.id) }
            refreshQuotes(a.stocks.map { it.code })
            a
        } catch (e: Exception) {
            upd { it.copy(analyzing = it.analyzing - item.id) }
            toast(e.message ?: "AI 分析失敗")
            null
        } finally {
            aiEnd()
        }
    }

    fun analyze(item: NewsItem) {
        if (item.id in _s.value.analyzing) return
        viewModelScope.launch { analyzeNow(item) }
    }

    fun analyzeBatch(items: List<NewsItem>, n: Int = 5) {
        if (_s.value.batchRunning) return
        val todo = items.filter { it.id !in _s.value.analyses }.take(n)
        if (todo.isEmpty()) { toast("這頁前面的新聞都分析過了"); return }
        upd { it.copy(batchRunning = true) }
        viewModelScope.launch {
            for ((i, it) in todo.withIndex()) {
                if (analyzeNow(it) == null) break
                if (i < todo.size - 1) delay(2500)
            }
            upd { it.copy(batchRunning = false) }
        }
    }

    // ---------- AI：批次翻譯 + 相關台股 ----------
    fun translateBatch(items: List<NewsItem>, auto: Boolean = false) {
        if (_s.value.translating) return
        if (store.geminiKey.isBlank()) { if (!auto) toast("請先到「設定」輸入 Gemini API Key"); return }
        if (auto && System.currentTimeMillis() - lastTrFail < 5 * 60_000L) return
        val todo = items.filter { it.id !in _s.value.translations && (!auto || it.id !in trAttempted) }.take(if (auto) 30 else 40)
        trAttempted.addAll(todo.map { it.id })
        if (todo.isEmpty()) { if (!auto) toast("這頁都翻譯好了"); return }
        upd { it.copy(translating = true) }
        aiBegin()
        viewModelScope.launch {
            try {
                val chunks = todo.chunked(10)
                for ((ci, chunk) in chunks.withIndex()) {
                    onAi("翻譯中 ${ci * 10 + 1}~${ci * 10 + chunk.size} / ${todo.size} 則")
                    val o = withContext(Dispatchers.IO) { Gemini.ask(store, Prompts.translate(chunk), lite = true) }
                    val arr = o.optJSONArray("items") ?: continue
                    val raw = HashMap<String, org.json.JSONObject>()
                    for (k in 0 until arr.length()) {
                        val e = arr.optJSONObject(k) ?: continue
                        val idx = e.n("i", k)
                        val item = chunk.getOrNull(idx) ?: continue
                        if (e.s("zh").isBlank()) continue
                        e.put("_t", System.currentTimeMillis())
                        raw[item.id] = e
                    }
                    withContext(Dispatchers.IO) { store.saveTranslations(raw) }
                    val parsed = raw.mapValues { parseTranslation(it.value) }
                    upd { it.copy(translations = it.translations + parsed) }
                    refreshQuotes(parsed.values.flatMap { t -> t.stocks.map { it.code } })
                    if (ci < chunks.size - 1) delay(1500)
                }
            } catch (e: Exception) {
                lastTrFail = System.currentTimeMillis()
                val m = e.message ?: "翻譯失敗"
                toast(if (m.contains("429")) "今天的免費 AI 額度暫時用完，5 分鐘後自動再試（雲端翻好的仍會顯示）" else m)
            } finally {
                upd { it.copy(translating = false) }
                aiEnd()
            }
        }
    }

    // ---------- AI：每日總結 ----------
    fun makeBrief() {
        if (_s.value.briefLoading) return
        val s = _s.value
        val cutoff = System.currentTimeMillis() - 36 * 3600_000L
        val recent = s.items.filter { it.time == 0L || it.time > cutoff }
        // 有台股題材的優先，總量控制在 40 則以內，AI 回得比較快
        fun top(c: Cat, n: Int) = recent.filter { it.cat == c }.sortedByDescending { if (it.themes.isNotEmpty()) 1 else 0 }.take(n)
        val pick = top(Cat.TRUMP, 12) + top(Cat.POLICY, 10) + top(Cat.NEWS, 18)
        if (pick.isEmpty()) { toast("還沒有新聞資料，請先重新整理"); return }
        upd { it.copy(briefLoading = true) }
        aiBegin()
        viewModelScope.launch {
            try {
                val o = withContext(Dispatchers.IO) { Gemini.ask(store, Prompts.brief(pick, s.markets), onAi) }
                o.put("_t", System.currentTimeMillis())
                withContext(Dispatchers.IO) { store.saveBrief(o) }
                val b = parseBrief(o)
                upd { it.copy(brief = b, briefLoading = false) }
                refreshQuotes((b.buy + b.sell + b.watch).map { it.code })
            } catch (e: Exception) {
                upd { it.copy(briefLoading = false) }
                toast(e.message ?: "AI 總結失敗")
            } finally {
                aiEnd()
            }
        }
    }

    // ---------- 個股 ----------
    fun relatedUsNews(code: String): List<NewsItem> {
        val themes = StockMap.themesOfStock(code).map { it.name }.toSet()
        val s = _s.value
        val byAi = s.analyses.filter { (_, a) -> a.stocks.any { it.code == code } }.keys +
                s.translations.filter { (_, t) -> t.stocks.any { it.code == code } }.keys
        return s.items.filter { it.id in byAi || it.themes.any { t -> t in themes } }.take(30)
    }

    fun loadStockNews(code: String) {
        viewModelScope.launch {
            val name = nameOf(code)
            val list = withContext(Dispatchers.IO) { runCatching { Repo.stockNews(code, name) }.getOrDefault(emptyList()) }
            upd { it.copy(stockNews = it.stockNews + (code to list)) }
        }
    }

    private suspend fun analyzeStockNow(code: String): Boolean {
        upd { it.copy(stockAnalyzing = it.stockAnalyzing + code) }
        aiBegin()
        return try {
            var q = _s.value.quotes[code]
            if (q == null) {
                q = withContext(Dispatchers.IO) { TwQuote.fetch(listOf(code)) }[code]
                q?.let { qq -> upd { it.copy(quotes = it.quotes + (code to qq)) } }
            }
            val name = nameOf(code)
            val cached = _s.value.stockNews[code]
            val tw: List<NewsItem> = if (cached != null) cached else {
                val fetched = withContext(Dispatchers.IO) { runCatching { Repo.stockNews(code, name) }.getOrDefault(emptyList()) }
                upd { it.copy(stockNews = it.stockNews + (code to fetched)) }
                fetched
            }
            val themes = StockMap.themesOfStock(code).map { it.name }
            val prompt = Prompts.stock(code, name, q, themes, relatedUsNews(code), tw)
            val o = withContext(Dispatchers.IO) { Gemini.ask(store, prompt, onAi) }
            o.put("_t", System.currentTimeMillis()).put("_code", code).put("_name", name)
            withContext(Dispatchers.IO) { store.saveStockAnalysis(code, o) }
            val sa = parseStockAnalysis(o)
            upd { it.copy(stockAnalyses = it.stockAnalyses + (code to sa), stockAnalyzing = it.stockAnalyzing - code) }
            true
        } catch (e: Exception) {
            upd { it.copy(stockAnalyzing = it.stockAnalyzing - code) }
            toast(e.message ?: "AI 分析失敗")
            false
        } finally {
            aiEnd()
        }
    }

    fun analyzeStock(code: String) {
        if (code in _s.value.stockAnalyzing) return
        viewModelScope.launch { analyzeStockNow(code) }
    }

    fun analyzeWatchlist() {
        if (_s.value.batchRunning) return
        val todo = _s.value.watchlist.take(6)
        upd { it.copy(batchRunning = true) }
        viewModelScope.launch {
            for ((i, c) in todo.withIndex()) {
                if (!analyzeStockNow(c)) break
                if (i < todo.size - 1) delay(2500)
            }
            upd { it.copy(batchRunning = false) }
        }
    }

    fun addWatch(raw: String) {
        val code = cleanCode(raw)
        if (!Regex("\\d{4,6}[A-Z]?").matches(code)) { toast("請輸入台股代號，例如 2330"); return }
        val list = (_s.value.watchlist + code).distinct()
        store.watchlist = list
        upd { it.copy(watchlist = list) }
        refreshQuotes(listOf(code))
    }

    fun removeWatch(code: String) {
        val list = _s.value.watchlist - code
        store.watchlist = list
        upd { it.copy(watchlist = list) }
    }

    // ---------- 設定 ----------
    fun loadModels(key: String) {
        viewModelScope.launch {
            try {
                val all = withContext(Dispatchers.IO) { Gemini.listModels(key.trim()) }
                val ranked = Gemini.rank(all)
                upd { it.copy(models = ranked.ifEmpty { all }) }
                if (ranked.isEmpty()) toast("找不到 Flash 模型")
            } catch (e: Exception) {
                toast(if (e is HttpException && e.code == 400) "API Key 無效" else "讀取模型失敗：${e.message}")
            }
        }
    }

    fun saveSettings(
        gemini: String, congress: String, model: String, notifyOn: Boolean, notifyAi: Boolean,
        interval: Int, keywords: String, autoTr: Boolean,
    ) {
        store.autoTranslate = autoTr
        val keyChanged = gemini.trim() != store.geminiKey
        store.geminiKey = gemini
        store.congressKey = congress
        store.model = model
        store.notifyOn = notifyOn
        store.notifyAi = notifyAi
        store.intervalMin = interval
        store.alertKeywords = keywords
        if (keyChanged) { store.modelCache = ""; store.modelCacheTime = 0L }
        App.schedule(getApplication())
        toast("已儲存")
        if (autoTr && store.geminiKey.isNotBlank()) translateBatch(_s.value.items.filter { it.cat == Cat.TRUMP }, auto = true)
    }
}
