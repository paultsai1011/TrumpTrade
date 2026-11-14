package com.paul.trumptrade

data class Theme(val name: String, val regex: Regex, val stocks: List<Pair<String, String>>)

/**
 * 美國新聞關鍵字 → 題材 → 台股對照表（不用 AI 也能即時標記）。
 * 英文關鍵字前面會自動加上單字邊界，短字自己在後面加 \\b。
 */
object StockMap {
    private fun t(name: String, en: List<String>, zh: List<String>, vararg stocks: Pair<String, String>): Theme {
        val parts = mutableListOf<String>()
        if (en.isNotEmpty()) parts.add("\\b(?:" + en.joinToString("|") + ")")
        if (zh.isNotEmpty()) parts.add("(?:" + zh.joinToString("|") + ")")
        return Theme(name, Regex(parts.joinToString("|"), RegexOption.IGNORE_CASE), stocks.toList())
    }

    val themes: List<Theme> = listOf(
        t(
            "半導體", listOf("chips?\\b", "semiconductor", "TSMC", "Nvidia", "AMD\\b", "Intel\\b", "foundr", "wafer", "Qualcomm", "Broadcom", "ASML"),
            listOf("晶片", "半導體", "台積電", "輝達"),
            "2330" to "台積電", "2454" to "聯發科", "2303" to "聯電", "3711" to "日月光投控", "3443" to "創意", "3661" to "世芯-KY"
        ),
        t(
            "AI伺服器", listOf("AI\\b", "artificial intelligence", "data cent", "servers?\\b", "OpenAI", "GPUs?\\b", "Stargate", "hyperscal"),
            listOf("人工智慧", "伺服器", "資料中心"),
            "2317" to "鴻海", "2382" to "廣達", "3231" to "緯創", "6669" to "緯穎", "2376" to "技嘉", "3017" to "奇鋐", "2356" to "英業達"
        ),
        t(
            "美中/出口管制", listOf("export control", "China\\b", "Chinese", "Beijing", "Xi\\b", "Huawei", "entity list", "sanction", "rare earth"),
            listOf("中國", "出口管制", "制裁", "稀土", "美中"),
            "2330" to "台積電", "3443" to "創意", "6488" to "環球晶", "2454" to "聯發科", "3034" to "聯詠"
        ),
        t(
            "關稅/貿易", listOf("tariff", "trade deal", "trade war", "duties\\b", "Section 232", "Section 301", "USTR", "imports?\\b", "trade deficit"),
            listOf("關稅", "貿易"),
            "2317" to "鴻海", "4938" to "和碩", "2357" to "華碩", "1476" to "儒鴻", "9910" to "豐泰", "1590" to "亞德客-KY"
        ),
        t(
            "台灣/兩岸", listOf("Taiwan", "Taipei"), listOf("台灣", "兩岸"),
            "0050" to "元大台灣50", "2330" to "台積電", "2634" to "漢翔"
        ),
        t(
            "蘋果供應鏈", listOf("Apple\\b", "iPhone", "Tim Cook"), listOf("蘋果"),
            "2317" to "鴻海", "3008" to "大立光", "4938" to "和碩", "2474" to "可成", "3406" to "玉晶光"
        ),
        t(
            "電動車", listOf("Tesla", "Musk", "electric vehicle", "EVs?\\b", "batter"), listOf("電動車", "特斯拉"),
            "2308" to "台達電", "3665" to "貿聯-KY", "1536" to "和大", "2201" to "裕隆", "1504" to "東元"
        ),
        t(
            "能源/電力", listOf("oil\\b", "OPEC", "energy", "power grid", "nuclear", "electricity", "LNG\\b", "natural gas"),
            listOf("能源", "電力", "核能", "油價"),
            "1519" to "華城", "1503" to "士電", "1513" to "中興電", "6505" to "台塑化", "2308" to "台達電"
        ),
        t(
            "利率/金融", listOf("Fed\\b", "Federal Reserve", "Powell", "interest rate", "rate cut", "rate hike", "inflation", "CPI\\b", "Treasur", "bond yield", "recession"),
            listOf("降息", "升息", "通膨", "聯準會"),
            "2881" to "富邦金", "2882" to "國泰金", "2891" to "中信金", "2886" to "兆豐金", "00679B" to "元大美債20年"
        ),
        t(
            "國防軍工", listOf("defen[cs]e", "military", "Pentagon", "NATO", "weapon", "drones?\\b", "missile"),
            listOf("國防", "軍工", "無人機"),
            "2634" to "漢翔", "8033" to "雷虎", "2645" to "長榮航太", "3005" to "神基"
        ),
        t(
            "航運", listOf("shipping", "freight", "Red Sea", "Panama", "ports?\\b", "container"), listOf("航運", "貨櫃"),
            "2603" to "長榮", "2609" to "陽明", "2615" to "萬海"
        ),
        t(
            "醫藥健保", listOf("drugs?\\b", "pharma", "Medicare", "Medicaid", "FDA\\b", "healthcare", "vaccine"),
            listOf("藥價", "醫療"),
            "1795" to "美時", "6446" to "藥華藥", "4743" to "合一", "1760" to "寶齡富錦"
        ),
        t(
            "鋼鐵原物料", listOf("steel", "alumin", "copper"), listOf("鋼鐵", "鋼價"),
            "2002" to "中鋼", "2014" to "中鴻", "2027" to "大成鋼"
        ),
        t(
            "記憶體", listOf("memory chip", "DRAM", "HBM\\b", "Micron", "NAND", "SK Hynix", "Samsung"), listOf("記憶體"),
            "2408" to "南亞科", "2344" to "華邦電", "8299" to "群聯", "3260" to "威剛"
        ),
        t(
            "網通衛星", listOf("Starlink", "satellite", "5G\\b", "broadband", "SpaceX"), listOf("衛星", "網通"),
            "2345" to "智邦", "3491" to "昇達科", "2314" to "台揚", "6285" to "啟碁"
        ),
        t(
            "民生消費", listOf("consumer", "retail", "Walmart", "spending", "Nike\\b", "jobs report", "payroll", "unemployment", "gas price", "grocer"),
            listOf("消費", "民生", "物價"),
            "2912" to "統一超", "1216" to "統一", "9904" to "寶成", "9910" to "豐泰"
        ),
    )

    private val names: Map<String, String> = themes.flatMap { it.stocks }.toMap()

    fun nameOf(code: String): String = names[code] ?: ""

    fun match(text: String): List<Theme> = themes.filter { it.regex.containsMatchIn(text) }

    fun themesOfStock(code: String): List<Theme> = themes.filter { th -> th.stocks.any { it.first == code } }

    fun theme(name: String): Theme? = themes.firstOrNull { it.name == name }

    val defaultWatch = listOf("2330", "2317", "2454", "2382", "3231", "2308", "0050")
}
