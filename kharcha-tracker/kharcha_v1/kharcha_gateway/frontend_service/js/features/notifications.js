/* Notification rendering and actions. Notifications belong to the
   persistent shell: bindShellNotifications() runs once during shell
   initialization, not per route entry. */

import { $ } from "../core/dom.js";
import { api } from "../api.js";
import { esc } from "../utils/html.js";
import { formatDate } from "../utils/formatters.js";
import { showToast } from "../ui/toast.js";

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

export async function loadNotifications() {
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

export async function markNotificationsRead() {
  try {
    await api.post("/users/me/notifications/read");
    await loadNotifications();
  } catch (err) {
    showToast(err.message || "Could not update notifications.", "error");
  }
}

export function toggleNotifPanel(force) {
  const panel = $("#notif-panel");
  if (!panel) return;
  const show = typeof force === "boolean" ? force : panel.hidden;
  panel.hidden = !show;
  if (show) loadNotifications();
}

/* Shell handlers — bound exactly once during shell initialization. */
export function bindShellNotifications() {
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
}
