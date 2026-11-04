/* Invitation list and actions. Invitations are a section within the
   Groups screen, not a separate route. */

import { $ } from "../core/dom.js";
import { api } from "../api.js";
import { esc } from "../utils/html.js";
import { formatDate } from "../utils/formatters.js";
import { showToast } from "../ui/toast.js";
import { setBusy } from "../ui/forms.js";
import { refreshGroupCount } from "./groups.js";
import { loadNotifications } from "./notifications.js";

function invitationCardHtml(inv) {
  const groupLabel = inv.group_name || "a group";
  const expires = inv.expires_at ? " &middot; expires " + esc(formatDate(inv.expires_at)) : "";
  return (
    '<div class="group-card" data-invitation-id="' + esc(inv.id) + '">' +
      '<div class="group-card-head">' +
        "<div>" +
          '<div class="group-card-name">' + esc(groupLabel) + "</div>" +
          '<div class="group-card-meta">You were invited to join ' + esc(groupLabel) + expires + "</div>" +
        "</div>" +
        '<div class="group-card-actions">' +
          '<button class="btn btn-primary btn-sm" data-action="accept" type="button">Accept</button>' +
          '<button class="btn btn-ghost btn-sm" data-action="decline" type="button">Decline</button>' +
        "</div>" +
      "</div>" +
    "</div>"
  );
}

export async function loadInvitations() {
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

export async function refreshInviteCount() {
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

export function mount() {
  bindInvitationActions();
}