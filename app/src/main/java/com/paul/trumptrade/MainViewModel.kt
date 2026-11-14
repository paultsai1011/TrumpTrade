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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    private val _s = MutableStateFlow(
        UiState(
            items = store.loadItems(),
            analyses = store.loadAnalyses(),
            stockAnalyses = store.loadStockAnalyses(),
            brief = store.loadBrief(),
            watchlist = store.watchlist,
        )
    )
    val state: StateFlow<UiState> = _s.asStateFlow()

    init {
        refresh()
    }

    private fun upd(f: (UiState) -> UiState) = _s.update(f)
    fun toast(m: String) = upd { it.copy(message = m) }
    fun clearMessage() = upd { it.copy(message = null) }

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
        }
    }

    fun refreshQuotes(extra: Collection<String> = emptyList()) {
        val s = _s.value
        val codes = LinkedHashSet<String>()
        codes.addAll(extra)
        codes.addAll(s.watchlist)
        s.brief?.let { b -> (b.buy + b.sell + b.watch).forEach { codes.add(it.code) } }
        s.analyses.values.sortedByDescending { it.time }.take(30).forEach { a -> a.stocks.forEach { codes.add(it.code) } }
        if (codes.isEmpty()) return
        viewModelScope.launch {
            val q = withContext(Dispatchers.IO) { TwQuote.fetch(codes.take(100)) }
            if (q.isNotEmpty()) upd { it.copy(quotes = it.quotes + q) }
        }
    }

    // ---------- AI：單則新聞 ----------
    private suspend fun analyzeNow(item: NewsItem): Analysis? {
        upd { it.copy(analyzing = it.analyzing + item.id) }
        return try {
            val o = withContext(Dispatchers.IO) { Gemini.ask(store, Prompts.news(item)) }
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

    // ---------- AI：每日總結 ----------
    fun makeBrief() {
        if (_s.value.briefLoading) return
        val s = _s.value
        val cutoff = System.currentTimeMillis() - 36 * 3600_000L
        val recent = s.items.filter { it.time == 0L || it.time > cutoff }
        val pick = (recent.filter { it.cat == Cat.TRUMP }.take(20) +
                recent.filter { it.cat == Cat.POLICY }.take(15) +
                recent.filter { it.cat == Cat.NEWS }.take(30))
        if (pick.isEmpty()) { toast("還沒有新聞資料，請先重新整理"); return }
        upd { it.copy(briefLoading = true) }
        viewModelScope.launch {
            try {
                val o = withContext(Dispatchers.IO) { Gemini.ask(store, Prompts.brief(pick, s.markets)) }
                o.put("_t", System.currentTimeMillis())
                withContext(Dispatchers.IO) { store.saveBrief(o) }
                val b = parseBrief(o)
                upd { it.copy(brief = b, briefLoading = false) }
                refreshQuotes((b.buy + b.sell + b.watch).map { it.code })
            } catch (e: Exception) {
                upd { it.copy(briefLoading = false) }
                toast(e.message ?: "AI 總結失敗")
            }
        }
    }

    // ---------- 個股 ----------
    fun relatedUsNews(code: String): List<NewsItem> {
        val themes = StockMap.themesOfStock(code).map { it.name }.toSet()
        val s = _s.value
        val byAi = s.analyses.filter { (_, a) -> a.stocks.any { it.code == code } }.keys
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
            val o = withContext(Dispatchers.IO) { Gemini.ask(store, prompt) }
            o.put("_t", System.currentTimeMillis()).put("_code", code).put("_name", name)
            withContext(Dispatchers.IO) { store.saveStockAnalysis(code, o) }
            val sa = parseStockAnalysis(o)
            upd { it.copy(stockAnalyses = it.stockAnalyses + (code to sa), stockAnalyzing = it.stockAnalyzing - code) }
            true
        } catch (e: Exception) {
            upd { it.copy(stockAnalyzing = it.stockAnalyzing - code) }
            toast(e.message ?: "AI 分析失敗")
            false
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
        interval: Int, keywords: String,
    ) {
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
    }
}
