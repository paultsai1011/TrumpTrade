// 由 StockMap.kt 自動產生：美國新聞關鍵字 → 題材 → 台股
export const THEMES_RAW = [
 {
  "name": "半導體",
  "en": [
   "chips?\\b",
   "semiconductor",
   "TSMC",
   "Nvidia",
   "AMD\\b",
   "Intel\\b",
   "foundr",
   "wafer",
   "Qualcomm",
   "Broadcom",
   "ASML"
  ],
  "zh": [
   "晶片",
   "半導體",
   "台積電",
   "輝達"
  ],
  "stocks": [
   [
    "2330",
    "台積電"
   ],
   [
    "2454",
    "聯發科"
   ],
   [
    "2303",
    "聯電"
   ],
   [
    "3711",
    "日月光投控"
   ],
   [
    "3443",
    "創意"
   ],
   [
    "3661",
    "世芯-KY"
   ]
  ]
 },
 {
  "name": "AI伺服器",
  "en": [
   "AI\\b",
   "artificial intelligence",
   "data cent",
   "servers?\\b",
   "OpenAI",
   "GPUs?\\b",
   "Stargate",
   "hyperscal"
  ],
  "zh": [
   "人工智慧",
   "伺服器",
   "資料中心"
  ],
  "stocks": [
   [
    "2317",
    "鴻海"
   ],
   [
    "2382",
    "廣達"
   ],
   [
    "3231",
    "緯創"
   ],
   [
    "6669",
    "緯穎"
   ],
   [
    "2376",
    "技嘉"
   ],
   [
    "3017",
    "奇鋐"
   ],
   [
    "2356",
    "英業達"
   ]
  ]
 },
 {
  "name": "美中/出口管制",
  "en": [
   "export control",
   "China\\b",
   "Chinese",
   "Beijing",
   "Xi\\b",
   "Huawei",
   "entity list",
   "sanction",
   "rare earth"
  ],
  "zh": [
   "中國",
   "出口管制",
   "制裁",
   "稀土",
   "美中"
  ],
  "stocks": [
   [
    "2330",
    "台積電"
   ],
   [
    "3443",
    "創意"
   ],
   [
    "6488",
    "環球晶"
   ],
   [
    "2454",
    "聯發科"
   ],
   [
    "3034",
    "聯詠"
   ]
  ]
 },
 {
  "name": "關稅/貿易",
  "en": [
   "tariff",
   "trade deal",
   "trade war",
   "duties\\b",
   "Section 232",
   "Section 301",
   "USTR",
   "imports?\\b",
   "trade deficit"
  ],
  "zh": [
   "關稅",
   "貿易"
  ],
  "stocks": [
   [
    "2317",
    "鴻海"
   ],
   [
    "4938",
    "和碩"
   ],
   [
    "2357",
    "華碩"
   ],
   [
    "1476",
    "儒鴻"
   ],
   [
    "9910",
    "豐泰"
   ],
   [
    "1590",
    "亞德客-KY"
   ]
  ]
 },
 {
  "name": "台灣/兩岸",
  "en": [
   "Taiwan",
   "Taipei"
  ],
  "zh": [
   "台灣",
   "兩岸"
  ],
  "stocks": [
   [
    "0050",
    "元大台灣50"
   ],
   [
    "2330",
    "台積電"
   ],
   [
    "2634",
    "漢翔"
   ]
  ]
 },
 {
  "name": "蘋果供應鏈",
  "en": [
   "Apple\\b",
   "iPhone",
   "Tim Cook"
  ],
  "zh": [
   "蘋果"
  ],
  "stocks": [
   [
    "2317",
    "鴻海"
   ],
   [
    "3008",
    "大立光"
   ],
   [
    "4938",
    "和碩"
   ],
   [
    "2474",
    "可成"
   ],
   [
    "3406",
    "玉晶光"
   ]
  ]
 },
 {
  "name": "電動車",
  "en": [
   "Tesla",
   "Musk",
   "electric vehicle",
   "EVs?\\b",
   "batter"
  ],
  "zh": [
   "電動車",
   "特斯拉"
  ],
  "stocks": [
   [
    "2308",
    "台達電"
   ],
   [
    "3665",
    "貿聯-KY"
   ],
   [
    "1536",
    "和大"
   ],
   [
    "2201",
    "裕隆"
   ],
   [
    "1504",
    "東元"
   ]
  ]
 },
 {
  "name": "能源/電力",
  "en": [
   "oil\\b",
   "OPEC",
   "energy",
   "power grid",
   "nuclear",
   "electricity",
   "LNG\\b",
   "natural gas"
  ],
  "zh": [
   "能源",
   "電力",
   "核能",
   "油價"
  ],
  "stocks": [
   [
    "1519",
    "華城"
   ],
   [
    "1503",
    "士電"
   ],
   [
    "1513",
    "中興電"
   ],
   [
    "6505",
    "台塑化"
   ],
   [
    "2308",
    "台達電"
   ]
  ]
 },
 {
  "name": "利率/金融",
  "en": [
   "Fed\\b",
   "Federal Reserve",
   "Powell",
   "interest rate",
   "rate cut",
   "rate hike",
   "inflation",
   "CPI\\b",
   "Treasur",
   "bond yield",
   "recession"
  ],
  "zh": [
   "降息",
   "升息",
   "通膨",
   "聯準會"
  ],
  "stocks": [
   [
    "2881",
    "富邦金"
   ],
   [
    "2882",
    "國泰金"
   ],
   [
    "2891",
    "中信金"
   ],
   [
    "2886",
    "兆豐金"
   ],
   [
    "00679B",
    "元大美債20年"
   ]
  ]
 },
 {
  "name": "國防軍工",
  "en": [
   "defen[cs]e",
   "military",
   "Pentagon",
   "NATO",
   "weapon",
   "drones?\\b",
   "missile"
  ],
  "zh": [
   "國防",
   "軍工",
   "無人機"
  ],
  "stocks": [
   [
    "2634",
    "漢翔"
   ],
   [
    "8033",
    "雷虎"
   ],
   [
    "2645",
    "長榮航太"
   ],
   [
    "3005",
    "神基"
   ]
  ]
 },
 {
  "name": "航運",
  "en": [
   "shipping",
   "freight",
   "Red Sea",
   "Panama",
   "ports?\\b",
   "container"
  ],
  "zh": [
   "航運",
   "貨櫃"
  ],
  "stocks": [
   [
    "2603",
    "長榮"
   ],
   [
    "2609",
    "陽明"
   ],
   [
    "2615",
    "萬海"
   ]
  ]
 },
 {
  "name": "醫藥健保",
  "en": [
   "drugs?\\b",
   "pharma",
   "Medicare",
   "Medicaid",
   "FDA\\b",
   "healthcare",
   "vaccine"
  ],
  "zh": [
   "藥價",
   "醫療"
  ],
  "stocks": [
   [
    "1795",
    "美時"
   ],
   [
    "6446",
    "藥華藥"
   ],
   [
    "4743",
    "合一"
   ],
   [
    "1760",
    "寶齡富錦"
   ]
  ]
 },
 {
  "name": "鋼鐵原物料",
  "en": [
   "steel",
   "alumin",
   "copper"
  ],
  "zh": [
   "鋼鐵",
   "鋼價"
  ],
  "stocks": [
   [
    "2002",
    "中鋼"
   ],
   [
    "2014",
    "中鴻"
   ],
   [
    "2027",
    "大成鋼"
   ]
  ]
 },
 {
  "name": "記憶體",
  "en": [
   "memory chip",
   "DRAM",
   "HBM\\b",
   "Micron",
   "NAND",
   "SK Hynix",
   "Samsung"
  ],
  "zh": [
   "記憶體"
  ],
  "stocks": [
   [
    "2408",
    "南亞科"
   ],
   [
    "2344",
    "華邦電"
   ],
   [
    "8299",
    "群聯"
   ],
   [
    "3260",
    "威剛"
   ]
  ]
 },
 {
  "name": "網通衛星",
  "en": [
   "Starlink",
   "satellite",
   "5G\\b",
   "broadband",
   "SpaceX"
  ],
  "zh": [
   "衛星",
   "網通"
  ],
  "stocks": [
   [
    "2345",
    "智邦"
   ],
   [
    "3491",
    "昇達科"
   ],
   [
    "2314",
    "台揚"
   ],
   [
    "6285",
    "啟碁"
   ]
  ]
 },
 {
  "name": "民生消費",
  "en": [
   "consumer",
   "retail",
   "Walmart",
   "spending",
   "Nike\\b",
   "jobs report",
   "payroll",
   "unemployment",
   "gas price",
   "grocer"
  ],
  "zh": [
   "消費",
   "民生",
   "物價"
  ],
  "stocks": [
   [
    "2912",
    "統一超"
   ],
   [
    "1216",
    "統一"
   ],
   [
    "9904",
    "寶成"
   ],
   [
    "9910",
    "豐泰"
   ]
  ]
 }
];

export const THEMES = THEMES_RAW.map(t => {
  const parts = [];
  if (t.en.length) parts.push("\\b(?:" + t.en.join("|") + ")");
  if (t.zh.length) parts.push("(?:" + t.zh.join("|") + ")");
  return { name: t.name, re: new RegExp(parts.join("|"), "i"), stocks: t.stocks };
});

export const STOCK_NAMES = Object.fromEntries(THEMES_RAW.flatMap(t => t.stocks));
export const matchThemes = text => THEMES.filter(t => t.re.test(text)).map(t => t.name);
export const themeByName = n => THEMES.find(t => t.name === n);
export const themesOfStock = c => THEMES.filter(t => t.stocks.some(s => s[0] === c));
export const DEFAULT_WATCH = ["2330", "2317", "2454", "2382", "3231", "2308", "0050"];
