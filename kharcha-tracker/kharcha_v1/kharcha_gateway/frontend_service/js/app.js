/* Kharcha Tracker — application composition and startup only.
   Feature implementations live in js/features/*.js; this file wires
   routes, binds the persistent shell, restores the session and starts
   the router. */
"use strict";

import { Icons } from "./icons.js";
import { Auth, api } from "./api.js";
import { $ } from "./core/dom.js";
import { state } from "./core/state.js";
import {
  registerRoute,
  setMagicHandler,
  renderNav,
  startRouter,
  navigate,
  currentRoute
} from "./core/router.js";
import { showToast } from "./ui/toast.js";

import * as auth from "./features/auth.js";
import * as dashboard from "./features/dashboard.js";
import * as groups from "./features/groups.js";
import * as expenses from "./features/expenses.js";
import * as invitations from "./features/invitations.js";
import * as notifications from "./features/notifications.js";
import * as activity from "./features/activity.js";
import * as settings from "./features/settings.js";
import * as subscription from "./features/subscription.js";

/* ------------------------------ Global shell events ------------------------------ */
function onSubscriptionRequired() {
  showToast("Your free entries are used up — choose a plan to continue.", "error");
  if (currentRoute() !== "subscription") navigate("subscription");
}

/* ------------------------------ Boot ------------------------------ */
async function boot() {
  const bell = $("#btn-bell");
  if (bell) bell.innerHTML = Icons.bell;
  const logoutBtn = $("#btn-logout");
  if (logoutBtn) logoutBtn.innerHTML = Icons.logout;

  renderNav();
  expenses.bindCrossScreenExpenseActions();

  $("#btn-logout").addEventListener("click", auth.logout);
  notifications.bindShellNotifications();
  window.addEventListener("auth:expired", auth.onExpired);
  window.addEventListener("subscription:required", onSubscriptionRequired);

  setMagicHandler(auth.handleMagicLink);
  registerRoute("login", { mount: auth.mountLogin });
  registerRoute("signup", { mount: auth.mountSignup });
  registerRoute("dashboard", { mount: dashboard.mount, onEnter: dashboard.onEnter });
  registerRoute("groups", {
    mount: () => { groups.mount(); invitations.mount(); },
    onEnter: () => { auth.applyUserChrome(); groups.loadGroups(); invitations.loadInvitations(); }
  });
  registerRoute("expenses", {
    mount: expenses.mount,
    onEnter: async () => { auth.applyUserChrome(); await groups.loadGroups(); expenses.renderExpensesScreen(); }
  });
  registerRoute("activity", { mount: activity.mount, onEnter: activity.onEnter });
  registerRoute("settings", { mount: settings.mount, onEnter: settings.onEnter });
  registerRoute("subscription", { mount: subscription.mount, onEnter: subscription.onEnter });

  if (Auth.access) {
    try {
      state.currentUser = await api.get("/users/me");
      Auth.saveUser(state.currentUser);
      notifications.loadNotifications();
    } catch (err) {
      if (err && err.status === 0 && Auth.user) state.currentUser = Auth.user;
      else Auth.clear();
    }
  }

  startRouter();
}

document.addEventListener("DOMContentLoaded", boot);