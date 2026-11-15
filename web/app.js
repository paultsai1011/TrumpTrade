import { STOCK_NAMES, themeByName, themesOfStock, DEFAULT_WATCH } from "./themes.js";

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
};
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
  if (d < 1) return "剛剛"; if (d < 60) return `${Math.floor(d)}分鐘前`; if (d < 1440) return `${Math.floor(d / 60)}小時前`;
  if (d < 10080) return `${Math.floor(d / 1440)}天前`;
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
    if (manual) toast(`已更新（雲端資料 ${ago(S.data.updated)}）`);
  } catch (e) {
    toast("資料讀取失敗：雲端資料可能還在準備中，請稍後再試");
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
async function ask(prompt) {
  if (!S.key) throw new Error("請先到 ⚙ 設定輸入 Gemini API Key");
  let list = await models();
  const ok = LS.get("lastModel", "");
  if (ok && list.includes(ok)) list = [ok, ...list.filter(m => m !== ok)];
  let last;
  for (const m of list.slice(0, 3)) {
    try {
      const t = await gen(m, prompt);
      const s = t.replace(/```json|```/g, ""); const a = s.indexOf("{"), b = s.lastIndexOf("}");
      const o = JSON.parse(s.slice(a, b + 1));
      LS.set("lastModel", m);
      return o;
    } catch (e) {
      last = e;
      if (e.status === 400 && /API key/i.test(e.body || "")) throw new Error("Gemini API Key 無效，請到設定檢查");
      if (e.status === 403) throw new Error("API Key 沒有權限 (403)");
    }
  }
  if (last?.status === 429) throw new Error("免費額度暫時用完(429)，請稍後再試");
  throw new Error("AI 分析失敗：" + (last?.message || ""));
}
const cleanCode = c => (String(c).toUpperCase().match(/\d{4,6}[A-Z]?/) || [String(c).trim()])[0];
const norm = (arr, def) => (Array.isArray(arr) ? arr : []).filter(p => p && p.code).map(p => ({
  code: cleanCode(p.code), name: p.name || STOCK_NAMES[cleanCode(p.code)] || "", action: p.action || def,
  confidence: p.confidence != null ? Math.max(0, Math.min(100, parseInt(p.confidence) || 0)) : undefined, reason: p.reason || "",
}));
const fmt = t => t ? new Date(t).toLocaleString("zh-TW", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }) : "未知";
const nowStr = () => new Date().toLocaleString("zh-TW", { hour12: false });
const RULE = "台股代號必須是真實存在的台灣上市櫃股票或ETF（4~6碼），name 用台灣常用簡稱。";

const P = {
  translate: items => `你是專業財經翻譯兼台股分析師。請把下面每一則川普發言／美國消息翻譯成通順的繁體中文（台灣用語；常見名稱用台灣譯名，例如 Nvidia→輝達、Fed→聯準會），並判斷可能有關的台股。
規則：
- zh：完整翻出原文意思，不要省略或加油添醋；如果只是連結、影片、轉貼或沒有實質內容，寫「（影片／連結貼文）」再簡述。
- stocks：0~4 檔最直接相關的台股，action 只能是「利多」或「利空」，reason 10~20 字說明關聯。跟台股沒關係就給空陣列，不要硬湊。
- importance：1~5，對市場的影響程度（一般政治口水給 1）。
- sentiment：對台股整體「利多／利空／中性」。
${RULE}
只輸出 JSON：{"items":[{"i":0,"zh":"","importance":1,"sentiment":"中性","stocks":[{"code":"2330","name":"台積電","action":"利多","reason":""}]}]}
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
stocks 列 2~6 檔最直接受影響的台股，action 只能是「買進」「賣出」「觀望」。${RULE} 若與市場無關，importance 給 1、stocks 給空陣列。`,

  brief: (items, markets) => `你是資深的台股投資策略分析師。以下是最近的美國財經新聞、川普發言與政策法案，以及最新市場指數。請整理成「今日美國消息 × 台股盤勢 AI 日報」。
現在時間：${nowStr()}（台灣時間）
市場指數：${markets.map(m => `${m.label} ${m.price.toFixed(2)} (${pct(m).toFixed(2)}%)`).join("；") || "（無）"}

消息列表：
${items.map(it => `[${CAT[it.cat]}][${it.source}][${fmt(it.time)}] ${(trOf(it)?.zh || it.title).slice(0, 200)}`).join("\n")}

只輸出 JSON：
{"mood":"偏多/偏空/中性/震盪","mood_score":-5~5,"headline":"一句話總結","events":[{"title":"事件","impact":"對台股影響","sectors":["產業"]}],"buy":[{"code":"","name":"","action":"買進","confidence":0~100,"reason":""}],"sell":[{"code":"","name":"","action":"賣出","confidence":0~100,"reason":""}],"watch":[{"code":"","name":"","action":"觀望","confidence":0~100,"reason":""}],"advice":"今日操作建議與風險提醒（3~5句）"}
events 挑 3~6 件；buy/sell/watch 各 0~5 檔。${RULE}`,

  stock: (code, name, q, themes, news) => `你是資深台股分析師。請評估台股 ${code} ${name} 在目前美國政策與國際新聞環境下的操作建議。
現在時間：${nowStr()}（台灣時間）
${q && q.price ? `現價 ${q.price}（${pct(q).toFixed(2)}%），昨收 ${q.prev}` : "報價暫無"}
所屬題材：${themes.join("、") || "未分類"}

相關美國新聞／川普／政策：
${news.slice(0, 15).map(it => `- [${fmt(it.time)}] ${(trOf(it)?.zh || it.title).slice(0, 180)}`).join("\n") || "（無）"}

只輸出 JSON：
{"action":"買進/賣出/觀望","confidence":0~100,"summary":"2~3句總結","short_term":"短線(1~2週)看法","mid_term":"中線(1~3個月)看法","reasons":["理由"],"risks":["風險"],"catalysts":["接下來要觀察的事件"]}`,
};

async function job(key, fn) {
  if (S.busy[key]) return;
  S.busy[key] = { t: Date.now(), msg: "AI 分析中" };
  render();
  const tick = setInterval(() => { if (S.busy[key]) updateProgress(); }, 1000);
  try { await fn(m => { if (S.busy[key]) S.busy[key].msg = m; updateProgress(); }); }
  catch (e) { toast(e.message || "AI 失敗"); }
  finally { clearInterval(tick); delete S.busy[key]; render(); }
}
function updateProgress() {
  document.querySelectorAll("[data-busy]").forEach(el => {
    const b = S.busy[el.dataset.busy];
    if (b) el.textContent = `${b.msg}… 已 ${Math.floor((Date.now() - b.t) / 1000)} 秒`;
  });
}
const progress = key => S.busy[key] ? `<div class="progress" data-busy="${esc(key)}">${esc(S.busy[key].msg)}…</div>` : "";

function translate(items) {
  const todo = items.filter(it => !trOf(it)).slice(0, 40);
  if (!todo.length) return toast("這頁都翻譯好了");
  job("tr", async status => {
    for (let i = 0; i < todo.length; i += 10) {
      const chunk = todo.slice(i, i + 10);
      status(`翻譯中 ${i + 1}~${i + chunk.length} / ${todo.length} 則`);
      const o = await ask(P.translate(chunk));
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
  if (!pick.length) return toast("還沒有新聞資料");
  job("brief", async () => {
    const o = await ask(P.brief(pick, S.data.markets));
    S.localBrief = { t: Date.now(), mood: o.mood || "中性", mood_score: parseInt(o.mood_score) || 0, headline: o.headline || "", events: o.events || [],
      buy: norm(o.buy, "買進"), sell: norm(o.sell, "賣出"), watch: norm(o.watch, "觀望"), advice: o.advice || "" };
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
const DISC = `<div class="disc">⚠️ AI 依公開新聞自動產生的分析可能有誤，僅供參考，不構成投資建議。投資請自行判斷並承擔風險。</div>`;
function priceHtml(code) {
  const q = S.data.quotes[code];
  if (!q || !q.price) return `<div class="price flat">--</div>`;
  const p = pct(q), c = cls(p);
  return `<div class="price ${c}"><b>${q.price.toFixed(2)}</b><br>${(q.price - q.prev >= 0 ? "+" : "") + (q.price - q.prev).toFixed(2)} (${p >= 0 ? "+" : ""}${p.toFixed(2)}%)</div>`;
}
function pickRow(p) {
  const conf = p.confidence != null ? ` ${p.confidence}%` : "";
  return `<div class="pick" data-stock="${esc(p.code)}"><div class="n">${esc(p.name || nameOf(p.code))} <span class="sub">${esc(p.code)}</span>
    ${p.reason ? `<div class="r">${esc(p.reason)}</div>` : ""}</div>${priceHtml(p.code)}<span class="badge ${actCls(p.action)} ${actCls(p.action)}">${esc(p.action)}${conf}</span></div>`;
}
function newsCard(it) {
  const t = trOf(it), a = S.analyses[it.id];
  const imp = a?.importance || t?.importance, sen = a?.sentiment || t?.sentiment;
  const picks = (t?.stocks?.length ? t.stocks : a?.stocks) || [];
  let body;
  if (t?.zh) body = `<div class="zh">${esc(t.zh)}</div><div class="orig">${esc(it.title)}</div>`;
  else body = `<div class="title clamp4">${esc(it.title)}</div>` + (a?.summary ? `<div class="ai">🤖 ${esc(a.summary)}</div>` : "");
  let chips = "";
  if (picks.length) chips = `<div class="chips">${picks.map(p => `<button class="chip ${actCls(p.action)}" data-stock="${esc(p.code)}">${esc(p.name || nameOf(p.code))} ${esc(p.code)} ${esc(p.action)}</button>`).join("")}</div>`;
  else if (!t && it.themes.length) chips = `<div class="chips">${it.themes.map(n => `<span class="chip tag">${esc(n)}</span>`).join("")}</div>`;
  else if (t) chips = `<div class="sub" style="margin-top:4px">與台股無直接關聯</div>`;
  return `<div class="card click" data-news="${esc(it.id)}">
    <div class="meta"><span class="grow">${esc(it.source)} · ${ago(it.time)}</span>${imp ? `<span class="gold">${"★".repeat(imp)}</span>` : ""}${sen ? `<b class="${senCls(sen)}">${esc(sen)}</b>` : ""}</div>
    ${body}${chips}</div>`;
}
function briefHtml() {
  const sb = S.data.brief, lb = S.localBrief;
  const b = lb && (!sb || (lb.t || 0) > (sb.t || 0)) ? lb : sb;
  let h = `<div class="card"><div class="row"><b class="grow" style="font-size:17px">🤖 AI 今日盤勢日報</b>
    ${S.key ? `<button class="btn" data-act="brief" ${S.busy.brief ? "disabled" : ""}>${b ? "重新產生" : "產生"}</button>` : ""}</div>${progress("brief")}`;
  if (!b) return h + `<div class="sub">${S.key ? "按「產生」讓 AI 讀完最近的川普發言、政策與美股新聞，整理出台股可能的買進／賣出名單。" : "雲端每 3 小時自動產生（需在 GitHub 設定 GEMINI_API_KEY），或到 ⚙ 設定填入自己的 Key 立即產生。"}</div></div>`;
  h += `<div class="sub">${full(b.t)} 產生</div>
    <div class="row" style="margin:6px 0"><span class="${senCls(b.mood)}" style="font-size:20px;font-weight:bold">${esc(b.mood)}</span>
    <span class="${cls(b.mood_score)}">情緒分數 ${b.mood_score > 0 ? "+" : ""}${b.mood_score}</span></div>
    ${b.headline ? `<div style="font-weight:bold;margin:4px 0">${esc(b.headline)}</div>` : ""}`;
  for (const e of b.events || []) h += `<div style="margin:8px 0"><b>• ${esc(e.title)}</b><div class="sub">${esc(e.impact)}</div>
    ${(e.sectors || []).length ? `<div class="chips">${e.sectors.map(s => `<span class="chip tag">${esc(s)}</span>`).join("")}</div>` : ""}</div>`;
  if (b.buy?.length) h += `<hr><b class="up">📈 可留意買進</b>` + b.buy.map(pickRow).join("");
  if (b.sell?.length) h += `<hr><b class="down">📉 可考慮賣出／避開</b>` + b.sell.map(pickRow).join("");
  if (b.watch?.length) h += `<hr><b>👀 觀察名單</b>` + b.watch.map(pickRow).join("");
  if (b.advice) h += `<hr><div>💡 ${esc(b.advice)}</div>`;
  return h + `</div>`;
}

// ================= 頁面 =================
function pageHome() {
  const d = S.data;
  let h = `<div class="markets">${d.markets.length ? d.markets.map(m => { const p = pct(m); return `<div class="mk"><div class="l">${esc(m.label)}</div>
    <div class="p">${m.price.toLocaleString("en-US", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}</div><div class="c ${cls(p)}">${p >= 0 ? "+" : ""}${p.toFixed(2)}%</div></div>`; }).join("")
    : `<span class="sub">市場指數載入中…</span>`}</div>`;
  h += briefHtml();
  const cutoff = Date.now() - 24 * 3600_000;
  const counts = {};
  d.items.filter(it => it.time > cutoff).forEach(it => it.themes.forEach(t => counts[t] = (counts[t] || 0) + 1));
  const hot = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 8);
  if (hot.length) {
    h += `<div class="card"><b style="font-size:17px">🔥 24小時題材熱度</b>`;
    for (const [n, c] of hot) {
      const t = themeByName(n);
      h += `<div style="margin-top:10px"><b>${esc(n)}</b> <span class="sub">${c} 則</span><div class="chips">${(t?.stocks || []).map(([code, nm]) => {
        const q = d.quotes[code], p = pct(q);
        return `<button class="chip ${q ? cls(p) : ""}" data-stock="${code}">${esc(nm)} ${q && q.price ? (p >= 0 ? "+" : "") + p.toFixed(1) + "%" : code}</button>`;
      }).join("")}</div></div>`;
    }
    h += `</div>`;
  }
  const trump = d.items.filter(it => it.cat === "TRUMP" && it.source === "Truth Social").slice(0, 3);
  h += `<h2>🇺🇸 川普最新動態</h2>` + (trump.length ? trump : d.items.filter(it => it.cat === "TRUMP").slice(0, 3)).map(newsCard).join("");
  h += `<h2>📜 最新政策</h2>` + d.items.filter(it => it.cat === "POLICY").slice(0, 3).map(newsCard).join("");
  h += `<div class="sub" style="margin-top:12px">雲端資料更新：${full(d.updated)}（${ago(d.updated)}，每 20 分鐘自動更新）</div>`;
  if (d.aiNote) h += `<div class="sub">${esc(d.aiNote)}</div>`;
  if (d.errors?.length) h += `<div class="sub">${esc(d.errors.join("、"))}</div>`;
  return h + DISC;
}

function pageFeed(cat) {
  const f = S.filters[cat] || (S.filters[cat] = { q: "", tw: false });
  const q = f.q.trim().toLowerCase();
  const list = S.data.items.filter(it => it.cat === cat).filter(it => {
    const t = trOf(it), a = S.analyses[it.id];
    if (f.tw && !(it.themes.length || t?.stocks?.length || a?.stocks?.length)) return false;
    if (!q) return true;
    return (it.title + " " + it.body + " " + (t?.zh || "") + " " + (a?.summary || "")).toLowerCase().includes(q);
  });
  const done = list.filter(trOf).length;
  let h = `<input type="text" id="search" placeholder="搜尋（例：tariff、晶片、Nvidia）" value="${esc(f.q)}">
    <div class="row" style="margin:8px 0">
      <button class="toggle ${f.tw ? "on" : ""}" data-act="tw">只看有台股關聯</button><span class="grow"></span>
      ${S.key ? `<button class="btn ghost" data-act="translate" ${S.busy.tr ? "disabled" : ""}>🌐 翻譯成中文＋相關台股</button>` : ""}
    </div>${progress("tr")}
    <div class="sub">共 ${list.length} 則 · 已翻譯 ${done} 則${!S.key && done < list.length ? " · 到 ⚙ 設定填入 Gemini Key 可翻譯其餘" : ""}</div>`;
  h += list.length ? list.map(newsCard).join("") : `<div class="sub" style="padding:20px">${S.loading ? "載入中…" : "目前沒有資料"}</div>`;
  return h;
}

function pageNews(id) {
  const it = S.data.items.find(x => x.id === id);
  if (!it) return `<div class="sub">找不到這則消息（可能已被更新擠出列表）</div>`;
  const t = trOf(it), a = S.analyses[id];
  let h = `<div class="sub">${CAT[it.cat]} · ${esc(it.source)} · ${full(it.time)}</div>`;
  const orig = it.body.length > it.title.length ? it.body : it.title;
  if (t?.zh) h += `<div class="sub" style="color:var(--pri);margin-top:8px">🌐 中文翻譯</div><div style="font-size:17px;font-weight:bold;line-height:1.6;white-space:pre-wrap">${esc(t.zh)}</div>
      <div class="sub" style="margin-top:10px">原文</div><div class="sub" style="line-height:1.5">${esc(orig.slice(0, 3000))}</div>`;
  else h += `<div style="font-size:17px;font-weight:bold;line-height:1.6;margin-top:6px">${esc(it.title)}</div>${it.body && it.body !== it.title ? `<div style="margin-top:6px;line-height:1.6">${esc(it.body.slice(0, 3000))}</div>` : ""}`;
  h += `<div class="row" style="margin:12px 0">${it.link ? `<a class="btn ghost" href="${esc(it.link)}" target="_blank" rel="noopener">開啟原文</a>` : ""}
    ${S.key ? `<button class="btn" data-act="analyze" data-id="${esc(id)}" ${S.busy["n:" + id] ? "disabled" : ""}>${a ? "重新分析" : "🤖 AI 分析影響"}</button>` : `<span class="sub">到 ⚙ 設定填 Gemini Key 可用 AI 深度分析</span>`}
    ${S.key && !t ? `<button class="btn ghost" data-act="tr1" data-id="${esc(id)}">🌐 翻譯</button>` : ""}</div>${progress("n:" + id)}${progress("tr")}`;
  if (t?.stocks?.length) h += `<div class="card"><b>可能相關的台股</b>${t.stocks.map(pickRow).join("")}</div>`;
  if (it.themes.length) {
    h += `<div class="card"><b>關鍵字比對到的題材</b>`;
    for (const n of it.themes) { const th = themeByName(n); if (th) h += `<div style="margin-top:8px">${esc(n)}<div class="chips">${th.stocks.map(([c, nm]) => `<button class="chip" data-stock="${c}">${esc(nm)} ${c}</button>`).join("")}</div></div>`; }
    h += `</div>`;
  }
  if (a) {
    h += `<div class="card"><div class="row"><b class="grow">🤖 AI 分析</b><span class="gold">重要度 ${"★".repeat(a.importance)}${"☆".repeat(5 - a.importance)}</span></div>
      <div class="${senCls(a.sentiment)}" style="font-size:17px;font-weight:bold;margin:6px 0">${esc(a.sentiment)}</div><div>${esc(a.summary)}</div>
      ${(a.sectors || []).length ? `<div class="chips">${a.sectors.map(s => `<span class="chip tag">${esc(s)}</span>`).join("")}</div>` : ""}
      ${a.us_impact ? `<h3>🇺🇸 對美國科技／民生</h3><div>${esc(a.us_impact)}</div>` : ""}
      ${a.tw_impact ? `<h3>🇹🇼 對台股</h3><div>${esc(a.tw_impact)}</div>` : ""}
      ${a.stocks?.length ? `<h3>相關台股與建議</h3>${a.stocks.map(pickRow).join("")}` : ""}
      ${a.risk ? `<h3>⚠️ 風險</h3><div class="sub">${esc(a.risk)}</div>` : ""}
      <div class="sub" style="margin-top:8px">分析時間 ${full(a.t)}</div></div>`;
  }
  return h + DISC;
}

function pageStocks() {
  let h = `<div class="row"><input type="text" id="addcode" inputmode="numeric" placeholder="加入自選股代號，例如 2330" class="grow" style="flex:1">
    <button class="btn" data-act="add">加入</button></div><h2>⭐ 自選股</h2>`;
  h += S.watch.map(c => {
    const sa = S.stockAn[c];
    return `<div class="card click" data-stock="${esc(c)}"><div class="row"><div class="grow"><b>${esc(nameOf(c))}</b> <span class="sub">${esc(c)}</span>
      ${sa ? `<div class="sub" style="margin-top:2px">${esc(sa.summary || "")}</div>` : ""}</div>${priceHtml(c)}
      ${sa ? `<span class="badge ${actCls(sa.action)} ${actCls(sa.action)}">${esc(sa.action)} ${sa.confidence ?? ""}%</span>` : ""}
      <button class="icon" data-del="${esc(c)}" style="font-size:16px" aria-label="移除">✕</button></div></div>`;
  }).join("") || `<div class="sub">還沒有自選股</div>`;
  // AI 提及統計（雲端翻譯 + 自己的分析）
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
    h += `<h2>🤖 近3天 AI 點名的台股</h2>`;
    h += rank.map(([c, m]) => `<div class="card click" data-stock="${esc(c)}"><div class="row"><div class="grow"><b>${esc(m.name || nameOf(c))}</b> <span class="sub">${esc(c)}</span>
      <div class="sub">被提到 ${m.n} 次 · <span class="up">利多/買進 ${m.up}</span> · <span class="down">利空/賣出 ${m.down}</span></div></div>${priceHtml(c)}</div></div>`).join("");
  }
  const cut24 = Date.now() - 24 * 3600_000, cnt = {}, why = {};
  for (const it of S.data.items) if (it.time > cut24) {
    const codes = new Set();
    for (const n of it.themes) for (const [c] of themeByName(n)?.stocks || []) { codes.add(c); (why[c] = why[c] || new Set()).add(n); }
    codes.forEach(c => cnt[c] = (cnt[c] || 0) + 1);
  }
  const top = Object.entries(cnt).sort((a, b) => b[1] - a[1]).slice(0, 15);
  if (top.length) {
    h += `<h2>📰 24小時新聞題材關聯台股</h2>` + top.map(([c, n]) => `<div class="card click" data-stock="${esc(c)}"><div class="row"><div class="grow"><b>${esc(nameOf(c))}</b> <span class="sub">${esc(c)}</span>
      <div class="sub">相關新聞 ${n} 則 · ${esc([...why[c]].join("、"))}</div></div>${priceHtml(c)}</div></div>`).join("");
  }
  return h + DISC;
}

function pageStock(code) {
  const q = S.data.quotes[code], sa = S.stockAn[code], inW = S.watch.includes(code);
  const th = themesOfStock(code);
  let h = `<div class="row"><div class="grow"><div class="big">${esc(nameOf(code) || code)}</div><div class="sub">${esc(code)}${q?.time ? " · " + esc(q.time) : ""}</div></div>${priceHtml(code)}</div>
    ${th.length ? `<div class="chips">${th.map(t => `<span class="chip tag">${esc(t.name)}</span>`).join("")}</div>` : ""}
    <div class="row" style="margin:12px 0">
      ${S.key ? `<button class="btn" data-act="sa" data-code="${esc(code)}" ${S.busy["s:" + code] ? "disabled" : ""}>${sa ? "重新分析" : "🤖 AI 買賣分析"}</button>` : ""}
      <button class="btn ghost" data-act="watch" data-code="${esc(code)}">${inW ? "移除自選" : "加入自選"}</button>
      <a class="btn ghost" href="https://tw.stock.yahoo.com/quote/${encodeURIComponent(code)}" target="_blank" rel="noopener">Yahoo股市</a>
    </div>${progress("s:" + code)}`;
  if (!S.key) h += `<div class="sub">到 ⚙ 設定填入 Gemini Key 可以用 AI 分析這檔股票。</div>`;
  if (sa) {
    const list = (t, arr) => arr?.length ? `<h3>${t}</h3>` + arr.map(x => `<div>• ${esc(typeof x === "string" ? x : JSON.stringify(x))}</div>`).join("") : "";
    h += `<div class="card"><div class="row"><b class="grow">🤖 AI 建議</b><span class="badge ${actCls(sa.action)} ${actCls(sa.action)}">${esc(sa.action)} ${sa.confidence ?? ""}%</span></div>
      <div style="margin-top:6px">${esc(sa.summary || "")}</div>
      ${sa.short_term ? `<h3>短線（1~2週）</h3><div>${esc(sa.short_term)}</div>` : ""}${sa.mid_term ? `<h3>中線（1~3個月）</h3><div>${esc(sa.mid_term)}</div>` : ""}
      ${list("理由", sa.reasons)}${list("⚠️ 風險", sa.risks)}${list("👀 接下來觀察", sa.catalysts)}
      <div class="sub" style="margin-top:8px">分析時間 ${full(sa.t)}</div></div>`;
  }
  h += DISC;
  const rel = relatedNews(code);
  if (rel.length) h += `<h2>🇺🇸 相關美國消息</h2>` + rel.slice(0, 15).map(newsCard).join("");
  return h;
}

function pageSettings() {
  return `<h2>🤖 Gemini AI（免費）</h2>
    <input type="password" id="key" placeholder="貼上 Gemini API Key" value="${esc(S.key)}">
    <div class="row" style="margin:8px 0"><button class="btn" data-act="savekey">儲存</button>
      <button class="btn ghost" data-act="showkey">顯示/隱藏</button>
      <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener">取得免費 Key →</a></div>
    <div class="sub">Key 只存在這個瀏覽器裡（不會上傳）。填了之後可以：翻譯還沒翻的消息、單則 AI 深度分析、個股買賣分析、自己產生日報。<br>
    目前使用模型：${esc(LS.get("lastModel", "") || "尚未使用（自動選最新免費 Flash）")}</div>
    <h2>☁️ 雲端自動更新</h2>
    <div class="sub">資料由 GitHub 每 20 分鐘自動抓取。若在 GitHub 倉庫設定了 <b>GEMINI_API_KEY</b>，雲端會自動翻譯川普貼文、標出相關台股，並每 3 小時產生一次 AI 日報，所有裝置打開就看得到。<br>
    最近更新：${full(S.data.updated)}<br>${esc(S.data.aiNote || "雲端 AI：已啟用")}</div>
    <h2>📱 安裝</h2>
    <div class="sub">手機瀏覽器選單 →「加到主畫面」，就能像 App 一樣開啟。<br>Android App 版：<a href="https://github.com/paultsai1011/TrumpTrade/releases/latest/download/TrumpTrade.apk">下載 APK</a></div>
    <h2>🧹 清除資料</h2>
    <button class="btn ghost" data-act="clear">清除這台裝置的 AI 分析與翻譯快取</button>
    <div class="sub" style="margin-top:14px">資料來源：Trump's Truth、Google News、CNBC、MarketWatch、Yahoo Finance、Federal Register、Congress.gov、台灣證交所／櫃買中心。</div>${DISC}`;
}

// ================= 路由與渲染 =================
function current() { return S.stack[S.stack.length - 1] || null; }
function push(p) { S.stack.push(p); history.pushState({ d: S.stack.length }, ""); render(true); }
function pop() { if (S.stack.length) { S.stack.pop(); render(true); } }
window.addEventListener("popstate", () => { if (S.stack.length) { S.stack.pop(); render(true); } });

let lastScroll = {};
function render(scrollTop) {
  const cur = current();
  const titles = { home: "川普財經雷達", TRUMP: "川普", NEWS: "財經", POLICY: "政策法案", stocks: "台股" };
  $("#title").textContent = cur ? ({ news: "消息分析", stock: "個股分析", settings: "設定" }[cur.type]) : titles[S.tab];
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

// 事件委派
document.addEventListener("click", e => {
  const el = e.target.closest("[data-stock],[data-news],[data-act],[data-tab],[data-del]");
  if (!el) return;
  if (el.dataset.del) { e.stopPropagation(); S.watch = S.watch.filter(c => c !== el.dataset.del); LS.set("watch", S.watch); return render(); }
  if (el.dataset.tab) { lastScroll[S.tab] = window.scrollY; S.tab = el.dataset.tab; S.stack = []; return render(true); }
  if (el.dataset.stock) { e.stopPropagation(); if (!current() || S.tab !== "home" || true) lastScroll[S.tab] = window.scrollY; return push({ type: "stock", code: el.dataset.stock }); }
  if (el.dataset.news) { lastScroll[S.tab] = window.scrollY; return push({ type: "news", id: el.dataset.news }); }
  const act = el.dataset.act;
  const cur = current();
  if (act === "brief") makeBrief();
  else if (act === "tw") { const f = S.filters[S.tab]; f.tw = !f.tw; render(); }
  else if (act === "translate") translate(S.data.items.filter(it => it.cat === S.tab));
  else if (act === "tr1") translate(S.data.items.filter(it => it.id === el.dataset.id));
  else if (act === "analyze") analyzeNews(S.data.items.find(x => x.id === el.dataset.id));
  else if (act === "sa") analyzeStock(el.dataset.code);
  else if (act === "watch") { const c = el.dataset.code; S.watch = S.watch.includes(c) ? S.watch.filter(x => x !== c) : [...S.watch, c]; LS.set("watch", S.watch); render(); }
  else if (act === "add") {
    const c = cleanCode($("#addcode").value);
    if (!/^\d{4,6}[A-Z]?$/.test(c)) return toast("請輸入台股代號，例如 2330");
    if (!S.watch.includes(c)) S.watch.push(c); LS.set("watch", S.watch);
    if (!S.data.quotes[c]) toast("已加入。報價會在下一次雲端更新（約 20 分鐘內）後出現");
    render();
  }
  else if (act === "savekey") { S.key = $("#key").value.trim(); LS.set("key", S.key); localStorage.removeItem("tt_models"); toast(S.key ? "已儲存" : "已清除 Key"); render(); }
  else if (act === "showkey") { const k = $("#key"); k.type = k.type === "password" ? "text" : "password"; }
  else if (act === "clear") { S.localTr = {}; S.analyses = {}; S.stockAn = {}; S.localBrief = null; ["tr", "an", "san", "brief"].forEach(k => localStorage.removeItem("tt_" + k)); toast("已清除"); render(); }
  void cur;
});
document.addEventListener("input", e => {
  if (e.target.id === "search") { S.filters[S.tab].q = e.target.value; render(); }
});
$("#back").onclick = () => history.back();
$("#reload").onclick = () => load(true);
$("#gear").onclick = () => push({ type: "settings" });

render();
load();
setInterval(() => { if (!document.hidden && !current()) load(); }, 5 * 60_000);
document.addEventListener("visibilitychange", () => { if (!document.hidden && Date.now() - (S.data.updated || 0) > 10 * 60_000) load(); });
