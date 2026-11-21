const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";
const urls = [
  "https://query1.finance.yahoo.com/v8/finance/chart/NVDA?range=6mo&interval=1d",
  "https://query2.finance.yahoo.com/v8/finance/chart/NVDA?range=6mo&interval=1d",
  "https://stooq.com/q/d/l/?s=nvda.us&i=d",
  "https://stooq.com/q/l/?s=nvda.us+aapl.us&f=sd2t2ohlcvp&h&e=csv",
  "https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol?symbols=NVDA|AAPL|QQQ&requestMethod=itv&noform=1&partnerId=2&fund=1&exthrs=1&output=json",
  "https://api.nasdaq.com/api/quote/NVDA/chart?assetclass=stocks&fromdate=2026-06-01&todate=2026-11-21",
  "https://api.nasdaq.com/api/quote/NVDA/historical?assetclass=stocks&fromdate=2026-05-01&limit=200",
];
for (const u of urls) {
  try {
    const r = await fetch(u, { headers: { "User-Agent": UA, "Accept": "application/json, text/plain, */*" } });
    const t = await r.text();
    console.log("\n=== ", r.status, u, "len", t.length);
    console.log(t.slice(0, 600).replace(/\s+/g, " "));
  } catch (e) { console.log("\n=== ERR", u, e.message); }
}
