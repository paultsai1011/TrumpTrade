import { STOCK_NAMES, themeByName, themesOfStock, DEFAULT_WATCH, THEME_EN, EN_NAMES } from "./themes.js";

// ================= 狀態 =================
const LS = {
  get: (k, d) => { try { const v = localStorage.getItem("tt_" + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set: (k, v) => { try { localStorage.setItem("tt_" + k, JSON.stringify(v)); } catch { } },
};
const S = {
  data: { items: [], markets: [], quotes: {}, brief: null, errors: [], updated: 0 },
  loading: false,
  tab: "home",
  stack: [],                                  // 子頁面堆疊：{type:'news',id} | {type:'stock',code} | {type:'settings'}
  key: LS.get("key", ""),
  watch: LS.get("watch", DEFAULT_WATCH),
  localTr: LS.get("tr", {}),
  analyses: LS.get("an", {}),
  stockAn: LS.get("san", {}),
  localBrief: LS.get("brief", null),
  busy: {},                                   // 進行中的 AI 工作
  filters: {},
  en: LS.get("en", false),
  kind: "all",
};
// ---- 中英文 ----
const T = (zh, en) => S.en ? en : zh;
const TERMS = { "利多": "Bullish", "利空": "Bearish", "中性": "Neutral", "多空交雜": "Mixed", "買進": "Buy", "賣出": "Sell", "觀望": "Hold", "相關": "Related", "偏多": "Bullish", "偏空": "Bearish", "震盪": "Volatile" };
const term = s => S.en ? (TERMS[String(s).trim()] || s) : s;
const themeLabel = n => S.en ? (THEME_EN[n] || n) : n;
const stockLabel = (code, zh) => S.en ? (EN_NAMES[code] || zh || code) : (zh || STOCK_NAMES[code] || code);
const reasonOf = p => S.en && p.reason_en ? p.reason_en : p.reason;
const pickEn = (o, k) => S.en && o[k + "_en"] ? o[k + "_en"] : o[k];
const TALK_RE = /\b(interview|interviewed|remarks|speech|speaks|spoke|press conference|news conference|told reporters|Oval Office|town hall|rally|address|briefing|podcast|Fox News|Meet the Press|60 Minutes|says in|said in)\b|專訪|受訪|演講|記者會/i;
const kindOf = it => it.source === "Truth Social" ? "posts" : TALK_RE.test(it.title) ? "talks" : "news";
const CAT = { TRUMP: "川普", NEWS: "財經", POLICY: "政策法案" };

// ================= 工具 =================
const $ = s => document.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const pct = q => q && q.price > 0 && q.prev > 0 ? (q.price - q.prev) / q.prev * 100 : 0;
const cls = v => v > 0.0001 ? "up" : v < -0.0001 ? "down" : "flat";
const actCls = a => /買|利多/.test(a) ? "up" : /賣|利空/.test(a) ? "down" : "flat";
const senCls = s => /多/.test(s) && !/空/.test(s) ? "up" : /空/.test(s) && !/多/.test(s) ? "down" : "gold";
function ago(t) {
  if (!t) return "";
  const d = (Date.now() - t) / 60000;
  if (d < 1) return T("剛剛", "just now"); if (d < 60) return T(`${Math.floor(d)}分鐘前`, `${Math.floor(d)}m ago`); if (d < 1440) return T(`${Math.floor(d / 60)}小時前`, `${Math.floor(d / 60)}h ago`);
  if (d < 10080) return T(`${Math.floor(d / 1440)}天前`, `${Math.floor(d / 1440)}d ago`);
  return new Date(t).toLocaleDateString("zh-TW", { month: "2-digit", day: "2-digit" });
}
const full = t => t ? new Date(t).toLocaleString("zh-TW", { hour12: false, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "";
let toastTimer;
function toast(m) { const el = $("#toast"); el.textContent = m; el.classList.remove("hidden"); clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.add("hidden"), 3500); }
const trOf = it => S.localTr[it.id] || it.tr || null;
const nameOf = c => S.data.quotes[c]?.name || STOCK_NAMES[c] || findPickName(c) || "";
function findPickName(c) {
  for (const it of S.data.items) for (const p of trOf(it)?.stocks || []) if (p.code === c && p.name) return p.name;
  for (const a of Object.values(S.analyses)) for (const p of a.stocks || []) if (p.code === c && p.name) return p.name;
  return "";
}

// ================= 資料 =================
async function load(manual) {
  S.loading = true; $("#bar").classList.remove("hidden");
  try {
    const r = await fetch(`data/data.json?t=${Date.now()}`, { cache: "no-store" });
    if (!r.ok) throw new Error(r.status);
    S.data = await r.json();
    if (manual) toast(T(`已更新（雲端資料 ${ago(S.data.updated)}）`, `Updated (cloud data ${ago(S.data.updated)})`));
  } catch (e) {
    toast(T("資料讀取失敗：雲端資料可能還在準備中，請稍後再試", "Failed to load data, please try again later"));
  }
  S.loading = false; $("#bar").classList.add("hidden");
  render();
}

// ================= Gemini（瀏覽器端，用自己的 Key） =================
const BASE = "https://generativelanguage.googleapis.com/v1beta";
const ver = n => parseFloat((n.match(/gemini-(\d+(?:\.\d+)?)/) || [])[1] || "0");
async function models() {
  const cache = LS.get("models", null);
  if (cache && Date.now() - cache.t < 12 * 3600_000 && cache.list.length) return cache.list;
  try {
    const r = await fetch(`${BASE}/models?pageSize=200&key=${encodeURIComponent(S.key)}`);
    const o = await r.json();
    const bad = ["image", "tts", "audio", "live", "embedding", "vision", "learnlm", "computer", "robotics", "native", "thinking", "8b"];
    const list = (o.models || []).filter(m => (m.supportedGenerationMethods || []).includes("generateContent")).map(m => m.name.replace("models/", ""))
      .filter(n => n.includes("gemini") && n.includes("flash") && !bad.some(b => n.includes(b)))
      .sort((a, b) => ver(b) - ver(a) || (a.includes("lite") - b.includes("lite")) || (/preview|exp/.test(a) - /preview|exp/.test(b)) || a.length - b.length);
    if (list.length) { LS.set("models", { t: Date.now(), list }); return list; }
  } catch { }
  return ["gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest"];
}
function thinking(m) {
  if (m.includes("2.5") && !m.includes("pro")) return { thinkingBudget: 0 };
  if (ver(m) >= 3 || m.includes("latest")) return { thinkingLevel: "low" };
  return null;
}
async function gen(model, prompt, useThink = true) {
  const gc = { temperature: 0.4, responseMimeType: "application/json" };
  const tc = useThink ? thinking(model) : null;
  if (tc) gc.thinkingConfig = tc;
  const r = await fetch(`${BASE}/models/${model}:generateContent?key=${encodeURIComponent(S.key)}`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ contents: [{ role: "user", parts: [{ text: prompt }] }], generationConfig: gc }),
  });
  const text = await r.text();
  if (!r.ok) {
    if (tc && r.status === 400 && /think/i.test(text)) return gen(model, prompt, false);
    const e = new Error(`HTTP ${r.status}`); e.status = r.status; e.body = text; throw e;
  }
  const o = JSON.parse(text);
  return (o.candidates?.[0]?.content?.parts || []).filter(p => !p.thought).map(p => p.text || "").join("");
}
async function ask(prompt, lite = false) {
  if (!S.key) throw new Error(T("請先到 ⚙ 設定輸入 Gemini API Key", "Enter your Gemini API key in ⚙ Settings first"));
  let list = await models();
  if (lite) list = [...list.filter(m => m.includes("lite")), ...list.filter(m => !m.includes("lite"))];
  const ok = LS.get(lite ? "lastLite" : "lastModel", "");
  if (ok && list.includes(ok)) list = [ok, ...list.filter(m => m !== ok)];
  let last;
  for (const m of list.slice(0, 4)) {
    try {
      const t = await gen(m, prompt);
      const s = t.replace(/```json|```/g, ""); const a = s.indexOf("{"), b = s.lastIndexOf("}");
      const o = JSON.parse(s.slice(a, b + 1));
      LS.set(lite ? "lastLite" : "lastModel", m);
      return o;
    } catch (e) {
      last = e;
      if (e.status === 400 && /API key/i.test(e.body || "")) throw new Error("Gemini API Key 無效，請到設定檢查");
      if (e.status === 403) throw new Error("API Key 沒有權限 (403)");
    }
  }
  if (last?.status === 429) throw new Error("今天的免費 AI 額度暫時用完(429)，請稍後再試");
  throw new Error("AI 分析失敗：" + (last?.message || ""));
}
const cleanCode = c => (String(c).toUpperCase().match(/\d{4,6}[A-Z]?/) || [String(c).trim()])[0];
const norm = (arr, def) => (Array.isArray(arr) ? arr : []).filter(p => p && p.code).map(p => ({
  code: cleanCode(p.code), name: p.name || STOCK_NAMES[cleanCode(p.code)] || "", action: p.action || def,
  confidence: p.confidence != null ? Math.max(0, Math.min(100, parseInt(p.confidence) || 0)) : undefined, reason: p.reason || "", reason_en: p.reason_en || "",
}));
const langRule = () => S.en ? "\n重要：除了 action、sentiment、mood 這些判斷詞維持中文關鍵字之外，其餘所有文字欄位一律用英文撰寫，股票 name 用英文公司名。" : "";
const fmt = t => t ? new Date(t).toLocaleString("zh-TW", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }) : "未知";
const nowStr = () => new Date().toLocaleString("zh-TW", { hour12: false });
const RULE = "台股代號必須是真實存在的台灣上市櫃股票或ETF（4~6碼），name 用台灣常用簡稱。";

const P = {
  translate: items => `你是專業財經翻譯兼台股分析師。請把下面每一則川普發言／美國消息翻譯成通順的繁體中文（台灣用語；常見名稱用台灣譯名，例如 Nvidia→輝達、Fed→聯準會），並判斷可能有關的台股。
規則：
- zh：完整翻出原文意思，不要省略或加油添醋；如果只是連結、影片、轉貼或沒有實質內容，寫「（影片／連結貼文）」再簡述。
- stocks：0~4 檔最直接相關的台股，action 只能是「利多」或「利空」，reason 10~20 字說明關聯，reason_en 是同一句的英文。跟台股沒關係就給空陣列，不要硬湊。
- importance：1~5，對市場的影響程度（一般政治口水給 1）。
- sentiment：對台股整體「利多／利空／中性」。
${RULE}
只輸出 JSON：{"items":[{"i":0,"zh":"","importance":1,"sentiment":"中性","stocks":[{"code":"2330","name":"台積電","action":"利多","reason":"","reason_en":""}]}]}
每一則都要有，i 對應編號。

` + items.map((it, i) => `[${i}]（${it.source}，${fmt(it.time)}）${it.themes.length ? " 題材提示：" + it.themes.join("、") : ""}\n${(it.body.length > it.title.length ? it.body : it.title).slice(0, 1500)}`).join("\n\n"),

  news: it => `你是資深的美股與台股產業分析師。請分析下面這則美國消息（新聞/川普發言/政策法案）對「美國科技業、美國民生經濟」以及「台灣股市」的可能影響。
現在時間：${nowStr()}（台灣時間）
類別：${CAT[it.cat]}　來源：${it.source}　發布時間：${fmt(it.time)}
內容：
${it.title}
${(it.body || "").slice(0, 3500)}

系統依關鍵字比對到的題材與台股（僅供參考）：
${it.themes.map(n => { const t = themeByName(n); return t ? `- ${t.name}：${t.stocks.map(s => s.join(" ")).join("、")}` : ""; }).join("\n") || "（無）"}

只輸出 JSON：
{"summary":"繁體中文 2~4 句重點摘要","importance":1~5,"sentiment":"利多/利空/中性/多空交雜","sectors":["受影響產業"],"us_impact":"對美國科技與民生的影響","tw_impact":"對台股與台灣產業鏈的影響","stocks":[{"code":"2330","name":"台積電","action":"買進","confidence":0~100,"reason":"一句理由"}],"risk":"主要風險與不確定性"}
stocks 列 2~6 檔最直接受影響的台股，action 只能是「買進」「賣出」「觀望」。${RULE} 若與市場無關，importance 給 1、stocks 給空陣列。${langRule()}`,

  brief: (items, markets) => `你是資深的台股投資策略分析師。以下是最近的美國財經新聞、川普發言與政策法案，以及最新市場指數。請整理成「今日美國消息 × 台股盤勢 AI 日報」。
現在時間：${nowStr()}（台灣時間）
市場指數：${markets.map(m => `${m.label} ${m.price.toFixed(2)} (${pct(m).toFixed(2)}%)`).join("；") || "（無）"}

消息列表：
${items.map(it => `[${CAT[it.cat]}][${it.source}][${fmt(it.time)}] ${(trOf(it)?.zh || it.title).slice(0, 200)}`).join("\n")}

只輸出 JSON：
{"mood":"偏多/偏空/中性/震盪","mood_score":-5~5,"headline":"一句話總結","events":[{"title":"事件","impact":"對台股影響","sectors":["產業"]}],"buy":[{"code":"","name":"","action":"買進","confidence":0~100,"reason":""}],"sell":[{"code":"","name":"","action":"賣出","confidence":0~100,"reason":""}],"watch":[{"code":"","name":"","action":"觀望","confidence":0~100,"reason":""}],"advice":"今日操作建議與風險提醒（3~5句）"}
events 挑 3~6 件；buy/sell/watch 各 0~5 檔。${RULE}
另外請同時提供英文版：headline_en、advice_en，每個 event 加 title_en、impact_en，每檔股票加 reason_en。`,

  stock: (code, name, q, themes, news) => `你是資深台股分析師。請評估台股 ${code} ${name} 在目前美國政策與國際新聞環境下的操作建議。
現在時間：${nowStr()}（台灣時間）
${q && q.price ? `現價 ${q.price}（${pct(q).toFixed(2)}%），昨收 ${q.prev}` : "報價暫無"}
所屬題材：${themes.join("、") || "未分類"}

相關美國新聞／川普／政策：
${news.slice(0, 15).map(it => `- [${fmt(it.time)}] ${(trOf(it)?.zh || it.title).slice(0, 180)}`).join("\n") || "（無）"}

只輸出 JSON：
{"action":"買進/賣出/觀望","confidence":0~100,"summary":"2~3句總結","short_term":"短線(1~2週)看法","mid_term":"中線(1~3個月)看法","reasons":["理由"],"risks":["風險"],"catalysts":["接下來要觀察的事件"]}${langRule()}`,
};

async function job(key, fn) {
  if (S.busy[key]) return;
  S.busy[key] = { t: Date.now(), msg: T("AI 分析中", "AI working") };
  render();
  const tick = setInterval(() => { if (S.busy[key]) updateProgress(); }, 1000);
  try { await fn(m => { if (S.busy[key]) S.busy[key].msg = m; updateProgress(); }); }
  catch (e) { toast(e.message || "AI 失敗"); }
  finally { clearInterval(tick); delete S.busy[key]; render(); }
}
function updateProgress() {
  document.querySelectorAll("[data-busy]").forEach(el => {
    const b = S.busy[el.dataset.busy];
    if (b) el.textContent = `${b.msg}… ${T("已 ", "")}${Math.floor((Date.now() - b.t) / 1000)}${T(" 秒", "s")}`;
  });
}
const progress = key => S.busy[key] ? `<div class="progress" data-busy="${esc(key)}">${esc(S.busy[key].msg)}…</div>` : "";

function translate(items) {
  const todo = items.filter(it => !trOf(it)).slice(0, 40);
  if (!todo.length) return toast(T("這頁都翻譯好了", "All translated"));
  job("tr", async status => {
    for (let i = 0; i < todo.length; i += 10) {
      const chunk = todo.slice(i, i + 10);
      status(T(`翻譯中 ${i + 1}~${i + chunk.length} / ${todo.length} 則`, `Translating ${i + 1}–${i + chunk.length} / ${todo.length}`));
      const o = await ask(P.translate(chunk), true);
      for (const [k, e] of (o.items || []).entries()) {
        const it = chunk[e.i ?? k]; if (!it || !e.zh) continue;
        S.localTr[it.id] = { zh: e.zh, importance: Math.max(1, Math.min(5, parseInt(e.importance) || 1)), sentiment: e.sentiment || "中性", stocks: norm(e.stocks, "相關"), t: Date.now() };
      }
      pruneSave("tr", S.localTr, 600);
      render();
    }
  });
}
function analyzeNews(it) {
  job("n:" + it.id, async () => {
    const o = await ask(P.news(it));
    S.analyses[it.id] = { ...o, stocks: norm(o.stocks, "觀望"), importance: Math.max(1, Math.min(5, parseInt(o.importance) || 1)), t: Date.now() };
    pruneSave("an", S.analyses, 200);
  });
}
function makeBrief() {
  const cutoff = Date.now() - 36 * 3600_000;
  const recent = S.data.items.filter(it => !it.time || it.time > cutoff);
  const top = (c, n) => recent.filter(it => it.cat === c).sort((a, b) => (b.themes.length > 0) - (a.themes.length > 0)).slice(0, n);
  const pick = [...top("TRUMP", 12), ...top("POLICY", 10), ...top("NEWS", 18)];
  if (!pick.length) return toast(T("還沒有新聞資料", "No news yet"));
  job("brief", async () => {
    const o = await ask(P.brief(pick, S.data.markets));
    S.localBrief = { t: Date.now(), mood: o.mood || "中性", mood_score: parseInt(o.mood_score) || 0, headline: o.headline || "", headline_en: o.headline_en || "", events: o.events || [],
      buy: norm(o.buy, "買進"), sell: norm(o.sell, "賣出"), watch: norm(o.watch, "觀望"), advice: o.advice || "", advice_en: o.advice_en || "" };
    LS.set("brief", S.localBrief);
  });
}
function analyzeStock(code) {
  job("s:" + code, async () => {
    const name = nameOf(code);
    const o = await ask(P.stock(code, name, S.data.quotes[code], themesOfStock(code).map(t => t.name), relatedNews(code)));
    S.stockAn[code] = { ...o, t: Date.now(), name };
    pruneSave("san", S.stockAn, 150);
  });
}
function pruneSave(k, obj, limit) {
  const keys = Object.keys(obj);
  if (keys.length > limit) keys.sort((a, b) => (obj[a].t || 0) - (obj[b].t || 0)).slice(0, keys.length - limit).forEach(x => delete obj[x]);
  LS.set(k, obj);
}
function relatedNews(code) {
  const th = new Set(themesOfStock(code).map(t => t.name));
  return S.data.items.filter(it => it.themes.some(t => th.has(t)) || (trOf(it)?.stocks || []).some(p => p.code === code)
    || (S.analyses[it.id]?.stocks || []).some(p => p.code === code)).slice(0, 30);
}

// ================= 畫面元件 =================
const catLabel = c => ({ TRUMP: T("川普", "Trump"), NEWS: T("財經", "Markets"), POLICY: T("政策法案", "Policy") }[c] || c);
const DISC = () => `<div class="disc">${T("⚠️ AI 依公開新聞自動產生的分析可能有誤，僅供參考，不構成投資建議。投資請自行判斷並承擔風險。", "⚠️ AI-generated from public news; may be wrong. For reference only, not investment advice.")}</div>`;
function priceHtml(code) {
  const q = S.data.quotes[code];
  if (!q || !q.price) return `<div class="price flat">--</div>`;
  const p = pct(q), c = cls(p);
  return `<div class="price ${c}"><b>${q.price.toFixed(2)}</b><br>${(q.price - q.prev >= 0 ? "+" : "") + (q.price - q.prev).toFixed(2)} (${p >= 0 ? "+" : ""}${p.toFixed(2)}%)</div>`;
}
function pickRow(p) {
  const conf = p.confidence != null ? ` ${p.confidence}%` : "";
  const r = reasonOf(p);
  return `<div class="pick" data-stock="${esc(p.code)}"><div class="n">${esc(stockLabel(p.code, p.name || nameOf(p.code)))} <span class="sub">${esc(p.code)}</span>
    ${r ? `<div class="r">${esc(r)}</div>` : ""}</div>${priceHtml(p.code)}<span class="badge ${actCls(p.action)} ${actCls(p.action)}">${esc(term(p.action))}${conf}</span></div>`;
}
function newsCard(it) {
  const t = trOf(it), a = S.analyses[it.id];
  const imp = a?.importance || t?.importance, sen = a?.sentiment || t?.sentiment;
  const picks = (t?.stocks?.length ? t.stocks : a?.stocks) || [];
  let body;
  if (t?.zh && !S.en) body = `<div class="zh">${esc(t.zh)}</div><div class="orig">${esc(it.title)}</div>`;
  else body = `<div class="title clamp4">${esc(it.title)}</div>` + (a?.summary ? `<div class="ai">🤖 ${esc(a.summary)}</div>` : "");
  let chips = "";
  if (picks.length) chips = `<div class="chips">${picks.map(p => `<button class="chip ${actCls(p.action)}" data-stock="${esc(p.code)}">${esc(stockLabel(p.code, p.name || nameOf(p.code)))} ${esc(p.code)} ${esc(term(p.action))}</button>`).join("")}</div>`;
  else if (!t && it.themes.length) chips = `<div class="chips">${it.themes.map(n => `<span class="chip tag">${esc(themeLabel(n))}</span>`).join("")}</div>`;
  else if (t) chips = `<div class="sub" style="margin-top:4px">${T("與台股無直接關聯", "No direct Taiwan stock link")}</div>`;
  return `<div class="card click" data-news="${esc(it.id)}">
    <div class="meta"><span class="grow">${esc(it.source)} · ${ago(it.time)}</span>${imp ? `<span class="gold">${"★".repeat(imp)}</span>` : ""}${sen ? `<b class="${senCls(sen)}">${esc(term(sen))}</b>` : ""}</div>
    ${body}${chips}</div>`;
}
function briefHtml() {
  const sb = S.data.brief, lb = S.localBrief;
  const b = lb && (!sb || (lb.t || 0) > (sb.t || 0)) ? lb : sb;
  let h = `<div class="card"><div class="row"><b class="grow" style="font-size:17px">${T("🤖 AI 今日盤勢日報", "🤖 AI daily brief")}</b>
    ${S.key ? `<button class="btn" data-act="brief" ${S.busy.brief ? "disabled" : ""}>${b ? T("重新產生", "Regenerate") : T("產生", "Generate")}</button>` : ""}</div>${progress("brief")}`;
  if (!b) return h + `<div class="sub">${S.key ? T("按「產生」讓 AI 讀完最近的川普發言、政策與美股新聞，整理出台股可能的買進／賣出名單。", "Tap Generate: AI reads recent Trump remarks, policy and US market news and lists Taiwan stocks to buy / sell.") : T("雲端每 6 小時自動產生，或到 ⚙ 設定填入自己的 Key 立即產生。", "Generated in the cloud every 6 hours, or add your own key in ⚙ Settings.")}</div></div>`;
  h += `<div class="sub">${T("產生時間 ", "Generated ")}${full(b.t)}</div>
    <div class="row" style="margin:6px 0"><span class="${senCls(b.mood)}" style="font-size:20px;font-weight:bold">${esc(term(b.mood))}</span>
    <span class="${cls(b.mood_score)}">${T("情緒分數", "Sentiment")} ${b.mood_score > 0 ? "+" : ""}${b.mood_score}</span></div>
    ${pickEn(b, "headline") ? `<div style="font-weight:bold;margin:4px 0">${esc(pickEn(b, "headline"))}</div>` : ""}`;
  for (const e of b.events || []) h += `<div style="margin:8px 0"><b>• ${esc(pickEn(e, "title"))}</b><div class="sub">${esc(pickEn(e, "impact"))}</div>
    ${(e.sectors || []).length && !S.en ? `<div class="chips">${e.sectors.map(s => `<span class="chip tag">${esc(s)}</span>`).join("")}</div>` : ""}</div>`;
  if (b.buy?.length) h += `<hr><b class="up">${T("📈 可留意買進", "📈 Consider buying")}</b>` + b.buy.map(pickRow).join("");
  if (b.sell?.length) h += `<hr><b class="down">${T("📉 可考慮賣出／避開", "📉 Consider selling / avoid")}</b>` + b.sell.map(pickRow).join("");
  if (b.watch?.length) h += `<hr><b>${T("👀 觀察名單", "👀 Watch")}</b>` + b.watch.map(pickRow).join("");
  if (pickEn(b, "advice")) h += `<hr><div>💡 ${esc(pickEn(b, "advice"))}</div>`;
  return h + `</div>`;
}
function legendHtml() {
  return `<details class="card legend"${LS.get("legendOpen", false) ? " open" : ""}>
    <summary>ⓘ <span class="gold">★</span>=${T("影響大小", "impact")}　<b class="up">${term("利多")}</b>／<b class="down">${term("利空")}</b>／<b class="gold">${term("中性")}</b><span class="grow"></span><span style="color:var(--pri)">${T("說明", "Help")}</span></summary>
    <div style="margin-top:8px"><b>${T("★ 星星：AI 判斷這則消息對市場的影響有多大", "★ Stars: how much AI thinks this moves the market")}</b>
    <div class="sub">${T("★ 影響很小（一般政治口水）　★★～★★★ 有點影響，值得留意　★★★★～★★★★★ 影響很大，相關股票可能明顯漲跌", "★ minor (political noise)　★★–★★★ worth watching　★★★★–★★★★★ major, related stocks may move a lot")}</div></div>
    <div style="margin-top:8px"><b>${T("右上角文字：對台股「整體」是好是壞", "Top-right label: overall effect on Taiwan stocks")}</b>
    <div class="sub">${T("利多＝偏好消息　利空＝偏壞消息　中性＝沒有明顯好壞或方向不明　多空交雜＝有些公司受惠、有些受害", "Bullish = good news　Bearish = bad news　Neutral = no clear direction　Mixed = some companies win, some lose")}</div></div>
    <div style="margin-top:8px"><b>${T("下方股票標籤：對「這一檔」的影響（漲紅跌綠）", "Stock tags: impact on that stock (red = up, green = down, Taiwan style)")}</b>
    <div class="chips"><span class="chip up">${T("可能受惠 利多", "May benefit · Bullish")}</span><span class="chip down">${T("可能受害 利空", "May suffer · Bearish")}</span><span class="chip">${T("有關但方向不明 觀望", "Related, unclear · Hold")}</span></div></div>
    <div class="sub" style="margin-top:6px">${T("點股票可看報價與 AI 買賣分析。以上皆為 AI 自動判斷，僅供參考，不構成投資建議。", "Tap a stock for quotes and AI analysis. AI judgments only, not investment advice.")}</div>
  </details>`;
}

// ================= 頁面 =================
function pageHome() {
  const d = S.data;
  let h = `<div class="markets">${d.markets.length ? d.markets.map(m => { const p = pct(m); return `<div class="mk"><div class="l">${esc(S.en ? m.symbol.replace("^GSPC", "S&P 500").replace("^IXIC", "Nasdaq").replace("^SOX", "SOX").replace("^TWII", "TAIEX").replace("^VIX", "VIX").replace("TWD=X", "USD/TWD").replace("TSM", "TSMC ADR") : m.label)}</div>
    <div class="p">${m.price.toLocaleString("en-US", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}</div><div class="c ${cls(p)}">${p >= 0 ? "+" : ""}${p.toFixed(2)}%</div></div>`; }).join("")
    : `<span class="sub">${T("市場指數載入中…", "Loading market indices…")}</span>`}</div>`;
  h += briefHtml();
  const cutoff = Date.now() - 24 * 3600_000;
  const counts = {};
  d.items.filter(it => it.time > cutoff).forEach(it => it.themes.forEach(t => counts[t] = (counts[t] || 0) + 1));
  const hot = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 8);
  if (hot.length) {
    h += `<div class="card"><b style="font-size:17px">${T("🔥 24小時題材熱度", "🔥 24h hot themes")}</b>`;
    for (const [n, c] of hot) {
      const t = themeByName(n);
      h += `<div style="margin-top:10px"><b>${esc(themeLabel(n))}</b> <span class="sub">${c} ${T("則", "items")}</span><div class="chips">${(t?.stocks || []).map(([code, nm]) => {
        const q = d.quotes[code], p = pct(q);
        return `<button class="chip ${q ? cls(p) : ""}" data-stock="${code}">${esc(stockLabel(code, nm))} ${q && q.price ? (p >= 0 ? "+" : "") + p.toFixed(1) + "%" : code}</button>`;
      }).join("")}</div></div>`;
    }
    h += `</div>`;
  }
  const trump = d.items.filter(it => it.cat === "TRUMP" && it.source === "Truth Social").slice(0, 3);
  h += `<h2>${T("🇺🇸 川普最新動態", "🇺🇸 Latest from Trump")}</h2>` + (trump.length ? trump : d.items.filter(it => it.cat === "TRUMP").slice(0, 3)).map(newsCard).join("");
  h += `<h2>${T("📜 最新政策", "📜 Latest policy")}</h2>` + d.items.filter(it => it.cat === "POLICY").slice(0, 3).map(newsCard).join("");
  h += `<div class="sub" style="margin-top:12px">${T("雲端資料更新：", "Cloud data updated: ")}${full(d.updated)}（${ago(d.updated)}${T("，每 20 分鐘自動更新", ", refreshes every 20 min")}）</div>`;
  if (d.errors?.length && !S.en) h += `<div class="sub">${esc(d.errors.join("、"))}</div>`;
  return h + DISC();
}

function pageFeed(cat) {
  const f = S.filters[cat] || (S.filters[cat] = { q: "", tw: false });
  const q = f.q.trim().toLowerCase();
  const all = S.data.items.filter(it => it.cat === cat);
  const list = all.filter(it => cat !== "TRUMP" || S.kind === "all" || kindOf(it) === S.kind).filter(it => {
    const t = trOf(it), a = S.analyses[it.id];
    if (f.tw && !(it.themes.length || t?.stocks?.length || a?.stocks?.length)) return false;
    if (!q) return true;
    return (it.title + " " + it.body + " " + (t?.zh || "") + " " + (a?.summary || "")).toLowerCase().includes(q);
  });
  const done = list.filter(trOf).length;
  let h = "";
  if (cat === "TRUMP") {
    const kinds = [["all", T("全部", "All")], ["posts", T("📱 他的貼文", "📱 His posts")], ["talks", T("🎤 訪談/演講", "🎤 Interviews")], ["news", T("📰 相關新聞", "📰 News")]];
    h += `<div class="chips" style="padding:0 0 8px">${kinds.map(([k, l]) => `<button class="toggle ${S.kind === k ? "on" : ""}" data-kind="${k}">${l} ${k === "all" ? all.length : all.filter(it => kindOf(it) === k).length}</button>`).join("")}</div>`;
  }
  h += `<input type="text" id="search" placeholder="${T("搜尋（例：tariff、晶片、Nvidia）", "Search (e.g. tariff, chips, Nvidia)")}" value="${esc(f.q)}">
    <div class="row" style="margin:8px 0">
      <button class="toggle ${f.tw ? "on" : ""}" data-act="tw">${T("只看有台股關聯", "Taiwan-related only")}</button><span class="grow"></span>
      ${S.key && !S.en ? `<button class="btn ghost" data-act="translate" ${S.busy.tr ? "disabled" : ""}>🌐 翻譯成中文＋相關台股</button>` : ""}
    </div>${progress("tr")}${legendHtml()}
    <div class="sub">${T(`共 ${list.length} 則 · 已翻譯 ${done} 則`, `${list.length} items`)}${!S.key && !S.en && done < list.length ? " · 到 ⚙ 設定填入 Gemini Key 可翻譯其餘" : ""}</div>`;
  h += list.length ? list.map(newsCard).join("") : `<div class="sub" style="padding:20px">${S.loading ? T("載入中…", "Loading…") : T("目前沒有資料", "No data")}</div>`;
  return h;
}

function pageNews(id) {
  const it = S.data.items.find(x => x.id === id);
  if (!it) return `<div class="sub">${T("找不到這則消息（可能已被更新擠出列表）", "This item is no longer available")}</div>`;
  const t = trOf(it), a = S.analyses[id];
  let h = `<div class="sub">${catLabel(it.cat)} · ${esc(it.source)} · ${full(it.time)}</div>`;
  const orig = it.body.length > it.title.length ? it.body : it.title;
  if (t?.zh && !S.en) h += `<div class="sub" style="color:var(--pri);margin-top:8px">🌐 中文翻譯</div><div style="font-size:17px;font-weight:bold;line-height:1.6;white-space:pre-wrap">${esc(t.zh)}</div>
      <div class="sub" style="margin-top:10px">原文</div><div class="sub" style="line-height:1.5">${esc(orig.slice(0, 3000))}</div>`;
  else h += `<div style="font-size:17px;font-weight:bold;line-height:1.6;margin-top:6px">${esc(it.title)}</div>${it.body && it.body !== it.title ? `<div style="margin-top:6px;line-height:1.6">${esc(it.body.slice(0, 3000))}</div>` : ""}`;
  h += `<div class="row" style="margin:12px 0">${it.link ? `<a class="btn ghost" href="${esc(it.link)}" target="_blank" rel="noopener">${T("開啟原文", "Open source")}</a>` : ""}
    ${S.key ? `<button class="btn" data-act="analyze" data-id="${esc(id)}" ${S.busy["n:" + id] ? "disabled" : ""}>${a ? T("重新分析", "Re-analyze") : T("🤖 AI 分析影響", "🤖 AI impact analysis")}</button>` : `<span class="sub">${T("到 ⚙ 設定填 Gemini Key 可用 AI 深度分析", "Add a Gemini key in ⚙ Settings for AI analysis")}</span>`}
    ${S.key && !t && !S.en ? `<button class="btn ghost" data-act="tr1" data-id="${esc(id)}">🌐 翻譯</button>` : ""}</div>${progress("n:" + id)}${progress("tr")}`;
  if (t?.stocks?.length) h += `<div class="card"><b>${T("可能相關的台股", "Possibly related Taiwan stocks")}</b>${t.stocks.map(pickRow).join("")}</div>`;
  if (it.themes.length) {
    h += `<div class="card"><b>${T("關鍵字比對到的題材", "Matched themes")}</b>`;
    for (const n of it.themes) { const th = themeByName(n); if (th) h += `<div style="margin-top:8px">${esc(themeLabel(n))}<div class="chips">${th.stocks.map(([c, nm]) => `<button class="chip" data-stock="${c}">${esc(stockLabel(c, nm))} ${c}</button>`).join("")}</div></div>`; }
    h += `</div>`;
  }
  if (a) {
    h += `<div class="card"><div class="row"><b class="grow">${T("🤖 AI 分析", "🤖 AI analysis")}</b><span class="gold">${T("重要度", "Impact")} ${"★".repeat(a.importance)}${"☆".repeat(5 - a.importance)}</span></div>
      <div class="${senCls(a.sentiment)}" style="font-size:17px;font-weight:bold;margin:6px 0">${esc(term(a.sentiment))}</div><div>${esc(a.summary)}</div>
      ${(a.sectors || []).length ? `<div class="chips">${a.sectors.map(s => `<span class="chip tag">${esc(s)}</span>`).join("")}</div>` : ""}
      ${a.us_impact ? `<h3>${T("🇺🇸 對美國科技／民生", "🇺🇸 US tech / economy")}</h3><div>${esc(a.us_impact)}</div>` : ""}
      ${a.tw_impact ? `<h3>${T("🇹🇼 對台股", "🇹🇼 Taiwan stocks")}</h3><div>${esc(a.tw_impact)}</div>` : ""}
      ${a.stocks?.length ? `<h3>${T("相關台股與建議", "Related Taiwan stocks")}</h3>${a.stocks.map(pickRow).join("")}` : ""}
      ${a.risk ? `<h3>${T("⚠️ 風險", "⚠️ Risks")}</h3><div class="sub">${esc(a.risk)}</div>` : ""}
      <div class="sub" style="margin-top:8px">${T("分析時間 ", "Analyzed ")}${full(a.t)}</div></div>`;
  }
  return h + DISC();
}

function pageStocks() {
  let h = `<div class="row"><input type="text" id="addcode" inputmode="numeric" placeholder="${T("加入自選股代號，例如 2330", "Add a stock code, e.g. 2330")}" class="grow" style="flex:1">
    <button class="btn" data-act="add">${T("加入", "Add")}</button></div><h2>${T("⭐ 自選股", "⭐ Watchlist")}</h2>`;
  h += S.watch.map(c => {
    const sa = S.stockAn[c];
    return `<div class="card click" data-stock="${esc(c)}"><div class="row"><div class="grow"><b>${esc(stockLabel(c, nameOf(c)))}</b> <span class="sub">${esc(c)}</span>
      ${sa ? `<div class="sub" style="margin-top:2px">${esc(sa.summary || "")}</div>` : ""}</div>${priceHtml(c)}
      ${sa ? `<span class="badge ${actCls(sa.action)} ${actCls(sa.action)}">${esc(term(sa.action))} ${sa.confidence ?? ""}%</span>` : ""}
      <button class="icon" data-del="${esc(c)}" style="font-size:16px" aria-label="${T("移除", "Remove")}">✕</button></div></div>`;
  }).join("") || `<div class="sub">${T("還沒有自選股", "No stocks yet")}</div>`;
  const cutoff = Date.now() - 3 * 86400_000, men = {};
  for (const it of S.data.items) {
    if (it.time && it.time < cutoff) continue;
    for (const p of [...(trOf(it)?.stocks || []), ...(S.analyses[it.id]?.stocks || [])]) {
      const m = men[p.code] || (men[p.code] = { name: p.name, n: 0, up: 0, down: 0 });
      m.n++; if (actCls(p.action) === "up") m.up++; else if (actCls(p.action) === "down") m.down++;
    }
  }
  const rank = Object.entries(men).sort((a, b) => b[1].n - a[1].n).slice(0, 20);
  if (rank.length) {
    h += `<h2>${T("🤖 近3天 AI 點名的台股", "🤖 Stocks AI mentioned (3 days)")}</h2>`;
    h += rank.map(([c, m]) => `<div class="card click" data-stock="${esc(c)}"><div class="row"><div class="grow"><b>${esc(stockLabel(c, m.name || nameOf(c)))}</b> <span class="sub">${esc(c)}</span>
      <div class="sub">${T(`被提到 ${m.n} 次`, `Mentioned ${m.n}×`)} · <span class="up">${T("利多/買進", "Bullish/Buy")} ${m.up}</span> · <span class="down">${T("利空/賣出", "Bearish/Sell")} ${m.down}</span></div></div>${priceHtml(c)}</div></div>`).join("");
  }
  const cut24 = Date.now() - 24 * 3600_000, cnt = {}, why = {};
  for (const it of S.data.items) if (it.time > cut24) {
    const codes = new Set();
    for (const n of it.themes) for (const [c] of themeByName(n)?.stocks || []) { codes.add(c); (why[c] = why[c] || new Set()).add(n); }
    codes.forEach(c => cnt[c] = (cnt[c] || 0) + 1);
  }
  const top = Object.entries(cnt).sort((a, b) => b[1] - a[1]).slice(0, 15);
  if (top.length) {
    h += `<h2>${T("📰 24小時新聞題材關聯台股", "📰 Stocks linked to 24h news themes")}</h2>` + top.map(([c, n]) => `<div class="card click" data-stock="${esc(c)}"><div class="row"><div class="grow"><b>${esc(stockLabel(c, nameOf(c)))}</b> <span class="sub">${esc(c)}</span>
      <div class="sub">${T(`相關新聞 ${n} 則`, `${n} related news`)} · ${esc([...why[c]].map(themeLabel).join(S.en ? ", " : "、"))}</div></div>${priceHtml(c)}</div></div>`).join("");
  }
  return h + DISC();
}

function pageStock(code) {
  const q = S.data.quotes[code], sa = S.stockAn[code], inW = S.watch.includes(code);
  const th = themesOfStock(code);
  let h = `<div class="row"><div class="grow"><div class="big">${esc(stockLabel(code, nameOf(code)))}</div><div class="sub">${esc(code)}${q?.time ? " · " + esc(q.time) : ""}</div></div>${priceHtml(code)}</div>
    ${th.length ? `<div class="chips">${th.map(t => `<span class="chip tag">${esc(themeLabel(t.name))}</span>`).join("")}</div>` : ""}
    <div class="row" style="margin:12px 0">
      ${S.key ? `<button class="btn" data-act="sa" data-code="${esc(code)}" ${S.busy["s:" + code] ? "disabled" : ""}>${sa ? T("重新分析", "Re-analyze") : T("🤖 AI 買賣分析", "🤖 AI buy/sell analysis")}</button>` : ""}
      <button class="btn ghost" data-act="watch" data-code="${esc(code)}">${inW ? T("移除自選", "Remove from watchlist") : T("加入自選", "Add to watchlist")}</button>
      <a class="btn ghost" href="https://tw.stock.yahoo.com/quote/${encodeURIComponent(code)}" target="_blank" rel="noopener">Yahoo</a>
    </div>${progress("s:" + code)}`;
  if (!S.key) h += `<div class="sub">${T("到 ⚙ 設定填入 Gemini Key 可以用 AI 分析這檔股票。", "Add a Gemini key in ⚙ Settings to analyze this stock with AI.")}</div>`;
  if (sa) {
    const list = (t, arr) => arr?.length ? `<h3>${t}</h3>` + arr.map(x => `<div>• ${esc(typeof x === "string" ? x : JSON.stringify(x))}</div>`).join("") : "";
    h += `<div class="card"><div class="row"><b class="grow">${T("🤖 AI 建議", "🤖 AI view")}</b><span class="badge ${actCls(sa.action)} ${actCls(sa.action)}">${esc(term(sa.action))} ${sa.confidence ?? ""}%</span></div>
      <div style="margin-top:6px">${esc(sa.summary || "")}</div>
      ${sa.short_term ? `<h3>${T("短線（1~2週）", "Short term (1–2 weeks)")}</h3><div>${esc(sa.short_term)}</div>` : ""}${sa.mid_term ? `<h3>${T("中線（1~3個月）", "Mid term (1–3 months)")}</h3><div>${esc(sa.mid_term)}</div>` : ""}
      ${list(T("理由", "Reasons"), sa.reasons)}${list(T("⚠️ 風險", "⚠️ Risks"), sa.risks)}${list(T("👀 接下來觀察", "👀 What to watch"), sa.catalysts)}
      <div class="sub" style="margin-top:8px">${T("分析時間 ", "Analyzed ")}${full(sa.t)}</div></div>`;
  }
  h += DISC();
  const rel = relatedNews(code);
  if (rel.length) h += `<h2>${T("🇺🇸 相關美國消息", "🇺🇸 Related US news")}</h2>` + rel.slice(0, 15).map(newsCard).join("");
  return h;
}

function pageSettings() {
  return `<h2>🌐 語言 Language</h2>
    <div class="row"><button class="toggle ${!S.en ? "on" : ""}" data-lang="zh">中文</button><button class="toggle ${S.en ? "on" : ""}" data-lang="en">English</button></div>
    <h2>${T("🤖 Gemini AI（免費）", "🤖 Gemini AI (free)")}</h2>
    <input type="password" id="key" placeholder="${T("貼上 Gemini API Key", "Paste your Gemini API key")}" value="${esc(S.key)}">
    <div class="row" style="margin:8px 0"><button class="btn" data-act="savekey">${T("儲存", "Save")}</button>
      <button class="btn ghost" data-act="showkey">${T("顯示/隱藏", "Show/Hide")}</button>
      <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener">${T("取得免費 Key →", "Get a free key →")}</a></div>
    <div class="sub">${T("Key 只存在這個瀏覽器裡（不會上傳）。填了之後可以：翻譯還沒翻的消息、單則 AI 深度分析、個股買賣分析、自己產生日報。", "The key stays in this browser only. It enables AI impact analysis, stock analysis and generating the brief yourself.")}<br>
    ${T("目前使用模型：", "Current model: ")}${esc(LS.get("lastModel", "") || T("尚未使用（自動選最新免費 Flash）", "not used yet (auto-picks newest free Flash)"))}</div>
    <h2>${T("☁️ 雲端自動更新", "☁️ Cloud updates")}</h2>
    <div class="sub">${T("資料由 GitHub 每 20 分鐘自動抓取，雲端會自動翻譯川普貼文、標出相關台股，並每 6 小時產生一次 AI 日報。", "Data is fetched by GitHub every 20 minutes. The cloud tags related Taiwan stocks and writes an AI brief every 6 hours.")}<br>
    ${T("最近更新：", "Last update: ")}${full(S.data.updated)}</div>
    <h2>${T("📱 安裝", "📱 Install")}</h2>
    <div class="sub">${T("手機瀏覽器選單 →「加到主畫面」，就能像 App 一樣開啟。", "In your phone browser menu choose \"Add to Home screen\" to use it like an app.")}<br>Android App: <a href="https://github.com/paultsai1011/TrumpTrade/releases/latest/download/TrumpTrade.apk">${T("下載 APK", "Download APK")}</a></div>
    <h2>${T("🧹 清除資料", "🧹 Clear data")}</h2>
    <button class="btn ghost" data-act="clear">${T("清除這台裝置的 AI 分析與翻譯快取", "Clear AI analysis cache on this device")}</button>
    <div class="sub" style="margin-top:14px">${T("資料來源", "Sources")}: Trump's Truth, Google News, CNBC, MarketWatch, Federal Register, Congress.gov, TWSE / TPEx.</div>${DISC()}`;
}

// ================= 路由與渲染 =================
function current() { return S.stack[S.stack.length - 1] || null; }
function push(p) { S.stack.push(p); history.pushState({ d: S.stack.length }, ""); render(true); }
window.addEventListener("popstate", () => { if (S.stack.length) { S.stack.pop(); render(true); } });

function applyLangChrome() {
  document.documentElement.lang = S.en ? "en" : "zh-Hant-TW";
  document.title = T("川普財經雷達", "Trump Market Radar");
  const labels = { home: T("總覽", "Home"), TRUMP: T("川普", "Trump"), NEWS: T("財經", "Markets"), POLICY: T("政策法案", "Policy"), stocks: T("台股", "TW Stocks") };
  document.querySelectorAll("#tabs button").forEach(b => { b.lastChild.textContent = labels[b.dataset.tab]; });
  $("#lang").textContent = S.en ? "中文" : "EN";
}

let lastScroll = {};
function render(scrollTop) {
  const cur = current();
  const titles = { home: T("川普財經雷達", "Trump Market Radar"), TRUMP: T("川普", "Trump"), NEWS: T("財經", "Markets"), POLICY: T("政策法案", "Policy"), stocks: T("台股", "TW Stocks") };
  applyLangChrome();
  $("#title").textContent = cur ? ({ news: T("消息分析", "Analysis"), stock: T("個股分析", "Stock analysis"), settings: T("設定", "Settings") }[cur.type]) : titles[S.tab];
  $("#back").classList.toggle("hidden", !cur);
  $("#reload").classList.toggle("hidden", !!cur);
  $("#gear").classList.toggle("hidden", !!cur);
  $("#tabs").classList.toggle("hidden", !!cur);
  document.querySelectorAll("#tabs button").forEach(b => b.classList.toggle("on", b.dataset.tab === S.tab));
  const focusSearch = document.activeElement?.id === "search";
  const caret = focusSearch ? document.activeElement.selectionStart : 0;
  let html;
  if (cur?.type === "news") html = pageNews(cur.id);
  else if (cur?.type === "stock") html = pageStock(cur.code);
  else if (cur?.type === "settings") html = pageSettings();
  else if (S.tab === "home") html = pageHome();
  else if (S.tab === "stocks") html = pageStocks();
  else html = pageFeed(S.tab);
  $("#view").innerHTML = html;
  if (focusSearch) { const s = $("#search"); if (s) { s.focus(); s.setSelectionRange(caret, caret); } }
  if (scrollTop) window.scrollTo(0, cur ? 0 : (lastScroll[S.tab] || 0));
  updateProgress();
}
function setLang(en) { S.en = en; LS.set("en", en); render(); }

document.addEventListener("click", e => {
  const el = e.target.closest("[data-stock],[data-news],[data-act],[data-tab],[data-del],[data-kind],[data-lang]");
  if (!el) return;
  if (el.dataset.lang) return setLang(el.dataset.lang === "en");
  if (el.dataset.kind) { S.kind = el.dataset.kind; return render(); }
  if (el.dataset.del) { e.stopPropagation(); S.watch = S.watch.filter(c => c !== el.dataset.del); LS.set("watch", S.watch); return render(); }
  if (el.dataset.tab) { lastScroll[S.tab] = window.scrollY; S.tab = el.dataset.tab; S.stack = []; return render(true); }
  if (el.dataset.stock) { e.stopPropagation(); lastScroll[S.tab] = window.scrollY; return push({ type: "stock", code: el.dataset.stock }); }
  if (el.dataset.news) { lastScroll[S.tab] = window.scrollY; return push({ type: "news", id: el.dataset.news }); }
  const act = el.dataset.act;
  if (act === "brief") makeBrief();
  else if (act === "tw") { const f = S.filters[S.tab]; f.tw = !f.tw; render(); }
  else if (act === "translate") translate(S.data.items.filter(it => it.cat === S.tab));
  else if (act === "tr1") translate(S.data.items.filter(it => it.id === el.dataset.id));
  else if (act === "analyze") analyzeNews(S.data.items.find(x => x.id === el.dataset.id));
  else if (act === "sa") analyzeStock(el.dataset.code);
  else if (act === "watch") { const c = el.dataset.code; S.watch = S.watch.includes(c) ? S.watch.filter(x => x !== c) : [...S.watch, c]; LS.set("watch", S.watch); render(); }
  else if (act === "add") {
    const c = cleanCode($("#addcode").value);
    if (!/^\d{4,6}[A-Z]?$/.test(c)) return toast(T("請輸入台股代號，例如 2330", "Enter a Taiwan stock code, e.g. 2330"));
    if (!S.watch.includes(c)) S.watch.push(c); LS.set("watch", S.watch);
    if (!S.data.quotes[c]) toast(T("已加入。報價會在下一次雲端更新（約 20 分鐘內）後出現", "Added. Quote appears after the next cloud update (within ~20 min)"));
    render();
  }
  else if (act === "savekey") { S.key = $("#key").value.trim(); LS.set("key", S.key); localStorage.removeItem("tt_models"); toast(S.key ? T("已儲存", "Saved") : T("已清除 Key", "Key cleared")); render(); }
  else if (act === "showkey") { const k = $("#key"); k.type = k.type === "password" ? "text" : "password"; }
  else if (act === "clear") { S.localTr = {}; S.analyses = {}; S.stockAn = {}; S.localBrief = null; ["tr", "an", "san", "brief"].forEach(k => localStorage.removeItem("tt_" + k)); toast(T("已清除", "Cleared")); render(); }
});
document.addEventListener("toggle", e => { if (e.target.classList?.contains("legend")) LS.set("legendOpen", e.target.open); }, true);
document.addEventListener("input", e => {
  if (e.target.id === "search") { S.filters[S.tab].q = e.target.value; render(); }
});
$("#back").onclick = () => history.back();
$("#reload").onclick = () => load(true);
$("#gear").onclick = () => push({ type: "settings" });
$("#lang").onclick = () => setLang(!S.en);

render();
load();
setInterval(() => { if (!document.hidden && !current()) load(); }, 5 * 60_000);
document.addEventListener("visibilitychange", () => { if (!document.hidden && Date.now() - (S.data.updated || 0) > 10 * 60_000) load(); });
