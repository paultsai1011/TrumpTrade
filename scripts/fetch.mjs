// 雲端資料抓取：GitHub Actions 每 20 分鐘執行，輸出 web/data/data.json 給網頁版使用
import { writeFileSync, mkdirSync } from "node:fs";
import { matchThemes, THEMES, STOCK_NAMES, DEFAULT_WATCH, themeByName } from "../web/themes.js";
import { updateChips } from "./chips.mjs";

const PAGES = "https://paultsai1011.github.io/TrumpTrade/data/data.json";
const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const CONGRESS_KEY = process.env.CONGRESS_API_KEY || "";
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
const errors = [];
const log = (...a) => console.log(...a);

async function get(url, opt = {}, timeout = 20000) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), timeout);
  try {
    const r = await fetch(url, { ...opt, signal: ctl.signal, headers: { "User-Agent": UA, "Accept-Language": "en-US,en;q=0.9", ...(opt.headers || {}) } });
    const text = await r.text();
    if (!r.ok) { const e = new Error(`HTTP ${r.status}`); e.status = r.status; e.body = text; throw e; }
    return text;
  } finally { clearTimeout(t); }
}

// ---------- RSS ----------
const ENT = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };
function decode(s) {
  return s.replace(/&(#x?[0-9a-f]+|\w+);/gi, (m, e) => {
    if (e[0] === "#") { const n = e[1] === "x" || e[1] === "X" ? parseInt(e.slice(2), 16) : parseInt(e.slice(1)); return isNaN(n) ? m : String.fromCodePoint(n); }
    return ENT[e.toLowerCase()] ?? m;
  });
}
function clean(s) {
  if (!s) return "";
  s = s.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1");
  s = decode(s);                       // 先解一次（內容常是被轉義的 HTML）
  s = s.replace(/<br\s*\/?>/gi, " ").replace(/<\/p>/gi, " ").replace(/<[^>]+>/g, "");
  return decode(s).replace(/\s+/g, " ").trim();
}
function tag(block, names) {
  for (const n of names) {
    const m = block.match(new RegExp(`<${n}(?:\\s[^>]*)?>([\\s\\S]*?)</${n}>`, "i"));
    if (m && m[1].trim()) return m[1];
  }
  return "";
}
function parseRss(xml) {
  const out = [];
  const re = /<(item|entry)[\s>][\s\S]*?<\/\1>/gi;
  let m;
  while ((m = re.exec(xml))) {
    const b = m[0];
    let link = tag(b, ["link"]);
    if (!link) { const h = b.match(/<link[^>]*href="([^"]+)"/i); if (h) link = h[1]; }
    out.push({
      title: clean(tag(b, ["title"])),
      link: clean(link),
      desc: clean(tag(b, ["content:encoded", "description", "summary", "content"])),
      date: Date.parse(clean(tag(b, ["pubDate", "published", "updated", "dc:date"]))) || 0,
      source: clean(tag(b, ["source"])),
    });
  }
  return out;
}

// ---------- 來源 ----------
const gnews = q => `https://news.google.com/rss/search?q=${encodeURIComponent(q)}&hl=en-US&gl=US&ceid=US:en`;
const FR = "https://www.federalregister.gov/api/v1/documents.json?per_page=25&order=newest";
const SOURCES = [
  ["Truth Social", "TRUMP", "https://www.trumpstruth.org/feed", "rss"],
  ["Google News", "TRUMP", gnews("Trump (tariff OR trade OR economy OR chips OR Fed OR tax OR China OR Taiwan OR stocks OR deal) when:1d"), "rss"],
  ["Google News", "TRUMP", gnews('Trump (interview OR remarks OR speech OR "press conference" OR "told reporters" OR "Oval Office" OR "town hall") when:2d'), "rss"],
  ["Google News", "NEWS", "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-US&gl=US&ceid=US:en", "rss"],
  ["Google News", "NEWS", gnews('(stocks OR "Wall Street" OR Nasdaq OR "S&P 500" OR Nvidia OR semiconductor) when:1d'), "rss"],
  ["CNBC", "NEWS", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114", "rss"],
  ["CNBC Tech", "NEWS", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=19854910", "rss"],
  ["MarketWatch", "NEWS", "https://feeds.content.dowjones.io/public/rss/mw_topstories", "rss"],
  ["白宮總統文件", "POLICY", `${FR}&conditions%5Btype%5D%5B%5D=PRESDOCU`, "fr"],
  ["商務部/USTR", "POLICY", `${FR}&conditions%5Bagencies%5D%5B%5D=industry-and-security-bureau&conditions%5Bagencies%5D%5B%5D=trade-representative-office-of-united-states`, "fr"],
  ["Google News", "POLICY", gnews("(Congress OR Senate OR House) (bill OR act OR legislation) (tech OR chips OR tariff OR tax OR AI OR crypto OR healthcare OR energy) when:3d"), "rss"],
  ["Google News", "POLICY", gnews('("executive order" OR "export controls" OR USTR OR "Section 232" OR "Section 301" OR "Commerce Department") when:3d'), "rss"],
];
if (CONGRESS_KEY) SOURCES.push(["Congress.gov", "POLICY", `https://api.congress.gov/v3/bill?format=json&limit=30&sort=updateDate+desc&api_key=${CONGRESS_KEY}`, "congress"]);

const CAT_LABEL = { TRUMP: "川普", NEWS: "財經", POLICY: "政策法案" };

function make(name, cat, title, body, link, source, time) {
  return { id: (link || title).slice(0, 300), title, body, link, source: source || name, time, cat, themes: matchThemes(`${title} ${body}`) };
}
function splitSource(title, src) {
  if (src) return [title.endsWith(` - ${src}`) ? title.slice(0, -(src.length + 3)) : title, src];
  const i = title.lastIndexOf(" - ");
  return i > 20 && title.length - i < 40 ? [title.slice(0, i), title.slice(i + 3)] : [title, ""];
}

async function fetchSource([name, cat, url, kind]) {
  if (kind === "fr") {
    const o = JSON.parse(await get(url));
    return (o.results || []).filter(r => r.title).map(r =>
      make(name, cat, r.title, r.abstract || "", r.html_url, name, Date.parse(r.publication_date + "T12:00:00-05:00") || 0));
  }
  if (kind === "congress") {
    const o = JSON.parse(await get(url));
    const kinds = { hr: "house-bill", s: "senate-bill", hjres: "house-joint-resolution", sjres: "senate-joint-resolution", hres: "house-resolution", sres: "senate-resolution" };
    return (o.bills || []).map(b => {
      const k = kinds[(b.type || "").toLowerCase()] || "house-bill";
      const la = b.latestAction || {};
      return make(name, cat, `[${b.type} ${b.number}] ${b.title}`, `最新進度：${la.text || ""}`,
        `https://www.congress.gov/bill/${b.congress}th-congress/${k}/${b.number}`, "Congress.gov", Date.parse(la.actionDate || b.updateDate) || 0);
    });
  }
  const items = parseRss(await get(url)).slice(0, 40);
  return items.map(r => {
    if (name === "Truth Social") {
      const text = r.desc || r.title;
      const title = text.length > 220 ? text.slice(0, 220) + "…" : text;
      return make(name, cat, title || "(圖片/影片貼文)", text, r.link, "Truth Social", r.date);
    }
    const [t, s] = splitSource(r.title, r.source);
    const body = r.desc.startsWith(t.slice(0, 30)) ? "" : r.desc;
    return make(name, cat, t, body, r.link, s || name, r.date);
  });
}

// ---------- 市場指數 / 台股報價 ----------
const MARKETS = [["^GSPC", "S&P500", ".SPX"], ["^IXIC", "那斯達克", ".IXIC"], ["^SOX", "費城半導體", ".SOX"], ["TSM", "台積電ADR", "TSM"],
  ["^TWII", "台股加權", null], ["^VIX", "恐慌指數", ".VIX"], ["TWD=X", "美元/台幣", "TWD="]];
async function yahoo(sym) {
  for (const host of ["query1", "query2"]) {
    try {
      const o = JSON.parse(await get(`https://${host}.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(sym)}?range=1d&interval=1d`));
      const m = o.chart.result[0].meta;
      if (m.regularMarketPrice) return { price: m.regularMarketPrice, prev: m.chartPreviousClose || m.previousClose || 0 };
    } catch (e) { log("Yahoo", host, sym, e.message); }
  }
  return null;
}
async function cnbc(symbols) {
  const out = {};
  try {
    const url = `https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol?symbols=${encodeURIComponent(symbols.join("|"))}&requestMethod=itv&noform=1&partnerId=2&fund=1&exthrs=1&output=json`;
    const o = JSON.parse(await get(url));
    let q = o?.FormattedQuoteResult?.FormattedQuote || [];
    if (!Array.isArray(q)) q = [q];
    for (const x of q) {
      const n = v => parseFloat(String(v ?? "").replace(/[,%+]/g, "")) || 0;
      const price = n(x.last), prev = n(x.previous_day_closing) || (price - n(x.change));
      if (price) out[x.symbol] = { price, prev };
    }
  } catch (e) { log("CNBC 報價失敗", e.message); }
  return out;
}
async function twii() {
  try {
    const o = JSON.parse(await get(`https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=tse_t00.tw&json=1&delay=0&_=${Date.now()}`,
      { headers: { Referer: "https://mis.twse.com.tw/stock/index.jsp" } }));
    const m = (o.msgArray || [])[0];
    if (m) { const price = num(m.z) || num(m.o), prev = num(m.y); if (price) return { price, prev }; }
  } catch (e) { log("加權指數失敗", e.message); }
  return null;
}
async function fetchMarkets() {
  const res = {};
  await Promise.all(MARKETS.map(async ([sym]) => { const r = await yahoo(sym); if (r) res[sym] = r; }));
  const miss = MARKETS.filter(([sym, , c]) => !res[sym] && c);
  if (miss.length) {
    const c = await cnbc(miss.map(m => m[2]));
    for (const [sym, , cs] of miss) if (c[cs]) res[sym] = c[cs];
  }
  if (!res["^TWII"]) { const t = await twii(); if (t) res["^TWII"] = t; }
  return MARKETS.filter(([sym]) => res[sym]).map(([sym, label]) => ({ symbol: sym, label, price: res[sym].price, prev: res[sym].prev }));
}

const num = s => { if (!s || s === "-") return 0; const v = parseFloat(String(s).split("_").find(x => x) || ""); return isNaN(v) ? 0 : v; };
async function fetchQuotes(codes) {
  const out = {};
  for (let i = 0; i < codes.length; i += 20) {
    const chunk = codes.slice(i, i + 20);
    try {
      const ex = chunk.flatMap(c => [`tse_${c}.tw`, `otc_${c}.tw`]).join("|");
      const o = JSON.parse(await get(`https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=${encodeURIComponent(ex)}&json=1&delay=0&_=${Date.now()}`,
        { headers: { Referer: "https://mis.twse.com.tw/stock/index.jsp" } }));
      for (const m of o.msgArray || []) {
        if (!m.c) continue;
        const prev = num(m.y);
        const price = num(m.z) || num(m.trade?.z) || num(m.b) || num(m.a) || num(m.o) || prev;
        const arr = s => String(s || "").split("_").filter(x => x !== "").map(x => parseFloat(x) || 0);
        out[m.c] = { name: m.n || STOCK_NAMES[m.c] || "", price, prev, time: m.t || "", vol: parseInt(m.v) || 0,
          ap: arr(m.a), av: arr(m.f), bp: arr(m.b), bv: arr(m.g), hi: num(m.h), lo: num(m.l), op: num(m.o) };
      }
    } catch (e) { log("mis 失敗", e.message); }
  }
  const missing = codes.filter(c => !out[c]);
  if (missing.length) {
    try {
      const arr = JSON.parse(await get("https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL", {}, 30000));
      for (const o of arr) if (missing.includes(o.Code)) {
        const close = parseFloat((o.ClosingPrice || "").replace(/,/g, "")) || 0, chg = parseFloat((o.Change || "").replace(/,|\+/g, "")) || 0;
        out[o.Code] = { name: o.Name, price: close, prev: close - chg, time: "收盤" };
      }
    } catch (e) { log("TWSE openapi 失敗", e.message); }
    const rest = missing.filter(c => !out[c]);
    if (rest.length) try {
      const arr = JSON.parse(await get("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes", {}, 30000));
      for (const o of arr) if (rest.includes(o.SecuritiesCompanyCode)) {
        const close = parseFloat((o.Close || "").replace(/,/g, "")) || 0, chg = parseFloat((o.Change || "").replace(/,|\+/g, "")) || 0;
        out[o.SecuritiesCompanyCode] = { name: o.CompanyName, price: close, prev: close - chg, time: "收盤" };
      }
    } catch (e) { log("TPEx openapi 失敗", e.message); }
  }
  return out;
}

// ---------- Gemini ----------
const BASE = "https://generativelanguage.googleapis.com/v1beta";
const ver = n => parseFloat((n.match(/gemini-(\d+(?:\.\d+)?)/) || [])[1] || "0");
async function rankModels() {
  try {
    const o = JSON.parse(await get(`${BASE}/models?pageSize=200`, { headers: { "x-goog-api-key": GEMINI_KEY } }));
    const bad = ["image", "tts", "audio", "live", "embedding", "vision", "learnlm", "computer", "robotics", "native", "thinking", "8b"];
    const names = (o.models || []).filter(m => (m.supportedGenerationMethods || []).includes("generateContent")).map(m => m.name.replace("models/", ""))
      .filter(n => n.includes("gemini") && n.includes("flash") && !bad.some(b => n.includes(b)));
    names.sort((a, b) => ver(b) - ver(a) || (a.includes("lite") - b.includes("lite")) || ((/preview|exp/.test(a)) - (/preview|exp/.test(b))) || a.length - b.length);
    if (names.length) return names;
  } catch (e) { log("列模型失敗", e.message); }
  return ["gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest"];
}
let MODELS = null;
function thinking(m) {
  if (m.includes("2.5") && !m.includes("pro")) return { thinkingBudget: 0 };
  if (ver(m) >= 3 || m.includes("latest")) return { thinkingLevel: "low" };
  return null;
}
async function gen(model, prompt, useThink = true) {
  const gc = { temperature: 0.4, responseMimeType: "application/json" };
  const tc = useThink ? thinking(model) : null;
  if (tc) gc.thinkingConfig = tc;
  try {
    const r = JSON.parse(await get(`${BASE}/models/${model}:generateContent`, {
      method: "POST", headers: { "x-goog-api-key": GEMINI_KEY, "Content-Type": "application/json" },
      body: JSON.stringify({ contents: [{ role: "user", parts: [{ text: prompt }] }], generationConfig: gc }),
    }, 50000));
    const parts = r.candidates?.[0]?.content?.parts || [];
    return parts.filter(p => !p.thought).map(p => p.text || "").join("");
  } catch (e) {
    if (tc && e.status === 400 && /think/i.test(e.body || "")) return gen(model, prompt, false);
    throw e;
  }
}
function extractJson(t) {
  const s = t.replace(/```json|```/g, ""); const a = s.indexOf("{"), b = s.lastIndexOf("}");
  return JSON.parse(s.slice(a, b + 1));
}
let LAST_OK = "", LAST_LITE = "";
// lite=true：翻譯用 Flash-Lite（免費額度較多），日報用一般 Flash；兩者額度分開計算
async function ask(prompt, lite = false) {
  MODELS = MODELS || await rankModels();
  let list = MODELS;
  if (lite) {
    list = [...MODELS.filter(m => m.includes("lite")), ...MODELS.filter(m => !m.includes("lite"))];
    if (LAST_LITE && list.includes(LAST_LITE)) list = [LAST_LITE, ...list.filter(m => m !== LAST_LITE)];
  } else if (LAST_OK && list.includes(LAST_OK)) list = [LAST_OK, ...list.filter(m => m !== LAST_OK)];
  let last;
  for (const m of list.slice(0, 4)) {
    try { const o = extractJson(await gen(m, prompt)); log("AI OK", m); if (lite) LAST_LITE = m; else LAST_OK = m; return o; }
    catch (e) { last = e; log("AI 失敗", m, e.message); if (e.status === 400 && /API key/i.test(e.body || "")) break; }
  }
  throw last;
}

const now = () => new Date().toLocaleString("zh-TW", { timeZone: "Asia/Taipei", hour12: false });
const fmt = t => t ? new Date(t).toLocaleString("zh-TW", { timeZone: "Asia/Taipei", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }) : "未知";
const STOCK_RULE = "台股代號必須是真實存在的台灣上市櫃股票或ETF（4~6碼），name 用台灣常用簡稱。";

function translatePrompt(items) {
  const list = items.map((it, i) => {
    const text = (it.body.length > it.title.length ? it.body : it.title).slice(0, 1500);
    const hint = it.themes.length ? " 題材提示：" + it.themes.join("、") : "";
    return `[${i}]（${it.source}，${fmt(it.time)}）${hint}\n${text}`;
  }).join("\n\n");
  return `你是專業財經翻譯兼台股分析師。請把下面每一則川普發言／美國消息翻譯成通順的繁體中文（台灣用語；常見名稱用台灣譯名，例如 Nvidia→輝達、Fed→聯準會），並判斷可能有關的台股。
規則：
- zh：完整翻出原文意思，不要省略或加油添醋；如果只是連結、影片、轉貼或沒有實質內容，寫「（影片／連結貼文）」再簡述。
- stocks：0~4 檔最直接相關的台股，action 只能是「利多」或「利空」，reason 10~20 字說明關聯，reason_en 是同一句的英文。跟台股沒關係就給空陣列，不要硬湊。
- importance：1~5，對市場的影響程度（一般政治口水給 1）。
- sentiment：對台股整體「利多／利空／中性」。
${STOCK_RULE}
只輸出 JSON：{"items":[{"i":0,"zh":"","importance":1,"sentiment":"中性","stocks":[{"code":"2330","name":"台積電","action":"利多","reason":"","reason_en":""}]}]}
每一則都要有，i 對應編號。

${list}`;
}

function briefPrompt(items, markets) {
  const lines = items.map(it => `[${CAT_LABEL[it.cat]}][${it.source}][${fmt(it.time)}] ${(it.tr?.zh || it.title).slice(0, 200)}`).join("\n");
  const mk = markets.map(m => `${m.label} ${m.price.toFixed(2)} (${(m.prev ? (m.price - m.prev) / m.prev * 100 : 0).toFixed(2)}%)`).join("；") || "（無資料）";
  return `你是資深的台股投資策略分析師。以下是最近 24~36 小時的美國財經新聞、川普發言與美國政策法案，以及最新市場指數。請整理成給台灣投資人看的「今日美國消息 × 台股盤勢 AI 日報」。
現在時間：${now()}（台灣時間）
市場指數：${mk}

消息列表：
${lines}

只輸出 JSON：
{"mood":"偏多/偏空/中性/震盪","mood_score":-5~5整數,"headline":"一句話總結今天最重要的事","events":[{"title":"事件(繁中)","impact":"對台股影響說明","sectors":["產業"]}],"buy":[{"code":"","name":"","action":"買進","confidence":0~100,"reason":""}],"sell":[{"code":"","name":"","action":"賣出","confidence":0~100,"reason":""}],"watch":[{"code":"","name":"","action":"觀望","confidence":0~100,"reason":""}],"advice":"今日操作建議與風險提醒（3~5句）"}
events 挑 3~6 件最重要的；buy/sell/watch 各 0~5 檔。${STOCK_RULE} action 只能是「買進」「賣出」「觀望」。
另外請同時提供英文版：headline_en、advice_en，每個 event 加 title_en、impact_en，每檔股票加 reason_en。`;
}

const cleanCode = c => (String(c).toUpperCase().match(/\d{4,6}[A-Z]?/) || [String(c).trim()])[0];
function normPicks(arr, def) {
  return (Array.isArray(arr) ? arr : []).filter(p => p && p.code).map(p => ({
    code: cleanCode(p.code), name: p.name || STOCK_NAMES[cleanCode(p.code)] || "", action: p.action || def,
    confidence: p.confidence != null ? Math.max(0, Math.min(100, parseInt(p.confidence) || 0)) : undefined, reason: p.reason || "", reason_en: p.reason_en || "",
  }));
}

// ---------- 主流程 ----------
async function main() {
  const chipsP = updateChips().catch(e => log("籌碼更新失敗", e.message));
  let prev = {};
  try { prev = JSON.parse(await get(`${PAGES}?t=${Date.now()}`)); log("讀到上一版資料", prev.items?.length); } catch { log("沒有上一版資料（第一次執行）"); }
  const prevTr = {};
  for (const it of prev.items || []) if (it.tr) prevTr[it.id] = it.tr;
  Object.assign(prevTr, prev.extraTr || {});
  LAST_OK = prev.lastModel || ""; LAST_LITE = prev.lastLite || "";

  const srcKey = s => s[2].replace(/when:\d+d|[?&]t=\d+/g, "").slice(0, 120);
  const results = await Promise.all(SOURCES.map(async s => {
    const key = srcKey(s);
    let r = [];
    for (let attempt = 0; attempt < 2; attempt++) {
      try { r = await fetchSource(s); break; }
      catch (e) {
        log(`${s[0]} 失敗(${attempt + 1})`, e.message);
        if (attempt === 0) { await new Promise(res => setTimeout(res, 3000 + Math.random() * 3000)); continue; }
        // 暫時失敗：沿用上一輪這個來源的資料
        r = (prev.items || []).filter(it => it.src === key);
        errors.push(`${s[0]}(${CAT_LABEL[s[1]]}) 暫時讀取失敗${r.length ? "，沿用上一輪" : ""}`);
      }
    }
    r.forEach(it => it.src = key);
    log(`${s[0]}(${s[1]}) ${r.length} 則`);
    return r;
  }));
  const seen = new Set();
  let items = results.flat().sort((a, b) => b.time - a.time).filter(it => {
    const k = it.title.toLowerCase().replace(/[^a-z0-9一-鿿]/g, "").slice(0, 60);
    if (!k || seen.has(k)) return false; seen.add(k); return true;
  });
  const ids = new Set(); items = items.filter(it => !ids.has(it.id) && ids.add(it.id));
  // 每類保留數量
  const cap = { TRUMP: 100, NEWS: 120, POLICY: 80 };
  const cnt = {}; items = items.filter(it => (cnt[it.cat] = (cnt[it.cat] || 0) + 1) <= cap[it.cat]);
  if (!items.length && prev.items) { items = prev.items; errors.push("本次全部來源失敗，顯示上一次資料"); }
  for (const it of items) if (!it.tr && prevTr[it.id]) it.tr = prevTr[it.id];

  const markets = await fetchMarkets();
  log("市場指數", markets.length);

  // AI 翻譯 + 日報
  let brief = prev.brief || null;
  let aiNote = GEMINI_KEY ? "" : "雲端未設定 GEMINI_API_KEY，翻譯需在網頁設定自己的 Key";
  if (GEMINI_KEY) {
    // 省額度：只翻最近 48 小時的川普分頁，每次最多 20 則（2 次 AI 請求）
    const cut48 = Date.now() - 48 * 3600_000;
    const todo = items.filter(it => it.cat === "TRUMP" && !it.tr && (!it.time || it.time > cut48)).slice(0, 20);
    for (let i = 0; i < todo.length; i += 10) {
      const chunk = todo.slice(i, i + 10);
      try {
        const o = await ask(translatePrompt(chunk), true);
        for (const [k, e] of (o.items || []).entries()) {
          const it = chunk[e.i ?? k]; if (!it || !e.zh) continue;
          it.tr = { zh: e.zh, importance: Math.max(1, Math.min(5, parseInt(e.importance) || 1)), sentiment: e.sentiment || "中性", stocks: normPicks(e.stocks, "相關"), t: Date.now() };
        }
        log(`翻譯 ${i + chunk.length}/${todo.length}`);
      } catch (e) { aiNote = "AI 翻譯暫時失敗：" + e.message; break; }
      await new Promise(r => setTimeout(r, 1500));
    }
    const briefAge = brief ? Date.now() - (brief.t || 0) : Infinity;
    if (briefAge > 6 * 3600_000) {
      const cutoff = Date.now() - 36 * 3600_000;
      const recent = items.filter(it => !it.time || it.time > cutoff);
      const top = (c, n) => recent.filter(it => it.cat === c).sort((a, b) => (b.themes.length > 0) - (a.themes.length > 0)).slice(0, n);
      const pick = [...top("TRUMP", 12), ...top("POLICY", 10), ...top("NEWS", 18)];
      if (pick.length) try {
        const o = await ask(briefPrompt(pick, markets));
        brief = {
          t: Date.now(), mood: o.mood || "中性", mood_score: Math.max(-5, Math.min(5, parseInt(o.mood_score) || 0)), headline: o.headline || "", headline_en: o.headline_en || "",
          events: (o.events || []).map(e => ({ title: e.title || "", impact: e.impact || "", title_en: e.title_en || "", impact_en: e.impact_en || "", sectors: e.sectors || [] })),
          buy: normPicks(o.buy, "買進"), sell: normPicks(o.sell, "賣出"), watch: normPicks(o.watch, "觀望"), advice: o.advice || "", advice_en: o.advice_en || "",
        };
        log("日報已更新");
      } catch (e) { log("日報失敗", e.message); }
    }
  }

  // 舊日報沒有英文版 → 用 Flash-Lite 補翻一次（給英文模式用）
  if (GEMINI_KEY && brief && !brief.headline_en) {
    try {
      const src = { headline: brief.headline, advice: brief.advice, events: (brief.events || []).map(e => ({ title: e.title, impact: e.impact })),
        buy: brief.buy.map(p => ({ reason: p.reason })), sell: brief.sell.map(p => ({ reason: p.reason })), watch: brief.watch.map(p => ({ reason: p.reason })) };
      const o = await ask(`Translate this Traditional Chinese stock-market brief into natural, concise English for foreign investors.
Return JSON with the SAME structure and order, only these keys:
{"headline_en":"","advice_en":"","events":[{"title_en":"","impact_en":""}],"buy":[{"reason_en":""}],"sell":[{"reason_en":""}],"watch":[{"reason_en":""}]}

${JSON.stringify(src)}`, true);
      brief.headline_en = o.headline_en || ""; brief.advice_en = o.advice_en || "";
      (brief.events || []).forEach((e, i) => { e.title_en = o.events?.[i]?.title_en || ""; e.impact_en = o.events?.[i]?.impact_en || ""; });
      for (const k of ["buy", "sell", "watch"]) brief[k].forEach((p, i) => { p.reason_en = o[k]?.[i]?.reason_en || ""; });
      log("日報英文版已補上");
    } catch (e) { log("日報英文翻譯失敗", e.message); }
  }

  // 台股報價：所有題材股 + AI 提到的股票 + 預設自選
  const codes = new Set([...DEFAULT_WATCH, ...THEMES.flatMap(t => t.stocks.map(s => s[0]))]);
  for (const it of items) for (const p of it.tr?.stocks || []) codes.add(p.code);
  if (brief) for (const p of [...brief.buy, ...brief.sell, ...brief.watch]) codes.add(p.code);
  const quotes = await fetchQuotes([...codes].slice(0, 200));
  log("台股報價", Object.keys(quotes).length, "/", codes.size);

  // 保留舊翻譯（避免同一則被擠出列表後又重翻）
  const extraTr = {};
  const live = new Set(items.map(i => i.id));
  for (const [k, v] of Object.entries(prevTr)) if (!live.has(k) && Date.now() - (v.t || 0) < 3 * 86400_000) extraTr[k] = v;

  await chipsP;
  mkdirSync("web/data", { recursive: true });
  const data = { updated: Date.now(), items, markets, quotes: Object.keys(quotes).length ? quotes : (prev.quotes || {}), brief, errors, aiNote, extraTr, lastModel: LAST_OK, lastLite: LAST_LITE };
  writeFileSync("web/data/data.json", JSON.stringify(data));
  log("完成：", items.length, "則，", errors.length, "個來源失敗", aiNote);
}

// 就算這次出錯，也保留上一版資料，網站不會變空白
main().catch(async e => {
  console.error("本次更新失敗：", e);
  try {
    const prev = JSON.parse(await get(`${PAGES}?t=${Date.now()}`));
    prev.errors = [...(prev.errors || []), "最近一次雲端更新失敗，顯示上一次資料"];
    mkdirSync("web/data", { recursive: true });
    writeFileSync("web/data/data.json", JSON.stringify(prev));
  } catch { }
});
