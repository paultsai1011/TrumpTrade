package com.paul.trumptrade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val Gold = Color(0xFFFFC107)
private val Blue = Color(0xFF8AB4F8)

private fun signed(v: Int?): String = when {
    v == null -> "–"; v > 0 -> "+%,d".format(v); else -> "%,d".format(v)
}
private fun intColor(v: Int?): Color = when { v == null || v == 0 -> Neutral; v > 0 -> UpRed; else -> DownGreen }
private fun md(d: Int): String { val s = d.toString(); return if (s.length == 8) "${s.substring(4, 6)}/${s.substring(6)}" else s }

@Composable
fun Bars(values: List<Double>, height: Int = 70, color: Color? = null, trend: Boolean = false) {
    if (values.isEmpty()) return
    val vals = if (trend) {
        val lo = values.min(); val hi = values.max()
        values.map { if (hi > lo) 0.15 + (it - lo) / (hi - lo) else 1.0 }
    } else values
    val signedChart = vals.any { it < 0 }
    val max = vals.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(height.dp).padding(vertical = 4.dp)) {
        val bw = size.width / vals.size
        val mid = if (signedChart) size.height / 2 else size.height
        val span = if (signedChart) size.height / 2 else size.height
        vals.forEachIndexed { i, v ->
            val bh = (abs(v) / max * span).toFloat()
            val top = if (v >= 0) mid - bh else mid
            drawRect(
                color = color ?: (if (v >= 0) UpRed else DownGreen),
                topLeft = Offset(i * bw + 2f, top), size = Size((bw - 4f).coerceAtLeast(1f), bh)
            )
        }
        if (signedChart) drawLine(axis, Offset(0f, mid), Offset(size.width, mid), 1f)
    }
}

@Composable
private fun Cell(t: String, w: Float, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false, start: Boolean = false) {
    Text(
        t, Modifier.fillMaxWidth(w), fontSize = 12.5.sp, color = color, textAlign = if (start) TextAlign.Start else TextAlign.End,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1
    )
}

@Composable
private fun TableRow(cells: List<Triple<String, Color, Boolean>>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { i, (t, c, b) ->
            Box(Modifier.weight(1f)) { Cell(t, 1f, c, b, start = i == 0) }
        }
    }
}

@Composable
fun ChipsSection(q: Quote?, c: ChipData?, loaded: Boolean) {
    // ---- 五檔 ----
    if (q != null && (q.bidP.isNotEmpty() || q.askP.isNotEmpty())) {
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("📊 即時五檔", "📊 Live order book"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(tr("成交量 ", "Vol ") + "%,d".format(q.vol) + tr(" 張", " lots"), fontSize = 12.sp, color = Neutral)
                }
                val sub = Neutral
                TableRow(listOf(Triple(tr("委買量", "Bid qty"), sub, false), Triple(tr("委買價", "Bid"), sub, false), Triple(tr("委賣價", "Ask"), sub, false), Triple(tr("委賣量", "Ask qty"), sub, false)))
                for (i in 0 until 5) {
                    TableRow(listOf(
                        Triple(q.bidV.getOrNull(i)?.toString() ?: "", UpRed, false),
                        Triple(q.bidP.getOrNull(i)?.let { "%.2f".format(it) } ?: "", UpRed, false),
                        Triple(q.askP.getOrNull(i)?.let { "%.2f".format(it) } ?: "", DownGreen, false),
                        Triple(q.askV.getOrNull(i)?.toString() ?: "", DownGreen, false),
                    ))
                }
                Text(
                    tr("五檔合計　買 ${q.bidV.sum()}／賣 ${q.askV.sum()} 張 · 每 10 秒自動更新", "Top-5 total bid ${q.bidV.sum()} / ask ${q.askV.sum()} lots · refreshes every 10s"),
                    fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
    if (c == null) {
        Text(if (loaded) tr("這檔暫無籌碼資料", "No chip data for this stock") else tr("籌碼資料載入中…", "Loading chip data…"), color = Neutral, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        return
    }
    val days = c.days
    // ---- 三大法人 ----
    if (days.isNotEmpty()) {
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(tr("🏦 三大法人買賣超（張）", "🏦 Institutional net buy/sell (lots)"), fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(tr("外資", "Foreign") to c.sum(5) { it.foreign }, tr("投信", "Trust") to c.sum(5) { it.trust }, tr("自營", "Dealer") to c.sum(5) { it.dealer })
                        .forEach { (l, v) -> Text("$l 5${tr("日", "d")} " + signed(v), fontSize = 12.sp, color = intColor(v), fontWeight = FontWeight.Bold) }
                }
                val t20 = c.sum(20) { it.total }
                Text("20${tr("日合計", "d total")} " + signed(t20), fontSize = 12.sp, color = intColor(t20), fontWeight = FontWeight.Bold)
                Bars(days.takeLast(20).map { (it.total ?: 0).toDouble() })
                val sub = Neutral
                TableRow(listOf(Triple(tr("日期", "Date"), sub, false), Triple(tr("外資", "Foreign"), sub, false), Triple(tr("投信", "Trust"), sub, false), Triple(tr("自營", "Dealer"), sub, false), Triple(tr("合計", "Total"), sub, false)))
                days.takeLast(10).reversed().forEach { d ->
                    TableRow(listOf(
                        Triple(md(d.date), MaterialTheme.colorScheme.onSurface, false),
                        Triple(signed(d.foreign), intColor(d.foreign), false),
                        Triple(signed(d.trust), intColor(d.trust), false),
                        Triple(signed(d.dealer), intColor(d.dealer), false),
                        Triple(signed(d.total), intColor(d.total), true),
                    ))
                }
            }
        }
        // ---- 融資融券 ----
        val m = days.filter { it.margin != null }
        if (m.isNotEmpty()) {
            val l = m.last(); val p1 = m.getOrNull(m.size - 2); val p5 = m[maxOf(0, m.size - 6)]
            fun diff(a: Int?, b: Int?): Int? = if (a == null || b == null) null else a - b
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(tr("💳 融資融券（張）", "💳 Margin / short (lots)"), fontWeight = FontWeight.Bold)
                    val sub = Neutral
                    TableRow(listOf(Triple("", sub, false), Triple(tr("餘額", "Balance"), sub, false), Triple(tr("單日", "1d"), sub, false), Triple(tr("5日", "5d"), sub, false)))
                    val on = MaterialTheme.colorScheme.onSurface
                    TableRow(listOf(Triple(tr("融資", "Margin"), on, false), Triple("%,d".format(l.margin ?: 0), on, false),
                        Triple(signed(diff(l.margin, p1?.margin)), intColor(diff(l.margin, p1?.margin)), false), Triple(signed(diff(l.margin, p5.margin)), intColor(diff(l.margin, p5.margin)), false)))
                    TableRow(listOf(Triple(tr("融券", "Short"), on, false), Triple("%,d".format(l.short ?: 0), on, false),
                        Triple(signed(diff(l.short, p1?.short)), intColor(diff(l.short, p1?.short)), false), Triple(signed(diff(l.short, p5.short)), intColor(diff(l.short, p5.short)), false)))
                    Text(tr("融資增加＝散戶借錢買進變多；融券增加＝放空變多", "Margin up = more leveraged retail buying; short up = more short selling") + "（${md(l.date)}）", fontSize = 11.sp, color = Neutral)
                }
            }
        }
        // ---- 成交量 ----
        val v = days.filter { it.vol != null }
        if (v.isNotEmpty()) {
            val v5 = v.takeLast(5).sumOf { it.vol ?: 0 } / minOf(5, v.size)
            val v20 = v.takeLast(20).sumOf { it.vol ?: 0 } / minOf(20, v.size)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("📈 成交量（張）", "📈 Volume (lots)"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("5${tr("日均", "d avg")} %,d · 20${tr("日均", "d avg")} %,d".format(v5, v20), fontSize = 11.sp, color = Neutral)
                    }
                    Bars(v.takeLast(20).map { (it.vol ?: 0).toDouble() }, color = Blue)
                    Text(
                        when { v5 > v20 * 1.3 -> tr("近期明顯放量", "Volume picking up"); v5 < v20 * 0.7 -> tr("近期量縮", "Volume shrinking"); else -> tr("量能持平", "Volume steady") },
                        fontSize = 11.sp, color = Neutral
                    )
                }
            }
        }
    }
    // ---- 集保股權分散 ----
    c.dist?.let { d ->
        val pv = c.distHist.getOrNull(c.distHist.size - 2)
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(tr("👥 籌碼分佈（集保股權分散）", "👥 Shareholder distribution (TDCC)") + "  ${md(d.date)}", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    @Composable
                    fun Stat(label: String, value: String, delta: Double?, modifier: Modifier) {
                        Column(modifier) {
                            Text(label, fontSize = 11.sp, color = Neutral)
                            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (delta != null) Text("%+.2f".format(delta), fontSize = 11.sp, color = if (delta > 0) UpRed else if (delta < 0) DownGreen else Neutral)
                        }
                    }
                    Stat(tr("千張大戶", "1000+ lots"), "${d.big1000}%", pv?.let { d.big1000 - it.big1000 }, Modifier.weight(1f))
                    Stat(tr("400張以上", "400+ lots"), "${d.big400}%", pv?.let { d.big400 - it.big400 }, Modifier.weight(1f))
                    Stat(tr("散戶≤50張", "Retail ≤50"), "${d.retail}%", pv?.let { d.retail - it.retail }, Modifier.weight(1f))
                }
                Text(tr("股東人數 ", "Holders ") + "%,d".format(d.holders) + (pv?.let { "（%+,d）".format(d.holders - it.holders) } ?: ""), fontSize = 12.sp)
                val labels = if (Lang.en) listOf("<1 lot", "1-5", "5-10", "10-15", "15-20", "20-30", "30-40", "40-50", "50-100", "100-200", "200-400", "400-600", "600-800", "800-1000", "1000+")
                else listOf("1-999股", "1-5張", "5-10張", "10-15張", "15-20張", "20-30張", "30-40張", "40-50張", "50-100張", "100-200張", "200-400張", "400-600張", "600-800張", "800-1000張", "1000張+")
                val max = d.lv.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                Spacer(Modifier.height(6.dp))
                d.lv.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(labels.getOrElse(i) { "" }, fontSize = 11.sp, color = Neutral, modifier = Modifier.width(76.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((p / max).toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp))
                                .background(if (i >= 11) UpRed else if (i <= 7) DownGreen else Blue))
                        }
                        Text("%.2f%%".format(p), fontSize = 11.sp, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                    }
                }
                if (c.distHist.size > 2) {
                    Text(tr("千張大戶持股走勢（${c.distHist.size}週）", "1000+ lot holders trend (${c.distHist.size}w)"), fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 8.dp))
                    Bars(c.distHist.map { it.big1000 }, height = 50, color = Gold, trend = true)
                }
                Text(
                    tr("大戶比例上升、股東人數減少＝籌碼集中，通常較健康；反之為籌碼分散。", "Big holders up + fewer holders = concentrating (usually healthier); the opposite = dispersing."),
                    fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
