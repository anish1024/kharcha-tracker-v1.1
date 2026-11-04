/* Subscription status, plan actions and the plan stat/banner renderers. */

import { $, $all } from "../core/dom.js";
import { api } from "../api.js";
import { formatDate } from "../utils/formatters.js";
import { showToast } from "../ui/toast.js";
import { setBusy } from "../ui/forms.js";
import { applyUserChrome } from "./auth.js";

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

export async function refreshPlanStat() {
  try {
    updatePlanStat(await api.get("/users/me/subscription"));
  } catch (e) { /* keep placeholder */ }
}

export async function refreshSettingsPlan() {
  const el = $("#settings-plan");
  if (!el) return;
  try {
    el.textContent = planSummary(await api.get("/users/me/subscription"));
  } catch (e) {
    el.textContent = "Could not load plan.";
  }
}

/* Plan purchase buttons exist only in the Subscription view. */
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

export function mount() {
  bindSubscriptionActions();
}

export function onEnter() {
  applyUserChrome();
  loadSubscription();
}
