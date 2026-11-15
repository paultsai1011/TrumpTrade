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
        Text("${s.aiStatus}… 已 ${sec} 秒", fontSize = 12.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp))
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
        d < 1 -> "剛剛"
        d < 60 -> "${d}分鐘前"
        d < 1440 -> "${d / 60}小時前"
        d < 1440 * 7 -> "${d / 1440}天前"
        else -> SimpleDateFormat("MM/dd", Locale.TAIWAN).format(Date(t))
    }
}

fun fullTime(t: Long): String =
    if (t <= 0) "" else SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN).format(Date(t))

private val tabs = listOf("總覽", "川普", "財經", "政策法案", "台股")

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
            title = { Text("發現新版本 ${up.name}") },
            text = {
                Column {
                    if (up.notes.isNotBlank()) Text(up.notes.take(600), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("按「立即更新」會用瀏覽器下載新版，下載完點通知列的檔案安裝即可，設定與紀錄都會保留。", fontSize = 12.sp, color = Neutral)
                }
            },
            confirmButton = {
                Button(onClick = { vm.startUpdate() }) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissUpdate() }) { Text("稍後") }
            }
        )
    }

    val title = when {
        settings -> "設定"
        stock != null -> "個股分析"
        detail != null -> "消息分析"
        else -> if (tab == 0) "川普財經雷達" else tabs[tab]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (inSub) IconButton(onClick = { goBack() }) { Icon(Icons.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (!inSub) {
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "重新整理") }
                        IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "設定") }
                    }
                }
            )
        },
        bottomBar = {
            if (!inSub) NavigationBar {
                val icons = listOf(Icons.Filled.Home, Icons.Filled.Person, Icons.Filled.List, Icons.Filled.Info, Icons.Filled.Star)
                tabs.forEachIndexed { i, t ->
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
        "⚠️ 以上為 AI 依公開新聞自動產生的分析，可能有誤，僅供參考，不構成投資建議。投資請自行判斷並承擔風險。",
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ActionBadge(action: String, confidence: Int? = null) {
    val c = actionColor(action)
    Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.18f)) {
        Text(
            action + (confidence?.let { " $it%" } ?: ""),
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
                Text("${p.name.ifBlank { q?.name ?: "" }}  ${p.code}", fontWeight = FontWeight.Bold)
            }
            PriceText(q)
            Spacer(Modifier.width(10.dp))
            ActionBadge(p.action, p.confidence.takeIf { it >= 0 })
        }
        if (p.reason.isNotBlank()) Text(p.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (sen != null) Text(sen, color = sentimentColor(sen), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            if (t != null && t.zh.isNotBlank()) {
                Text(t.zh, style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(item.title, fontSize = 12.sp, color = Neutral, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (a != null && a.summary.isNotBlank() && t == null) {
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
                            Text("${p.name} ${p.code} ${p.action}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp, color = c)
                        }
                    }
                }
            } else if (t == null && item.themes.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState())) { item.themes.forEach { Tag(it) } }
            } else if (t != null) {
                Text("與台股無直接關聯", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp))
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
                if (s.markets.isEmpty()) Text("市場指數載入中…", color = Neutral, fontSize = 12.sp)
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
                    Text("尚未設定 Gemini API Key", fontWeight = FontWeight.Bold)
                    Text("新聞可以照常看，但 AI 分析、買賣建議需要免費的 Gemini Key（和你 LINE 翻譯機器人用的同一把即可）。", fontSize = 13.sp)
                    TextButton(onClick = onSettings) { Text("前往設定") }
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
                    SectionTitle("🔥 24小時題材熱度")
                    counts.forEach { (name, c) ->
                        val th = StockMap.theme(name)
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("$name（$c 則）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (th != null) Row(Modifier.horizontalScroll(rememberScrollState())) {
                                th.stocks.forEach { (code, nm) ->
                                    val q = s.quotes[code]
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp, top = 4.dp).clickable { onStock(code) }
                                    ) {
                                        Text(
                                            "$nm " + (q?.let { "%+.1f%%".format(it.pct) } ?: code),
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
        item { SectionTitle("🇺🇸 川普最新動態") }
        val trump = s.items.filter { it.cat == Cat.TRUMP && it.source == "Truth Social" }.take(3)
            .ifEmpty { s.items.filter { it.cat == Cat.TRUMP }.take(3) }
        items(trump, key = { "o_" + it.id }) { NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock) { onNews(it) } }
        item { SectionTitle("📜 最新政策") }
        items(s.items.filter { it.cat == Cat.POLICY }.take(3), key = { "p_" + it.id }) {
            NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock) { onNews(it) }
        }
        item {
            if (s.lastUpdate > 0) Text("更新時間：${fullTime(s.lastUpdate)}", fontSize = 11.sp, color = Neutral)
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
                Text("🤖 AI 今日盤勢日報", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (s.briefLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Button(onClick = { vm.makeBrief() }) { Text(if (b == null) "產生" else "更新") }
            }
            if (s.briefLoading) AiProgress(s)
            if (b == null) {
                Text("按「產生」讓 AI 讀完最近的川普發言、政策與美股新聞，整理出台股可能的買進／賣出名單。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("${fullTime(b.time)} 產生", fontSize = 11.sp, color = Neutral)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.mood, color = sentimentColor(b.mood), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("情緒分數 %+d".format(b.moodScore), color = if (b.moodScore > 0) UpRed else if (b.moodScore < 0) DownGreen else Neutral)
            }
            if (b.headline.isNotBlank()) Text(b.headline, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            b.events.forEach { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("• ${e.title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (e.impact.isNotBlank()) Text(e.impact, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.sectors.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) { e.sectors.forEach { Tag(it) } }
                }
            }
            if (b.buy.isNotEmpty()) { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text("📈 可留意買進", color = UpRed, fontWeight = FontWeight.Bold) }
            b.buy.forEach { PickRow(it, s.quotes[it.code], onStock) }
            if (b.sell.isNotEmpty()) { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text("📉 可考慮賣出／避開", color = DownGreen, fontWeight = FontWeight.Bold) }
            b.sell.forEach { PickRow(it, s.quotes[it.code], onStock) }
            if (b.watch.isNotEmpty()) { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text("👀 觀察名單", fontWeight = FontWeight.Bold) }
            b.watch.forEach { PickRow(it, s.quotes[it.code], onStock) }
            if (b.advice.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("💡 " + b.advice, fontSize = 13.sp)
            }
        }
    }
}

// ---------------- 新聞列表 ----------------

@Composable
fun FeedList(vm: MainViewModel, s: UiState, cat: Cat, onStock: (String) -> Unit, onOpen: (NewsItem) -> Unit) {
    var onlyTw by rememberSaveable(cat.name) { mutableStateOf(false) }
    var q by rememberSaveable(cat.name) { mutableStateOf("") }
    LaunchedEffect(cat, s.items.size) {
        if (cat == Cat.TRUMP && vm.store.autoTranslate) vm.translateBatch(s.items.filter { it.cat == Cat.TRUMP }, auto = true)
    }
    val shown = s.items.filter { it.cat == cat }.filter { item ->
        (!onlyTw || item.themes.isNotEmpty() || (s.analyses[item.id]?.stocks?.isNotEmpty() == true) ||
                (s.translations[item.id]?.stocks?.isNotEmpty() == true)) &&
                (q.isBlank() || item.title.contains(q, true) || item.body.contains(q, true) ||
                        (s.analyses[item.id]?.summary?.contains(q, true) == true) ||
                        (s.translations[item.id]?.zh?.contains(q, true) == true))
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                label = { Text("搜尋（例：tariff、晶片、Nvidia）") }, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = onlyTw, onClick = { onlyTw = !onlyTw }, label = { Text("只看有台股關聯") })
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.analyzeBatch(shown) }, enabled = !s.batchRunning) {
                    Text(if (s.batchRunning) "分析中…" else "AI 分析前5則")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { vm.translateBatch(shown) }, enabled = !s.translating) {
                    Text(if (s.translating) "翻譯中…" else "🌐 翻譯成中文＋相關台股")
                }
            }
            if (s.batchRunning || s.translating) AiProgress(s)
            Legend()
            Text("共 ${shown.size} 則 · 已翻譯 ${shown.count { it.id in s.translations }} 則", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
        }
        if (shown.isEmpty()) item {
            Text(if (s.loading) "載入中…" else "目前沒有資料，請按右上角重新整理", color = Neutral, modifier = Modifier.padding(16.dp))
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
                Text("=影響大小　", fontSize = 12.sp)
                Text("利多", color = UpRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("／", fontSize = 12.sp)
                Text("利空", color = DownGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("／", fontSize = 12.sp)
                Text("中性", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (open) "收起 ▲" else "說明 ▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text("★ 星星：AI 判斷這則消息對市場的影響有多大", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("★ 影響很小（一般政治口水）　★★～★★★ 有點影響，值得留意　★★★★～★★★★★ 影響很大，相關股票可能明顯漲跌", fontSize = 12.sp, color = Neutral)
                Spacer(Modifier.height(6.dp))
                Text("右上角文字：對台股「整體」是好是壞", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("利多＝偏好消息　利空＝偏壞消息　中性＝沒有明顯好壞或方向不明　多空交雜＝有些公司受惠、有些受害", fontSize = 12.sp, color = Neutral)
                Spacer(Modifier.height(6.dp))
                Text("下方股票標籤：對「這一檔」的影響（漲紅跌綠）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(vertical = 2.dp)) {
                    Tag("可能受惠 利多", UpRed.copy(alpha = 0.18f), UpRed)
                    Tag("可能受害 利空", DownGreen.copy(alpha = 0.18f), DownGreen)
                    Tag("有關但方向不明 觀望", Neutral.copy(alpha = 0.18f), Neutral)
                }
                Text("點股票可看報價與 AI 買賣分析。以上皆為 AI 自動判斷，僅供參考，不構成投資建議。", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp))
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
            Text("${news.cat.label} · ${news.source} · ${fullTime(news.time)}", fontSize = 12.sp, color = Neutral)
            Spacer(Modifier.height(4.dp))
            val tr = s.translations[news.id]
            if (tr != null && tr.zh.isNotBlank()) {
                Text("🌐 中文翻譯", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(tr.zh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("原文", fontSize = 12.sp, color = Neutral)
                Text(if (news.body.length > news.title.length) news.body.take(3000) else news.title, fontSize = 13.sp, color = Neutral)
            } else {
                Text(news.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (news.body.isNotBlank() && news.body != news.title) {
                    Spacer(Modifier.height(6.dp))
                    Text(news.body.take(3000), fontSize = 14.sp)
                }
                if (s.translating) AiProgress(s)
                else TextButton(onClick = { vm.translateBatch(listOf(news)) }) { Text("🌐 翻譯成中文") }
            }
            if (tr != null && tr.stocks.isNotEmpty()) {
                SectionTitle("可能相關的台股")
                tr.stocks.forEach { PickRow(it, s.quotes[it.code], onStock) }
            }
            Row(Modifier.padding(top = 10.dp)) {
                if (news.link.isNotBlank()) OutlinedButton(onClick = { runCatching { uri.openUri(news.link) } }) { Text("開啟原文") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.analyze(news) }, enabled = !busy) { Text(if (a == null) "🤖 AI 分析影響" else "重新分析") }
            }
            if (busy) AiProgress(s)
        }
        if (news.themes.isNotEmpty()) item {
            SectionTitle("關鍵字比對到的題材")
            news.themes.mapNotNull { StockMap.theme(it) }.forEach { th ->
                Text(th.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    th.stocks.forEach { (code, nm) ->
                        Surface(
                            shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 6.dp, top = 4.dp, bottom = 6.dp).clickable { onStock(code) }
                        ) { Text("$nm $code", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp) }
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
                Text("🤖 AI 分析", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("重要度 " + "★".repeat(a.importance) + "☆".repeat(5 - a.importance), color = Color(0xFFFFC107), fontSize = 13.sp)
            }
            Text(a.sentiment, color = sentimentColor(a.sentiment), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            Text(a.summary, fontSize = 14.sp)
            if (a.sectors.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) { a.sectors.forEach { Tag(it) } }
            if (a.usImpact.isNotBlank()) { SectionTitle("🇺🇸 對美國科技／民生"); Text(a.usImpact, fontSize = 14.sp) }
            if (a.twImpact.isNotBlank()) { SectionTitle("🇹🇼 對台股"); Text(a.twImpact, fontSize = 14.sp) }
            if (a.stocks.isNotEmpty()) {
                SectionTitle("相關台股與建議")
                a.stocks.forEach { PickRow(it, quotes[it.code], onStock) }
            }
            if (a.risk.isNotBlank()) { SectionTitle("⚠️ 風險"); Text(a.risk, fontSize = 13.sp) }
            Text("分析時間 ${fullTime(a.time)}", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
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
                    label = { Text("加入自選股代號") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.addWatch(input); input = "" }) { Text("加入") }
            }
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = { vm.refreshQuotes() }) { Text("更新報價") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.analyzeWatchlist() }, enabled = !s.batchRunning) {
                    Text(if (s.batchRunning) "分析中…" else "🤖 AI 分析自選股")
                }
            }
            SectionTitle("⭐ 自選股")
        }
        items(s.watchlist, key = { "w_$it" }) { code ->
            WatchRow(code, vm.nameOf(code), s.quotes[code], s.stockAnalyses[code], code in s.stockAnalyzing,
                onClick = { onStock(code) }, onDelete = { vm.removeWatch(code) })
        }
        // AI 提及統計
        val picks = s.analyses.values.filter { System.currentTimeMillis() - it.time < 3 * 86400_000L }.flatMap { it.stocks }
        if (picks.isNotEmpty()) {
            item { SectionTitle("🤖 近3天 AI 分析提及的個股") }
            val grouped = picks.groupBy { it.code }.entries.sortedByDescending { it.value.size }.take(20)
            items(grouped, key = { "ai_" + it.key }) { (code, list) ->
                val buy = list.count { it.action.contains("買") }
                val sell = list.count { it.action.contains("賣") }
                Card(onClick = { onStock(code) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${list.first().name.ifBlank { vm.nameOf(code) }}  $code", fontWeight = FontWeight.Bold)
                            Text("提及 ${list.size} 次 · 買進 $buy · 賣出 $sell · 觀望 ${list.size - buy - sell}", fontSize = 12.sp, color = Neutral)
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
            item { SectionTitle("📰 24小時新聞題材關聯台股") }
            items(counts, key = { "m_" + it.key }) { (code, c) ->
                Card(onClick = { onStock(code) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${vm.nameOf(code)}  $code", fontWeight = FontWeight.Bold)
                            Text("相關新聞 $c 則 · ${mention[code]?.joinToString("、") ?: ""}", fontSize = 12.sp, color = Neutral, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text("${name.ifBlank { q?.name ?: "" }}  $code", fontWeight = FontWeight.Bold)
                if (sa != null) Text(sa.summary, fontSize = 12.sp, color = Neutral, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            PriceText(q)
            Spacer(Modifier.width(8.dp))
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else if (sa != null) ActionBadge(sa.action, sa.confidence)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "移除", tint = Neutral) }
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
                    Text(name.ifBlank { code }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(code + (q?.time?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = Neutral)
                }
                PriceText(q)
            }
            val themes = StockMap.themesOfStock(code)
            if (themes.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) { themes.forEach { Tag(it.name) } }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                Button(onClick = { vm.analyzeStock(code) }, enabled = !busy) { Text(if (sa == null) "🤖 AI 買賣分析" else "重新分析") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (inWatch) vm.removeWatch(code) else vm.addWatch(code) }) { Text(if (inWatch) "移除自選" else "加入自選") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { runCatching { uri.openUri("https://tw.stock.yahoo.com/quote/$code") } }) { Text("Yahoo股市") }
            }
            if (busy) AiProgress(s)
        }
        if (sa != null) item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 AI 建議", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ActionBadge(sa.action, sa.confidence)
                    }
                    Text(sa.summary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    if (sa.shortTerm.isNotBlank()) { SectionTitle("短線（1~2週）"); Text(sa.shortTerm, fontSize = 14.sp) }
                    if (sa.midTerm.isNotBlank()) { SectionTitle("中線（1~3個月）"); Text(sa.midTerm, fontSize = 14.sp) }
                    if (sa.reasons.isNotEmpty()) { SectionTitle("理由"); sa.reasons.forEach { Text("• $it", fontSize = 13.sp) } }
                    if (sa.risks.isNotEmpty()) { SectionTitle("⚠️ 風險"); sa.risks.forEach { Text("• $it", fontSize = 13.sp) } }
                    if (sa.catalysts.isNotEmpty()) { SectionTitle("👀 接下來觀察"); sa.catalysts.forEach { Text("• $it", fontSize = 13.sp) } }
                    Text("分析時間 ${fullTime(sa.time)}", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Disclaimer() }
        if (related.isNotEmpty()) {
            item { SectionTitle("🇺🇸 相關美國消息") }
            items(related.take(15), key = { "r_" + it.id }) { NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock = {}) { onNews(it) } }
        }
        val tw = s.stockNews[code]
        item { SectionTitle("🇹🇼 台灣個股新聞") }
        if (tw == null) item { Text("載入中…", color = Neutral) }
        else if (tw.isEmpty()) item { Text("沒有找到近期新聞", color = Neutral) }
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
            SectionTitle("🤖 Gemini AI（免費）")
            OutlinedTextField(
                value = key, onValueChange = { key = it }, singleLine = true, label = { Text("Gemini API Key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隱藏" else "顯示") } }
            )
            TextButton(onClick = { runCatching { uri.openUri("https://aistudio.google.com/apikey") } }) { Text("到 Google AI Studio 取得免費 Key →") }
            Text("Key 只存在你的手機裡，不會上傳到 GitHub。", fontSize = 12.sp, color = Neutral)
        }
        item {
            Text("AI 模型", fontWeight = FontWeight.Bold)
            Text("建議用「自動」：會自動挑最新的免費 Flash 模型，額度用完會自動換下一個。目前使用：${st.lastModel.ifBlank { "尚未使用" }}", fontSize = 12.sp, color = Neutral)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { model = "auto" }) {
                RadioButton(selected = model == "auto", onClick = { model = "auto" }); Text("自動（推薦）")
            }
            s.models.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { model = m }) {
                    RadioButton(selected = model == m, onClick = { model = m }); Text(m, fontSize = 14.sp)
                }
            }
            OutlinedButton(onClick = { vm.loadModels(key) }, enabled = key.isNotBlank()) { Text("讀取我可用的模型") }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("川普貼文自動翻譯成中文")
                    Text("每次更新自動翻譯最新 30 則並標出相關台股（每 10 則用 1 次 AI 額度）", fontSize = 12.sp, color = Neutral)
                }
                Switch(checked = autoTr, onCheckedChange = { autoTr = it })
            }
        }
        item {
            HorizontalDivider()
            SectionTitle("🔔 背景通知")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("川普新貼文／白宮新命令通知", Modifier.weight(1f)); Switch(checked = notifyOn, onCheckedChange = { notifyOn = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("通知附 AI 分析與相關台股")
                    Text("每次最多分析 2 則，會用到一點免費額度", fontSize = 12.sp, color = Neutral)
                }
                Switch(checked = notifyAi, onCheckedChange = { notifyAi = it })
            }
            Text("檢查頻率", fontWeight = FontWeight.Bold)
            Row {
                listOf(15, 30, 60, 120).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { interval = m }) {
                        RadioButton(selected = interval == m, onClick = { interval = m })
                        Text(if (m < 60) "${m}分" else "${m / 60}小時", fontSize = 13.sp)
                    }
                }
            }
            OutlinedTextField(
                value = kw, onValueChange = { kw = it }, label = { Text("通知關鍵字（逗號分隔）") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )
            TextButton(onClick = { kw = Store.DEFAULT_KW }) { Text("恢復預設關鍵字") }
            OutlinedButton(onClick = {
                if (Notifier.canNotify(ctx)) Notifier.show(ctx, 1, "★★★★ 利空｜測試通知", "這是測試通知。之後川普發文提到關稅、晶片等關鍵字時，會像這樣通知你並附上相關台股。")
                else vm.toast("請到手機設定允許本 App 的通知權限")
            }) { Text("發送測試通知") }
            Text("提醒：部分手機（小米、OPPO、vivo、三星省電模式）會擋背景工作，請把本 App 的電池設為「不限制」。", fontSize = 12.sp, color = Neutral)
        }
        item {
            HorizontalDivider()
            SectionTitle("🏛️ 國會法案（選填）")
            OutlinedTextField(
                value = congress, onValueChange = { congress = it }, singleLine = true,
                label = { Text("Congress.gov API Key") }, modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { runCatching { uri.openUri("https://api.congress.gov/sign-up/") } }) { Text("免費申請 Congress.gov Key →") }
            Text("不填也可以，政策分頁仍會顯示白宮行政命令、商務部/USTR 公告與國會法案新聞。", fontSize = 12.sp, color = Neutral)
        }
        item {
            HorizontalDivider()
            SectionTitle("📲 App 版本")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目前版本 v${Updater.currentName(ctx)}", Modifier.weight(1f))
                OutlinedButton(onClick = { vm.checkUpdate(true) }) { Text("檢查更新") }
            }
        }
        item {
            Button(
                onClick = { vm.saveSettings(key, congress, model, notifyOn, notifyAi, interval, kw, autoTr) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("儲存設定") }
            Text("資料來源：Trump's Truth (trumpstruth.org)、Google News、CNBC、MarketWatch、Yahoo Finance、Federal Register、Congress.gov、台灣證交所、Yahoo 財經。", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 10.dp))
            Disclaimer()
        }
    }
}
