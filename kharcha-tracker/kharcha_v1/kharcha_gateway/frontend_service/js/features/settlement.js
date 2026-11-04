/* Settlement/payout/corpus rendering and actions, generated dynamically
   inside group cards. Must not import groups.js — groups.js imports this
   module and injects a group-reload callback for the FINAL settle flow. */

import { $ } from "../core/dom.js";
import { state, memberName } from "../core/state.js";
import { Auth, api } from "../api.js";
import { esc } from "../utils/html.js";
import { formatAmount } from "../utils/formatters.js";
import { showToast } from "../ui/toast.js";
import { setError, setBusy } from "../ui/forms.js";

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
  const meId = String((state.currentUser || Auth.user || {}).id || "");
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
  const expenses = (state.expensesCache[group.id] || []).filter(function (exp) {
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
  const members = state.membersCache[group.id] || [];
  const meId = String((state.currentUser || Auth.user || {}).id || "");
  const memberOptions = function (selectedId) {
    return members.map(function (m) {
      const sel = String(m.id) === selectedId ? " selected" : "";
      return '<option value="' + esc(m.id) + '"' + sel + ">" + esc(m.name || m.email) + "</option>";
    }).join("");
  };
  const other = members.find((m) => String(m.id) !== meId) || members[0] || {};
  const expenseOptions = (state.expensesCache[group.id] || []).map(function (exp) {
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

/* E6: corpus fund — derived read-only from the settlement preview.
   corpus = Σ SETTLED orphan ad-hoc payouts + Σ max(0, net_dues[user]).
   Positive net_dues = the group owes that member (i.e. overpaid); orphan
   payouts never enter net_dues, so the two terms are disjoint. Only
   SETTLED orphan payouts count (PENDING/PAID/FAILED are excluded). */
function corpusFromPreview(preview) {
  const settledOrphans = (preview.orphan_payouts || []).filter(function (p) {
    return p.status === "SETTLED";
  });
  const orphanTotal = settledOrphans.reduce(function (sum, p) {
    return sum + (Number(p.amt) || 0);
  }, 0);
  const netDues = preview.net_dues || {};
  const overpaidTotal = Object.keys(netDues).reduce(function (sum, uid) {
    return sum + Math.max(0, Number(netDues[uid]) || 0);
  }, 0);
  return {
    orphanTotal: orphanTotal,
    overpaidTotal: overpaidTotal,
    total: orphanTotal + overpaidTotal,
  };
}

function corpusSectionHtml(group, preview) {
  const corpus = corpusFromPreview(preview);
  const deposits = (state.expensesCache[group.id] || []).filter(function (e) {
    return e.expn_ctgry === "CORPUS";
  });
  const depositRows = deposits.map(function (e) {
    const payers = (e.paid_by || [])
      .map(function (p) { return memberName(group.id, p.user_id); })
      .join(", ");
    return (
      '<div class="corpus-deposit">' +
        "<span>" + esc(e.expn_desc) + (payers ? " &middot; " + esc(payers) : "") + "</span>" +
        '<span class="corpus-deposit-amt">' + esc(formatAmount(e.expn_amnt, group.currency)) + "</span>" +
      "</div>"
    );
  }).join("");
  return (
    '<div class="corpus-fund">' +
      '<div class="settle-section-title">Corpus fund <span class="corpus-total">' +
        esc(formatAmount(corpus.total, group.currency)) +
      "</span></div>" +
      '<div class="corpus-line"><span>Orphan payments (settled)</span><span>' +
        esc(formatAmount(corpus.orphanTotal, group.currency)) + "</span></div>" +
      '<div class="corpus-line"><span>Overpaid balances</span><span>' +
        esc(formatAmount(corpus.overpaidTotal, group.currency)) + "</span></div>" +
      (depositRows
        ? '<div class="corpus-deposits">' +
          '<div class="corpus-subtitle">Deposits</div>' +
          depositRows +
        "</div>"
        : "") +
    "</div>"
  );
}

export function settlePanelHtml(group, preview) {
  const meId = String((state.currentUser || Auth.user || {}).id || "");
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
    corpusSectionHtml(group, preview) +
    payoutSectionHtml("Open payouts", preview.open_payouts, group, true) +
    payoutSectionHtml("Proposed payouts", preview.proposed_payouts, group, false) +
    payoutSectionHtml("Orphan payments", preview.orphan_payouts, group, true) +
    adhocFormHtml(group)
  );
}

export async function loadSettlePanel(groupId) {
  const panel = $('[data-settle-for="' + groupId + '"]');
  if (!panel) return;
  try {
    const preview = await api.get("/groups/" + encodeURIComponent(groupId) + "/settle");
    state.settleCache[groupId] = preview;
    const group = state.groupsCache.find((g) => g.id === groupId) || { id: groupId, currency: "" };
    panel.innerHTML = settlePanelHtml(group, preview);
  } catch (e) {
    panel.innerHTML = '<p class="empty empty--sm">Could not load settlement.</p>';
  }
}

/* Settlement event responsibilities split out of the old monolithic
   bindGroupActions(). Bound once, after the Groups view is mounted, on
   the stable #groups-list container. `reloadGroups` is an injected
   callback (async () => { loadGroups(); refreshGroupCount(); }) so this
   module never imports groups.js. */
export function bindSettlementActions(reloadGroups) {
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
          await reloadGroups();
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
