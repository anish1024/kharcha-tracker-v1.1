/* Shared form error and busy-state helpers. */

export function setError(el, message) {
  if (!el) return;
  el.textContent = message || "";
  el.hidden = !message;
}

export function setBusy(button, busy, busyLabel) {
  if (!button) return;
  if (busy) {
    button.dataset.label = button.textContent;
    button.disabled = true;
    if (busyLabel) button.textContent = busyLabel;
  } else {
    button.disabled = false;
    if (button.dataset.label) button.textContent = button.dataset.label;
  }
}
