/* Kharcha Tracker — hash router, nav, auth flows. */
"use strict";

const NAV_ITEMS = [
  { route: "dashboard", label: "Dashboard", icon: Icons.dashboard },
  { route: "groups", label: "Groups", icon: Icons.groups },
  { route: "expenses", label: "Expenses", icon: Icons.card },
  { route: "settings", label: "Settings", icon: Icons.settings }
];

const ROUTE_TITLES = {
  login: "Log in",
  signup: "Sign up",
  magic: "Signing you in",
  dashboard: "Dashboard",
  groups: "Groups",
  expenses: "Expenses",
  settings: "Settings",
  subscription: "Subscription"
};

const PROTECTED_ROUTES = new Set(["dashboard", "groups", "expenses", "settings", "subscription"]);

let currentUser = null;

/* Per-route enter hooks — later phases attach more. */
const routeHandlers = {};

function $(sel, root) { return (root || document).querySelector(sel); }
function $all(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

function currentRoute() {
  const raw = location.hash.replace(/^#\/?/, "").split("?")[0];
  return raw || "login";
}

function navigate(route) {
  const target = "#/" + route;
  if (location.hash === target) router();
  else location.hash = target;
}

function esc(value) {
  return String(value == null ? "" : value).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[c]);
}

/* ------------------------------ Toast ------------------------------ */
let toastTimer = null;
function showToast(message, kind) {
  const el = $("#toast");
  el.textContent = message;
  el.className = "toast" + (kind ? " toast--" + kind : "");
  el.hidden = false;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 3200);
}

/* ------------------------------ Form errors ------------------------------ */
function setError(el, message) {
  if (!el) return;
  el.textContent = message || "";
  el.hidden = !message;
}
function setBusy(button, busy, busyLabel) {
  if (!button) return;
  if (busy) {
    button.dataset.label = button.textContent;
    button.disabled = true;
    if (busyLabel) button.textContent = busyLabel;
  } else {
    button.disabled = false;
    if (button.dataset.label) button.textContent = button.dataset.label;
  }
}

/* ------------------------------ Modal ------------------------------ */
function openModal(title, contentHtml) {
  const root = $("#modal-root");
  root.innerHTML =
    '<div class="modal-overlay" data-modal-overlay>' +
      '<div class="modal" role="dialog" aria-modal="true">' +
        '<div class="modal-head">' +
          '<span class="modal-title"></span>' +
          '<button class="icon-btn" type="button" data-modal-close aria-label="Close dialog">' + Icons.x + "</button>" +
        "</div>" +
        '<div class="modal-body"></div>' +
      "</div>" +
    "</div>";
  const dialog = root.querySelector(".modal");
  dialog.setAttribute("aria-label", title);
  root.querySelector(".modal-title").textContent = title;
  root.querySelector(".modal-body").innerHTML = contentHtml;
  root.querySelector("[data-modal-overlay]").addEventListener("click", (e) => {
    if (e.target.hasAttribute("data-modal-overlay")) closeModal();
  });
  root.querySelector("[data-modal-close]").addEventListener("click", closeModal);
  root.hidden = false;
  document.addEventListener("keydown", modalEscHandler);
}

function closeModal() {
  const root = $("#modal-root");
  root.hidden = true;
  root.innerHTML = "";
  document.removeEventListener("keydown", modalEscHandler);
}

function modalEscHandler(e) {
  if (e.key === "Escape") closeModal();
}

/* ------------------------------ Nav ------------------------------ */
function navItemHtml(item) {
  return (
    '<a class="nav-item" href="#/' + item.route + '" data-route="' + item.route + '"' +
    ' title="' + esc(item.label) + '">' + item.icon +
    '<span class="nav-label">' + esc(item.label) + "</span></a>"
  );
}

function renderNav() {
  const items = NAV_ITEMS.map(navItemHtml).join("");
  $("#sidebar").innerHTML =
    items + '<span class="nav-spacer"></span>' +
    '<span class="nav-foot" id="nav-foot-email"></span>';
  $("#topnav").innerHTML = items;
  $("#tabbar").innerHTML = items;
}

function setActiveNav(route) {
  $all("[data-route]").forEach((el) => {
    el.classList.toggle("is-active", el.dataset.route === route);
  });
}

/* ------------------------------ Router ------------------------------ */
function router() {
  const route = currentRoute();
  if (route === "magic") { handleMagicLink(); return; }

  const isAuth = route === "login" || route === "signup";
  const authed = Boolean(Auth.access);

  if (PROTECTED_ROUTES.has(route) && !authed) { navigate("login"); return; }
  if (isAuth && authed && currentUser) { navigate("dashboard"); return; }
  if (!ROUTE_TITLES[route]) { navigate(authed ? "dashboard" : "login"); return; }

  $all(".view").forEach((v) => { v.hidden = v.id !== "view-" + route; });
  $("#app-shell").hidden = isAuth;

  setActiveNav(route);
  document.title = ROUTE_TITLES[route] + " · Kharcha Tracker";

  const handler = routeHandlers[route];
  if (handler) handler();
}

/* ------------------------------ Session ------------------------------ */
function applyUserChrome() {
  const user = currentUser || Auth.user;
  if (!user) return;
  const name = user.name || user.email || "";
  const initial = (name.trim()[0] || "?").toUpperCase();
  $("#user-chip-name").textContent = name;
  $("#user-avatar").textContent = initial;
  const bell = $("#btn-bell");
  if (bell) bell.hidden = false;
  const foot = $("#nav-foot-email");
  if (foot) foot.textContent = user.email || "";
}

function logout() {
  Auth.clear();
  currentUser = null;
  navigate("login");
  showToast("Logged out.");
}

function onExpired() {
  currentUser = null;
  navigate("login");
  showToast("Session expired — please log in again.", "error");
}

function onSubscriptionRequired() {
  showToast("Your free entries are used up — choose a plan to continue.", "error");
  if (currentRoute() !== "subscription") navigate("subscription");
}

/* ------------------------------ Magic link (email SSO) ------------------------------ */
let magicInFlight = false;

async function establishSession(base) {
  Auth.setSession({ access: base.auth_token, refresh: base.refresh_token });
  currentUser = await api.get("/users/me");
  Auth.saveUser(currentUser);
}

async function handleMagicLink() {
  if (magicInFlight) return;
  magicInFlight = true;
  $all(".view").forEach((v) => { v.hidden = v.id !== "view-login"; });
  $("#app-shell").hidden = true;
  document.title = "Signing you in… · Kharcha Tracker";

  const query = location.hash.split("?")[1] || "";
  const token = new URLSearchParams(query).get("token") || "";
  if (!token) {
    magicInFlight = false;
    navigate("login");
    showToast("Missing magic-link token.", "error");
    return;
  }
  try {
    const base = await api.post("/auth/magic-link/verify", { token: token }, { auth: false });
    await establishSession(base);
    showToast("Signed in with email.", "success");
    navigate("dashboard");
  } catch (err) {
    showToast(err.message || "Magic link is invalid or expired.", "error");
    navigate("login");
  } finally {
    magicInFlight = false;
  }
}

async function requestMagicLink(email) {
  const res = await api.post("/auth/magic-link", { email: email }, { auth: false });
  if (res && res.magic_token) {
    const base = await api.post(
      "/auth/magic-link/verify", { token: res.magic_token }, { auth: false }
    );
    await establishSession(base);
    return true;
  }
  return false;
}

/* ------------------------------ Auth forms ------------------------------ */
function bindAuthForms() {
  $("#form-login").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#login-error");
    const btn = $("#login-submit");
    setError(errEl, "");
    const email = $("#login-email").value.trim();
    const password = $("#login-password").value;
    if (!email || !password) { setError(errEl, "Email and password are required."); return; }

    setBusy(btn, true, "Logging in…");
    try {
      const base = await api.post("/auth/login", { email, password }, { auth: false });
      await establishSession(base);
      navigate("dashboard");
    } catch (err) {
      Auth.clear();
      setError(errEl, err.message || "Login failed.");
    } finally {
      setBusy(btn, false);
    }
  });

  $("#form-signup").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#signup-error");
    const btn = $("#signup-submit");
    setError(errEl, "");

    const name = $("#signup-name").value.trim();
    const email = $("#signup-email").value.trim();
    const password = $("#signup-password").value;

    if (!name) { setError(errEl, "Username is required."); return; }
    if (!email || !email.includes("@")) { setError(errEl, "Enter a valid email address."); return; }
    if (password.length < 6) { setError(errEl, "Password must be at least 6 characters."); return; }

    setBusy(btn, true, "Creating account…");
    try {
      await api.post("/auth/signup", { name: name, email: email, password: password }, { auth: false });
      const base = await api.post("/auth/login", { email: email, password: password }, { auth: false });
      await establishSession(base);
      navigate("dashboard");
      showToast("Welcome to Kharcha Tracker!", "success");
    } catch (err) {
      setError(errEl, err.message || "Sign up failed.");
    } finally {
      setBusy(btn, false);
    }
  });

  const magicBtn = $("#btn-magic-link");
  if (magicBtn) {
    magicBtn.addEventListener("click", async () => {
      const errEl = $("#login-error");
      const hintEl = $("#magic-hint");
      setError(errEl, "");
      hintEl.hidden = true;
      const email = $("#login-email").value.trim();
      if (!email || !email.includes("@")) {
        setError(errEl, "Enter your email above, then tap “Continue with email”.");
        return;
      }
      setBusy(magicBtn, true, "Sending link…");
      try {
        const immediate = await requestMagicLink(email);
        if (immediate) {
          showToast("Signed in with email.", "success");
          navigate("dashboard");
        } else {
          hintEl.textContent = "Check your inbox for a sign-in link.";
          hintEl.hidden = false;
        }
      } catch (err) {
        setError(errEl, err.message || "Could not send the link.");
      } finally {
        setBusy(magicBtn, false);
      }
    });
  }
}

/* ------------------------------ Route handlers ------------------------------ */
routeHandlers.dashboard = function () {
  const user = currentUser || Auth.user;
  if (!user) return;
  $("#dashboard-sub").textContent = "Welcome back, " + (user.name || user.email || "") + ".";
  applyUserChrome();
  refreshGroupCount();
  refreshPlanStat();
  refreshInviteCount();
  loadNotifications();
  loadExpenseSummary();
};

routeHandlers.groups = function () {
  applyUserChrome();
  loadGroups();
  loadInvitations();
};

routeHandlers.expenses = async function () {
  applyUserChrome();
  await loadGroups();
  renderExpensesScreen();
};

routeHandlers.settings = function () {
  const user = currentUser || Auth.user;
  if (!user) return;
  $("#profile-name").value = user.name || "";
  $("#profile-email").value = user.email || "";
  $("#profile-avatar").value = user.avatar_url || "";
  setError($("#profile-error"), "");
  $("#profile-success").hidden = true;
  applyUserChrome();
  refreshSettingsPlan();
};

routeHandlers.subscription = function () {
  applyUserChrome();
  loadSubscription();
};

/* ------------------------------ Subscription ------------------------------ */
const PLAN_LABELS = {
  none: "Free (trial)",
  monthly: "Monthly",
  "6month": "6 Months",
  yearly: "Yearly",
  lifetime: "Lifetime"
};

function planLabel(plan) {
  return PLAN_LABELS[plan] || plan || "—";
}

function formatDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function planSummary(status) {
  if (status.plan_status === "active") {
    return status.plan === "lifetime"
      ? planLabel(status.plan) + " plan — active, never expires"
      : planLabel(status.plan) + " plan — active until " + formatDate(status.plan_ends_at);
  }
  if (status.is_expired) {
    return "Free trial ended — " + status.entries_remaining + " free entries left";
  }
  return "Free trial — unlimited until " + formatDate(status.trial_ends_at);
}

function shortPlan(status) {
  if (status.plan_status === "active") return planLabel(status.plan);
  return status.is_expired ? "Expired" : "Trial";
}

function renderSubscriptionBanner(status) {
  const el = $("#sub-current");
  el.hidden = false;
  el.classList.remove("banner--ok", "banner--warn", "banner--info");
  if (status.plan_status === "active") {
    el.classList.add("banner--ok");
    el.textContent = "Current plan: " + planSummary(status) + ".";
  } else if (status.is_expired) {
    el.classList.add("banner--warn");
    el.textContent = "Your trial has ended. " + status.entries_remaining +
      " free entries remaining — choose a plan to keep going.";
  } else {
    el.classList.add("banner--info");
    el.textContent = "You're on the free trial — unlimited until " +
      formatDate(status.trial_ends_at) + ".";
  }
}

async function loadSubscription() {
  const el = $("#sub-current");
  try {
    const status = await api.get("/users/me/subscription");
    renderSubscriptionBanner(status);
    updatePlanStat(status);
  } catch (err) {
    el.hidden = false;
    el.className = "banner banner--warn";
    el.textContent = "Could not load your subscription: " + err.message;
  }
}

function updatePlanStat(status) {
  const el = $("#stat-plan");
  if (el) el.textContent = shortPlan(status);
}

async function refreshPlanStat() {
  try {
    updatePlanStat(await api.get("/users/me/subscription"));
  } catch (e) { /* keep placeholder */ }
}

async function refreshSettingsPlan() {
  const el = $("#settings-plan");
  if (!el) return;
  try {
    el.textContent = planSummary(await api.get("/users/me/subscription"));
  } catch (e) {
    el.textContent = "Could not load plan.";
  }
}

function bindSubscriptionActions() {
  $all(".plan-buy").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const plan = btn.dataset.plan;
      if (!window.confirm("Confirm mock purchase: " + planLabel(plan) + "?")) return;
      setBusy(btn, true, "Processing…");
      try {
        const status = await api.post("/subscription", { plan: plan });
        renderSubscriptionBanner(status);
        updatePlanStat(status);
        showToast(planLabel(plan) + " plan activated (mock payment).", "success");
      } catch (err) {
        showToast(err.message || "Could not activate the plan.", "error");
      } finally {
        setBusy(btn, false);
      }
    });
  });
}

/* ------------------------------ Dashboard expense summary ------------------------------ */
let dashGroupId = "";
let dashRange = "daily";
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
    if (bucket) bucket.value += Number(e.expn_amnt) || 0;
  });
  return built.buckets.map((b) => ({
    label: b.label,
    value: b.value,
    valueText: formatAmount(b.value, currency)
  }));
}

function renderExpenseSummary(group, expenses) {
  const statsEl = $("#dash-expense-stats");
  const emptyEl = $("#dash-expense-empty");
  if (!statsEl || !emptyEl) return;
  if (!expenses.length) {
    statsEl.hidden = true;
    statsEl.innerHTML = "";
    emptyEl.textContent = "No expenses yet for " + (group.name || "this group") + ".";
    emptyEl.hidden = false;
    return;
  }
  emptyEl.hidden = true;
  const total = expenses.reduce((sum, e) => sum + (Number(e.expn_amnt) || 0), 0);
  const byCat = {};
  expenses.forEach((e) => {
    const key = e.expn_ctgry || "OTHER";
    byCat[key] = (byCat[key] || 0) + (Number(e.expn_amnt) || 0);
  });
  const cats = Object.keys(byCat).sort((a, b) => byCat[b] - byCat[a]);
  statsEl.innerHTML =
    '<div class="card stat-card">' +
      '<span class="stat-label">Total spent</span>' +
      '<span class="stat-value">' + esc(formatAmount(total, group.currency)) + "</span>" +
    "</div>" +
    cats.map((c) =>
      '<div class="card stat-card">' +
        '<span class="stat-label">' + esc(c.replace(/_/g, " ")) + "</span>" +
        '<span class="stat-value">' + esc(formatAmount(byCat[c], group.currency)) + "</span>" +
      "</div>"
    ).join("");
  statsEl.hidden = false;
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

  const series = buildRangeSeries(expenses, dashRange, group.currency);
  const rangeTotal = series.reduce((sum, b) => sum + b.value, 0);
  if (rangeTotal <= 0) {
    barBox.hidden = true;
    barBox.innerHTML = "";
    barEmpty.hidden = false;
  } else {
    barEmpty.hidden = true;
    barBox.innerHTML = Charts.barChart(series);
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
  legend.innerHTML = slices.map((s, i) =>
    '<li class="legend-item">' +
      '<span class="legend-dot" style="background:' + Charts.color(i) + '"></span>' +
      esc(s.label) +
      '<span class="legend-val">' + esc(s.valueText) + "</span>" +
    "</li>"
  ).join("");
  legend.hidden = false;
}

async function loadExpenseSummary() {
  const select = $("#dash-expense-group");
  if (!select) return;
  const emptyEl = $("#dash-expense-empty");
  const statsEl = $("#dash-expense-stats");
  const trendEmpty = $("#dash-trend-empty");
  const chartsEl = $("#dash-charts");

  if (!groupsCache.length) {
    try {
      groupsCache = await api.get("/groups");
    } catch (e) {
      groupsCache = [];
    }
  }
  if (!groupsCache.length) {
    dashGroupId = "";
    select.innerHTML = '<option value="">Select a group&hellip;</option>';
    if (emptyEl) {
      emptyEl.textContent = "No groups yet — create one from the Groups screen first.";
      emptyEl.hidden = false;
    }
    if (statsEl) statsEl.hidden = true;
    if (trendEmpty) trendEmpty.hidden = false;
    if (chartsEl) chartsEl.hidden = true;
    return;
  }
  if (!groupsCache.some((g) => String(g.id) === String(dashGroupId))) {
    dashGroupId = groupsCache[0].id;
  }
  select.innerHTML = '<option value="">Select a group&hellip;</option>' +
    groupsCache.map((g) =>
      '<option value="' + esc(g.id) + '"' +
      (String(g.id) === String(dashGroupId) ? " selected" : "") + ">" +
      esc(g.name) + "</option>").join("");

  const group = groupsCache.find((g) => String(g.id) === String(dashGroupId));
  let expenses = expensesCache[dashGroupId];
  if (!expenses) {
    try {
      expenses = await api.get("/groups/" + encodeURIComponent(dashGroupId) + "/expenses");
      expensesCache[dashGroupId] = expenses;
    } catch (e) {
      expenses = null;
    }
  }
  if (!expenses) {
    if (emptyEl) {
      emptyEl.textContent = "Could not load expenses for this group.";
      emptyEl.hidden = false;
    }
    if (statsEl) statsEl.hidden = true;
    if (trendEmpty) trendEmpty.hidden = false;
    if (chartsEl) chartsEl.hidden = true;
    return;
  }
  renderExpenseSummary(group, expenses);
  renderExpenseCharts(group, expenses);
}

function bindDashboard() {
  const select = $("#dash-expense-group");
  if (select) {
    select.addEventListener("change", (e) => {
      dashGroupId = e.target.value;
      loadExpenseSummary();
    });
  }
  const toggle = $("#dash-range-toggle");
  if (toggle) {
    toggle.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-range]");
      if (!btn) return;
      dashRange = btn.dataset.range;
      $all("[data-range]", toggle).forEach((b) => b.classList.toggle("is-active", b === btn));
      loadExpenseSummary();
    });
  }
}

/* ------------------------------ Groups ------------------------------ */
let groupsCache = [];
let membersCache = {};
let expensesCache = {};
let settleCache = {};

const EXPENSE_CATEGORIES = [
  "GROCERY", "UTILITY", "TRAVEL", "ENTERTAINMENT", "FOOD_N_DINING",
  "OP_EXP", "CAP_EXP", "ACCOMMODATION", "CUSTOM"
];

async function refreshGroupCount() {
  try {
    const groups = await api.get("/groups");
    groupsCache = groups;
    $("#stat-groups").textContent = String(groups.length);
  } catch (e) { /* keep placeholder */ }
}

function formatAmount(value, currency) {
  const n = Number(value || 0);
  return (currency ? currency + " " : "") + n.toFixed(2);
}

function memberName(groupId, userId) {
  const members = membersCache[groupId] || [];
  const match = members.find((m) => m.id === userId);
  return match ? (match.name || match.email || "?") : "Unknown";
}

function memberChipsHtml(group) {
  const members = membersCache[group.id] || [];
  if (!members.length) return "";
  return (
    '<div class="member-chips">' +
      members.map((m) =>
        '<span class="member-chip" title="' + esc(m.email || "") + '">' +
        esc(m.name || m.email || "?") + "</span>"
      ).join("") +
    "</div>"
  );
}

function expenseRowHtml(exp, group) {
  const payers = (exp.paid_by || [])
    .map((p) => memberName(group.id, p.user_id))
    .join(", ");
  const isOpen = String(group.status || "").toLowerCase() === "open";
  const dis = isOpen ? "" : " disabled";
  const lockTitle = isOpen ? "" : ' title="Only open groups can be edited"';
  return (
    '<div class="expense-row" data-trans-id="' + esc(exp.trans_id) + '" data-group-id="' + esc(group.id) + '">' +
      '<div class="expense-row-main">' +
        '<span class="expense-desc">' + esc(exp.expn_desc) + "</span>" +
        '<span class="expense-amt">' + esc(formatAmount(exp.expn_amnt, group.currency)) + "</span>" +
      "</div>" +
      '<div class="expense-row-meta">' +
        esc(exp.expn_ctgry) + " &middot; paid by " + esc(payers || "—") +
        " &middot; " + esc(formatDate(exp.date_time)) +
      "</div>" +
      '<div class="expense-row-actions">' +
        '<button class="icon-btn icon-btn--sm" data-action="edit-expense" type="button" aria-label="Edit expense"' + lockTitle + dis + ">" + Icons.edit + "</button>" +
        '<button class="icon-btn icon-btn--sm icon-btn--danger" data-action="delete-expense" type="button" aria-label="Delete expense"' + lockTitle + dis + ">" + Icons.trash + "</button>" +
      "</div>" +
    "</div>"
  );
}

function expensesListHtml(group) {
  const expenses = expensesCache[group.id] || [];
  if (!expenses.length) {
    return '<p class="empty empty--sm">No expenses yet.</p>';
  }
  return expenses.map((exp) => expenseRowHtml(exp, group)).join("");
}

function expenseFormHtml(group) {
  const members = membersCache[group.id] || [];
  const payerOptions = members.map((m) =>
    '<option value="' + esc(m.id) + '">' + esc(m.name || m.email) + "</option>"
  ).join("");
  const categoryOptions = EXPENSE_CATEGORIES.map((c) =>
    '<option value="' + c + '">' + c.replace(/_/g, " ") + "</option>"
  ).join("");
  return (
    '<form class="expense-form" data-expense-form="' + esc(group.id) + '" hidden novalidate>' +
      '<div class="card-title">Add expense to ' + esc(group.name) + "</div>" +
      '<div class="form-row">' +
        '<div class="field">' +
          "<label>Description</label>" +
          '<input class="exp-desc" type="text" placeholder="e.g. Groceries" maxlength="200" required>' +
        "</div>" +
        '<div class="field field--narrow">' +
          "<label>Amount (" + esc(group.currency) + ")</label>" +
          '<input class="exp-amount" type="number" min="0.01" step="0.01" placeholder="0.00" required>' +
        "</div>" +
        '<div class="field field--narrow">' +
          "<label>Category</label>" +
          '<select class="exp-category">' + categoryOptions + "</select>" +
        "</div>" +
      "</div>" +
      '<div class="form-row">' +
        '<div class="field">' +
          "<label>Paid by</label>" +
          '<select class="exp-payer">' + payerOptions + "</select>" +
        "</div>" +
        '<div class="field field--narrow">' +
          "<label>Split</label>" +
          '<p class="form-hint" data-split-hint>Equally among all ' + members.length + " member(s)</p>" +
          '<button class="btn btn-ghost btn-sm" type="button" data-action="open-split">Customize</button>' +
        "</div>" +
      "</div>" +
      '<p class="form-error" hidden></p>' +
      '<div class="form-actions">' +
        '<button class="btn btn-ghost" type="button" data-action="cancel-expense">Cancel</button>' +
        '<button class="btn btn-primary" type="submit">Add expense</button>' +
      "</div>" +
    "</form>"
  );
}

function toLocalInputValue(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "";
  const pad = (n) => (n < 10 ? "0" : "") + n;
  return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
    "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
}

function expenseEditFormHtml(exp, group) {
  const members = membersCache[group.id] || [];
  const categoryOptions = EXPENSE_CATEGORIES.map((c) =>
    '<option value="' + c + '"' + (c === exp.expn_ctgry ? " selected" : "") + ">" +
      c.replace(/_/g, " ") + "</option>"
  ).join("");
  const contribRows = members.map((m) => {
    const paid = (exp.paid_by || []).find((p) => String(p.user_id) === String(m.id));
    const amt = paid ? Number(paid.amt) : 0;
    return (
      '<div class="contrib-row">' +
        '<span class="contrib-name">' + esc(m.name || m.email || "?") + "</span>" +
        '<input class="contrib-amt" type="number" min="0" step="0.01" value="' +
          (amt ? amt.toFixed(2) : "0.00") + '" data-user-id="' + esc(m.id) +
          '" aria-label="Amount paid by ' + esc(m.name || m.email || "?") + '">' +
      "</div>"
    );
  }).join("");
  const amount = Number(exp.expn_amnt);
  return (
    '<form class="expense-edit-form" data-edit-form novalidate>' +
      '<div class="form-row">' +
        '<div class="field">' +
          "<label>Description</label>" +
          '<input class="exp-desc" type="text" maxlength="200" required value="' + esc(exp.expn_desc) + '">' +
        "</div>" +
      "</div>" +
      '<div class="form-row">' +
        '<div class="field">' +
          "<label>Category</label>" +
          '<select class="exp-category">' + categoryOptions + "</select>" +
        "</div>" +
        '<div class="field field--narrow">' +
          "<label>Date</label>" +
          '<input class="exp-date" type="datetime-local" value="' + toLocalInputValue(exp.date_time) + '">' +
        "</div>" +
      "</div>" +
      '<div class="contrib-block">' +
        '<div class="contrib-head">' +
          "<span>Contributions (" + esc(group.currency) + ")</span>" +
          '<span class="contrib-sum" data-contrib-sum>0.00 / ' + amount.toFixed(2) + "</span>" +
        "</div>" +
        contribRows +
      "</div>" +
      '<p class="form-error" hidden></p>' +
      '<div class="form-actions">' +
        '<button class="btn btn-ghost" type="button" data-modal-cancel>Cancel</button>' +
        '<button class="btn btn-primary" type="submit">Save changes</button>' +
      "</div>" +
    "</form>"
  );
}

async function openExpenseEditModal(transId, groupId) {
  const group = groupsCache.find((g) => String(g.id) === String(groupId));
  const exp = (expensesCache[groupId] || []).find((e) => String(e.trans_id) === String(transId));
  if (!group || !exp) {
    showToast("Expense not found.", "error");
    return;
  }

  openModal("Edit expense #" + exp.trans_id, expenseEditFormHtml(exp, group));
  const root = $("#modal-root");
  const form = root.querySelector("[data-edit-form]");
  const errEl = form.querySelector(".form-error");
  const sumEl = form.querySelector("[data-contrib-sum]");
  const amount = Number(exp.expn_amnt);

  const updateSum = () => {
    let total = 0;
    form.querySelectorAll(".contrib-amt").forEach((input) => {
      total += Math.max(0, parseFloat(input.value) || 0);
    });
    sumEl.textContent = total.toFixed(2) + " / " + amount.toFixed(2);
    sumEl.classList.toggle("contrib-sum--bad", Math.abs(total - amount) > 0.01);
    return total;
  };
  updateSum();
  form.addEventListener("input", (e) => {
    if (e.target.classList.contains("contrib-amt")) updateSum();
  });
  root.querySelector("[data-modal-cancel]").addEventListener("click", closeModal);

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    setError(errEl, "");
    const desc = form.querySelector(".exp-desc").value.trim();
    const category = form.querySelector(".exp-category").value;
    const dateVal = form.querySelector(".exp-date").value;
    if (!desc) { setError(errEl, "Description is required."); return; }

    const paidBy = [];
    let total = 0;
    form.querySelectorAll(".contrib-amt").forEach((input) => {
      const amt = parseFloat(input.value);
      if (!isNaN(amt) && amt > 0) {
        const rounded = Math.round(amt * 100) / 100;
        paidBy.push({ user_id: input.dataset.userId, amt: rounded });
        total += rounded;
      }
    });
    if (!paidBy.length) {
      setError(errEl, "At least one member must have paid something.");
      return;
    }
    if (Math.abs(total - amount) > 0.01) {
      setError(errEl, "Contributions must add up to " + amount.toFixed(2) + ".");
      return;
    }

    const body = { expn_desc: desc, expn_ctgry: category, paid_by: paidBy };
    if (dateVal) body.date_time = new Date(dateVal).toISOString();

    const btn = form.querySelector('button[type="submit"]');
    setBusy(btn, true, "Saving…");
    try {
      await api.patch("/expenses/" + encodeURIComponent(transId), body);
      closeModal();
      showToast("Expense updated.", "success");
      await loadGroupExpenses(groupId);
    } catch (err) {
      setError(errEl, err.message || "Could not update expense.");
    } finally {
      setBusy(btn, false);
    }
  });
}

/* Mirror of main._normalize_splits' EQUAL split: basis points with the
   remainder handed to the first members, so the prefill matches what an
   untouched EQUAL split would produce. */
function equalSplitPercentages(count) {
  const baseBp = Math.floor(10000 / count);
  const remainder = 10000 - baseBp * count;
  const out = [];
  for (let i = 0; i < count; i++) out.push((baseBp + (i < remainder ? 1 : 0)) / 100);
  return out;
}

function splitPopupHtml(members, percentages) {
  const rows = members.map((m, i) =>
    '<div class="contrib-row">' +
      '<span class="contrib-name">' + esc(m.name || m.email || "?") + "</span>" +
      '<input class="split-pct" type="number" min="0" max="100" step="0.01" value="' +
        percentages[i].toFixed(2) + '" data-user-id="' + esc(m.id) +
        '" aria-label="Split percentage for ' + esc(m.name || m.email || "?") + '">' +
    "</div>"
  ).join("");
  return (
    '<form class="expense-edit-form" data-split-form novalidate>' +
      '<div class="contrib-block">' +
        '<div class="contrib-head">' +
          "<span>Split percentages (%)</span>" +
          '<span class="contrib-sum" data-split-sum>100.00 / 100.00</span>' +
        "</div>" +
        rows +
      "</div>" +
      '<p class="form-hint">0% leaves a member out of this expense.</p>' +
      '<p class="form-error" hidden></p>' +
      '<div class="form-actions">' +
        '<button class="btn btn-ghost" type="button" data-split-reset>Reset to equal</button>' +
        '<button class="btn btn-ghost" type="button" data-modal-cancel>Cancel</button>' +
        '<button class="btn btn-primary" type="submit">Use split</button>' +
      "</div>" +
    "</form>"
  );
}

function openSplitPopup(form) {
  const groupId = form.dataset.expenseForm;
  const members = membersCache[groupId] || [];
  if (!members.length) {
    showToast("This group has no members to split between.", "error");
    return;
  }

  const equalPct = equalSplitPercentages(members.length);
  const current = Array.isArray(form._split) ? form._split : null;
  const initial = members.map((m, i) => {
    if (!current) return equalPct[i];
    const hit = current.find((e) => String(e.user_id) === String(m.id));
    return hit ? Number(hit.percentage) : 0;
  });

  openModal("Custom split", splitPopupHtml(members, initial));
  const root = $("#modal-root");
  const popup = root.querySelector("[data-split-form]");
  const errEl = popup.querySelector(".form-error");
  const sumEl = popup.querySelector("[data-split-sum]");

  const updateSum = () => {
    let total = 0;
    popup.querySelectorAll(".split-pct").forEach((input) => {
      total += Math.max(0, parseFloat(input.value) || 0);
    });
    sumEl.textContent = total.toFixed(2) + " / 100.00";
    sumEl.classList.toggle("contrib-sum--bad", Math.abs(total - 100) > 0.01);
    return total;
  };
  updateSum();
  popup.addEventListener("input", (e) => {
    if (e.target.classList.contains("split-pct")) updateSum();
  });

  root.querySelector("[data-split-reset]").addEventListener("click", () => {
    popup.querySelectorAll(".split-pct").forEach((input, i) => {
      input.value = equalPct[i].toFixed(2);
    });
    setError(errEl, "");
    updateSum();
  });
  root.querySelector("[data-modal-cancel]").addEventListener("click", closeModal);

  popup.addEventListener("submit", (e) => {
    e.preventDefault();
    setError(errEl, "");
    const entries = [];
    let total = 0;
    popup.querySelectorAll(".split-pct").forEach((input) => {
      const pct = parseFloat(input.value);
      if (!isNaN(pct) && pct > 0) {
        const rounded = Math.round(pct * 100) / 100;
        entries.push({ user_id: input.dataset.userId, percentage: rounded });
        total += rounded;
      }
    });
    if (!entries.length) {
      setError(errEl, "Give at least one member a share.");
      return;
    }
    if (Math.abs(total - 100) > 0.01) {
      setError(errEl, "Percentages must add up to 100.");
      return;
    }
    form._split = entries;
    const hint = form.querySelector("[data-split-hint]");
    if (hint) hint.textContent = "Custom split across " + entries.length + " member(s)";
    closeModal();
  });
}

function groupCardHtml(group) {
  const meId = (currentUser || Auth.user || {}).id;
  const isOwner = group.created_by_user_id === meId;
  const memberCount = (group.members || []).length;
  const currency = esc(group.currency || "");
  const memberWord = memberCount === 1 ? "member" : "members";

  return (
    '<div class="card group-card" data-group-id="' + esc(group.id) + '">' +
      '<div class="group-card-head">' +
        '<div>' +
          '<div class="group-card-name">' + esc(group.name) + "</div>" +
          '<div class="group-card-meta">' + currency + " &middot; " + memberCount + " " + memberWord +
            ' &middot; <span class="group-status group-status--' + esc(String(group.status || "").toLowerCase()) + '">' +
            esc(group.status || "") + "</span></div>" +
        "</div>" +
        '<div class="group-card-actions">' +
          '<button class="btn btn-ghost btn-sm" data-action="toggle-settle" type="button">Settle up</button>' +
          '<button class="btn btn-ghost btn-sm" data-action="regen" type="button" title="Generate a fresh invite code">New code</button>' +
          (isOwner
            ? '<button class="btn btn-ghost btn-sm btn-danger" data-action="delete" type="button">Delete</button>'
            : '<button class="btn btn-ghost btn-sm" data-action="leave" type="button">Leave</button>') +
        "</div>" +
      "</div>" +
      memberChipsHtml(group) +
      (group.invite_code
        ? '<div class="invite-code-row">' +
            '<span class="invite-code" data-code="' + esc(group.invite_code) + '">' + esc(group.invite_code) + "</span>" +
            '<button class="btn btn-ghost btn-sm" data-action="copy" type="button">' + Icons.copy + " Copy</button>" +
          "</div>"
        : "") +
      '<div class="group-expenses" data-expenses-for="' + esc(group.id) + '">' +
        '<div class="card-subtitle">Expenses</div>' +
        '<div class="expense-list">' + expensesListHtml(group) + "</div>" +
      "</div>" +
      '<div class="group-expense-actions">' +
        '<button class="btn btn-primary btn-sm" data-action="add-expense-nav" type="button">' +
          Icons.plus + " Add expense</button>" +
      "</div>" +
      '<div class="settle-panel" data-settle-for="' + esc(group.id) + '" hidden></div>' +
    "</div>"
  );
}

async function loadGroupExpenses(groupId) {
  const render = (html) => {
    $all('[data-expenses-for="' + groupId + '"]').forEach((container) => {
      const list = container.classList.contains("expense-list")
        ? container
        : container.querySelector(".expense-list");
      if (list) list.innerHTML = html;
    });
  };
  try {
    const expenses = await api.get("/groups/" + encodeURIComponent(groupId) + "/expenses");
    expensesCache[groupId] = expenses;
    const group = groupsCache.find((g) => String(g.id) === String(groupId)) || { id: groupId, currency: "" };
    render(expensesListHtml(group));
  } catch (e) {
    render('<p class="empty empty--sm">Could not load expenses.</p>');
  }
}

/* ------------------------------ Expenses screen ------------------------------ */
let expensesGroupId = "";

function renderExpensesScreen() {
  const select = $("#expenses-group-select");
  const emptyEl = $("#expenses-empty");
  if (!select) return;
  if (!groupsCache.length) {
    expensesGroupId = "";
    select.innerHTML = '<option value="">Select a group&hellip;</option>';
    if (emptyEl) emptyEl.hidden = false;
    applyExpensesGroup("");
    return;
  }
  if (emptyEl) emptyEl.hidden = true;
  if (!groupsCache.some((g) => String(g.id) === String(expensesGroupId))) {
    expensesGroupId = "";
  }
  select.innerHTML = '<option value="">Select a group&hellip;</option>' +
    groupsCache.map((g) =>
      '<option value="' + esc(g.id) + '"' +
      (String(g.id) === String(expensesGroupId) ? " selected" : "") + ">" +
      esc(g.name) + "</option>").join("");
  applyExpensesGroup(expensesGroupId);
}

function applyExpensesGroup(groupId) {
  const block = $("#expenses-add-block");
  const addRow = $("#expenses-add-row");
  const slot = $("#expenses-form-slot");
  const listCard = $("#expenses-list-card");
  if (!block || !slot || !listCard) return;
  const group = groupsCache.find((g) => String(g.id) === String(groupId)) || null;
  if (!group) {
    block.dataset.groupId = "";
    if (addRow) addRow.hidden = true;
    slot.innerHTML = "";
    listCard.hidden = true;
    listCard.dataset.groupId = "";
    listCard.dataset.expensesFor = "";
    return;
  }
  block.dataset.groupId = group.id;
  if (addRow) addRow.hidden = false;
  slot.innerHTML = expenseFormHtml(group);
  const form = slot.querySelector("[data-expense-form]");
  if (form) form.hidden = false;
  listCard.hidden = false;
  listCard.dataset.groupId = group.id;
  listCard.dataset.expensesFor = group.id;
  loadGroupExpenses(group.id);
}

async function selectExpensesGroup(groupId) {
  expensesGroupId = groupId || "";
  const select = $("#expenses-group-select");
  if (select && select.value !== expensesGroupId) select.value = expensesGroupId;
  applyExpensesGroup(expensesGroupId);
}

/* ------------------------------ Settlement ------------------------------ */
const PAYOUT_ESCALATION_DAYS = 7;

function payoutStatusPillHtml(status) {
  return (
    '<span class="payout-status payout-status--' + esc(String(status || "").toLowerCase()) + '">' +
    esc(status || "?") + "</span>"
  );
}

function isInvolvedInExpense(exp, userId) {
  const id = String(userId);
  const inSplit = (exp.expn_splt || []).some((s) => String(s.user_id) === id);
  const inPaid = (exp.paid_by || []).some((p) => String(p.user_id) === id);
  return inSplit || inPaid;
}

function payoutActionsHtml(payout, group) {
  const meId = String((currentUser || Auth.user || {}).id || "");
  const isPayor = String(payout.payor_id) === meId;
  const isPayee = String(payout.payee_id) === meId;
  const pid = esc(payout.id);
  const buttons = [];

  if (payout.status === "PENDING") {
    if (isPayor) buttons.push('<button class="btn btn-primary btn-sm" data-action="payout-paid" data-payout-id="' + pid + '" type="button">Mark paid</button>');
    if (isPayee) buttons.push('<button class="btn btn-primary btn-sm" data-action="payout-settled" data-payout-id="' + pid + '" type="button">Confirm</button>');
    if (isPayee) buttons.push('<button class="btn btn-ghost btn-sm" data-action="payout-remind" data-payout-id="' + pid + '" type="button">Remind</button>');
    if (isPayor || isPayee) buttons.push('<button class="btn btn-ghost btn-sm btn-danger" data-action="payout-cancel" data-payout-id="' + pid + '" type="button">Cancel</button>');
  } else if (payout.status === "PAID") {
    if (isPayee) buttons.push('<button class="btn btn-primary btn-sm" data-action="payout-settled" data-payout-id="' + pid + '" type="button">Confirm</button>');
    if (isPayor) buttons.push('<button class="btn btn-ghost btn-sm" data-action="payout-remind" data-payout-id="' + pid + '" type="button">Remind</button>');
    if (isPayor || isPayee) buttons.push('<button class="btn btn-ghost btn-sm btn-danger" data-action="payout-failed" data-payout-id="' + pid + '" type="button">Fail</button>');
    const paidAt = payout.paid_at ? new Date(payout.paid_at) : null;
    if (paidAt && Date.now() - paidAt.getTime() > PAYOUT_ESCALATION_DAYS * 86400000) {
      buttons.push('<button class="btn btn-ghost btn-sm" data-action="payout-escalate" data-payout-id="' + pid + '" type="button">Escalate</button>');
    }
  }
  if (payout.is_adhoc && !(payout.expense_ids || []).length && (isPayor || isPayee)) {
    buttons.push('<button class="btn btn-ghost btn-sm" data-action="toggle-link-expenses" data-payout-id="' + pid + '" type="button">Link expenses</button>');
  }
  return buttons.length ? '<div class="payout-row-actions">' + buttons.join("") + "</div>" : "";
}

function linkExpensesFormHtml(payout, group) {
  const expenses = (expensesCache[group.id] || []).filter(function (exp) {
    return isInvolvedInExpense(exp, payout.payor_id) && isInvolvedInExpense(exp, payout.payee_id);
  });
  if (!expenses.length) return "";
  const options = expenses.map(function (exp) {
    return '<option value="' + esc(exp.trans_id) + '">' +
      esc("#" + exp.trans_id + " " + exp.expn_desc) + " · " + esc(formatAmount(exp.expn_amnt, group.currency)) + "</option>";
  }).join("");
  return (
    '<form class="payout-link-form" data-link-form="' + esc(payout.id) + '" data-link-group="' + esc(group.id) + '" hidden novalidate>' +
      '<div class="field"><label>Link expenses (both of you must be on them)</label>' +
        '<select class="link-expenses-select" multiple size="3">' + options + "</select></div>" +
      '<p class="form-error" hidden></p>' +
      '<div class="form-actions">' +
        '<button class="btn btn-ghost btn-sm" type="button" data-action="cancel-link-expenses">Cancel</button>' +
        '<button class="btn btn-primary btn-sm" type="submit">Link</button>' +
      "</div>" +
    "</form>"
  );
}

function payoutRowHtml(payout, group, interactive) {
  const payor = memberName(group.id, payout.payor_id);
  const payee = memberName(group.id, payout.payee_id);
  const meta = [];
  if ((payout.expense_ids || []).length) meta.push("expenses #" + payout.expense_ids.join(", #"));
  if (payout.is_adhoc) meta.push("ad-hoc");
  if (payout.status_reason) meta.push(esc(payout.status_reason));
  return (
    '<div class="payout-row" data-payout-row="' + esc(payout.id) + '">' +
      '<div class="payout-row-main">' +
        payoutStatusPillHtml(payout.status) +
        '<span class="payout-route">' + esc(payor) + " &rarr; " + esc(payee) + "</span>" +
        '<span class="payout-amt">' + esc(formatAmount(payout.amt, group.currency)) + "</span>" +
      "</div>" +
      (meta.length ? '<div class="payout-row-meta">' + meta.join(" · ") + "</div>" : "") +
      (interactive ? payoutActionsHtml(payout, group) : "") +
      (interactive ? linkExpensesFormHtml(payout, group) : "") +
    "</div>"
  );
}

function payoutSectionHtml(title, payouts, group, interactive) {
  if (!payouts || !payouts.length) return "";
  return (
    "<div>" +
      '<div class="settle-section-title">' + title + "</div>" +
      payouts.map((p) => payoutRowHtml(p, group, interactive)).join("") +
    "</div>"
  );
}

function adhocFormHtml(group) {
  const members = membersCache[group.id] || [];
  const meId = String((currentUser || Auth.user || {}).id || "");
  const memberOptions = function (selectedId) {
    return members.map(function (m) {
      const sel = String(m.id) === selectedId ? " selected" : "";
      return '<option value="' + esc(m.id) + '"' + sel + ">" + esc(m.name || m.email) + "</option>";
    }).join("");
  };
  const other = members.find((m) => String(m.id) !== meId) || members[0] || {};
  const expenseOptions = (expensesCache[group.id] || []).map(function (exp) {
    const involved = [];
    (exp.expn_splt || []).forEach(function (s) { involved.push(String(s.user_id)); });
    (exp.paid_by || []).forEach(function (p) { involved.push(String(p.user_id)); });
    return '<option value="' + esc(exp.trans_id) + '" data-involved="' + esc(involved.join(",")) + '">' +
      esc("#" + exp.trans_id + " " + exp.expn_desc) + " · " + esc(formatAmount(exp.expn_amnt, group.currency)) + "</option>";
  }).join("");
  return (
    '<form class="adhoc-form" data-adhoc-form="' + esc(group.id) + '" hidden novalidate>' +
      '<div class="card-title">Record a payment</div>' +
      '<div class="form-row">' +
        '<div class="field"><label>Paid by</label><select class="adhoc-payor">' + memberOptions(meId) + "</select></div>" +
        '<div class="field"><label>Received by</label><select class="adhoc-payee">' + memberOptions(String(other.id || "")) + "</select></div>" +
        '<div class="field field--narrow"><label>Amount (' + esc(group.currency) + ')</label>' +
          '<input class="adhoc-amt" type="number" min="0.01" step="0.01" placeholder="0.00"></div>' +
      "</div>" +
      (expenseOptions
        ? '<div class="field"><label>Link to expenses (optional &mdash; both of you must be on them)</label>' +
          '<select class="adhoc-expenses" multiple size="3">' + expenseOptions + "</select></div>"
        : "") +
      '<p class="form-error" hidden></p>' +
      '<div class="form-actions">' +
        '<button class="btn btn-ghost" type="button" data-action="cancel-adhoc">Cancel</button>' +
        '<button class="btn btn-primary" type="submit">Record payment</button>' +
      "</div>" +
    "</form>"
  );
}

function settlePanelHtml(group, preview) {
  const meId = String((currentUser || Auth.user || {}).id || "");
  const isOwner = group.created_by_user_id === meId;
  const status = preview.group_status;
  const canRun = status === "OPEN" || status === "LOCKED";
  const canFinal = isOwner && (status === "LOCKED" || status === "PARTIAL_SETTLEMENT");
  const dues = preview.net_dues || {};
  const duesRows = Object.keys(dues).map(function (uid) {
    const amt = dues[uid];
    if (!amt) return "";
    const name = uid === meId ? "You" : memberName(group.id, uid);
    const label = amt > 0 ? "is owed" : "owes";
    return (
      '<div class="net-dues-row">' +
        "<span>" + esc(name) + " " + label + "</span>" +
        '<span class="net-dues-amt">' + esc(formatAmount(Math.abs(amt), group.currency)) + "</span>" +
      "</div>"
    );
  }).join("");

  return (
    '<div class="settle-head">' +
      '<div class="settle-section-title">Settlement <span class="group-status group-status--' + esc(String(status || "").toLowerCase()) + '">' + esc(status) + "</span></div>" +
      '<div class="settle-actions">' +
        (canRun
          ? '<button class="btn btn-primary btn-sm" data-action="settle-me" type="button">Settle my dues</button>' +
            '<button class="btn btn-ghost btn-sm" data-action="settle-periodic" type="button">Start periodic settle</button>'
          : "") +
        (canFinal
          ? '<button class="btn btn-ghost btn-sm" data-action="settle-final" type="button">Final settle</button>'
          : "") +
        '<button class="btn btn-ghost btn-sm" data-action="toggle-adhoc" type="button">Record payment</button>' +
      "</div>" +
    "</div>" +
    "<div>" +
      '<div class="settle-section-title">Net dues</div>' +
      (duesRows || '<p class="empty empty--sm">No outstanding dues.</p>') +
    "</div>" +
    payoutSectionHtml("Open payouts", preview.open_payouts, group, true) +
    payoutSectionHtml("Proposed payouts", preview.proposed_payouts, group, false) +
    payoutSectionHtml("Orphan payments", preview.orphan_payouts, group, true) +
    adhocFormHtml(group)
  );
}

async function loadSettlePanel(groupId) {
  const panel = document.querySelector('[data-settle-for="' + groupId + '"]');
  if (!panel) return;
  try {
    const preview = await api.get("/groups/" + encodeURIComponent(groupId) + "/settle");
    settleCache[groupId] = preview;
    const group = groupsCache.find((g) => g.id === groupId) || { id: groupId, currency: "" };
    panel.innerHTML = settlePanelHtml(group, preview);
  } catch (e) {
    panel.innerHTML = '<p class="empty empty--sm">Could not load settlement.</p>';
  }
}

async function loadGroups() {
  const listEl = $("#groups-list");
  const selectEl = $("#invite-group");
  try {
    const groups = await api.get("/groups");
    groupsCache = groups;
    settleCache = {};
    await Promise.all(groups.map(async (g) => {
      try {
        membersCache[g.id] = await api.get("/groups/" + encodeURIComponent(g.id) + "/members");
      } catch (e) {
        membersCache[g.id] = (g.members || []).map((id) => ({ id: id, name: id, email: "" }));
      }
      try {
        expensesCache[g.id] = await api.get("/groups/" + encodeURIComponent(g.id) + "/expenses");
      } catch (e) {
        expensesCache[g.id] = [];
      }
    }));
    if (!groups.length) {
      listEl.innerHTML = '<p class="empty">No groups yet — create one or join with an invite code.</p>';
    } else {
      listEl.innerHTML = groups.map(groupCardHtml).join("");
    }
    selectEl.innerHTML = '<option value="">Select a group&hellip;</option>' +
      groups.map((g) => '<option value="' + esc(g.id) + '">' + esc(g.name) + "</option>").join("");
  } catch (err) {
    listEl.innerHTML = '<p class="empty">Could not load groups: ' + esc(err.message) + "</p>";
  }
}

function bindGroupActions() {
  /* Toggle the create-group form */
  $("#btn-new-group").addEventListener("click", () => {
    const form = $("#form-create-group");
    form.hidden = !form.hidden;
    if (!form.hidden) $("#new-group-name").focus();
  });
  $("#btn-cancel-create-group").addEventListener("click", () => {
    $("#form-create-group").hidden = true;
    setError($("#create-group-error"), "");
  });

  /* Create group */
  $("#form-create-group").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#create-group-error");
    const btn = $("#create-group-submit");
    setError(errEl, "");
    const name = $("#new-group-name").value.trim();
    const currency = $("#new-group-currency").value;
    if (!name) { setError(errEl, "Group name cannot be blank."); return; }

    setBusy(btn, true, "Creating…");
    try {
      await api.post("/groups", { name: name, currency: currency });
      $("#form-create-group").hidden = true;
      $("#new-group-name").value = "";
      showToast("Group created.", "success");
      await loadGroups();
      await refreshGroupCount();
    } catch (err) {
      setError(errEl, err.message || "Could not create group.");
    } finally {
      setBusy(btn, false);
    }
  });

  /* Join by group id + invite code */
  $("#form-join-group").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#join-error");
    const btn = $("#join-submit");
    setError(errEl, "");
    const groupId = $("#join-group-id").value.trim();
    const code = $("#join-code").value.trim().toUpperCase();
    if (!groupId || !code) { setError(errEl, "Group ID and invite code are both required."); return; }

    setBusy(btn, true, "Joining…");
    try {
      await api.post("/groups/" + encodeURIComponent(groupId) + "/invite/accept", { invite_code: code });
      $("#join-group-id").value = "";
      $("#join-code").value = "";
      showToast("Joined group!", "success");
      await loadGroups();
      await refreshGroupCount();
    } catch (err) {
      setError(errEl, err.message || "Could not join group.");
    } finally {
      setBusy(btn, false);
    }
  });

  /* Invite by email -> creates a pending invitation the invitee must accept */
  $("#form-invite-email").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#invite-error");
    const btn = $("#invite-submit");
    setError(errEl, "");
    const groupId = $("#invite-group").value;
    const email = $("#invite-email").value.trim();
    if (!groupId) { setError(errEl, "Pick a group first."); return; }
    if (!email || !email.includes("@")) { setError(errEl, "Enter a valid email address."); return; }

    setBusy(btn, true, "Inviting…");
    try {
      await api.post("/groups/" + encodeURIComponent(groupId) + "/invitations", { email: email });
      $("#invite-email").value = "";
      showToast("Invitation sent to " + email + ".", "success");
    } catch (err) {
      setError(errEl, err.message || "Could not send invitation.");
    } finally {
      setBusy(btn, false);
    }
  });

  /* Delegated actions on the group list: copy / regen / delete / leave.
     Expense actions (rows, form toggle, split popup) are delegated on
     #main instead (see below). */
  $("#groups-list").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const card = btn.closest("[data-group-id]");
    if (!card) return;
    const groupId = card.dataset.groupId;
    const action = btn.dataset.action;

    if (action === "toggle-settle") {
      const panel = card.querySelector("[data-settle-for]");
      if (!panel) return;
      panel.hidden = !panel.hidden;
      if (!panel.hidden) await loadSettlePanel(groupId);
      return;
    }
    if (action === "toggle-adhoc") {
      const form = card.querySelector("[data-adhoc-form]");
      if (form) {
        form.hidden = !form.hidden;
        if (!form.hidden) {
          const amt = form.querySelector(".adhoc-amt");
          if (amt) amt.focus();
        }
      }
      return;
    }
    if (action === "cancel-adhoc") {
      const form = card.querySelector("[data-adhoc-form]");
      if (form) {
        form.hidden = true;
        setError(form.querySelector(".form-error"), "");
      }
      return;
    }
    if (action === "toggle-link-expenses") {
      const pid = btn.dataset.payoutId;
      const form = card.querySelector('[data-link-form="' + pid + '"]');
      if (form) form.hidden = !form.hidden;
      return;
    }
    if (action === "cancel-link-expenses") {
      const form = btn.closest("[data-link-form]");
      if (form) {
        form.hidden = true;
        setError(form.querySelector(".form-error"), "");
      }
      return;
    }
    if (action === "settle-me" || action === "settle-periodic" || action === "settle-final") {
      setBusy(btn, true);
      try {
        if (action === "settle-me") {
          const payouts = await api.post("/groups/" + encodeURIComponent(groupId) + "/settle/me", {});
          showToast(payouts.length ? "Issued " + payouts.length + " payout(s)." : "Nothing new to settle.", "success");
        } else if (action === "settle-periodic") {
          const payouts = await api.post("/groups/" + encodeURIComponent(groupId) + "/settle", { kind: "PERIODIC" });
          showToast(payouts.length ? "Issued " + payouts.length + " payout(s)." : "Nothing new to settle.", "success");
        } else {
          if (!window.confirm("Run FINAL settlement? The group will be closed for good once every payout settles.")) return;
          const payouts = await api.post("/groups/" + encodeURIComponent(groupId) + "/settle", { kind: "FINAL" });
          showToast("Final settlement started — " + payouts.length + " payout(s).", "success");
          await loadGroups();
          await refreshGroupCount();
        }
        await loadSettlePanel(groupId);
      } catch (err) {
        showToast(err.message || "Settlement failed.", "error");
      } finally {
        setBusy(btn, false);
      }
      return;
    }
    if (action === "payout-paid" || action === "payout-settled" || action === "payout-remind" ||
        action === "payout-failed" || action === "payout-cancel" || action === "payout-escalate") {
      const pid = btn.dataset.payoutId;
      const base = "/groups/" + encodeURIComponent(groupId) + "/payouts/" + encodeURIComponent(pid);
      let body = {};
      if (action === "payout-failed" || action === "payout-cancel") {
        const reason = window.prompt(action === "payout-failed" ? "Why did this payout fail?" : "Why cancel this payout?");
        if (reason === null) return;
        if (!reason.trim()) { showToast("A reason is required.", "error"); return; }
        body = { reason: reason.trim() };
      }
      const suffix = { "payout-paid": "paid", "payout-settled": "settled", "payout-remind": "remind",
        "payout-failed": "failed", "payout-cancel": "cancel", "payout-escalate": "escalate" }[action];
      setBusy(btn, true);
      try {
        await api.post(base + "/" + suffix, body);
        showToast("Payout updated.", "success");
        await loadSettlePanel(groupId);
      } catch (err) {
        showToast(err.message || "Action failed.", "error");
      } finally {
        setBusy(btn, false);
      }
      return;
    }

    try {
      if (action === "copy") {
        const code = card.querySelector(".invite-code");
        const value = code ? code.dataset.code : "";
        if (!value) return;
        try {
          await navigator.clipboard.writeText(value);
          showToast("Invite code copied.", "success");
        } catch (clipErr) {
          showToast("Copy failed — code is " + value, "error");
        }
      } else if (action === "regen") {
        setBusy(btn, true);
        const res = await api.post("/groups/" + encodeURIComponent(groupId) + "/invite");
        const fresh = res && res.invite_code;
        const codeEl = card.querySelector(".invite-code");
        if (codeEl && fresh) {
          codeEl.textContent = fresh;
          codeEl.dataset.code = fresh;
        }
        showToast("New invite code generated.", "success");
      } else if (action === "delete") {
        if (!window.confirm("Delete this group? This cannot be undone.")) return;
        setBusy(btn, true);
        await api.del("/groups/" + encodeURIComponent(groupId));
        showToast("Group deleted.", "success");
        await loadGroups();
        await refreshGroupCount();
      } else if (action === "leave") {
        if (!window.confirm("Leave this group?")) return;
        setBusy(btn, true);
        await api.del("/groups/" + encodeURIComponent(groupId) + "/members/me");
        showToast("You left the group.", "success");
        await loadGroups();
        await refreshGroupCount();
      }
    } catch (err) {
      showToast(err.message || "Action failed.", "error");
    } finally {
      setBusy(btn, false);
    }
  });

  /* Delegated expense actions — on #main so they work from both the groups
     view and the dedicated expenses screen without firing twice through
     the groups-list listener: edit/delete rows, split popup, add-expense
     form toggle, and the "Add expense" navigation from a group card. */
  $("#main").addEventListener("click", async (e) => {
    const btn = e.target.closest(
      '[data-action="edit-expense"], [data-action="delete-expense"], [data-action="open-split"],' +
      ' [data-action="toggle-expense"], [data-action="cancel-expense"], [data-action="add-expense-nav"]'
    );
    if (!btn) return;
    const action = btn.dataset.action;

    if (action === "open-split") {
      const expForm = btn.closest("[data-expense-form]");
      if (expForm) openSplitPopup(expForm);
      return;
    }
    if (action === "add-expense-nav") {
      const navCard = btn.closest("[data-group-id]");
      if (!navCard) return;
      expensesGroupId = navCard.dataset.groupId;
      navigate("expenses");
      return;
    }
    if (action === "toggle-expense" || action === "cancel-expense") {
      const formCard = btn.closest("[data-group-id]");
      if (!formCard) return;
      const form = formCard.querySelector("[data-expense-form]");
      if (!form) return;
      if (action === "cancel-expense") {
        form.hidden = true;
        setError(form.querySelector(".form-error"), "");
      } else {
        form.hidden = !form.hidden;
        if (!form.hidden) {
          const desc = form.querySelector(".exp-desc");
          if (desc) desc.focus();
        }
      }
      return;
    }

    const row = btn.closest("[data-trans-id]");
    if (!row) return;
    const transId = row.dataset.transId;
    const groupId = row.dataset.groupId;
    if (!groupId) return;

    if (action === "edit-expense") {
      openExpenseEditModal(transId, groupId);
      return;
    }
    if (action === "delete-expense") {
      if (!window.confirm("Delete expense #" + transId + "?")) return;
      setBusy(btn, true);
      try {
        await api.del("/expenses/" + encodeURIComponent(transId));
        showToast("Expense deleted.", "success");
        await loadGroupExpenses(groupId);
      } catch (err) {
        showToast(err.message || "Could not delete expense.", "error");
      } finally {
        setBusy(btn, false);
      }
    }
  });

  /* Delegated submit for the per-card ad-hoc payment form. */
  $("#groups-list").addEventListener("submit", async (e) => {
    const form = e.target.closest("[data-adhoc-form]");
    if (!form) return;
    e.preventDefault();
    const groupId = form.dataset.adhocForm;
    const errEl = form.querySelector(".form-error");
    const btn = form.querySelector('button[type="submit"]');
    setError(errEl, "");

    const payor = form.querySelector(".adhoc-payor").value;
    const payee = form.querySelector(".adhoc-payee").value;
    const amount = parseFloat(form.querySelector(".adhoc-amt").value);
    const expensesSel = form.querySelector(".adhoc-expenses");
    const expenseIds = expensesSel
      ? Array.from(expensesSel.selectedOptions).map((o) => parseInt(o.value, 10))
      : [];

    if (!payor || !payee) { setError(errEl, "Payor and payee are both required."); return; }
    if (payor === payee) { setError(errEl, "Payor and payee must be different."); return; }
    if (!(amount > 0)) { setError(errEl, "Amount must be a positive number."); return; }

    setBusy(btn, true, "Saving…");
    try {
      await api.post("/groups/" + encodeURIComponent(groupId) + "/payments", {
        payor_id: payor,
        payee_id: payee,
        amt: amount,
        expense_ids: expenseIds
      });
      showToast("Payment recorded.", "success");
      form.reset();
      form.hidden = true;
      await loadSettlePanel(groupId);
    } catch (err) {
      setError(errEl, err.message || "Could not record payment.");
    } finally {
      setBusy(btn, false);
    }
  });

  /* Delegated submit for linking expenses to an orphan payout. */
  $("#groups-list").addEventListener("submit", async (e) => {
    const form = e.target.closest("[data-link-form]");
    if (!form) return;
    e.preventDefault();
    const payoutId = form.dataset.linkForm;
    const groupId = form.dataset.linkGroup;
    const errEl = form.querySelector(".form-error");
    const btn = form.querySelector('button[type="submit"]');
    setError(errEl, "");

    const sel = form.querySelector(".link-expenses-select");
    const expenseIds = Array.from(sel.selectedOptions).map((o) => parseInt(o.value, 10));
    if (!expenseIds.length) { setError(errEl, "Select at least one expense."); return; }

    setBusy(btn, true, "Linking…");
    try {
      await api.post(
        "/groups/" + encodeURIComponent(groupId) + "/payouts/" + encodeURIComponent(payoutId) + "/expenses",
        { expense_ids: expenseIds }
      );
      showToast("Expenses linked.", "success");
      form.hidden = true;
      await loadSettlePanel(groupId);
    } catch (err) {
      setError(errEl, err.message || "Could not link expenses.");
    } finally {
      setBusy(btn, false);
    }
  });

  /* Delegated change: filter the ad-hoc expense multi-select so only
     expenses involving BOTH the selected payor and payee stay visible. */
  $("#groups-list").addEventListener("change", (e) => {
    const form = e.target.closest("[data-adhoc-form]");
    if (!form) return;
    if (!e.target.classList.contains("adhoc-payor") && !e.target.classList.contains("adhoc-payee")) return;
    const payor = form.querySelector(".adhoc-payor").value;
    const payee = form.querySelector(".adhoc-payee").value;
    const sel = form.querySelector(".adhoc-expenses");
    if (!sel) return;
    Array.from(sel.options).forEach(function (opt) {
      const involved = (opt.dataset.involved || "").split(",");
      const keep = involved.indexOf(payor) !== -1 && involved.indexOf(payee) !== -1;
      opt.hidden = !keep;
      if (!keep) opt.selected = false;
    });
  });
}

/* ------------------------------ Expenses screen bindings ------------------------------ */
function bindExpensesScreen() {
  const select = $("#expenses-group-select");
  if (select) {
    select.addEventListener("change", (e) => selectExpensesGroup(e.target.value));
  }

  /* Delegated submit for the add-expense form (EQUAL split among all
     members, single payer — or the custom PERCENTAGE split from the
     popup). Lives on the expenses screen: the form only exists there. */
  $("#view-expenses").addEventListener("submit", async (e) => {
    const form = e.target.closest("[data-expense-form]");
    if (!form) return;
    e.preventDefault();
    const groupId = form.dataset.expenseForm;
    const errEl = form.querySelector(".form-error");
    const btn = form.querySelector('button[type="submit"]');
    setError(errEl, "");

    const desc = form.querySelector(".exp-desc").value.trim();
    const amount = parseFloat(form.querySelector(".exp-amount").value);
    const category = form.querySelector(".exp-category").value;
    const payer = form.querySelector(".exp-payer").value;

    if (!desc) { setError(errEl, "Description is required."); return; }
    if (!(amount > 0)) { setError(errEl, "Amount must be a positive number."); return; }

    const memberIds = (membersCache[groupId] || []).map((m) => m.id);
    if (!memberIds.length) {
      setError(errEl, "This group has no members to split between.");
      return;
    }

    const split = Array.isArray(form._split) && form._split.length ? form._split : null;
    if (split) {
      const splitTotal = split.reduce((sum, entry) => sum + Number(entry.percentage || 0), 0);
      if (Math.abs(splitTotal - 100) > 0.01) {
        setError(errEl, "Custom split must add up to 100%.");
        return;
      }
    }

    setBusy(btn, true, "Saving…");
    try {
      const body = {
        group_id: groupId,
        expn_ctgry: category,
        expn_desc: desc,
        expn_amnt: amount,
        paid_by_user_id: payer
      };
      if (split) {
        body.split_mode = "PERCENTAGE";
        body.expn_splt = split;
      } else {
        body.split_mode = "EQUAL";
        body.split_user_ids = memberIds;
      }
      await api.post("/expenses", body);
      showToast("Expense added.", "success");
      form.reset();
      form._split = null;
      const hint = form.querySelector("[data-split-hint]");
      if (hint) hint.textContent = "Equally among all " + memberIds.length + " member(s)";
      form.hidden = true;
      await loadGroupExpenses(groupId);
    } catch (err) {
      setError(errEl, err.message || "Could not add expense.");
    } finally {
      setBusy(btn, false);
    }
  });
}

/* ------------------------------ Invitations ------------------------------ */
function invitationCardHtml(inv) {
  const expires = inv.expires_at ? " &middot; expires " + esc(formatDate(inv.expires_at)) : "";
  return (
    '<div class="group-card" data-invitation-id="' + esc(inv.id) + '">' +
      '<div class="group-card-head">' +
        "<div>" +
          '<div class="group-card-name">Group invitation</div>' +
          '<div class="group-card-meta">You were invited to join a group' + expires + "</div>" +
        "</div>" +
        '<div class="group-card-actions">' +
          '<button class="btn btn-primary btn-sm" data-action="accept" type="button">Accept</button>' +
          '<button class="btn btn-ghost btn-sm" data-action="decline" type="button">Decline</button>' +
        "</div>" +
      "</div>" +
    "</div>"
  );
}

async function loadInvitations() {
  const listEl = $("#invites-list");
  if (!listEl) return [];
  try {
    const invites = await api.get("/users/me/invitations");
    listEl.innerHTML = invites.length
      ? invites.map(invitationCardHtml).join("")
      : '<p class="empty">No pending invitations.</p>';
    return invites;
  } catch (err) {
    listEl.innerHTML = '<p class="empty">Could not load invitations: ' + esc(err.message) + "</p>";
    return [];
  }
}

async function refreshInviteCount() {
  try {
    const invites = await api.get("/users/me/invitations");
    $("#stat-invites").textContent = String(invites.length);
  } catch (e) { /* keep placeholder */ }
}

function bindInvitationActions() {
  $("#invites-list").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const card = btn.closest("[data-invitation-id]");
    if (!card) return;
    const id = card.dataset.invitationId;
    const action = btn.dataset.action;

    setBusy(btn, true);
    try {
      if (action === "accept") {
        await api.post("/invitations/" + encodeURIComponent(id) + "/accept");
        showToast("Invitation accepted.", "success");
      } else if (action === "decline") {
        await api.post("/invitations/" + encodeURIComponent(id) + "/decline");
        showToast("Invitation declined.");
      }
      await loadInvitations();
      await refreshInviteCount();
      await refreshGroupCount();
      await loadNotifications();
    } catch (err) {
      showToast(err.message || "Action failed.", "error");
    } finally {
      setBusy(btn, false);
    }
  });
}

/* ------------------------------ Notifications ------------------------------ */
const NOTIFICATION_LABELS = {
  GROUP_INVITE_ACCEPTED: "accepted your group invitation",
  GROUP_INVITE_DECLINED: "declined your group invitation",
  GROUP_SETTLED: "settled a group",
  ADHOC_PAYMENT: "recorded a payment",
  PAYOUT_ISSUED: "issued a payout",
  PAYOUT_PAID: "marked a payout paid",
  PAYOUT_SETTLED: "settled a payout",
  PAYOUT_FAILED: "had a payout fail",
  PAYOUT_CANCELLED: "cancelled a payout",
  PAYOUT_REMINDER: "sent a payout reminder",
  PAYOUT_ESCALATED: "escalated a payout",
};

function notificationText(note) {
  if (note.message) return note.message;
  const label = NOTIFICATION_LABELS[note.kind];
  return label ? "Someone " + label + "." : "New notification";
}

function renderNotifications(notes) {
  const listEl = $("#notif-list");
  if (!listEl) return;
  if (!notes.length) {
    listEl.innerHTML = '<p class="empty">No notifications yet.</p>';
    return;
  }
  listEl.innerHTML = notes
    .map(function (n) {
      return (
        '<div class="notif-item' + (n.is_read ? "" : " notif-item--unread") + '">' +
          '<div class="notif-item-title">' + esc(notificationText(n)) + "</div>" +
          '<span class="notif-item-time">' + esc(formatDate(n.created_at)) + "</span>" +
        "</div>"
      );
    })
    .join("");
}

function setBellBadge(count) {
  const bell = $("#btn-bell");
  if (!bell) return;
  if (count > 0) {
    bell.classList.add("icon-btn--badge");
    bell.dataset.count = String(count);
  } else {
    bell.classList.remove("icon-btn--badge");
    delete bell.dataset.count;
  }
}

async function loadNotifications() {
  try {
    const notes = await api.get("/users/me/notifications");
    renderNotifications(notes);
    setBellBadge(notes.filter((n) => !n.is_read).length);
    return notes;
  } catch (e) {
    setBellBadge(0);
    return [];
  }
}

async function markNotificationsRead() {
  try {
    await api.post("/users/me/notifications/read");
    await loadNotifications();
  } catch (err) {
    showToast(err.message || "Could not update notifications.", "error");
  }
}

function toggleNotifPanel(force) {
  const panel = $("#notif-panel");
  if (!panel) return;
  const show = typeof force === "boolean" ? force : panel.hidden;
  panel.hidden = !show;
  if (show) loadNotifications();
}

/* ------------------------------ Boot ------------------------------ */
async function boot() {
  $("#btn-new-group-icon").innerHTML = Icons.plus;
  $("#join-icon").innerHTML = Icons.key;
  $("#invite-icon").innerHTML = Icons.mail;
  $("#magic-icon").innerHTML = Icons.mail;
  $("#btn-bell").innerHTML = Icons.bell;
  $("#btn-logout").innerHTML = Icons.logout;
  const expensesAddIcon = $("#expenses-add-icon");
  if (expensesAddIcon) expensesAddIcon.innerHTML = Icons.plus;

  renderNav();
  bindAuthForms();
  bindDashboard();
  bindGroupActions();
  bindExpensesScreen();
  bindInvitationActions();
  bindSubscriptionActions();

  $("#btn-logout").addEventListener("click", logout);
  $("#btn-logout-settings").addEventListener("click", logout);
  $("#btn-bell").addEventListener("click", (e) => {
    e.stopPropagation();
    toggleNotifPanel();
  });
  $("#notif-mark-read").addEventListener("click", markNotificationsRead);
  document.addEventListener("click", (e) => {
    const panel = $("#notif-panel");
    if (!panel || panel.hidden) return;
    if (panel.contains(e.target) || e.target.closest("#btn-bell")) return;
    panel.hidden = true;
  });
  window.addEventListener("hashchange", router);
  window.addEventListener("auth:expired", onExpired);
  window.addEventListener("subscription:required", onSubscriptionRequired);

  if (Auth.access) {
    try {
      currentUser = await api.get("/users/me");
      Auth.saveUser(currentUser);
      loadNotifications();
    } catch (err) {
      if (err && err.status === 0 && Auth.user) currentUser = Auth.user;
      else Auth.clear();
    }
  }

  router();
}

document.addEventListener("DOMContentLoaded", boot);
