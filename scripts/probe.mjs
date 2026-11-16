const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";
const urls = [
  "https://www.twse.com.tw/rwd/zh/fund/T86?date=20261113&selectType=ALLBUT0999&response=json",
  "https://www.twse.com.tw/rwd/zh/marginTrading/MI_MARGN?date=20261113&selectType=ALL&response=json",
  "https://www.twse.com.tw/rwd/zh/afterTrading/MI_INDEX?date=20261113&type=ALLBUT0999&response=json",
  "https://www.tpex.org.tw/www/zh-tw/insti/dailyTrade?type=Daily&sect=EW&date=2026/11/13&response=json",
  "https://www.tpex.org.tw/www/zh-tw/margin/balance?date=2026/11/13&response=json",
  "https://www.tpex.org.tw/www/zh-tw/afterTrading/otc?date=2026/11/13&type=EW&response=json",
  "https://www.tpex.org.tw/openapi/v1/tpex_3insti_daily_trading",
  "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_margin_balance",
  "https://openapi.twse.com.tw/v1/fund/T86",
  "https://openapi.twse.com.tw/v1/exchangeReport/MI_MARGN",
  "https://opendata.tdcc.com.tw/getOD.ashx?id=1-5",
  "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=tse_2330.tw&json=1&delay=0",
];
for (const u of urls) {
  try {
    const r = await fetch(u, { headers: { "User-Agent": UA } });
    const t = await r.text();
    console.log("\n=== ", r.status, u, "len", t.length);
    console.log(t.slice(0, 900).replace(/\s+/g, " "));
    if (u.includes("tdcc")) console.log("LINES:", t.split("\n").slice(0, 25).join(" | "));
  } catch (e) { console.log("\n=== ERR", u, e.message); }
}
