package com.paul.trumptrade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

private val CMa5 = Color(0xFFFFC107)
private val CMa20 = Color(0xFF8AB4F8)
private val CMa60 = Color(0xFFCE93D8)

fun techColor(t: TechResult): Color = when { t.bullish -> UpRed; t.bearish -> DownGreen; else -> Neutral }

@Composable
fun TechBadge(t: TechResult, withScore: Boolean = true) {
    ActionBadge(t.label + if (withScore && t.ok) " %+d".format(t.score) else "", null, techColor(t))
}

@Composable
fun TechSection(k: KLine?, t: TechResult?, loaded: Boolean) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🕯️ K線與技術分析", "🕯️ Chart & technicals"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (t != null) TechBadge(t)
            }
            if (k == null || t == null) {
                Text(if (loaded) tr("這檔暫無K線資料（雲端仍在補歷史資料）", "No K-line data yet (cloud is still backfilling)") else tr("K線資料載入中…", "Loading K-line…"), fontSize = 13.sp, color = Neutral)
                return@Column
            }
            if (!t.ok) {
                Text(tr("目前只有 ${t.n} 天資料，至少要 26 天才能計算，雲端正在補抓歷史資料。", "Only ${t.n} days so far; need 26+. Cloud is backfilling."), fontSize = 13.sp, color = Neutral)
                return@Column
            }
            val n = k.size; val s0 = max(0, n - 60)
            val grid = MaterialTheme.colorScheme.outlineVariant
            // K 線 + 均線 + 量
            Canvas(Modifier.fillMaxWidth().height(210.dp).padding(top = 6.dp)) {
                val volH = size.height * 0.2f; val priceH = size.height - volH - 6f
                val m = n - s0; val bw = size.width / m
                val hi = k.h.subList(s0, n).max(); val lo = k.l.subList(s0, n).min()
                val vmax = k.v.subList(s0, n).max().takeIf { it > 0 } ?: 1.0
                fun y(p: Double) = ((hi - p) / (if (hi > lo) hi - lo else 1.0) * priceH).toFloat()
                for (i in s0 until n) {
                    val x = (i - s0) * bw + bw / 2
                    val col = if (k.c[i] >= k.o[i]) UpRed else DownGreen
                    drawLine(col, Offset(x, y(k.h[i])), Offset(x, y(k.l[i])), 1.5f)
                    val top = y(max(k.o[i], k.c[i])); val bot = y(minOf(k.o[i], k.c[i]))
                    drawRect(col, Offset(x - bw * 0.35f, top), Size(bw * 0.7f, max(1.5f, bot - top)))
                    val vh = (k.v[i] / vmax * (volH - 2)).toFloat()
                    drawRect(col.copy(alpha = 0.55f), Offset(x - bw * 0.35f, size.height - vh), Size(bw * 0.7f, vh))
                }
                fun line(arr: List<Double?>, c: Color) {
                    val path = Path(); var started = false
                    for (i in s0 until n) { val v = arr.getOrNull(i) ?: continue; val x = (i - s0) * bw + bw / 2; if (!started) { path.moveTo(x, y(v)); started = true } else path.lineTo(x, y(v)) }
                    if (started) drawPath(path, c, style = Stroke(2f))
                }
                line(t.ma5, CMa5); line(t.ma20, CMa20); line(t.ma60, CMa60)
            }
            Row {
                Text("━ MA5　", color = CMa5, fontSize = 11.sp); Text("━ MA20　", color = CMa20, fontSize = 11.sp); Text("━ MA60", color = CMa60, fontSize = 11.sp)
            }
            // KD
            Text("KD  K ${"%.1f".format(t.k.last())}  D ${"%.1f".format(t.dd.last())}", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 8.dp))
            Canvas(Modifier.fillMaxWidth().height(50.dp)) {
                val m = n - s0; val bw = size.width / m
                fun y(v: Double) = ((100 - v) / 100 * size.height).toFloat()
                listOf(20.0, 80.0).forEach { drawLine(grid, Offset(0f, y(it)), Offset(size.width, y(it)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))) }
                fun line(arr: List<Double>, c: Color) {
                    val path = Path()
                    for (i in s0 until n) { val x = (i - s0) * bw + bw / 2; if (i == s0) path.moveTo(x, y(arr[i])) else path.lineTo(x, y(arr[i])) }
                    drawPath(path, c, style = Stroke(2f))
                }
                line(t.k, CMa5); line(t.dd, CMa20)
            }
            // MACD
            val rsiLast = t.rsi.lastOrNull()
            Text("MACD ${tr("柱狀體", "histogram")} ${"%.2f".format(t.osc.last())}　RSI ${rsiLast?.let { "%.1f".format(it) } ?: "–"}", fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp))
            Canvas(Modifier.fillMaxWidth().height(45.dp)) {
                val m = n - s0; val bw = size.width / m
                val mx = t.osc.subList(s0, n).maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
                val mid = size.height / 2
                for (i in s0 until n) {
                    val v = t.osc[i]; val h = (abs(v) / mx * (mid - 1)).toFloat()
                    drawRect(if (v >= 0) UpRed else DownGreen, Offset((i - s0) * bw + 1f, if (v >= 0) mid - h else mid), Size(max(1f, bw - 2f), h))
                }
            }
            Text(tr("訊號", "Signals"), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            t.reasons.forEach { r ->
                Text(
                    (if (r.dir > 0) "▲ " else if (r.dir < 0) "▼ " else "● ") + r.text, fontSize = 13.sp,
                    color = if (r.dir > 0) UpRed else if (r.dir < 0) DownGreen else Neutral, modifier = Modifier.padding(vertical = 1.dp)
                )
            }
            Text(
                tr("判斷方式：各訊號加減分，≥4 轉強、2~3 偏多、-1~1 盤整、-2~-3 偏弱、≤-4 轉弱。技術分析只反映過去價量，不保證未來走勢。",
                    "Scoring: ≥4 turning strong, 2–3 bullish, -1–1 sideways, -2–-3 bearish, ≤-4 turning weak. Technicals reflect past price/volume only."),
                fontSize = 11.sp, color = Neutral, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
