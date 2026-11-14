# 川普財經雷達 TrumpTrade

追蹤美國財經新聞、川普 Truth Social 發言、白宮行政命令／商務部／USTR 公告與國會法案，
自動對應受影響的**台灣股票**，並用 **Gemini AI（免費）** 分析利多利空與買進／賣出／觀望建議。

## 下載安裝（Android）
最新版 APK： https://github.com/paultsai1011/TrumpTrade/releases/latest/download/TrumpTrade.apk

用手機瀏覽器打開上面連結 → 下載 → 安裝（第一次要允許「安裝未知來源應用程式」）。
之後有新版本，直接下載覆蓋安裝即可，設定與分析紀錄都會保留。

## 第一次使用
1. 右上角 ⚙️ 設定 → 貼上 Gemini API Key（https://aistudio.google.com/apikey 免費申請）→ 儲存
2. 允許通知（川普發文提到關稅、晶片、台灣等關鍵字會推播）
3. 總覽 → 「產生」AI 今日盤勢日報

## 功能
| 分頁 | 內容 |
|---|---|
| 總覽 | 美股/台股指數、AI 今日日報（買進/賣出/觀察名單）、24h 題材熱度、川普與政策最新動態 |
| 川普 | Truth Social 貼文 + 川普相關經濟新聞 |
| 財經 | Google News、CNBC、MarketWatch、Yahoo Finance |
| 政策法案 | Federal Register 總統文件、BIS 出口管制、USTR、國會法案（可選 Congress.gov Key） |
| 台股 | 自選股即時報價、AI 個股買賣分析、AI 提及排行、新聞題材關聯個股 |

每則消息可按「AI 分析影響」：中文摘要、重要度、利多/利空、對美國科技民生與台股影響、相關台股與建議。

## 雲端編譯
每次推送到 `main`，GitHub Actions 會自動編譯並發佈到 Releases。

> ⚠️ AI 分析僅供參考，不構成投資建議。
