// 美股：CNBC 即時報價（每次）＋ Nasdaq 日K（每天補）→ web/data/us.json
import { writeFileSync, mkdirSync } from "node:fs";
import { US_LIST } from "../web/us.js";

const PAGES = "https://paultsai1011.github.io/TrumpTrade/data/us.json";
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
const log = (...a) => console.log("[美股]", ...a);
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function get(u, t = 25000) {
  const c = new AbortController(); const tm = setTimeout(() => c.abort(), t);
  try { const r = await fetch(u, { signal: c.signal, headers: { "User-Agent": UA, Accept: "application/json, text/plain, */*" } }); const s = await r.text(); if (!r.ok) throw new Error("HTTP " + r.status); return s; }
  finally { clearTimeout(tm); }
}
const num = s => { const v = parseFloat(String(s ?? "").replace(/[$,%+]/g, "")); return isNaN(v) ? 0 : v; };

export async function cnbcQuotes(tickers) {
  const out = {};
  for (let i = 0; i < tickers.length; i += 20) {
    const chunk = tickers.slice(i, i + 20);
    try {
      const o = JSON.parse(await get(`https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol?symbols=${encodeURIComponent(chunk.join("|"))}&requestMethod=itv&noform=1&partnerId=2&fund=1&exthrs=1&output=json`));
      let q = o?.FormattedQuoteResult?.FormattedQuote || []; if (!Array.isArray(q)) q = [q];
      for (const x of q) {
        if (!x?.symbol || !num(x.last)) continue;
        const ext = x.ExtendedMktQuote || null;
        out[x.symbol] = {
          name: x.name || x.shortName || "", price: num(x.last), prev: num(x.previous_day_closing) || num(x.last) - num(x.change),
          op: num(x.open), hi: num(x.high), lo: num(x.low), vol: Math.round(num(x.volume)), time: x.last_timedate || "", date: x.last_time || "",
          ext: ext && num(ext.last) ? { price: num(ext.last), chg: num(ext.change_pct), type: ext.type || "", time: ext.last_timedate || "" } : null,
        };
      }
    } catch (e) { log("CNBC 失敗", e.message); }
    await sleep(500);
  }
  return out;
}

export async function nasdaqK(t, etf) {
  const from = new Date(Date.now() - 200 * 86400_000).toISOString().slice(0, 10);
  for (const cls of etf ? ["etf", "stocks"] : ["stocks", "etf"]) {
    try {
      const o = JSON.parse(await get(`https://api.nasdaq.com/api/quote/${encodeURIComponent(t)}/historical?assetclass=${cls}&fromdate=${from}&limit=200`));
      const rows = o?.data?.tradesTable?.rows; if (!rows?.length) continue;
      const k = { d: [], o: [], h: [], l: [], c: [], v: [] };
      for (const r of rows.slice().reverse()) {
        const [m, d, y] = r.date.split("/"); const c = num(r.close); if (!c) continue;
        k.d.push(+(y + m.padStart(2, "0") + d.padStart(2, "0"))); k.c.push(c);
        k.o.push(num(r.open) || c); k.h.push(num(r.high) || c); k.l.push(num(r.low) || c); k.v.push(Math.round(num(r.volume)));
      }
      return k;
    } catch (e) { log("Nasdaq 失敗", t, cls, e.message); }
  }
  return null;
}

export async function updateUs() {
  let prev = {};
  try { prev = JSON.parse(await get(`${PAGES}?t=${Date.now()}`)); } catch { log("沒有上一版"); }
  const tickers = US_LIST.map(x => x.t);
  const quotes = await cnbcQuotes(tickers);
  log("報價", Object.keys(quotes).length, "/", tickers.length);
  const k = prev.k || {};
  let n = 0;
  for (const x of US_LIST) {
    const q = quotes[x.t];
    const last = k[x.t]?.d?.at(-1) || 0;
    const qd = q?.date ? +q.date.replace(/-/g, "") : 0;
    // 已經有最新交易日的 K 就不再抓；每天最多補 60 檔
    if (last && qd && last >= qd) continue;
    if (last && Date.now() - (prev.kFetched?.[x.t] || 0) < 3 * 3600_000) continue;
    if (n >= 60) break;
    const kk = await nasdaqK(x.t, x.etf); n++;
    if (kk) { k[x.t] = kk; (prev.kFetched = prev.kFetched || {})[x.t] = Date.now(); }
    await sleep(700);
  }
  log("K線更新", n, "檔，共", Object.keys(k).length);
  mkdirSync("web/data", { recursive: true });
  writeFileSync("web/data/us.json", JSON.stringify({ updated: Date.now(), quotes: Object.keys(quotes).length ? quotes : (prev.quotes || {}), k, kFetched: prev.kFetched || {} }));
}
