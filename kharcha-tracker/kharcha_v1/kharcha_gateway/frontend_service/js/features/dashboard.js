/* Dashboard spending trends: range buckets, series builder, chart
   rendering, and dashboard wiring. dashGroupId/dashRange live in
   state.dashboard; shared caches come from core/state.js. The per-group
   expense summary now lives on the Groups screen (see groups.js /
   expenseSummaryHtml in expenses.js). */

import { $, $all } from "../core/dom.js";
import { state } from "../core/state.js";
import { api, Auth } from "../api.js";
import { Charts } from "../charts.js";
import { esc } from "../utils/html.js";
import { formatAmount } from "../utils/formatters.js";
import { applyUserChrome } from "./auth.js";
import { refreshGroupCount } from "./groups.js";
import { refreshPlanStat } from "./subscription.js";
import { refreshInviteCount } from "./invitations.js";
import { loadNotifications } from "./notifications.js";

const MONTH_SHORTS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function dayKey(d) {
  return d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate();
}

function weekStart(d) {
  const s = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  s.setDate(s.getDate() - ((s.getDay() + 6) % 7));
  return s;
}

function isoWeekNumber(d) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  t.setUTCDate(t.getUTCDate() + 3 - ((t.getUTCDay() + 6) % 7));
  const week1 = new Date(Date.UTC(t.getUTCFullYear(), 0, 4));
  return 1 + Math.round((((t - week1) / 86400000) + 3 + ((week1.getUTCDay() + 6) % 7)) / 7);
}

function rangeBuckets(range) {
  const now = new Date();
  const buckets = [];
  let keyOf;
  if (range === "weekly") {
    const thisWeek = weekStart(now);
    for (let i = 7; i >= 0; i--) {
      const d = new Date(thisWeek.getFullYear(), thisWeek.getMonth(), thisWeek.getDate() - i * 7);
      buckets.push({ key: dayKey(d), label: "W" + isoWeekNumber(d), value: 0 });
    }
    keyOf = (d) => dayKey(weekStart(d));
  } else if (range === "monthly") {
    const thisMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    for (let i = 5; i >= 0; i--) {
      const d = new Date(thisMonth.getFullYear(), thisMonth.getMonth() - i, 1);
      buckets.push({
        key: d.getFullYear() + "-" + (d.getMonth() + 1),
        label: MONTH_SHORTS[d.getMonth()],
        value: 0
      });
    }
    keyOf = (d) => d.getFullYear() + "-" + (d.getMonth() + 1);
  } else {
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    for (let i = 13; i >= 0; i--) {
      const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
      buckets.push({ key: dayKey(d), label: String(d.getDate()), value: 0 });
    }
    keyOf = dayKey;
  }
  return { buckets: buckets, keyOf: keyOf };
}

function buildRangeSeries(expenses, range, currency) {
  const built = rangeBuckets(range);
  const byKey = {};
  built.buckets.forEach((b) => { byKey[b.key] = b; });
  expenses.forEach((e) => {
    const d = new Date(e.date_time);
    if (isNaN(d.getTime())) return;
    const bucket = byKey[built.keyOf(d)];
    if (!bucket) return;
    const amt = Number(e.expn_amnt) || 0;
    bucket.value += amt;
    // E4: accumulate per-category amounts per bucket (stacked-bar parts).
    const cat = e.expn_ctgry || "OTHER";
    if (!bucket.byCat) bucket.byCat = {};
    bucket.byCat[cat] = (bucket.byCat[cat] || 0) + amt;
  });
  return built.buckets.map((b) => {
    const parts = Object.keys(b.byCat || {})
      .sort((a, z) => (b.byCat[z] || 0) - (b.byCat[a] || 0))
      .map((c) => {
        const label = c.replace(/_/g, " ");
        return {
          label: label,
          value: b.byCat[c],
          valueText: formatAmount(b.byCat[c], currency),
          color: Charts.colorFor(label)
        };
      });
    return {
      label: b.label,
      value: b.value,
      valueText: formatAmount(b.value, currency),
      parts: parts
    };
  });
}

function renderExpenseCharts(group, expenses) {
  const chartsEl = $("#dash-charts");
  const trendEmpty = $("#dash-trend-empty");
  const barEmpty = $("#dash-bar-empty");
  const barBox = $("#dash-bar-chart");
  const pieBox = $("#dash-pie-chart");
  const legend = $("#dash-pie-legend");
  if (!chartsEl || !trendEmpty) return;
  if (!expenses.length) {
    chartsEl.hidden = true;
    trendEmpty.hidden = false;
    return;
  }
  trendEmpty.hidden = true;
  chartsEl.hidden = false;

  const series = buildRangeSeries(expenses, state.dashboard.range, group.currency);

  const rangeTotal = series.reduce((sum, b) => sum + b.value, 0);
  if (rangeTotal <= 0) {
    barBox.hidden = true;
    barBox.innerHTML = "";
    barEmpty.hidden = false;
  } else {
    barEmpty.hidden = true;
    barBox.innerHTML = Charts.stackedBarChart(series);
    barBox.hidden = false;
  }

  const byCat = {};
  expenses.forEach((e) => {
    const key = e.expn_ctgry || "OTHER";
    byCat[key] = (byCat[key] || 0) + (Number(e.expn_amnt) || 0);
  });
  const slices = Object.keys(byCat)
    .sort((a, b) => byCat[b] - byCat[a])
    .map((c) => ({
      label: c.replace(/_/g, " "),
      value: byCat[c],
      valueText: formatAmount(byCat[c], group.currency)
    }));
  pieBox.innerHTML = Charts.pieChart(slices);
  pieBox.hidden = false;
  legend.innerHTML = slices.map((s) =>
    '<li class="legend-item">' +
      '<span class="legend-dot" style="background:' + Charts.colorFor(s.label) + '"></span>' +
      esc(s.label) +
      '<span class="legend-val">' + esc(s.valueText) + "</span>" +
    "</li>"
  ).join("");
  legend.hidden = false;
}

async function loadTrends() {
  const select = $("#dash-expense-group");
  if (!select) return;
  const trendEmpty = $("#dash-trend-empty");
  const chartsEl = $("#dash-charts");

  if (!state.groupsCache.length) {
    try {
      state.groupsCache = await api.get("/groups");
    } catch (e) {
      state.groupsCache = [];
    }
  }
  if (!state.groupsCache.length) {
    state.dashboard.groupId = "";
    select.innerHTML = '<option value="">Select a group&hellip;</option>';
    if (trendEmpty) trendEmpty.hidden = false;
    if (chartsEl) chartsEl.hidden = true;
    return;
  }
  if (!state.groupsCache.some((g) => String(g.id) === String(state.dashboard.groupId))) {
    state.dashboard.groupId = state.groupsCache[0].id;
  }
  select.innerHTML = '<option value="">Select a group&hellip;</option>' +
    state.groupsCache.map((g) =>
      '<option value="' + esc(g.id) + '"' +
      (String(g.id) === String(state.dashboard.groupId) ? " selected" : "") + ">" +
      esc(g.name) + "</option>").join("");

  const group = state.groupsCache.find((g) => String(g.id) === String(state.dashboard.groupId));
  let expenses = state.expensesCache[state.dashboard.groupId];
  if (!expenses) {
    try {
      expenses = await api.get("/groups/" + encodeURIComponent(state.dashboard.groupId) + "/expenses");
      state.expensesCache[state.dashboard.groupId] = expenses;
    } catch (e) {
      expenses = null;
    }
  }
  if (!expenses) {
    if (trendEmpty) trendEmpty.hidden = false;
    if (chartsEl) chartsEl.hidden = true;
    return;
  }
  renderExpenseCharts(group, expenses);
}

function bindDashboard() {
  const select = $("#dash-expense-group");
  if (select) {
    select.addEventListener("change", (e) => {
      state.dashboard.groupId = e.target.value;
      loadTrends();
    });
  }
  const toggle = $("#dash-range-toggle");
  if (toggle) {
    toggle.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-range]");
      if (!btn) return;
      state.dashboard.range = btn.dataset.range;
      $all("[data-range]", toggle).forEach((b) => b.classList.toggle("is-active", b === btn));
      loadTrends();
    });
  }
}

export function mount() {
  bindDashboard();
}

export async function onEnter() {
  const user = state.currentUser || Auth.user;
  if (!user) return;
  $("#dashboard-sub").textContent = "Welcome back, " + (user.name || user.email || "") + ".";
  applyUserChrome();
  refreshGroupCount();
  refreshPlanStat();
  refreshInviteCount();
  loadNotifications();
  await loadTrends();
}
