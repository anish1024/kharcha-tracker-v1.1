/* Groups screen: group cards, group CRUD, group notes and group-level
   event bindings. Settlement handlers are split into settlement.js; this
   module wires them in mount() with a group-reload callback so
   settlement.js never imports groups.js. */

import { $, $all } from "../core/dom.js";
import { state, memberName } from "../core/state.js";
import { api, Auth } from "../api.js";
import { Icons } from "../icons.js";
import { esc } from "../utils/html.js";
import { showToast } from "../ui/toast.js";
import { setError, setBusy } from "../ui/forms.js";
import { expensesListHtml } from "./expenses.js";
import { bindSettlementActions } from "./settlement.js";

export async function refreshGroupCount() {
  try {
    const groups = await api.get("/groups");
    state.groupsCache = groups;
    $("#stat-groups").textContent = String(groups.length);
  } catch (e) { /* keep placeholder */ }
}

function memberChipsHtml(group) {
  const members = state.membersCache[group.id] || [];
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

function notesListHtml(groupId) {
  const notes = state.notesCache[groupId];
  if (!notes) return '<p class="empty empty--sm">Loading notes&hellip;</p>';
  if (!notes.length) return '<p class="empty empty--sm">No notes yet.</p>';
  return notes.map(noteRowHtml).join("");
}

function noteRowHtml(note) {
  const tags = (note.tags || []).length
    ? '<div class="note-tags">' +
      note.tags.map((t) => '<span class="tag-chip">' + esc(t) + "</span>").join("") +
      "</div>"
    : "";
  const modified = note.modified_at ? ' <span class="note-modified">(edited)</span>' : "";
  return (
    '<div class="note-row" data-note-id="' + esc(note.id) + '">' +
      '<div class="note-row-head">' +
        '<span class="note-date">' + esc(String(note.date || "").slice(0, 10)) + modified + "</span>" +
        '<span class="note-row-actions">' +
          '<button class="icon-btn icon-btn--sm" data-action="edit-note" type="button" aria-label="Edit note">' +
            Icons.edit + "</button>" +
          '<button class="icon-btn icon-btn--sm icon-btn--danger" data-action="delete-note" type="button" aria-label="Delete note">' +
            Icons.trash + "</button>" +
        "</span>" +
      "</div>" +
      '<div class="note-message">' + esc(note.message) + "</div>" +
      tags +
    "</div>"
  );
}

function resetNoteForm(form) {
  delete form.dataset.editingNote;
  form.reset();
  const dateEl = form.querySelector(".note-date");
  if (dateEl) dateEl.value = new Date().toISOString().slice(0, 10);
  const submitBtn = form.querySelector('button[type="submit"]');
  if (submitBtn) submitBtn.textContent = "Add note";
  setError(form.querySelector(".form-error"), "");
}

function groupCardHtml(group) {
  const meId = String((state.currentUser || Auth.user || {}).id || "");
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
      '<div class="group-notes" data-notes-for="' + esc(group.id) + '">' +
        '<div class="card-subtitle">Notes</div>' +
        '<div class="notes-list">' + notesListHtml(group.id) + "</div>" +
        '<div class="group-notes-actions">' +
          '<button class="btn btn-ghost btn-sm" data-action="toggle-notes" type="button">' +
            Icons.plus + " Add note</button>" +
        "</div>" +
        '<form class="note-form" data-note-form="' + esc(group.id) + '" hidden novalidate>' +
          '<div class="form-row">' +
            '<div class="field"><label>Note</label>' +
              '<input class="note-message" type="text" maxlength="500" placeholder="e.g. Flight lands 18:40" required></div>' +
          "</div>" +
          '<div class="form-row">' +
            '<div class="field"><label>Date</label>' +
              '<input class="note-date" type="date" required></div>' +
            '<div class="field"><label>Tags (comma separated)</label>' +
              '<input class="note-tags" type="text" placeholder="trip, food"></div>' +
          "</div>" +
          '<p class="form-error" hidden></p>' +
          '<div class="form-actions">' +
            '<button class="btn btn-ghost" type="button" data-action="cancel-note">Cancel</button>' +
            '<button class="btn btn-primary" type="submit">Add note</button>' +
          "</div>" +
        "</form>" +
      "</div>" +
    "</div>"
  );
}

export async function loadGroups() {
  const listEl = $("#groups-list");
  const selectEl = $("#invite-group");
  try {
    const groups = await api.get("/groups");
    state.groupsCache = groups;
    state.settleCache = {};
    state.notesCache = {};
    await Promise.all(groups.map(async (g) => {
      try {
        state.membersCache[g.id] = await api.get("/groups/" + encodeURIComponent(g.id) + "/members");
      } catch (e) {
        state.membersCache[g.id] = (g.members || []).map((id) => ({ id: id, name: id, email: "" }));
      }
      try {
        state.expensesCache[g.id] = await api.get("/groups/" + encodeURIComponent(g.id) + "/expenses");
      } catch (e) {
        state.expensesCache[g.id] = [];
      }
      try {
        state.notesCache[g.id] = await api.get("/groups/" + encodeURIComponent(g.id) + "/notes");
      } catch (e) {
        /* left undefined; loadGroupNotes below shows the error state */
      }
    }));
    if (!groups.length) {
      listEl.innerHTML = '<p class="empty">No groups yet — create one or join with an invite code.</p>';
    } else {
      listEl.innerHTML = groups.map(groupCardHtml).join("");
      groups.forEach((g) => {
        if (state.notesCache[g.id] === undefined) loadGroupNotes(g.id);
      });
    }
    selectEl.innerHTML = '<option value="">Select a group&hellip;</option>' +
      groups.map((g) => '<option value="' + esc(g.id) + '">' + esc(g.name) + "</option>").join("");
  } catch (err) {
    listEl.innerHTML = '<p class="empty">Could not load groups: ' + esc(err.message) + "</p>";
  }
}

async function loadGroupNotes(groupId) {
  const render = (html) => {
    $all('[data-notes-for="' + groupId + '"]').forEach((container) => {
      const list = container.querySelector(".notes-list");
      if (list) list.innerHTML = html;
    });
  };
  try {
    state.notesCache[groupId] = await api.get("/groups/" + encodeURIComponent(groupId) + "/notes");
    render(notesListHtml(groupId));
  } catch (e) {
    render('<p class="empty empty--sm">Could not load notes.</p>');
  }
}

/* Delegated actions for group cards and group notes (copy / regen /
   delete / leave, note toggle / cancel / edit / delete). */
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

  /* Delegated actions on the group list: toggle-notes, cancel-note,
     edit-note, delete-note, copy, regen, delete, leave. Settlement
     actions (toggle-settle / toggle-adhoc / cancel-adhoc / toggle-link-
     expenses / cancel-link-expenses / settle-* / payout-*) are handled
     by settlement.js bound from mount(); both listen on #groups-list but
     each ignores the other's actions. */
  $("#groups-list").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const card = btn.closest("[data-group-id]");
    if (!card) return;
    const groupId = card.dataset.groupId;
    const action = btn.dataset.action;

    if (action === "toggle-notes") {
      const form = card.querySelector("[data-note-form]");
      if (!form) return;
      form.hidden = !form.hidden;
      if (!form.hidden) {
        resetNoteForm(form);
        const msg = form.querySelector(".note-message");
        if (msg) msg.focus();
      }
      return;
    }
    if (action === "cancel-note") {
      const form = card.querySelector("[data-note-form]");
      if (form) {
        form.hidden = true;
        resetNoteForm(form);
      }
      return;
    }
    if (action === "edit-note") {
      const row = btn.closest("[data-note-id]");
      const form = card.querySelector("[data-note-form]");
      if (!row || !form) return;
      const note = (state.notesCache[groupId] || []).find((n) => String(n.id) === String(row.dataset.noteId));
      if (!note) return;
      resetNoteForm(form);
      form.dataset.editingNote = note.id;
      form.querySelector(".note-message").value = note.message || "";
      const dateEl = form.querySelector(".note-date");
      if (dateEl) dateEl.value = String(note.date || "").slice(0, 10);
      form.querySelector(".note-tags").value = (note.tags || []).join(", ");
      const submitBtn = form.querySelector('button[type="submit"]');
      if (submitBtn) submitBtn.textContent = "Save changes";
      form.hidden = false;
      const msg = form.querySelector(".note-message");
      if (msg) msg.focus();
      return;
    }
    if (action === "delete-note") {
      const row = btn.closest("[data-note-id]");
      if (!row) return;
      if (!window.confirm("Delete this note?")) return;
      setBusy(btn, true);
      try {
        await api.del(
          "/groups/" + encodeURIComponent(groupId) + "/notes/" + encodeURIComponent(row.dataset.noteId)
        );
        showToast("Note deleted.", "success");
        await loadGroupNotes(groupId);
      } catch (err) {
        showToast(err.message || "Could not delete note.", "error");
      } finally {
        setBusy(btn, false);
      }
      return;
    }

    if (action !== "copy" && action !== "regen" && action !== "delete" && action !== "leave") return;

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

  /* Delegated submit for the per-card note form (create or update). */
  $("#groups-list").addEventListener("submit", async (e) => {
    const form = e.target.closest("[data-note-form]");
    if (!form) return;
    e.preventDefault();
    const groupId = form.dataset.noteForm;
    const errEl = form.querySelector(".form-error");
    const btn = form.querySelector('button[type="submit"]');
    const message = (form.querySelector(".note-message").value || "").trim();
    const tags = (form.querySelector(".note-tags").value || "")
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);
    const date = form.querySelector(".note-date").value || undefined;

    if (!message) { setError(errEl, "Note message is required."); return; }

    setBusy(btn, true, "Saving…");
    try {
      const editingNote = form.dataset.editingNote;
      const body = { message: message, tags: tags, date: date };
      if (editingNote) {
        await api.patch(
          "/groups/" + encodeURIComponent(groupId) + "/notes/" + encodeURIComponent(editingNote),
          body
        );
        showToast("Note updated.", "success");
      } else {
        await api.post(
          "/groups/" + encodeURIComponent(groupId) + "/notes",
          body
        );
        showToast("Note added.", "success");
      }
      form.hidden = true;
      resetNoteForm(form);
      await loadGroupNotes(groupId);
    } catch (err) {
      setError(errEl, err.message || "Could not save the note.");
    } finally {
      setBusy(btn, false);
    }
  });
}

export function mount() {
  const newGroupIcon = $("#btn-new-group-icon");
  if (newGroupIcon) newGroupIcon.innerHTML = Icons.plus;
  const joinIcon = $("#join-icon");
  if (joinIcon) joinIcon.innerHTML = Icons.key;
  const inviteIcon = $("#invite-icon");
  if (inviteIcon) inviteIcon.innerHTML = Icons.mail;
  bindGroupActions();
  bindSettlementActions(async () => { await loadGroups(); await refreshGroupCount(); });
}
