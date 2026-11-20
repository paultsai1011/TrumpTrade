package com.paul.trumptrade

import android.content.Context
import android.content.Intent

/** 分享：叫出系統分享選單（LINE、Messenger、WhatsApp…） */
object Share {
    const val SITE = "https://paultsai1011.github.io/TrumpTrade/"

    fun send(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, tr("分享到", "Share via")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun footer() = "\n" + tr("⚠️ AI 自動分析，僅供參考，不構成投資建議", "⚠️ AI-generated, for reference only, not investment advice") +
            "\n👉 " + tr("川普財經雷達", "Trump Market Radar") + " $SITE"

    private fun pct(q: Quote?): String = if (q == null || q.price <= 0) "" else " %.2f（%+.2f%%）".format(q.price, q.pct)

    private fun picks(list: List<StockPick>, quotes: Map<String, Quote>): String = list.joinToString("\n") { p ->
        val r = p.reasonShown
        "• ${StockMap.label(p.code, p.name)} ${p.code}${pct(quotes[p.code])}" + if (r.isNotBlank()) "：$r" else ""
    }

    fun brief(b: Brief, quotes: Map<String, Quote>, markets: List<MarketQuote>): String {
        val sb = StringBuilder()
        sb.append(tr("📊 川普財經雷達｜AI 今日盤勢", "📊 Trump Market Radar | AI daily brief")).append("（").append(fullTime(b.time)).append("）\n")
        if (markets.isNotEmpty()) sb.append(markets.take(5).joinToString("　") { "${marketLabel(it)} %+.2f%%".format(it.pct) }).append("\n")
        sb.append(tr("情緒：", "Mood: ")).append(term(b.mood)).append("（%+d）".format(b.moodScore)).append("\n")
        val headline = if (Lang.en && b.headlineEn.isNotBlank()) b.headlineEn else b.headline
        if (headline.isNotBlank()) sb.append("\n📌 ").append(headline).append("\n")
        b.events.take(5).forEach { e ->
            val t = if (Lang.en && e.titleEn.isNotBlank()) e.titleEn else e.title
            val im = if (Lang.en && e.impactEn.isNotBlank()) e.impactEn else e.impact
            sb.append("• ").append(t).append(if (im.isNotBlank()) " — $im" else "").append("\n")
        }
        if (b.buy.isNotEmpty()) sb.append("\n").append(tr("📈 可留意買進", "📈 Consider buying")).append("\n").append(picks(b.buy, quotes)).append("\n")
        if (b.sell.isNotEmpty()) sb.append("\n").append(tr("📉 可考慮賣出／避開", "📉 Consider selling / avoid")).append("\n").append(picks(b.sell, quotes)).append("\n")
        if (b.watch.isNotEmpty()) sb.append("\n").append(tr("👀 觀察名單", "👀 Watch")).append("\n").append(picks(b.watch, quotes)).append("\n")
        val advice = if (Lang.en && b.adviceEn.isNotBlank()) b.adviceEn else b.advice
        if (advice.isNotBlank()) sb.append("\n💡 ").append(advice).append("\n")
        return sb.toString().trimEnd() + "\n" + footer()
    }

    fun news(it: NewsItem, t: Translation?, a: Analysis?, quotes: Map<String, Quote>): String {
        val sb = StringBuilder()
        sb.append(if (it.cat == Cat.TRUMP) tr("🇺🇸 川普動態", "🇺🇸 Trump") else catLabel(it.cat)).append("｜").append(it.source).append("　").append(fullTime(it.time)).append("\n\n")
        if (t != null && t.zh.isNotBlank() && !Lang.en) sb.append(t.zh).append("\n\n").append(tr("原文：", "Original: ")).append(it.title).append("\n")
        else sb.append(it.title).append("\n")
        val imp = a?.importance ?: t?.importance
        val sen = a?.sentiment ?: t?.sentiment
        if (imp != null) sb.append("\n").append(tr("影響：", "Impact: ")).append("★".repeat(imp)).append(sen?.let { " " + term(it) } ?: "").append("\n")
        if (a != null && a.summary.isNotBlank()) sb.append("🤖 ").append(a.summary).append("\n")
        val ps = (t?.stocks ?: emptyList()).ifEmpty { a?.stocks ?: emptyList() }
        if (ps.isNotEmpty()) sb.append(tr("相關台股：", "Taiwan stocks: ")).append(ps.joinToString("、") { "${StockMap.label(it.code, it.name)}${it.code}(${term(it.action)})" }).append("\n")
        if (it.link.isNotBlank()) sb.append("\n🔗 ").append(it.link).append("\n")
        return sb.toString() + footer()
    }

    fun stock(code: String, name: String, q: Quote?, sa: StockAnalysis?, c: ChipData?, t: TechResult? = null): String {
        val sb = StringBuilder()
        sb.append("📈 ").append(StockMap.label(code, name)).append(" ").append(code).append(pct(q)).append("\n")
        if (c != null && c.days.isNotEmpty()) {
            sb.append(tr("法人5日：", "Institutions 5d: "))
                .append(tr("外資 ", "Foreign ")).append("%+,d".format(c.sum(5) { it.foreign }))
                .append(tr("、投信 ", ", Trust ")).append("%+,d".format(c.sum(5) { it.trust }))
                .append(tr("、自營 ", ", Dealer ")).append("%+,d".format(c.sum(5) { it.dealer })).append(tr(" 張", " lots")).append("\n")
        }
        if (t != null && t.ok) sb.append(tr("技術面：", "Technicals: ")).append(t.label).append("（")
            .append(t.reasons.filter { it.dir != 0 }.take(3).joinToString(tr("；", "; ")) { it.text }).append("）\n")
        c?.dist?.let { sb.append(tr("千張大戶 ", "1000+ lot holders ")).append(it.big1000).append("%").append(tr("、散戶 ", ", retail ")).append(it.retail).append("%\n") }
        if (sa != null) {
            sb.append("\n🤖 ").append(tr("AI 建議：", "AI view: ")).append(term(sa.action)).append(" ").append(sa.confidence).append("%\n").append(sa.summary).append("\n")
            if (sa.chips.isNotBlank()) sb.append(tr("籌碼：", "Chips: ")).append(sa.chips).append("\n")
        }
        return sb.toString() + footer()
    }
}
