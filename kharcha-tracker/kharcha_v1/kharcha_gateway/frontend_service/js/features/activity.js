/* Activity screen: loading, filters, pagination and CSV export.
   Selection/paging state lives in state.activity. */

import { $ } from "../core/dom.js";
import { state } from "../core/state.js";
import { api } from "../api.js";
import { Icons } from "../icons.js";
import { esc } from "../utils/html.js";
import { showToast } from "../ui/toast.js";
import { setBusy } from "../ui/forms.js";
import { applyUserChrome } from "./auth.js";

const ACTIVITY_PAGE_SIZE = 20;

function activityRangeParams() {
  const fromVal = ($("#activity-from") || {}).value || "";
  const toVal = ($("#activity-to") || {}).value || "";
  if (fromVal && toVal && fromVal > toVal) {
    throw new Error("From date must be on or before To date.");
  }
  const params = new URLSearchParams();
  if (fromVal) params.set("date_from", new Date(fromVal + "T00:00:00").toISOString());
  if (toVal) params.set("date_to", new Date(toVal + "T23:59:59.999").toISOString());
  return params;
}

function clearActivityList() {
  const card = $("#activity-list-card");
  if (card) card.hidden = true;
  state.activity.page = 0;
  state.activity.total = 0;
  const exportBtn = $("#activity-export-btn");
  if (exportBtn) exportBtn.hidden = true;
  const pager = $("#activity-pager");
  if (pager) { pager.hidden = true; pager.innerHTML = ""; }
}

async function loadActivityScreen() {
  const select = $("#activity-group-select");
  if (!select) return;
  try {
    state.activity.groups = await api.get("/groups?sort=activity");
  } catch (err) {
    state.activity.groups = [];
    select.innerHTML = '<option value="">Select a group&hellip;</option>';
    select.value = "";
    const emptyEl = $("#activity-empty");
    if (emptyEl) {
      emptyEl.textContent = "Could not load groups: " + err.message;
      emptyEl.hidden = false;
    }
    clearActivityList();
    return;
  }
  await Promise.all(state.activity.groups.map(async (g) => {
    if (state.membersCache[String(g.id)]) return;
    try {
      state.membersCache[g.id] = await api.get("/groups/" + encodeURIComponent(g.id) + "/members");
    } catch (e) {
      state.membersCache[g.id] = (g.members || []).map((id) => ({ id: id, name: id, email: "" }));
    }
  }));
  if (!state.activity.groups.some((g) => String(g.id) === String(state.activity.groupId))) {
    state.activity.groupId = state.activity.groups.length ? String(state.activity.groups[0].id) : "";
  }
  select.innerHTML = '<option value="">Select a group&hellip;</option>' +
    state.activity.groups.map((g) =>
      '<option value="' + esc(g.id) + '"' +
      (String(g.id) === String(state.activity.groupId) ? " selected" : "") + ">" +
      esc(g.name) + "</option>").join("");
  select.value = state.activity.groupId;
  const emptyEl = $("#activity-empty");
  if (emptyEl) {
    emptyEl.hidden = state.activity.groups.length > 0;
    if (!state.activity.groups.length) {
      emptyEl.textContent = "No groups yet — create one from the Groups screen first.";
    }
  }
  if (state.activity.groupId) await fetchActivityPage(0);
  else clearActivityList();
}

async function fetchActivityPage(page) {
  const slot = $("#activity-table-slot");
  const card = $("#activity-list-card");
  if (!slot || !card) return;
  if (!state.activity.groupId) { clearActivityList(); return; }
  state.activity.page = Math.max(0, page || 0);

  let params;
  try {
    params = activityRangeParams();
  } catch (err) {
    showToast(err.message, "error");
    return;
  }
  params.set("limit", String(ACTIVITY_PAGE_SIZE));
  params.set("offset", String(state.activity.page * ACTIVITY_PAGE_SIZE));

  card.hidden = false;
  slot.innerHTML = '<p class="empty empty--sm">Loading&hellip;</p>';
  try {
    const path =
      "/groups/" + encodeURIComponent(state.activity.groupId) + "/activity?" + params.toString();
    const res = await api.getMeta(path);
    state.activity.total = parseInt(res.headers.get("X-Total-Count") || "0", 10) || 0;
    renderActivityTable(Array.isArray(res.data) ? res.data : []);
    renderActivityPager(state.activity.total);
    const exportBtn = $("#activity-export-btn");
    if (exportBtn) exportBtn.hidden = state.activity.total <= 0;
  } catch (err) {
    slot.innerHTML = '<p class="empty">Could not load activity: ' + esc(err.message) + "</p>";
    const pager = $("#activity-pager");
    if (pager) { pager.hidden = true; pager.innerHTML = ""; }
    const exportBtn = $("#activity-export-btn");
    if (exportBtn) exportBtn.hidden = true;
  }
}

function formatActivityTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return String(iso);
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }) +
    " · " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

function activityMemberName(userId) {
  const idStr = String(userId || "");
  if (!idStr) return "unknown";
  const cached = state.membersCache[String(state.activity.groupId)] || [];
  const hit = cached.find((m) => String(m.id) === idStr);
  if (hit && hit.name && String(hit.name) !== idStr) return hit.name;
  return idStr.slice(0, 8);
}

function activityActorName(log) {
  if (log.actor_name) return log.actor_name;
  if (!log.actor_user_id) return "—";
  return activityMemberName(log.actor_user_id);
}

function eventPillClass(event) {
  const e = String(event || "").toUpperCase();
  if (e === "CREATE") return "create";
  if (e === "UPDATE") return "update";
  if (e === "DELETE") return "delete";
  if (e === "LOCK") return "lock";
  if (e === "UNLOCK") return "unlock";
  return "other";
}

function prettifyActivity(log) {
  const type = String(log.entity_type || "");
  const event = String(log.event || log.action || "").toUpperCase();
  const text = String(log.new_value || "");
  let m;
  if (type === "group") {
    if (event === "CREATE") return "Created group “" + (text || "group") + "”";
    if ((m = /^Added member (.+)$/.exec(text))) return "Added member " + activityMemberName(m[1]);
    if ((m = /^User joined (.+)$/.exec(text))) return "Joined (" + activityMemberName(m[1]) + ")";
    if ((m = /^User (.+) left group$/.exec(text))) return activityMemberName(m[1]) + " left group";
    if (event === "LOCK") return "Group locked";
    if (event === "UNLOCK") return "Group unlocked";
    if (event === "DELETE") return "Group deleted";
    if (text) return text;
    return event || "—";
  }
  if (text) return text;
  if (event === "CREATE") return "Added";
  if (event === "UPDATE") return "Updated";
  if (event === "DELETE") return "Deleted";
  return event || "—";
}

function activityRowHtml(log) {
  const event = String(log.event || log.action || "");
  return (
    "<tr>" +
      '<td data-label="When">' + esc(formatActivityTime(log.created_at)) + "</td>" +
      '<td data-label="Event"><span class="event-pill event-pill--' +
        esc(eventPillClass(event)) + '">' + esc(event || "?") + "</span></td>" +
      '<td data-label="Actor">' + esc(activityActorName(log)) + "</td>" +
      '<td data-label="Details">' + esc(prettifyActivity(log)) + "</td>" +
    "</tr>"
  );
}

function renderActivityTable(rows) {
  const slot = $("#activity-table-slot");
  if (!slot) return;
  const countEl = $("#activity-count");
  if (countEl) {
    countEl.textContent = state.activity.total
      ? "(" + state.activity.total + (state.activity.total === 1 ? " event)" : " events)")
      : "";
  }
  if (!rows.length) {
    slot.innerHTML = '<p class="empty empty--sm">No activity for this range.</p>';
    return;
  }
  slot.innerHTML =
    '<div class="table-wrap">' +
      '<table class="activity-table">' +
        "<thead><tr><th>When</th><th>Event</th><th>Actor</th><th>Details</th></tr></thead>" +
        "<tbody>" + rows.map(activityRowHtml).join("") + "</tbody>" +
      "</table>" +
    "</div>";
}

function renderActivityPager(total) {
  const pager = $("#activity-pager");
  if (!pager) return;
  const pages = Math.max(1, Math.ceil(total / ACTIVITY_PAGE_SIZE));
  const page = Math.min(state.activity.page, pages - 1);
  pager.hidden = total <= ACTIVITY_PAGE_SIZE;
  if (pager.hidden) { pager.innerHTML = ""; return; }
  pager.innerHTML =
    '<button class="btn btn-ghost btn-sm" type="button" data-activity-page="prev"' +
      (page <= 0 ? " disabled" : "") + ">Prev</button>" +
    '<span class="pager-info">Page ' + (page + 1) + " of " + pages + "</span>" +
    '<button class="btn btn-ghost btn-sm" type="button" data-activity-page="next"' +
      (page >= pages - 1 ? " disabled" : "") + ">Next</button>";
}

const ACTIVITY_EXPORT_CHUNK = 1000;
const ACTIVITY_EXPORT_CAP = 10000;

function csvCell(value) {
  const s = String(value == null ? "" : value);
  if (/[",\r\n]/.test(s)) return '"' + s.replace(/"/g, '""') + '"';
  return s;
}

function activityGroupSlug(name) {
  const slug = String(name || "group").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "group";
}

async function exportActivityCsv() {
  const btn = $("#activity-export-btn");
  if (!state.activity.groupId) return;
  let params;
  try {
    params = activityRangeParams();
  } catch (err) {
    showToast(err.message, "error");
    return;
  }
  setBusy(btn, true, "Exporting…");
  try {
    const rows = [];
    let total = state.activity.total;
    let offset = 0;
    while (offset < Math.min(total || Number.MAX_SAFE_INTEGER, ACTIVITY_EXPORT_CAP)) {
      const chunkParams = new URLSearchParams(params);
      chunkParams.set("limit", String(ACTIVITY_EXPORT_CHUNK));
      chunkParams.set("offset", String(offset));
      const res = await api.getMeta(
        "/groups/" + encodeURIComponent(state.activity.groupId) + "/activity?" + chunkParams.toString()
      );
      const page = Array.isArray(res.data) ? res.data : [];
      const headerTotal = parseInt(res.headers.get("X-Total-Count") || "0", 10) || 0;
      if (headerTotal) total = headerTotal;
      if (!page.length) break;
      rows.push.apply(rows, page);
      offset += page.length;
    }
    const truncated = total > ACTIVITY_EXPORT_CAP;
    const fromVal = ($("#activity-from") || {}).value || "all";
    const toVal = ($("#activity-to") || {}).value || "all";
    const group = state.activity.groups.find((g) => String(g.id) === String(state.activity.groupId));
    const filename =
      "activity_" + activityGroupSlug(group && group.name) + "_" + fromVal + "_" + toVal + ".csv";
    const lines = rows.map((r) =>
      [
        r.created_at || "",
        r.event || r.action || "",
        activityActorName(r),
        prettifyActivity(r),
        r.entity_type || ""
      ].map(csvCell).join(",")
    );
    const csv = "\uFEFF" + "created_at,event,actor,details,entity_type\r\n" +
      lines.join("\r\n") + "\r\n";
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    if (truncated) {
      showToast("Exported the first " + ACTIVITY_EXPORT_CAP + " events (of " + total + ").", "error");
    } else {
      showToast("Exported " + rows.length + (rows.length === 1 ? " event." : " events."), "success");
    }
  } catch (err) {
    showToast("Export failed: " + err.message, "error");
  } finally {
    setBusy(btn, false);
  }
}

function bindActivityScreen() {
  const exportBtn = $("#activity-export-btn");
  if (exportBtn) exportBtn.addEventListener("click", exportActivityCsv);
  const select = $("#activity-group-select");
  if (select) {
    select.addEventListener("change", (e) => {
      state.activity.groupId = e.target.value || "";
      fetchActivityPage(0);
    });
  }
  ["#activity-from", "#activity-to"].forEach((sel) => {
    const el = $(sel);
    if (el) el.addEventListener("change", () => fetchActivityPage(0));
  });
  const clearBtn = $("#activity-clear-btn");
  if (clearBtn) {
    clearBtn.addEventListener("click", () => {
      $("#activity-from").value = "";
      $("#activity-to").value = "";
      fetchActivityPage(0);
    });
  }
  const pager = $("#activity-pager");
  if (pager) {
    pager.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-activity-page]");
      if (!btn || btn.disabled) return;
      if (btn.dataset.activityPage === "prev" && state.activity.page > 0) {
        fetchActivityPage(state.activity.page - 1);
      }
      if (btn.dataset.activityPage === "next") fetchActivityPage(state.activity.page + 1);
    });
  }
}

export function mount() {
  const icon = $("#activity-export-icon");
  if (icon) icon.innerHTML = Icons.download;
  bindActivityScreen();
}

export async function onEnter() {
  applyUserChrome();
  await loadActivityScreen();
}
