@file:OptIn(ExperimentalMaterial3Api::class)

package com.paul.trumptrade

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiProgress(s: UiState) {
    if (s.aiStatus.isBlank()) return
    var sec by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.aiStart) {
        while (true) {
            sec = ((System.currentTimeMillis() - s.aiStart) / 1000).toInt()
            kotlinx.coroutines.delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(tr("${s.aiStatus}… 已 ${sec} 秒", "${s.aiStatus}… ${sec}s"), fontSize = 12.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp))
    }
}

// 台灣習慣：漲紅跌綠
val UpRed = Color(0xFFEF5350)
val DownGreen = Color(0xFF26A69A)
val Neutral = Color(0xFF9E9E9E)

fun pctColor(p: Double): Color = when {
    p > 0.0001 -> UpRed; p < -0.0001 -> DownGreen; else -> Neutral
}

fun actionColor(a: String): Color = when {
    a.contains("買") || a == "利多" -> UpRed; a.contains("賣") || a == "利空" -> DownGreen; else -> Neutral
}

fun sentimentColor(s: String): Color = when {
    s.contains("多") && !s.contains("空") -> UpRed
    s.contains("空") && !s.contains("多") -> DownGreen
    else -> Color(0xFFFFB74D)
}

fun ago(t: Long): String {
    if (t <= 0) return ""
    val d = (System.currentTimeMillis() - t) / 60000
    return when {
        d < 1 -> tr("剛剛", "just now")
        d < 60 -> tr("${d}分鐘前", "${d}m ago")
        d < 1440 -> tr("${d / 60}小時前", "${d / 60}h ago")
        d < 1440 * 7 -> tr("${d / 1440}天前", "${d / 1440}d ago")
        else -> SimpleDateFormat("MM/dd", Locale.TAIWAN).format(Date(t))
    }
}

fun fullTime(t: Long): String =
    if (t <= 0) "" else SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN).format(Date(t))

private fun tabs() = listOf(tr("總覽", "Home"), tr("川普", "Trump"), tr("財經", "Markets"), tr("政策法案", "Policy"), tr("台股", "TW Stocks"))

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<NewsItem?>(null) }
    var stock by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(s.message) {
        val m = s.message
        if (m != null) {
            vm.clearMessage()
            snack.showSnackbar(m)
        }
    }

    val inSub = detail != null || stock != null || settings
    fun goBack() {
        if (stock != null) stock = null
        else if (detail != null) detail = null
        else settings = false
    }
    BackHandler(enabled = inSub) { goBack() }

    val up = s.update
    if (up != null && !s.updateDismissed) {
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text(tr("發現新版本 ${up.name}", "New version ${up.name}")) },
            text = {
                Column {
                    if (up.notes.isNotBlank()) Text(up.notes.take(600), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("按「立即更新」會用瀏覽器下載新版，下載完點通知列的檔案安裝即可，設定與紀錄都會保留。", "Tap Update to download in your browser, then open the file to install. Settings are kept."), fontSize = 12.sp, color = Neutral)
                }
            },
            confirmButton = {
                Button(onClick = { vm.startUpdate() }) { Text(tr("立即更新", "Update now")) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissUpdate() }) { Text(tr("稍後", "Later")) }
            }
        )
    }

    val title = when {
        settings -> tr("設定", "Settings")
        stock != null -> tr("個股分析", "Stock analysis")
        detail != null -> tr("消息分析", "Analysis")
        else -> if (tab == 0) tr("川普財經雷達", "Trump Market Radar") else tabs()[tab]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (inSub) IconButton(onClick = { goBack() }) { Icon(Icons.Filled.ArrowBack, tr("返回", "Back")) }
                },
                actions = {
                    TextButton(onClick = { vm.setLang(!Lang.en) }) { Text(if (Lang.en) "中文" else "EN", fontWeight = FontWeight.Bold) }
                    if (!inSub) {
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, tr("重新整理", "Refresh")) }
                        IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, tr("設定", "Settings")) }
                    }
                }
            )
        },
        bottomBar = {
            if (!inSub) NavigationBar {
                val icons = listOf(Icons.Filled.Home, Icons.Filled.Person, Icons.Filled.List, Icons.Filled.Info, Icons.Filled.Star)
                tabs().forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], null) },
                        label = { Text(t, fontSize = 11.sp) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val st = stock
            val dt = detail
            when {
                settings -> SettingsScreen(vm, s)
                st != null -> StockScreen(vm, s, st, onNews = { detail = it; stock = null })
                dt != null -> DetailScreen(vm, s, dt, onStock = { stock = it })
                else -> when (tab) {
                    0 -> Overview(vm, s, onStock = { stock = it }, onNews = { detail = it }, onSettings = { settings = true })
                    1 -> FeedList(vm, s, Cat.TRUMP, onStock = { stock = it }) { detail = it }
                    2 -> FeedList(vm, s, Cat.NEWS, onStock = { stock = it }) { detail = it }
                    3 -> FeedList(vm, s, Cat.POLICY, onStock = { stock = it }) { detail = it }
                    else -> StockTab(vm, s) { stock = it }
                }
            }
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

// ---------------- 共用元件 ----------------

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.padding(end = 6.dp, top = 4.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = textColor)
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun Disclaimer() {
    Text(
        tr("⚠️ 以上為 AI 依公開新聞自動產生的分析，可能有誤，僅供參考，不構成投資建議。投資請自行判斷並承擔風險。", "⚠️ AI-generated from public news; may be wrong. For reference only, not investment advice."),
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ActionBadge(action: String, confidence: Int? = null) {
    val c = actionColor(action)
    Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.18f)) {
        Text(
            term(action) + (confidence?.let { " $it%" } ?: ""),
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PriceText(q: Quote?) {
    if (q == null || q.price <= 0) {
        Text("--", color = Neutral, fontSize = 13.sp); return
    }
    val c = pctColor(q.pct)
    Column(horizontalAlignment = Alignment.End) {
        Text("%.2f".format(q.price), color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("%+.2f (%+.2f%%)".format(q.change, q.pct), color = c, fontSize = 11.sp)
    }
}

@Composable
fun PickRow(p: StockPick, q: Quote?, onStock: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onStock(p.code) }.padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${StockMap.label(p.code, p.name.ifBlank { q?.name ?: "" })}  ${p.code}", fontWeight = FontWeight.Bold)
            }
            PriceText(q)
            Spacer(Modifier.width(10.dp))
            ActionBadge(p.action, p.confidence.takeIf { it >= 0 })
        }
        if (p.reasonShown.isNotBlank()) Text(p.reasonShown, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NewsCard(
    item: NewsItem, a: Analysis?, analyzing: Boolean, t: Translation? = null,
    onStock: (String) -> Unit = {}, onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.source} · ${ago(item.time)}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1
                )
                if (analyzing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                val imp = a?.importance ?: t?.importance
                val sen = a?.sentiment ?: t?.sentiment
                if (imp != null) {
                    Text("★".repeat(imp), color = Color(0xFFFFC107), fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                }
                if (sen != null) Text(term(sen), color = sentimentColor(sen), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            if (t != null && t.zh.isNotBlank() && !Lang.en) {
                Text(t.zh, style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(item.title, fontSize = 12.sp, color = Neutral, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (a != null && a.summary.isNotBlank() && (t == null || Lang.en)) {
                Spacer(Modifier.height(4.dp))
                Text("🤖 " + a.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val picks = (t?.stocks ?: emptyList()).ifEmpty { a?.stocks ?: emptyList() }
            if (picks.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp)) {
                    picks.forEach { p ->
                        val c = actionColor(p.action)
                        Surface(
                            shape = RoundedCornerShape(50), color = c.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 6.dp, top = 4.dp).clickable { onStock(p.code) }
                        ) {
                            Text("${StockMap.label(p.code, p.name)} ${p.code} ${term(p.action)}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp, color = c)
                        }
                    }
                }
            } else if (t == null && item.themes.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState())) { item.themes.forEach { Tag(StockMap.themeLabel(it)) } }
            } else if (t != null) {
                Text(tr("與台股無直接關聯", "No direct Taiwan stock link"), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------------- 總覽 ----------------

@Composable
fun Overview(vm: MainViewModel, s: UiState, onStock: (String) -> Unit, onNews: (NewsItem) -> Unit, onSettings: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                if (s.markets.isEmpty()) Text(tr("市場指數載入中…", "Loading market indices…"), color = Neutral, fontSize = 12.sp)
                s.markets.forEach { m ->
                    Card(Modifier.padding(end = 8.dp)) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(m.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%,.2f".format(m.price), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("%+.2f%%".format(m.pct), color = pctColor(m.pct), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (vm.store.geminiKey.isBlank()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(tr("尚未設定 Gemini API Key", "Gemini API key not set"), fontWeight = FontWeight.Bold)
                    Text(tr("新聞可以照常看，但 AI 分析、買賣建議需要免費的 Gemini Key（和你 LINE 翻譯機器人用的同一把即可）。", "News works without it, but AI analysis and buy/sell ideas need a free Gemini key."), fontSize = 13.sp)
                    TextButton(onClick = onSettings) { Text(tr("前往設定", "Open settings")) }
                }
            }
        }
        item { BriefCard(vm, s, onStock) }
        item {
            val cutoff = System.currentTimeMillis() - 24 * 3600_000L
            val counts = s.items.filter { it.time > cutoff }.flatMap { it.themes }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(8)
            if (counts.isNotEmpty()) Card {
                Column(Modifier.padding(12.dp)) {
                    SectionTitle(tr("🔥 24小時題材熱度", "🔥 24h hot themes"))
                    counts.forEach { (name, c) ->
                        val th = StockMap.theme(name)
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(tr("$name（$c 則）", "${StockMap.themeLabel(name)} ($c)"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (th != null) Row(Modifier.horizontalScroll(rememberScrollState())) {
                                th.stocks.forEach { (code, nm) ->
                                    val q = s.quotes[code]
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp, top = 4.dp).clickable { onStock(code) }
                                    ) {
                                        Text(
                                            "${StockMap.label(code, nm)} " + (q?.let { "%+.1f%%".format(it.pct) } ?: code),
                                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp,
                                            color = q?.let { pctColor(it.pct) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle(tr("🇺🇸 川普最新動態", "🇺🇸 Latest from Trump")) }
        val trump = s.items.filter { it.cat == Cat.TRUMP && it.source == "Truth Social" }.take(3)
            .ifEmpty { s.items.filter { it.cat == Cat.TRUMP }.take(3) }
        items(trump, key = { "o_" + it.id }) { NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock) { onNews(it) } }
        item { SectionTitle(tr("📜 最新政策", "📜 Latest policy")) }
        items(s.items.filter { it.cat == Cat.POLICY }.take(3), key = { "p_" + it.id }) {
            NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock) { onNews(it) }
        }
        item {
            if (s.lastUpdate > 0) Text(tr("更新時間：${fullTime(s.lastUpdate)}", "Updated ${fullTime(s.lastUpdate)}"), fontSize = 11.sp, color = Neutral)
            if (s.errors.isNotEmpty()) Text(s.errors.joinToString("、"), fontSize = 11.sp, color = Neutral)
            Disclaimer()
        }
    }
}

@Composable
fun BriefCard(vm: MainViewModel, s: UiState, onStock: (String) -> Unit) {
    val b = s.brief
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🤖 AI 今日盤勢日報", "🤖 AI daily brief"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (s.briefLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Button(onClick = { vm.makeBrief() }) { Text(if (b == null) tr("產生", "Generate") else tr("更新", "Update")) }
            }
            if (s.briefLoading) AiProgress(s)
            if (b == null) {
                Text(tr("按「產生」讓 AI 讀完最近的川普發言、政策與美股新聞，整理出台股可能的買進／賣出名單。", "Tap Generate: AI reads recent Trump remarks, policy and US market news and lists Taiwan stocks to buy / sell."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(tr("${fullTime(b.time)} 產生", "Generated ${fullTime(b.time)}"), fontSize = 11.sp, color = Neutral)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(term(b.mood), color = sentimentColor(b.mood), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(tr("情緒分數 %+d", "Sentiment %+d").format(b.moodScore), color = if (b.moodScore > 0) UpRed else if (b.moodScore < 0) DownGreen else Neutral)
            }
            val headline = if (Lang.en && b.headlineEn.isNotBlank()) b.headlineEn else b.headline
            if (headline.isNotBlank()) Text(headline, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            b.events.forEach { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("• " + (if (Lang.en && e.titleEn.isNotBlank()) e.titleEn else e.title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    val impact = if (Lang.en && e.impactEn.isNotBlank()) e.impactEn else e.impact
                    if (impact.isNotBlank()) Text(impact, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.sectors.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) { e.sectors.forEach { Tag(it) } }
                }
            }
            if (b.buy.isNotEmpty()) { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text(tr("📈 可留意買進", "📈 Consider buying"), color = UpRed, fontWeight = FontWeight.Bold) }
            b.buy.forEach { PickRow(it, s.quotes[it.code], onStock) }
            if (b.sell.isNotEmpty()) { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text(tr("📉 可考慮賣出／避開", "📉 Consider selling / avoid"), color = DownGreen, fontWeight = FontWeight.Bold) }
            b.sell.forEach { PickRow(it, s.quotes[it.code], onStock) }
            if (b.watch.isNotEmpty()) { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text(tr("👀 觀察名單", "👀 Watch"), fontWeight = FontWeight.Bold) }
            b.watch.forEach { PickRow(it, s.quotes[it.code], onStock) }
            val advice = if (Lang.en && b.adviceEn.isNotBlank()) b.adviceEn else b.advice
            if (advice.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("💡 " + advice, fontSize = 13.sp)
            }
        }
    }
}

// ---------------- 新聞列表 ----------------

@Composable
fun FeedList(vm: MainViewModel, s: UiState, cat: Cat, onStock: (String) -> Unit, onOpen: (NewsItem) -> Unit) {
    var onlyTw by rememberSaveable(cat.name) { mutableStateOf(false) }
    var q by rememberSaveable(cat.name) { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(TrumpKind.ALL) }
    LaunchedEffect(cat, s.items.size, s.translations.size, s.translating) {
        if (cat == Cat.TRUMP && vm.store.autoTranslate && !Lang.en) vm.translateBatch(s.items.filter { it.cat == Cat.TRUMP }, auto = true)
    }
    val shown = s.items.filter { it.cat == cat }
        .filter { cat != Cat.TRUMP || kind == TrumpKind.ALL || trumpKind(it) == kind }
        .filter { item ->
        (!onlyTw || item.themes.isNotEmpty() || (s.analyses[item.id]?.stocks?.isNotEmpty() == true) ||
                (s.translations[item.id]?.stocks?.isNotEmpty() == true)) &&
                (q.isBlank() || item.title.contains(q, true) || item.body.contains(q, true) ||
                        (s.analyses[item.id]?.summary?.contains(q, true) == true) ||
                        (s.translations[item.id]?.zh?.contains(q, true) == true))
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (cat == Cat.TRUMP) {
                val all = s.items.filter { it.cat == Cat.TRUMP }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
                    TrumpKind.values().forEach { k ->
                        val n = if (k == TrumpKind.ALL) all.size else all.count { trumpKind(it) == k }
                        FilterChip(
                            selected = kind == k, onClick = { kind = k },
                            label = { Text("${kindLabel(k)} $n") }, modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                label = { Text(tr("搜尋（例：tariff、晶片、Nvidia）", "Search (e.g. tariff, chips, Nvidia)")) }, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = onlyTw, onClick = { onlyTw = !onlyTw }, label = { Text(tr("只看有台股關聯", "Taiwan-related only")) })
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.analyzeBatch(shown) }, enabled = !s.batchRunning) {
                    Text(if (s.batchRunning) tr("分析中…", "Analyzing…") else tr("AI 分析前5則", "AI: top 5"))
                }
            }
            if (!Lang.en) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { vm.translateBatch(shown) }, enabled = !s.translating) {
                    Text(if (s.translating) tr("翻譯中…", "Translating…") else tr("🌐 翻譯成中文＋相關台股", "🌐 Translate + tag stocks"))
                }
            }
            if (s.batchRunning || s.translating) AiProgress(s)
            Legend()
            Text(tr("共 ${shown.size} 則 · 已翻譯 ${shown.count { it.id in s.translations }} 則", "${shown.size} items"), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
        }
        if (shown.isEmpty()) item {
            Text(if (s.loading) tr("載入中…", "Loading…") else tr("目前沒有資料，請按右上角重新整理", "No data yet. Tap refresh at the top right."), color = Neutral, modifier = Modifier.padding(16.dp))
        }
        items(shown, key = { it.id }) { item ->
            NewsCard(item, s.analyses[item.id], item.id in s.analyzing, s.translations[item.id], onStock) { onOpen(item) }
        }
    }
}

@Composable
fun Legend() {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(onClick = { open = !open }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ⓘ ", fontSize = 13.sp)
                Text("★", color = Color(0xFFFFC107), fontSize = 12.sp)
                Text(tr("=影響大小　", "=impact　"), fontSize = 12.sp)
                Text(term("利多"), color = UpRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("／", fontSize = 12.sp)
                Text(term("利空"), color = DownGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("／", fontSize = 12.sp)
                Text(term("中性"), color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (open) tr("收起 ▲", "Hide ▲") else tr("說明 ▼", "Help ▼"), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text(tr("★ 星星：AI 判斷這則消息對市場的影響有多大", "★ Stars: how much AI thinks this moves the market"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(tr("★ 影響很小（一般政治口水）　★★～★★★ 有點影響，值得留意　★★★★～★★★★★ 影響很大，相關股票可能明顯漲跌", "★ minor (political noise)　★★–★★★ worth watching　★★★★–★★★★★ major, related stocks may move a lot"), fontSize = 12.sp, color = Neutral)
                Spacer(Modifier.height(6.dp))
                Text(tr("右上角文字：對台股「整體」是好是壞", "Top-right label: overall effect on Taiwan stocks"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(tr("利多＝偏好消息　利空＝偏壞消息　中性＝沒有明顯好壞或方向不明　多空交雜＝有些公司受惠、有些受害", "Bullish = good news　Bearish = bad news　Neutral = no clear direction　Mixed = some companies win, some lose"), fontSize = 12.sp, color = Neutral)
                Spacer(Modifier.height(6.dp))
                Text(tr("下方股票標籤：對「這一檔」的影響（漲紅跌綠）", "Stock tags: impact on that stock (red = up, green = down, Taiwan style)"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(vertical = 2.dp)) {
                    Tag(tr("可能受惠 利多", "May benefit · Bullish"), UpRed.copy(alpha = 0.18f), UpRed)
                    Tag(tr("可能受害 利空", "May suffer · Bearish"), DownGreen.copy(alpha = 0.18f), DownGreen)
                    Tag(tr("有關但方向不明 觀望", "Related, unclear · Hold"), Neutral.copy(alpha = 0.18f), Neutral)
                }
                Text(tr("點股票可看報價與 AI 買賣分析。以上皆為 AI 自動判斷，僅供參考，不構成投資建議。", "Tap a stock for quotes and AI buy/sell analysis. AI judgments only, not investment advice."), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------------- 單則分析 ----------------

@Composable
fun DetailScreen(vm: MainViewModel, s: UiState, news: NewsItem, onStock: (String) -> Unit) {
    val a = s.analyses[news.id]
    val busy = news.id in s.analyzing
    val uri = LocalUriHandler.current
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${catLabel(news.cat)} · ${news.source} · ${fullTime(news.time)}", fontSize = 12.sp, color = Neutral)
            Spacer(Modifier.height(4.dp))
            val trx = s.translations[news.id]
            if (trx != null && trx.zh.isNotBlank() && !Lang.en) {
                Text(tr("🌐 中文翻譯", "🌐 Chinese translation"), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(trx.zh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(tr("原文", "Original"), fontSize = 12.sp, color = Neutral)
                Text(if (news.body.length > news.title.length) news.body.take(3000) else news.title, fontSize = 13.sp, color = Neutral)
            } else {
                Text(news.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (news.body.isNotBlank() && news.body != news.title) {
                    Spacer(Modifier.height(6.dp))
                    Text(news.body.take(3000), fontSize = 14.sp)
                }
                if (!Lang.en) {
                    if (s.translating) AiProgress(s)
                    else TextButton(onClick = { vm.translateBatch(listOf(news)) }) { Text("🌐 翻譯成中文") }
                }
            }
            if (trx != null && trx.stocks.isNotEmpty()) {
                SectionTitle(tr("可能相關的台股", "Possibly related Taiwan stocks"))
                trx.stocks.forEach { PickRow(it, s.quotes[it.code], onStock) }
            }
            Row(Modifier.padding(top = 10.dp)) {
                if (news.link.isNotBlank()) OutlinedButton(onClick = { runCatching { uri.openUri(news.link) } }) { Text(tr("開啟原文", "Open source")) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.analyze(news) }, enabled = !busy) { Text(if (a == null) tr("🤖 AI 分析影響", "🤖 AI impact analysis") else tr("重新分析", "Re-analyze")) }
            }
            if (busy) AiProgress(s)
        }
        if (news.themes.isNotEmpty()) item {
            SectionTitle(tr("關鍵字比對到的題材", "Matched themes"))
            news.themes.mapNotNull { StockMap.theme(it) }.forEach { th ->
                Text(StockMap.themeLabel(th.name), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    th.stocks.forEach { (code, nm) ->
                        Surface(
                            shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 6.dp, top = 4.dp, bottom = 6.dp).clickable { onStock(code) }
                        ) { Text("${StockMap.label(code, nm)} $code", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp) }
                    }
                }
            }
        }
        if (a != null) item { AnalysisCard(a, s.quotes, onStock) }
        item { Disclaimer() }
    }
}

@Composable
fun AnalysisCard(a: Analysis, quotes: Map<String, Quote>, onStock: (String) -> Unit) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🤖 AI 分析", "🤖 AI analysis"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(tr("重要度 ", "Impact ") + "★".repeat(a.importance) + "☆".repeat(5 - a.importance), color = Color(0xFFFFC107), fontSize = 13.sp)
            }
            Text(term(a.sentiment), color = sentimentColor(a.sentiment), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            Text(a.summary, fontSize = 14.sp)
            if (a.sectors.isNotEmpty() && !Lang.en) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) { a.sectors.forEach { Tag(it) } }
            if (a.usImpact.isNotBlank()) { SectionTitle(tr("🇺🇸 對美國科技／民生", "🇺🇸 US tech / economy")); Text(a.usImpact, fontSize = 14.sp) }
            if (a.twImpact.isNotBlank()) { SectionTitle(tr("🇹🇼 對台股", "🇹🇼 Taiwan stocks")); Text(a.twImpact, fontSize = 14.sp) }
            if (a.stocks.isNotEmpty()) {
                SectionTitle(tr("相關台股與建議", "Related Taiwan stocks"))
                a.stocks.forEach { PickRow(it, quotes[it.code], onStock) }
            }
            if (a.risk.isNotBlank()) { SectionTitle(tr("⚠️ 風險", "⚠️ Risks")); Text(a.risk, fontSize = 13.sp) }
            Text(tr("分析時間 ${fullTime(a.time)}", "Analyzed ${fullTime(a.time)}"), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------- 台股分頁 ----------------

@Composable
fun StockTab(vm: MainViewModel, s: UiState, onStock: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text(tr("加入自選股代號", "Add stock code")) }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.addWatch(input); input = "" }) { Text(tr("加入", "Add")) }
            }
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = { vm.refreshQuotes() }) { Text(tr("更新報價", "Refresh quotes")) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.analyzeWatchlist() }, enabled = !s.batchRunning) {
                    Text(if (s.batchRunning) tr("分析中…", "Analyzing…") else tr("🤖 AI 分析自選股", "🤖 AI: analyze watchlist"))
                }
            }
            SectionTitle(tr("⭐ 自選股", "⭐ Watchlist"))
        }
        items(s.watchlist, key = { "w_$it" }) { code ->
            WatchRow(code, vm.nameOf(code), s.quotes[code], s.stockAnalyses[code], code in s.stockAnalyzing,
                onClick = { onStock(code) }, onDelete = { vm.removeWatch(code) })
        }
        // AI 提及統計
        val picks = s.analyses.values.filter { System.currentTimeMillis() - it.time < 3 * 86400_000L }.flatMap { it.stocks }
        if (picks.isNotEmpty()) {
            item { SectionTitle(tr("🤖 近3天 AI 分析提及的個股", "🤖 Stocks AI mentioned (3 days)")) }
            val grouped = picks.groupBy { it.code }.entries.sortedByDescending { it.value.size }.take(20)
            items(grouped, key = { "ai_" + it.key }) { (code, list) ->
                val buy = list.count { it.action.contains("買") }
                val sell = list.count { it.action.contains("賣") }
                Card(onClick = { onStock(code) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${StockMap.label(code, list.first().name.ifBlank { vm.nameOf(code) })}  $code", fontWeight = FontWeight.Bold)
                            Text(tr("提及 ${list.size} 次 · 買進 $buy · 賣出 $sell · 觀望 ${list.size - buy - sell}", "Mentioned ${list.size}× · Buy $buy · Sell $sell · Hold ${list.size - buy - sell}"), fontSize = 12.sp, color = Neutral)
                        }
                        PriceText(s.quotes[code])
                    }
                }
            }
        }
        // 新聞題材統計
        val cutoff = System.currentTimeMillis() - 24 * 3600_000L
        val mention = HashMap<String, MutableSet<String>>()
        s.items.filter { it.time > cutoff }.forEach { item ->
            item.themes.mapNotNull { StockMap.theme(it) }.forEach { th -> th.stocks.forEach { (c, _) -> mention.getOrPut(c) { HashSet() }.add(th.name) } }
        }
        val counts = s.items.filter { it.time > cutoff }.flatMap { item ->
            item.themes.mapNotNull { StockMap.theme(it) }.flatMap { th -> th.stocks.map { it.first } }.distinct()
        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(15)
        if (counts.isNotEmpty()) {
            item { SectionTitle(tr("📰 24小時新聞題材關聯台股", "📰 Stocks linked to 24h news themes")) }
            items(counts, key = { "m_" + it.key }) { (code, c) ->
                Card(onClick = { onStock(code) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${StockMap.label(code, vm.nameOf(code))}  $code", fontWeight = FontWeight.Bold)
                            Text(tr("相關新聞 $c 則 · ", "$c related news · ") + (mention[code]?.joinToString("、") { StockMap.themeLabel(it) } ?: ""), fontSize = 12.sp, color = Neutral, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        PriceText(s.quotes[code])
                    }
                }
            }
        }
        item { Disclaimer() }
    }
}

@Composable
fun WatchRow(code: String, name: String, q: Quote?, sa: StockAnalysis?, busy: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${StockMap.label(code, name.ifBlank { q?.name ?: "" })}  $code", fontWeight = FontWeight.Bold)
                if (sa != null) Text(sa.summary, fontSize = 12.sp, color = Neutral, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            PriceText(q)
            Spacer(Modifier.width(8.dp))
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else if (sa != null) ActionBadge(sa.action, sa.confidence)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, tr("移除", "Remove"), tint = Neutral) }
        }
    }
}

// ---------------- 個股頁 ----------------

@Composable
fun StockScreen(vm: MainViewModel, s: UiState, code: String, onNews: (NewsItem) -> Unit) {
    LaunchedEffect(code) {
        vm.refreshQuotes(listOf(code))
        if (s.stockNews[code] == null) vm.loadStockNews(code)
    }
    val uri = LocalUriHandler.current
    val q = s.quotes[code]
    val name = vm.nameOf(code)
    val sa = s.stockAnalyses[code]
    val busy = code in s.stockAnalyzing
    val inWatch = code in s.watchlist
    val related = vm.relatedUsNews(code)
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(StockMap.label(code, name).ifBlank { code }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(code + (q?.time?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = Neutral)
                }
                PriceText(q)
            }
            val themes = StockMap.themesOfStock(code)
            if (themes.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) { themes.forEach { Tag(StockMap.themeLabel(it.name)) } }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                Button(onClick = { vm.analyzeStock(code) }, enabled = !busy) { Text(if (sa == null) tr("🤖 AI 買賣分析", "🤖 AI buy/sell analysis") else tr("重新分析", "Re-analyze")) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (inWatch) vm.removeWatch(code) else vm.addWatch(code) }) { Text(if (inWatch) tr("移除自選", "Remove from watchlist") else tr("加入自選", "Add to watchlist")) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { runCatching { uri.openUri("https://tw.stock.yahoo.com/quote/$code") } }) { Text(tr("Yahoo股市", "Yahoo")) }
            }
            if (busy) AiProgress(s)
        }
        if (sa != null) item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("🤖 AI 建議", "🤖 AI view"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ActionBadge(sa.action, sa.confidence)
                    }
                    Text(sa.summary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    if (sa.shortTerm.isNotBlank()) { SectionTitle(tr("短線（1~2週）", "Short term (1–2 weeks)")); Text(sa.shortTerm, fontSize = 14.sp) }
                    if (sa.midTerm.isNotBlank()) { SectionTitle(tr("中線（1~3個月）", "Mid term (1–3 months)")); Text(sa.midTerm, fontSize = 14.sp) }
                    if (sa.reasons.isNotEmpty()) { SectionTitle(tr("理由", "Reasons")); sa.reasons.forEach { Text("• $it", fontSize = 13.sp) } }
                    if (sa.risks.isNotEmpty()) { SectionTitle(tr("⚠️ 風險", "⚠️ Risks")); sa.risks.forEach { Text("• $it", fontSize = 13.sp) } }
                    if (sa.catalysts.isNotEmpty()) { SectionTitle(tr("👀 接下來觀察", "👀 What to watch")); sa.catalysts.forEach { Text("• $it", fontSize = 13.sp) } }
                    Text(tr("分析時間 ${fullTime(sa.time)}", "Analyzed ${fullTime(sa.time)}"), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Disclaimer() }
        if (related.isNotEmpty()) {
            item { SectionTitle(tr("🇺🇸 相關美國消息", "🇺🇸 Related US news")) }
            items(related.take(15), key = { "r_" + it.id }) { NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock = {}) { onNews(it) } }
        }
        val tw = s.stockNews[code]
        item { SectionTitle(tr("🇹🇼 台灣個股新聞", "🇹🇼 Taiwan news")) }
        if (tw == null) item { Text(tr("載入中…", "Loading…"), color = Neutral) }
        else if (tw.isEmpty()) item { Text(tr("沒有找到近期新聞", "No recent news"), color = Neutral) }
        else items(tw, key = { "tw_" + it.id }) { n ->
            Card(onClick = { runCatching { uri.openUri(n.link) } }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${n.source} · ${ago(n.time)}", fontSize = 11.sp, color = Neutral)
                    Text(n.title, fontSize = 14.sp)
                }
            }
        }
    }
}

// ---------------- 設定 ----------------

@Composable
fun SettingsScreen(vm: MainViewModel, s: UiState) {
    val st = vm.store
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf(st.geminiKey) }
    var showKey by remember { mutableStateOf(false) }
    var congress by remember { mutableStateOf(st.congressKey) }
    var model by remember { mutableStateOf(st.model) }
    var notifyOn by remember { mutableStateOf(st.notifyOn) }
    var notifyAi by remember { mutableStateOf(st.notifyAi) }
    var interval by remember { mutableIntStateOf(st.intervalMin) }
    var kw by remember { mutableStateOf(st.alertKeywords) }
    var autoTr by remember { mutableStateOf(st.autoTranslate) }

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionTitle("🌐 語言 Language")
            Row {
                listOf(false to "中文", true to "English").forEach { (en, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.setLang(en) }) {
                        RadioButton(selected = Lang.en == en, onClick = { vm.setLang(en) }); Text(label)
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        item {
            SectionTitle(tr("🤖 Gemini AI（免費）", "🤖 Gemini AI (free)"))
            OutlinedTextField(
                value = key, onValueChange = { key = it }, singleLine = true, label = { Text("Gemini API Key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) tr("隱藏", "Hide") else tr("顯示", "Show")) } }
            )
            TextButton(onClick = { runCatching { uri.openUri("https://aistudio.google.com/apikey") } }) { Text(tr("到 Google AI Studio 取得免費 Key →", "Get a free key at Google AI Studio →")) }
            Text(tr("Key 只存在你的手機裡，不會上傳到 GitHub。", "The key stays on your phone only."), fontSize = 12.sp, color = Neutral)
        }
        item {
            Text(tr("AI 模型", "AI model"), fontWeight = FontWeight.Bold)
            Text(tr("建議用「自動」：會自動挑最新的免費 Flash 模型，額度用完會自動換下一個。目前使用：", "\"Auto\" picks the newest free Flash model and switches when quota runs out. Current: ") + st.lastModel.ifBlank { tr("尚未使用", "not used yet") }, fontSize = 12.sp, color = Neutral)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { model = "auto" }) {
                RadioButton(selected = model == "auto", onClick = { model = "auto" }); Text(tr("自動（推薦）", "Auto (recommended)"))
            }
            s.models.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { model = m }) {
                    RadioButton(selected = model == m, onClick = { model = m }); Text(m, fontSize = 14.sp)
                }
            }
            OutlinedButton(onClick = { vm.loadModels(key) }, enabled = key.isNotBlank()) { Text(tr("讀取我可用的模型", "Load my available models")) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("川普貼文自動翻譯成中文", "Auto-translate Trump posts to Chinese"))
                    Text(tr("每次更新自動翻譯最新 30 則並標出相關台股（每 10 則用 1 次 AI 額度）", "Translates the latest 30 posts on each refresh and tags related Taiwan stocks (1 AI call per 10)"), fontSize = 12.sp, color = Neutral)
                }
                Switch(checked = autoTr, onCheckedChange = { autoTr = it })
            }
        }
        item {
            HorizontalDivider()
            SectionTitle(tr("🔔 背景通知", "🔔 Background alerts"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("川普新貼文／白宮新命令通知", "Alerts for new Trump posts / White House orders"), Modifier.weight(1f)); Switch(checked = notifyOn, onCheckedChange = { notifyOn = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("通知附 AI 分析與相關台股", "Include AI analysis in alerts"))
                    Text(tr("每次最多分析 2 則，會用到一點免費額度", "Up to 2 per check, uses a little free quota"), fontSize = 12.sp, color = Neutral)
                }
                Switch(checked = notifyAi, onCheckedChange = { notifyAi = it })
            }
            Text(tr("檢查頻率", "Check interval"), fontWeight = FontWeight.Bold)
            Row {
                listOf(15, 30, 60, 120).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { interval = m }) {
                        RadioButton(selected = interval == m, onClick = { interval = m })
                        Text(if (m < 60) tr("${m}分", "${m}m") else tr("${m / 60}小時", "${m / 60}h"), fontSize = 13.sp)
                    }
                }
            }
            OutlinedTextField(
                value = kw, onValueChange = { kw = it }, label = { Text(tr("通知關鍵字（逗號分隔）", "Alert keywords (comma separated)")) },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )
            TextButton(onClick = { kw = Store.DEFAULT_KW }) { Text(tr("恢復預設關鍵字", "Reset default keywords")) }
            OutlinedButton(onClick = {
                if (Notifier.canNotify(ctx)) Notifier.show(ctx, 1, tr("★★★★ 利空｜測試通知", "★★★★ Bearish | Test notification"), tr("這是測試通知。之後川普發文提到關稅、晶片等關鍵字時，會像這樣通知你並附上相關台股。", "Test notification. When Trump posts about tariffs, chips etc. you'll get alerts like this with related Taiwan stocks."))
                else vm.toast(tr("請到手機設定允許本 App 的通知權限", "Please allow notifications for this app in phone settings"))
            }) { Text(tr("發送測試通知", "Send test notification")) }
            Text(tr("提醒：部分手機（小米、OPPO、vivo、三星省電模式）會擋背景工作，請把本 App 的電池設為「不限制」。", "Note: some phones (Xiaomi, OPPO, vivo, Samsung power saving) block background work. Set this app's battery to Unrestricted."), fontSize = 12.sp, color = Neutral)
        }
        item {
            HorizontalDivider()
            SectionTitle(tr("🏛️ 國會法案（選填）", "🏛️ Congress bills (optional)"))
            OutlinedTextField(
                value = congress, onValueChange = { congress = it }, singleLine = true,
                label = { Text("Congress.gov API Key") }, modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { runCatching { uri.openUri("https://api.congress.gov/sign-up/") } }) { Text(tr("免費申請 Congress.gov Key →", "Get a free Congress.gov key →")) }
            Text(tr("不填也可以，政策分頁仍會顯示白宮行政命令、商務部/USTR 公告與國會法案新聞。", "Optional. The Policy tab still shows White House orders, Commerce/USTR notices and bill news."), fontSize = 12.sp, color = Neutral)
        }
        item {
            HorizontalDivider()
            SectionTitle(tr("📲 App 版本", "📲 App version"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("目前版本 v${Updater.currentName(ctx)}", "Version v${Updater.currentName(ctx)}"), Modifier.weight(1f))
                OutlinedButton(onClick = { vm.checkUpdate(true) }) { Text(tr("檢查更新", "Check for updates")) }
            }
        }
        item {
            Button(
                onClick = { vm.saveSettings(key, congress, model, notifyOn, notifyAi, interval, kw, autoTr) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("儲存設定", "Save settings")) }
            Text(tr("資料來源：Trump's Truth (trumpstruth.org)、Google News、CNBC、MarketWatch、Yahoo Finance、Federal Register、Congress.gov、台灣證交所、Yahoo 財經。", "Sources: Trump's Truth, Google News, CNBC, MarketWatch, Yahoo Finance, Federal Register, Congress.gov, TWSE, Yahoo."), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 10.dp))
            Disclaimer()
        }
    }
}
