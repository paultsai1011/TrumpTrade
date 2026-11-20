// 技術分析：均線、KD、MACD、RSI、K線型態 → 轉強 / 偏多 / 盤整 / 偏弱 / 轉弱
// App 版 Tech.kt 用同一套規則

export function sma(a, n) {
  const out = new Array(a.length).fill(null); let s = 0;
  for (let i = 0; i < a.length; i++) { s += a[i]; if (i >= n) s -= a[i - n]; if (i >= n - 1) out[i] = s / n; }
  return out;
}
export function ema(a, n) {
  const out = new Array(a.length).fill(null); const k = 2 / (n + 1); let e = null;
  for (let i = 0; i < a.length; i++) { e = e == null ? a[i] : a[i] * k + e * (1 - k); out[i] = e; }
  return out;
}
export function kd(h, l, c, n = 9) {
  const K = [], D = []; let k = 50, d = 50;
  for (let i = 0; i < c.length; i++) {
    const s = Math.max(0, i - n + 1);
    const hh = Math.max(...h.slice(s, i + 1)), ll = Math.min(...l.slice(s, i + 1));
    const rsv = hh > ll ? (c[i] - ll) / (hh - ll) * 100 : 50;
    k = k * 2 / 3 + rsv / 3; d = d * 2 / 3 + k / 3; K.push(k); D.push(d);
  }
  return { K, D };
}
export function macd(c) {
  const e12 = ema(c, 12), e26 = ema(c, 26);
  const dif = c.map((_, i) => e12[i] - e26[i]);
  const dea = ema(dif, 9);
  return { dif, dea, osc: dif.map((v, i) => v - dea[i]) };
}
export function rsi(c, n = 14) {
  const out = new Array(c.length).fill(null); let g = 0, l = 0;
  for (let i = 1; i < c.length; i++) {
    const ch = c[i] - c[i - 1], up = Math.max(ch, 0), dn = Math.max(-ch, 0);
    if (i <= n) { g += up / n; l += dn / n; if (i === n) out[i] = l === 0 ? 100 : 100 - 100 / (1 + g / l); }
    else { g = (g * (n - 1) + up) / n; l = (l * (n - 1) + dn) / n; out[i] = l === 0 ? 100 : 100 - 100 / (1 + g / l); }
  }
  return out;
}

/** k = {d,o,h,l,c,v}（舊→新） */
export function analyze(k) {
  const n = k?.c?.length || 0;
  if (n < 26) return { status: "資料不足", statusEn: "Not enough data", score: 0, reasons: [], n };
  const { o, h, l, c, v } = k;
  const ma5 = sma(c, 5), ma10 = sma(c, 10), ma20 = sma(c, 20), ma60 = sma(c, 60);
  const { K, D } = kd(h, l, c); const M = macd(c); const R = rsi(c);
  const i = n - 1, p = n - 2;
  const R_ = []; let score = 0;
  const add = (pts, zh, en) => { score += pts; R_.push({ zh, en, dir: Math.sign(pts) }); };
  const note = (zh, en) => R_.push({ zh, en, dir: 0 });

  // 均線
  if (ma20[i] != null) {
    if (c[i] > ma20[i] && c[p] <= ma20[p]) add(2, "今天站上月線（20日均線）", "Closed above the 20-day MA today");
    else if (c[i] < ma20[i] && c[p] >= ma20[p]) add(-2, "今天跌破月線（20日均線）", "Closed below the 20-day MA today");
    else if (c[i] > ma20[i]) add(1, "股價在月線之上", "Price above the 20-day MA");
    else add(-1, "股價在月線之下", "Price below the 20-day MA");
  }
  const crossUp = j => ma5[j] != null && ma20[j] != null && ma5[j - 1] != null && ma5[j] > ma20[j] && ma5[j - 1] <= ma20[j - 1];
  const crossDn = j => ma5[j] != null && ma20[j] != null && ma5[j - 1] != null && ma5[j] < ma20[j] && ma5[j - 1] >= ma20[j - 1];
  if ([i, i - 1, i - 2].some(crossUp)) add(2, "5日線向上穿過20日線（黃金交叉）", "5-day MA crossed above 20-day (golden cross)");
  if ([i, i - 1, i - 2].some(crossDn)) add(-2, "5日線向下穿過20日線（死亡交叉）", "5-day MA crossed below 20-day (death cross)");
  if (ma60[i] != null) {
    if (ma5[i] > ma20[i] && ma20[i] > ma60[i]) add(1, "均線多頭排列（5>20>60）", "Bullish MA alignment (5>20>60)");
    else if (ma5[i] < ma20[i] && ma20[i] < ma60[i]) add(-1, "均線空頭排列（5<20<60）", "Bearish MA alignment (5<20<60)");
    if (c[i] > ma60[i] && c[p] <= ma60[p]) add(1, "站上季線（60日均線）", "Closed above the 60-day MA");
    if (c[i] < ma60[i] && c[p] >= ma60[p]) add(-1, "跌破季線（60日均線）", "Closed below the 60-day MA");
  }
  // KD
  const kUp = j => K[j] > D[j] && K[j - 1] <= D[j - 1], kDn = j => K[j] < D[j] && K[j - 1] >= D[j - 1];
  if (kUp(i) || kUp(p)) { if (K[i] < 30) add(2, `KD 低檔黃金交叉（K=${K[i].toFixed(0)}）`, `KD golden cross at low level (K=${K[i].toFixed(0)})`); else add(1, "KD 黃金交叉", "KD golden cross"); }
  if (kDn(i) || kDn(p)) { if (K[i] > 70) add(-2, `KD 高檔死亡交叉（K=${K[i].toFixed(0)}）`, `KD death cross at high level (K=${K[i].toFixed(0)})`); else add(-1, "KD 死亡交叉", "KD death cross"); }
  if (K[i] > 80) note(`KD 過熱（K=${K[i].toFixed(0)}），留意追高風險`, `KD overbought (K=${K[i].toFixed(0)}), beware chasing`);
  if (K[i] < 20) note(`KD 超賣（K=${K[i].toFixed(0)}），可能醞釀反彈`, `KD oversold (K=${K[i].toFixed(0)}), rebound possible`);
  // MACD
  if (M.osc[i] > 0 && M.osc[p] <= 0) add(2, "MACD 柱狀體翻紅", "MACD histogram turned positive");
  else if (M.osc[i] < 0 && M.osc[p] >= 0) add(-2, "MACD 柱狀體翻綠", "MACD histogram turned negative");
  else if (M.osc[i] > M.osc[p] && M.osc[p] > M.osc[i - 2]) add(1, "MACD 動能連續增強", "MACD momentum rising");
  else if (M.osc[i] < M.osc[p] && M.osc[p] < M.osc[i - 2]) add(-1, "MACD 動能連續減弱", "MACD momentum fading");
  // RSI
  if (R[i] != null) {
    if (R[i] > 75) note(`RSI 偏高（${R[i].toFixed(0)}），短線過熱`, `RSI high (${R[i].toFixed(0)}), short-term overheated`);
    else if (R[i] < 25) note(`RSI 偏低（${R[i].toFixed(0)}），短線超跌`, `RSI low (${R[i].toFixed(0)}), short-term oversold`);
  }
  // 量價
  const v5 = (v.slice(Math.max(0, i - 5), i).reduce((a, b) => a + b, 0)) / Math.min(5, i);
  const chg = (c[i] - c[p]) / c[p] * 100;
  if (v5 > 0 && v[i] > v5 * 1.5) {
    if (chg > 0) add(1, `量增價漲（量為5日均量 ${(v[i] / v5).toFixed(1)} 倍）`, `Rising on heavy volume (${(v[i] / v5).toFixed(1)}× 5-day avg)`);
    else if (chg < 0) add(-1, `量增價跌（量為5日均量 ${(v[i] / v5).toFixed(1)} 倍），留意賣壓`, `Falling on heavy volume (${(v[i] / v5).toFixed(1)}× 5-day avg)`);
  }
  // K 線型態
  const body = (c[i] - o[i]) / o[i] * 100;
  if (body > 3) add(1, `收長紅K（+${body.toFixed(1)}%）`, `Long bullish candle (+${body.toFixed(1)}%)`);
  if (body < -3) add(-1, `收長黑K（${body.toFixed(1)}%）`, `Long bearish candle (${body.toFixed(1)}%)`);
  if (c[i] > o[i] && c[p] < o[p] && c[i] >= o[p] && o[i] <= c[p]) add(1, "多頭吞噬（紅K包覆前一根黑K）", "Bullish engulfing");
  if (c[i] < o[i] && c[p] > o[p] && c[i] <= o[p] && o[i] >= c[p]) add(-1, "空頭吞噬（黑K包覆前一根紅K）", "Bearish engulfing");
  // 突破 / 跌破
  if (n > 21) {
    const hh = Math.max(...h.slice(i - 20, i)), ll = Math.min(...l.slice(i - 20, i));
    if (c[i] > hh) add(1, "突破近20日高點", "Broke above 20-day high");
    if (c[i] < ll) add(-1, "跌破近20日低點", "Broke below 20-day low");
  }
  const status = score >= 4 ? "轉強" : score >= 2 ? "偏多" : score <= -4 ? "轉弱" : score <= -2 ? "偏弱" : "盤整";
  const statusEn = { "轉強": "Turning strong", "偏多": "Bullish bias", "盤整": "Sideways", "偏弱": "Bearish bias", "轉弱": "Turning weak" }[status];
  return {
    status, statusEn, score, reasons: R_, n, date: k.d[i],
    last: { c: c[i], ma5: ma5[i], ma10: ma10[i], ma20: ma20[i], ma60: ma60[i], k: K[i], d: D[i], dif: M.dif[i], dea: M.dea[i], osc: M.osc[i], rsi: R[i] },
    series: { ma5, ma20, ma60, K, D, osc: M.osc },
  };
}

/** 給 AI 用的技術面摘要 */
export function techSummary(t) {
  if (!t || !t.last) return "";
  const L = t.last, f = x => x == null ? "–" : x.toFixed(2);
  return `技術面（${t.date}）：${t.status}（分數 ${t.score}）；收盤 ${f(L.c)}，MA5 ${f(L.ma5)}、MA20 ${f(L.ma20)}、MA60 ${f(L.ma60)}；K ${f(L.k)}、D ${f(L.d)}；MACD柱 ${f(L.osc)}；RSI ${f(L.rsi)}。訊號：${t.reasons.map(r => r.zh).join("；")}`;
}
