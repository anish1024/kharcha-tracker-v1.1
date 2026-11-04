/* Toast notifications on the persistent #toast root. */

import { $ } from "../core/dom.js";

let toastTimer = null;

export function showToast(message, kind) {
  const el = $("#toast");
  el.textContent = message;
  el.className = "toast" + (kind ? " toast--" + kind : "");
  el.hidden = false;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 3200);
}
