/* Hash router, navigation rendering and asynchronous view sequencing. */

import { Icons } from "../icons.js";
import { Auth } from "../api.js";
import { ensureViewMounted } from "./view-loader.js";

export const NAV_ITEMS = [
  { route: "dashboard", label: "Dashboard", icon: Icons.dashboard },
  { route: "groups", label: "Groups", icon: Icons.groups },
  { route: "expenses", label: "Expenses", icon: Icons.card },
  { route: "activity", label: "Activity", icon: Icons.clock },
  { route: "settings", label: "Settings", icon: Icons.settings }
];

export const ROUTE_TITLES = {
  login: "Log in",
  signup: "Sign up",
  magic: "Signing you in",
  dashboard: "Dashboard",
  groups: "Groups",
  expenses: "Expenses",
  activity: "Activity",
  settings: "Settings",
  subscription: "Subscription"
};

const PROTECTED_ROUTES = new Set([
  "dashboard", "groups", "expenses", "activity", "settings", "subscription"
]);

const routeFeatures = new Map();
const mountedFeatures = new Set();
let magicHandler = null;
let navigationSequence = 0;
let routerStarted = false;

export function registerRoute(route, feature) {
  routeFeatures.set(route, feature || {});
}

export function setMagicHandler(handler) {
  magicHandler = handler;
}

export function currentRoute() {
  const raw = location.hash.replace(/^#\/?/, "").split("?")[0];
  return raw || "login";
}

export function navigate(route) {
  const target = "#/" + route;
  if (location.hash === target) router();
  else location.hash = target;
}

function navItemHtml(item) {
  return (
    '<a class="nav-item" href="#/' + item.route + '" data-route="' + item.route + '"' +
    ' title="' + esc(item.label) + '">' + item.icon +
    '<span class="nav-label">' + esc(item.label) + "</span></a>"
  );
}

function esc(value) {
  return String(value == null ? "" : value).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[c]);
}

export function renderNav() {
  const items = NAV_ITEMS.map(navItemHtml).join("");
  const sidebar = document.getElementById("sidebar");
  const topnav = document.getElementById("topnav");
  const tabbar = document.getElementById("tabbar");
  if (sidebar) {
    sidebar.innerHTML =
      items + '<span class="nav-spacer"></span>' +
      '<span class="nav-foot" id="nav-foot-email"></span>';
  }
  if (topnav) topnav.innerHTML = items;
  if (tabbar) tabbar.innerHTML = items;
}

function setActiveNav(route) {
  document.querySelectorAll("[data-route]").forEach((el) => {
    el.classList.toggle("is-active", el.dataset.route === route);
  });
}

function showView(route, isAuth) {
  document.querySelectorAll(".view").forEach((view) => {
    view.hidden = view.id !== "view-" + route;
  });

  const shell = document.getElementById("app-shell");
  if (shell) shell.hidden = isAuth;

  const authContainer = document.getElementById("auth-view-container");
  if (authContainer) authContainer.hidden = !isAuth;

  const viewContainer = document.getElementById("view-container");
  if (viewContainer) viewContainer.hidden = isAuth;
}

function mountFeature(route, root) {
  if (mountedFeatures.has(route)) return;
  mountedFeatures.add(route);
  const feature = routeFeatures.get(route);
  if (feature && typeof feature.mount === "function") {
    feature.mount(root);
  }
}

function clearViewError() {
  document.querySelectorAll("[data-view-error]").forEach((el) => el.remove());
}

function showViewError(route, error, isAuth) {
  showView(route, isAuth);
  setActiveNav(route);
  document.title = (ROUTE_TITLES[route] || route) + " · Kharcha Tracker";

  const containerId = isAuth ? "auth-view-container" : "view-container";
  const container = document.getElementById(containerId);
  if (!container) return;

  clearViewError();
  const box = document.createElement("div");
  box.className = "empty";
  box.dataset.viewError = route;

  const message = document.createElement("p");
  message.textContent = "This screen could not be loaded. " +
    (error && error.message ? error.message : "Please try again.");

  const retry = document.createElement("button");
  retry.className = "btn btn-ghost btn-sm";
  retry.type = "button";
  retry.textContent = "Retry";
  retry.addEventListener("click", () => {
    clearViewError();
    router();
  });

  box.appendChild(message);
  box.appendChild(retry);
  container.appendChild(box);
}

export async function router() {
  const sequence = ++navigationSequence;
  const route = currentRoute();

  if (route === "magic") {
    try {
      const root = await ensureViewMounted("login");
      if (sequence !== navigationSequence) return;
      mountFeature("login", root);
      if (sequence !== navigationSequence) return;
      showView("login", true);
      clearViewError();
      if (typeof magicHandler === "function") await magicHandler();
      else navigate("login");
    } catch (err) {
      if (sequence !== navigationSequence) return;
      showViewError("login", err, true);
    }
    return;
  }

  const isAuth = route === "login" || route === "signup";
  const authed = Boolean(Auth.access);

  if (PROTECTED_ROUTES.has(route) && !authed) {
    navigate("login");
    return;
  }
  if (isAuth && authed && Auth.user) {
    navigate("dashboard");
    return;
  }
  if (!ROUTE_TITLES[route]) {
    navigate(authed ? "dashboard" : "login");
    return;
  }

  let root;
  try {
    root = await ensureViewMounted(route);
  } catch (err) {
    if (sequence !== navigationSequence) return;
    showViewError(route, err, isAuth);
    return;
  }

  if (sequence !== navigationSequence) return;

  mountFeature(route, root);
  if (sequence !== navigationSequence) return;

  clearViewError();
  showView(route, isAuth);
  setActiveNav(route);
  document.title = ROUTE_TITLES[route] + " · Kharcha Tracker";

  const feature = routeFeatures.get(route);
  if (feature && typeof feature.onEnter === "function") {
    try {
      await feature.onEnter(root);
    } catch (err) {
      console.error("Route entry failed for " + route + ":", err);
    }
  }
}

export function startRouter() {
  if (routerStarted) return;
  routerStarted = true;
  window.addEventListener("hashchange", router);
  router();
}
