/* Settings screen initialization and user field population.
   NOTE: the source app has no submit handler for #form-profile; per the
   specification none is invented here — recorded as an open product
   question instead. */

import { $ } from "../core/dom.js";
import { state } from "../core/state.js";
import { Auth } from "../api.js";
import { setError } from "../ui/forms.js";
import { applyUserChrome, logout } from "./auth.js";
import { refreshSettingsPlan } from "./subscription.js";

export function mount() {
  const logoutBtn = $("#btn-logout-settings");
  if (logoutBtn) logoutBtn.addEventListener("click", logout);
}

export function onEnter() {
  const user = state.currentUser || Auth.user;
  if (!user) return;
  $("#profile-name").value = user.name || "";
  $("#profile-email").value = user.email || "";
  $("#profile-avatar").value = user.avatar_url || "";
  setError($("#profile-error"), "");
  $("#profile-success").hidden = true;
  applyUserChrome();
  refreshSettingsPlan();
}
