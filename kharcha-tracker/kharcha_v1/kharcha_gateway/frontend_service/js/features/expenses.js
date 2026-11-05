/* Expense rendering, forms, splits and CRUD — including expenses shown
   inside group cards and the dedicated Expenses screen. Does not import
   groups.js; shared data comes from core/state.js. groups.js imports the
   markup helpers exported here. */

import { $, $all } from "../core/dom.js";
import { state, memberName } from "../core/state.js";
import { api } from "../api.js";
import { Icons } from "../icons.js";
import { navigate } from "../core/router.js";
import { esc } from "../utils/html.js";
import { formatAmount, formatDate } from "../utils/formatters.js";
import { showToast } from "../ui/toast.js";
import { setError, setBusy } from "../ui/forms.js";
import { openModal, closeModal } from "../ui/modal.js";

const EXPENSE_CATEGORIES = [
  "GROCERY", "UTILITY", "TRAVEL", "ENTERTAINMENT", "FOOD_N_DINING",
  "OP_EXP", "CAP_EXP", "ACCOMMODATION", "CUSTOM",
  /* E6: corpus fund deposit (recorded as an expense, split equally). */
  "CORPUS"
];

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

export function expensesListHtml(group) {
  const expenses = state.expensesCache[group.id] || [];
  if (!expenses.length) {
    return '<p class="empty empty--sm">No expenses yet.</p>';
  }
  return expenses.map((exp) => expenseRowHtml(exp, group)).join("");
}

function expenseFormHtml(group) {
  const members = state.membersCache[group.id] || [];
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
  const members = state.membersCache[group.id] || [];
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
  const group = state.groupsCache.find((g) => String(g.id) === String(groupId));
  const exp = (state.expensesCache[groupId] || []).find((e) => String(e.trans_id) === String(transId));
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
  const members = state.membersCache[groupId] || [];
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

export async function loadGroupExpenses(groupId) {
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
    state.expensesCache[groupId] = expenses;
    const group = state.groupsCache.find((g) => String(g.id) === String(groupId)) || { id: groupId, currency: "" };
    render(expensesListHtml(group));
  } catch (e) {
    render('<p class="empty empty--sm">Could not load expenses.</p>');
  }
}

/* E3: remember the last-used group across visits (localStorage). */
const LAST_EXPENSE_GROUP_KEY = "kharcha_last_expense_group";

function rememberLastExpenseGroupId(groupId) {
  try {
    localStorage.setItem(LAST_EXPENSE_GROUP_KEY, String(groupId));
  } catch (e) {
    /* localStorage unavailable (e.g. blocked) — non-fatal */
  }
}

function lastUsedExpenseGroupId() {
  try {
    return localStorage.getItem(LAST_EXPENSE_GROUP_KEY) || null;
  } catch (e) {
    return null;
  }
}

export function renderExpensesScreen() {
  const select = $("#expenses-group-select");
  const emptyEl = $("#expenses-empty");
  if (!select) return;
  if (!state.groupsCache.length) {
    state.expenses.groupId = "";
    select.innerHTML = '<option value="">Select a group&hellip;</option>';
    if (emptyEl) emptyEl.hidden = false;
    applyExpensesGroup("");
    return;
  }
  if (emptyEl) emptyEl.hidden = true;
  if (!state.groupsCache.some((g) => String(g.id) === String(state.expenses.groupId))) {
    state.expenses.groupId = "";
  }
  // E3: pre-select the last-used group when it is still in our groups,
  // otherwise fall back to the first group in the list.
  if (!state.expenses.groupId) {
    const stored = lastUsedExpenseGroupId();
    if (stored && state.groupsCache.some((g) => String(g.id) === String(stored))) {
      state.expenses.groupId = stored;
    } else {
      state.expenses.groupId = state.groupsCache[0].id;
    }
  }
  select.innerHTML = '<option value="">Select a group&hellip;</option>' +
    state.groupsCache.map((g) =>
      '<option value="' + esc(g.id) + '"' +
      (String(g.id) === String(state.expenses.groupId) ? " selected" : "") + ">" +
      esc(g.name) + "</option>").join("");
  applyExpensesGroup(state.expenses.groupId);
}

function applyExpensesGroup(groupId) {
  const block = $("#expenses-add-block");
  const addRow = $("#expenses-add-row");
  const slot = $("#expenses-form-slot");
  const listCard = $("#expenses-list-card");
  if (!block || !slot || !listCard) return;
  const group = state.groupsCache.find((g) => String(g.id) === String(groupId)) || null;
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
  state.expenses.groupId = groupId || "";
  // E3: remember the choice (only for a real group) so the next visit
  // pre-selects it.
  if (state.expenses.groupId) rememberLastExpenseGroupId(state.expenses.groupId);
  const select = $("#expenses-group-select");
  if (select && select.value !== state.expenses.groupId) select.value = state.expenses.groupId;
  applyExpensesGroup(state.expenses.groupId);
}

/* Cross-screen delegated expense actions — bound once at boot on the
   persistent #main so they work from both the Groups view and the
   dedicated Expenses screen without firing twice through the
   groups-list listener: edit/delete rows, split popup, add-expense form
   toggle, and the "Add expense" navigation from a group card. */
let crossScreenExpenseActionsBound = false;
export function bindCrossScreenExpenseActions() {
  if (crossScreenExpenseActionsBound) return;
  crossScreenExpenseActionsBound = true;

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
      state.expenses.groupId = navCard.dataset.groupId;
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
}

/* Export the selected group's expenses as a JSON file. The server returns
   the portable envelope (six fields per row, user names instead of UUIDs);
   we just turn it into a downloadable file, like the activity CSV export. */
async function exportExpensesJson() {
  const btn = $("#expenses-export-btn");
  const groupId = state.expenses.groupId;
  if (!btn || !groupId) return;
  setBusy(btn, true, "Exporting…");
  try {
    const envelope = await api.get(
      "/groups/" + encodeURIComponent(groupId) + "/expenses/export"
    );
    const expenses = Array.isArray(envelope && envelope.expenses)
      ? envelope.expenses
      : [envelope];
    const group = (state.groupsCache || []).find(
      (g) => String(g.id) === String(groupId)
    );
    const slug = group && group.name
      ? String(group.name).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "")
      : "group";
    const filename =
      "kharcha-expenses-" + slug + "-" + new Date().toISOString().slice(0, 10) + ".json";
    const blob = new Blob([JSON.stringify(envelope, null, 2)], {
      type: "application/json;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    showToast(
      "Exported " + expenses.length + (expenses.length === 1 ? " expense." : " expenses."),
      "success"
    );
  } catch (err) {
    showToast("Export failed: " + (err.message || "unknown error"), "error");
  } finally {
    setBusy(btn, false);
  }
}

/* Import an expense JSON file (envelope, bare array, or a single row) into
   the selected group. The server maps user names to this deployment's member
   ids and re-validates each row; valid rows are saved, invalid ones are
   reported back in `skipped`. */
async function importExpensesJson(file) {
  const btn = $("#expenses-import-btn");
  const groupId = state.expenses.groupId;
  if (!btn || !groupId) return;
  setBusy(btn, true, "Importing…");
  try {
    const text = await file.text();
    let payload;
    try {
      payload = JSON.parse(text);
    } catch (e) {
      throw new Error("File is not valid JSON.");
    }
    const result = await api.post(
      "/groups/" + encodeURIComponent(groupId) + "/expenses/import",
      payload
    );
    const imported = result && Number(result.imported) || 0;
    const skipped = (result && Array.isArray(result.skipped)) ? result.skipped : [];
    if (skipped.length) {
      const reasons = skipped.slice(0, 3).map((s) => "row " + (s.index + 1) + ": " + s.reason);
      if (skipped.length > 3) reasons.push("…and " + (skipped.length - 3) + " more");
      showToast("Imported " + imported + " · skipped " + skipped.length + " — " + reasons.join("; "), "error");
    } else {
      showToast("Imported " + imported + (imported === 1 ? " expense." : " expenses."), "success");
    }
    if (imported > 0) await loadGroupExpenses(groupId);
  } catch (err) {
    showToast("Import failed: " + (err.message || "unknown error"), "error");
  } finally {
    setBusy(btn, false);
  }
}

function bindExpensesScreen() {
  const select = $("#expenses-group-select");
  if (select) {
    select.addEventListener("change", (e) => selectExpensesGroup(e.target.value));
  }

  /* Export/Import JSON — only act when a group is selected; the buttons
     themselves are hidden by applyExpensesGroup() otherwise. */
  const exportBtn = $("#expenses-export-btn");
  if (exportBtn) exportBtn.addEventListener("click", exportExpensesJson);
  const importBtn = $("#expenses-import-btn");
  const importFile = $("#expenses-import-file");
  if (importBtn && importFile) {
    importBtn.addEventListener("click", () => importFile.click());
    importFile.addEventListener("change", async (e) => {
      const file = e.target.files && e.target.files[0];
      e.target.value = "";
      if (!file) return;
      await importExpensesJson(file);
    });
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

    const memberIds = (state.membersCache[groupId] || []).map((m) => m.id);
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

export function mount() {
  const icon = $("#expenses-add-icon");
  if (icon) icon.innerHTML = Icons.plus;
  const exportIcon = $("#expenses-export-icon");
  if (exportIcon) exportIcon.innerHTML = Icons.download;
  const importIcon = $("#expenses-import-icon");
  if (importIcon) importIcon.innerHTML = Icons.upload;
  bindExpensesScreen();
}
