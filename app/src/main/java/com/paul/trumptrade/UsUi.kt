package com.paul.trumptrade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UsPrice(q: UsQuote?) {
    if (q == null) { Text("--", color = Neutral, fontSize = 13.sp); return }
    val c = pctColor(q.pct)
    Column(horizontalAlignment = Alignment.End) {
        Text("$%.2f".format(q.price), color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("%+.2f (%+.2f%%)".format(q.change, q.pct), color = c, fontSize = 11.sp)
    }
}

/** 台股分頁裡的美股自選區塊（放在 LazyColumn 的 item 裡） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsWatchSection(vm: MainViewModel, s: UiState, onUs: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    LaunchedEffect(s.usWatch) { vm.loadUs(s.usWatch) }
    Column {
        SectionTitle(tr("🇺🇸 美股自選", "🇺🇸 US watchlist"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.uppercase() }, singleLine = true,
                label = { Text(tr("加入美股代號，例如 NVDA", "Add a US ticker, e.g. NVDA")) }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addUsWatch(input); input = "" }) { Text(tr("加入", "Add")) }
        }
        // 熱門快速加入
        val quick = Us.list.map { it.t }.filter { it !in s.usWatch }.take(12)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            quick.forEach { t ->
                Text("+ $t", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp).clickable { vm.addUsWatch(t) }.padding(vertical = 4.dp))
            }
        }
        if (s.usWatch.isEmpty()) Text(tr("還沒有美股自選", "No US stocks yet"), color = Neutral, fontSize = 13.sp)
        s.usWatch.forEach { t ->
            val q = s.usQuotes[t]
            val tk = vm.usTech(t)
            val sa = s.stockAnalyses[t]
            Card(onClick = { onUs(t) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(Us.name(t, q?.name ?: ""), fontSize = 12.sp, color = Neutral, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val tech = tk?.second
                        if (tech != null && tech.ok) Text("🕯️" + tech.label, fontSize = 11.sp, color = techColor(tech), fontWeight = FontWeight.Bold)
                        if (sa != null) Text(sa.summary, fontSize = 12.sp, color = Neutral, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    UsPrice(q)
                    if (sa != null) { Spacer(Modifier.width(6.dp)); ActionBadge(sa.action, sa.confidence) }
                    IconButton(onClick = { vm.removeUsWatch(t) }) { Icon(Icons.Filled.Delete, tr("移除", "Remove"), tint = Neutral) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsStockScreen(vm: MainViewModel, s: UiState, t: String, onStock: (String) -> Unit, onNews: (NewsItem) -> Unit) {
    LaunchedEffect(t) {
        vm.loadUs(listOf(t))
        Us.map[t]?.tw?.let { vm.refreshQuotes(it) }
        while (true) {
            kotlinx.coroutines.delay(30_000)
            vm.loadUs(listOf(t), withK = false)
        }
    }
    val q = s.usQuotes[t]
    val info = Us.map[t]
    val sa = s.stockAnalyses[t]
    val busy = t in s.stockAnalyzing
    val inWatch = t in s.usWatch
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val tk = remember(s.klines[t], q) { vm.usTech(t) }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(Us.name(t, q?.name ?: "") + (q?.time?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = Neutral, fontSize = 13.sp)
                }
                UsPrice(q)
            }
            if (q != null && q.extPrice > 0) Text(tr("盤前／盤後：", "Extended hours: ") + "$%.2f（%+.2f%%）".format(q.extPrice, q.extPct), fontSize = 12.sp, color = pctColor(q.extPct))
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                Button(onClick = { vm.analyzeUs(t) }, enabled = !busy) { Text(if (sa == null) tr("🤖 AI 分析＋台股連動", "🤖 AI + Taiwan impact") else tr("重新分析", "Re-analyze")) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (inWatch) vm.removeUsWatch(t) else vm.addUsWatch(t) }) { Text(if (inWatch) tr("移除自選", "Remove from watchlist") else tr("加入自選", "Add to watchlist")) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { runCatching { uri.openUri("https://finance.yahoo.com/quote/$t") } }) { Text("Yahoo") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { Share.send(ctx, Share.us(t, q, sa, tk?.second, s.quotes)) }) {
                    Icon(Icons.Filled.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("分享", "Share"))
                }
            }
            if (busy) AiProgress(s)
        }
        if (info != null && info.tw.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(tr("🔗 台股連動（供應鏈／相關）", "🔗 Linked Taiwan stocks"), fontWeight = FontWeight.Bold)
                    info.tw.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { onStock(c) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${StockMap.label(c, vm.nameOf(c))}  $c", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            PriceText(s.quotes[c])
                        }
                    }
                }
            }
        }
        item { TechSection(tk?.first, tk?.second, s.klines.containsKey(t)) }
        if (sa != null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("🤖 AI 建議", "🤖 AI view"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ActionBadge(sa.action, sa.confidence)
                    }
                    Text(sa.summary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    if (sa.tech.isNotBlank()) { SectionTitle(tr("技術面", "Technicals")); Text(sa.tech, fontSize = 14.sp) }
                    if (sa.shortTerm.isNotBlank()) { SectionTitle(tr("短線（1~2週）", "Short term (1–2 weeks)")); Text(sa.shortTerm, fontSize = 14.sp) }
                    if (sa.midTerm.isNotBlank()) { SectionTitle(tr("中線（1~3個月）", "Mid term (1–3 months)")); Text(sa.midTerm, fontSize = 14.sp) }
                    if (sa.twImpact.isNotBlank()) { SectionTitle(tr("🇹🇼 對台股的影響", "🇹🇼 Impact on Taiwan stocks")); Text(sa.twImpact, fontSize = 14.sp) }
                    sa.twStocks.forEach { PickRow(it, s.quotes[it.code], onStock) }
                    if (sa.reasons.isNotEmpty()) { SectionTitle(tr("理由", "Reasons")); sa.reasons.forEach { Text("• $it", fontSize = 13.sp) } }
                    if (sa.risks.isNotEmpty()) { SectionTitle(tr("⚠️ 風險", "⚠️ Risks")); sa.risks.forEach { Text("• $it", fontSize = 13.sp) } }
                    if (sa.catalysts.isNotEmpty()) { SectionTitle(tr("👀 接下來觀察", "👀 What to watch")); sa.catalysts.forEach { Text("• $it", fontSize = 13.sp) } }
                    Text(tr("分析時間 ", "Analyzed ") + fullTime(sa.time), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Disclaimer() }
        val rel = vm.usNews(t)
        if (rel.isNotEmpty()) {
            item { SectionTitle(tr("📰 相關新聞", "📰 Related news")) }
            items(rel, key = { "usn_" + it.id }) { NewsCard(it, s.analyses[it.id], it.id in s.analyzing, s.translations[it.id], onStock) { onNews(it) } }
        }
    }
}
