/* Generic modal open/close on the persistent #modal-root.
   Feature modules generate the content HTML; no template language here. */

import { $ } from "../core/dom.js";
import { Icons } from "../icons.js";

export function openModal(title, contentHtml) {
  const root = $("#modal-root");
  root.innerHTML =
    '<div class="modal-overlay" data-modal-overlay>' +
      '<div class="modal" role="dialog" aria-modal="true">' +
        '<div class="modal-head">' +
          '<span class="modal-title"></span>' +
          '<button class="icon-btn" type="button" data-modal-close aria-label="Close dialog">' + Icons.x + "</button>" +
        "</div>" +
        '<div class="modal-body"></div>' +
      "</div>" +
    "</div>";
  const dialog = root.querySelector(".modal");
  dialog.setAttribute("aria-label", title);
  root.querySelector(".modal-title").textContent = title;
  root.querySelector(".modal-body").innerHTML = contentHtml;
  root.querySelector("[data-modal-overlay]").addEventListener("click", (e) => {
    if (e.target.hasAttribute("data-modal-overlay")) closeModal();
  });
  root.querySelector("[data-modal-close]").addEventListener("click", closeModal);
  root.hidden = false;
  document.addEventListener("keydown", modalEscHandler);
}

export function closeModal() {
  const root = $("#modal-root");
  root.hidden = true;
  root.innerHTML = "";
  document.removeEventListener("keydown", modalEscHandler);
}

function modalEscHandler(e) {
  if (e.key === "Escape") closeModal();
}
