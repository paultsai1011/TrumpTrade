package com.paul.trumptrade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 中英文切換（預設中文）。Compose 讀到 Lang.en 會自動重繪。 */
object Lang {
    var en by mutableStateOf(false)
}

fun tr(zh: String, en: String): String = if (Lang.en) en else zh

/** AI 回傳的中文判斷詞 → 英文 */
fun term(s: String): String {
    if (!Lang.en) return s
    return when (s.trim()) {
        "利多" -> "Bullish"; "利空" -> "Bearish"; "中性" -> "Neutral"; "多空交雜" -> "Mixed"
        "買進" -> "Buy"; "賣出" -> "Sell"; "觀望" -> "Hold"; "相關" -> "Related"
        "偏多" -> "Bullish"; "偏空" -> "Bearish"; "震盪" -> "Volatile"
        else -> s
    }
}

fun catLabel(c: Cat): String = when (c) {
    Cat.TRUMP -> tr("川普", "Trump"); Cat.NEWS -> tr("財經", "Markets"); Cat.POLICY -> tr("政策法案", "Policy")
}

/** 川普分頁的子分類 */
enum class TrumpKind { ALL, POSTS, TALKS, NEWS }

private val talkRe = Regex(
    "\\b(interview|interviewed|remarks|speech|speaks|spoke|press conference|news conference|told reporters|" +
            "Oval Office|town hall|rally|address|briefing|podcast|Fox News|Meet the Press|60 Minutes|says in|said in)\\b|專訪|受訪|演講|記者會",
    RegexOption.IGNORE_CASE
)

fun trumpKind(it: NewsItem): TrumpKind = when {
    it.source == "Truth Social" -> TrumpKind.POSTS
    talkRe.containsMatchIn(it.title) -> TrumpKind.TALKS
    else -> TrumpKind.NEWS
}

fun kindLabel(k: TrumpKind): String = when (k) {
    TrumpKind.ALL -> tr("全部", "All")
    TrumpKind.POSTS -> tr("📱 他的貼文", "📱 His posts")
    TrumpKind.TALKS -> tr("🎤 訪談/演講", "🎤 Interviews")
    TrumpKind.NEWS -> tr("📰 相關新聞", "📰 News")
}
