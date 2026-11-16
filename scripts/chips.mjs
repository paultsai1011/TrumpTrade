// 籌碼資料：三大法人、融資融券、成交量（每日）＋集保股權分散（每週）
// 輸出 web/data/chips/{0-9}.json（依代號第一碼分檔）與 web/data/chips/meta.json
import { writeFileSync, mkdirSync } from "node:fs";

const PAGES = "https://paultsai1011.github.io/TrumpTrade/data/chips";
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
const log = (...a) => console.log("[籌碼]", ...a);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const KEEP_DAYS = 30, KEEP_WEEKS = 12;

async function getText(url, timeout = 30000) {
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), timeout);
  try {
    const r = await fetch(url, { signal: ctl.signal, headers: { "User-Agent": UA, "Accept-Language": "zh-TW" } });
    const s = await r.text(); if (!r.ok) throw new Error(`HTTP ${r.status}`); return s;
  } finally { clearTimeout(t); }
}
const getJson = async u => JSON.parse(await getText(u));
const n = s => { const v = parseFloat(String(s ?? "").replace(/,/g, "").replace(/<[^>]+>/g, "")); return isNaN(v) ? 0 : v; };
const lots = s => Math.round(n(s) / 1000);           // 股 → 張
const okCode = c => /^(\d{4}|00\d{2,4}[A-Z]?)$/.test(c);

// 台北時間
function tpeNow() { return new Date(Date.now() + 8 * 3600_000); }
const ymd = d => d.toISOString().slice(0, 10).replace(/-/g, "");
function recentWeekdays(count) {
  const out = []; const d = tpeNow();
  const afterClose = d.getUTCHours() * 60 + d.getUTCMinutes() >= 15 * 60 + 30;
  if (!afterClose) d.setUTCDate(d.getUTCDate() - 1);
  while (out.length < count) {
    const w = d.getUTCDay();
    if (w !== 0 && w !== 6) out.push(ymd(d));
    d.setUTCDate(d.getUTCDate() - 1);
  }
  return out; // 新 → 舊
}
const slash = d => `${d.slice(0, 4)}/${d.slice(4, 6)}/${d.slice(6)}`;

// ---------- 各來源 ----------
async function inst(d) {
  const out = {};
  const t = await getJson(`https://www.twse.com.tw/rwd/zh/fund/T86?date=${d}&selectType=ALLBUT0999&response=json`);
  if (t.stat !== "OK") return null;                    // 休市或尚未公布
  for (const r of t.data || []) {
    const c = r[0].trim(); if (!okCode(c)) continue;
    out[c] = [lots(r[4]) + lots(r[7]), lots(r[10]), lots(r[11]), lots(r[18])];
  }
  await sleep(2500);
  try {
    const o = await getJson(`https://www.tpex.org.tw/www/zh-tw/insti/dailyTrade?type=Daily&sect=EW&date=${slash(d)}&response=json`);
    for (const r of o.tables?.[0]?.data || []) {
      const c = String(r[0]).trim(); if (!okCode(c)) continue;
      out[c] = [lots(r[10]), lots(r[13]), lots(r[22]), lots(r[23])];
    }
  } catch (e) { log("櫃買法人失敗", d, e.message); }
  return out;
}
async function margin(d) {
  const out = {};
  const t = await getJson(`https://www.twse.com.tw/rwd/zh/marginTrading/MI_MARGN?date=${d}&selectType=ALL&response=json`);
  if (t.stat !== "OK") return null;
  const tb = (t.tables || []).find(x => (x.fields || []).includes("代號")) || t.tables?.[1];
  for (const r of tb?.data || []) {
    const c = String(r[0]).trim(); if (!okCode(c)) continue;
    out[c] = [n(r[6]), n(r[12])];                      // 融資餘額、融券餘額（張）
  }
  await sleep(2500);
  try {
    const o = await getJson(`https://www.tpex.org.tw/www/zh-tw/margin/balance?date=${slash(d)}&response=json`);
    for (const r of o.tables?.[0]?.data || []) {
      const c = String(r[0]).trim(); if (!okCode(c)) continue;
      out[c] = [n(r[6]), n(r[14])];
    }
  } catch (e) { log("櫃買融資券失敗", d, e.message); }
  return out;
}
async function volume(d) {
  const out = {};
  const t = await getJson(`https://www.twse.com.tw/rwd/zh/afterTrading/MI_INDEX?date=${d}&type=ALLBUT0999&response=json`);
  if (t.stat && t.stat !== "OK") return null;
  const tb = (t.tables || []).find(x => (x.fields || []).includes("證券代號") && (x.fields || []).includes("成交股數"));
  if (!tb) return null;
  const f = tb.fields, iC = f.indexOf("證券代號"), iV = f.indexOf("成交股數"), iP = f.indexOf("收盤價");
  for (const r of tb.data || []) {
    const c = String(r[iC]).trim(); if (!okCode(c)) continue;
    out[c] = [lots(r[iV]), n(r[iP])];
  }
  await sleep(2500);
  try {
    const o = await getJson(`https://www.tpex.org.tw/www/zh-tw/afterTrading/otc?date=${slash(d)}&type=EW&response=json`);
    for (const r of o.tables?.[0]?.data || []) {
      const c = String(r[0]).trim(); if (!okCode(c)) continue;
      out[c] = [lots(r[7]), n(r[2])];
    }
  } catch (e) { log("櫃買成交量失敗", d, e.message); }
  return out;
}
// 集保股權分散表：級距 1~15（1-999股 … 1,000,001股以上），17=合計
async function tdcc() {
  const csv = await getText("https://opendata.tdcc.com.tw/getOD.ashx?id=1-5", 60000);
  const rows = csv.split(/\r?\n/).slice(1);
  const out = {}; let date = "";
  for (const line of rows) {
    const p = line.split(","); if (p.length < 6) continue;
    const [d, code, lv, people, , pct] = p; const c = code.trim(); if (!okCode(c)) continue;
    date = d;
    const o = out[c] || (out[c] = { lv: new Array(15).fill(0), holders: 0 });
    const L = parseInt(lv);
    if (L >= 1 && L <= 15) o.lv[L - 1] = parseFloat(pct) || 0;
    if (L === 17) o.holders = parseInt(people) || 0;
  }
  // 彙總：散戶 ≤50張(級距1~8)、400張以上(12~15)、千張以上(15)
  for (const o of Object.values(out)) {
    const sum = (a, b) => Math.round(o.lv.slice(a - 1, b).reduce((x, y) => x + y, 0) * 100) / 100;
    o.retail = sum(1, 8); o.big400 = sum(12, 15); o.big1000 = sum(15, 15);
  }
  return { date, data: out };
}

// ---------- 主流程 ----------
export async function updateChips() {
  // 讀上一版
  let meta = { done: {}, tdccDate: "", tdccFetched: 0 };
  const store = {};
  try { meta = { ...meta, ...(await getJson(`${PAGES}/meta.json?t=${Date.now()}`)) }; } catch { log("沒有上一版 meta（第一次）"); }
  for (let i = 0; i <= 9; i++) {
    try { Object.assign(store, await getJson(`${PAGES}/${i}.json?t=${Date.now()}`)); } catch { }
  }
  log("讀到上一版", Object.keys(store).length, "檔");

  const ensure = c => store[c] || (store[c] = { h: [], dist: null, dh: [] });
  const setDay = (c, d, idx, vals) => {
    const s = ensure(c);
    let row = s.h.find(r => r[0] === +d);
    if (!row) { row = [+d, null, null, null, null, null, null, null, null]; s.h.push(row); }
    vals.forEach((v, k) => row[idx + k] = v);
  };

  const days = recentWeekdays(22);
  const today = days[0];
  let fetched = 0;
  for (const d of days) {
    const st = meta.done[d] || (meta.done[d] = {});
    if (st.holiday) continue;
    if (fetched >= 12) break;                          // 每次最多補 12 天，避免被證交所擋
    try {
      if (!st.inst) {
        const r = await inst(d); await sleep(2500); fetched++;
        if (r === null) { if (d !== today) st.holiday = 1; continue; }
        for (const [c, v] of Object.entries(r)) setDay(c, d, 1, v);   // 1:外資 2:投信 3:自營 4:合計
        st.inst = 1; log("法人", d, Object.keys(r).length);
      }
      if (!st.vol) {
        const r = await volume(d); await sleep(2500);
        if (r) { for (const [c, v] of Object.entries(r)) setDay(c, d, 7, v); st.vol = 1; log("成交量", d, Object.keys(r).length); }  // 7:量 8:收盤
      }
      if (!st.margin) {
        const r = await margin(d); await sleep(2500);
        if (r) { for (const [c, v] of Object.entries(r)) setDay(c, d, 5, v); st.margin = 1; log("融資券", d, Object.keys(r).length); }  // 5:融資 6:融券
      }
    } catch (e) { log("失敗", d, e.message); await sleep(5000); }
  }
  // 清掉太舊的 done 紀錄
  const keepSet = new Set(days);
  for (const k of Object.keys(meta.done)) if (!keepSet.has(k)) delete meta.done[k];

  // 集保：每 12 小時檢查一次
  if (Date.now() - (meta.tdccFetched || 0) > 12 * 3600_000) {
    try {
      const t = await tdcc();
      meta.tdccFetched = Date.now();
      if (t.date && t.date !== meta.tdccDate) {
        for (const [c, o] of Object.entries(t.data)) {
          const s = ensure(c);
          s.dist = { d: +t.date, holders: o.holders, retail: o.retail, big400: o.big400, big1000: o.big1000, lv: o.lv };
          s.dh = (s.dh || []).filter(r => r[0] !== +t.date);
          s.dh.push([+t.date, o.big1000, o.big400, o.retail, o.holders]);
          s.dh = s.dh.sort((a, b) => a[0] - b[0]).slice(-KEEP_WEEKS);
        }
        meta.tdccDate = t.date; log("集保更新", t.date, Object.keys(t.data).length);
      }
    } catch (e) { log("集保失敗", e.message); }
  }

  // 整理、輸出
  const shards = Array.from({ length: 10 }, () => ({}));
  for (const [c, s] of Object.entries(store)) {
    s.h = s.h.sort((a, b) => a[0] - b[0]).slice(-KEEP_DAYS);
    const k = parseInt(c[0]); if (isNaN(k)) continue;
    shards[k][c] = s;
  }
  mkdirSync("web/data/chips", { recursive: true });
  shards.forEach((sh, i) => writeFileSync(`web/data/chips/${i}.json`, JSON.stringify(sh)));
  const lastDay = Math.max(0, ...Object.values(store).flatMap(s => s.h.map(r => r[0])));
  meta.updated = Date.now(); meta.lastDay = lastDay;
  writeFileSync("web/data/chips/meta.json", JSON.stringify(meta));
  log("輸出完成", Object.keys(store).length, "檔，最新交易日", lastDay);
}
