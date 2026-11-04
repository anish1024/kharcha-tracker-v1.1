/* Login, signup, magic-link and session flows.
   api.js remains the owner of token storage and HTTP refresh mechanics;
   this module coordinates application login/session flows only. */

import { $, $all } from "../core/dom.js";
import { state } from "../core/state.js";
import { Auth, api } from "../api.js";
import { Icons } from "../icons.js";
import { navigate } from "../core/router.js";
import { showToast } from "../ui/toast.js";
import { setError, setBusy } from "../ui/forms.js";

export function applyUserChrome() {
  const user = state.currentUser || Auth.user;
  if (!user) return;
  const name = user.name || user.email || "";
  const initial = (name.trim()[0] || "?").toUpperCase();
  $("#user-chip-name").textContent = name;
  $("#user-avatar").textContent = initial;
  const bell = $("#btn-bell");
  if (bell) bell.hidden = false;
  const foot = $("#nav-foot-email");
  if (foot) foot.textContent = user.email || "";
}

export function logout() {
  Auth.clear();
  state.currentUser = null;
  navigate("login");
  showToast("Logged out.");
}

export function onExpired() {
  state.currentUser = null;
  navigate("login");
  showToast("Session expired — please log in again.", "error");
}

/* ------------------------------ Magic link (email SSO) ------------------------------ */
let magicInFlight = false;

export async function establishSession(base) {
  Auth.setSession({ access: base.auth_token, refresh: base.refresh_token });
  state.currentUser = await api.get("/users/me");
  Auth.saveUser(state.currentUser);
}

export async function handleMagicLink() {
  if (magicInFlight) return;
  magicInFlight = true;
  $all(".view").forEach((v) => { v.hidden = v.id !== "view-login"; });
  $("#app-shell").hidden = true;
  document.title = "Signing you in… · Kharcha Tracker";

  const query = location.hash.split("?")[1] || "";
  const token = new URLSearchParams(query).get("token") || "";
  if (!token) {
    magicInFlight = false;
    navigate("login");
    showToast("Missing magic-link token.", "error");
    return;
  }
  try {
    const base = await api.post("/auth/magic-link/verify", { token: token }, { auth: false });
    await establishSession(base);
    showToast("Signed in with email.", "success");
    navigate("dashboard");
  } catch (err) {
    showToast(err.message || "Magic link is invalid or expired.", "error");
    navigate("login");
  } finally {
    magicInFlight = false;
  }
}

export async function requestMagicLink(email) {
  const res = await api.post("/auth/magic-link", { email: email }, { auth: false });
  if (res && res.magic_token) {
    const base = await api.post(
      "/auth/magic-link/verify", { token: res.magic_token }, { auth: false }
    );
    await establishSession(base);
    return true;
  }
  return false;
}

/* ------------------------------ Auth forms ------------------------------ */
function bindLoginForm() {
  const form = $("#form-login");
  if (!form) return;

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#login-error");
    const btn = $("#login-submit");
    setError(errEl, "");
    const email = $("#login-email").value.trim();
    const password = $("#login-password").value;
    if (!email || !password) { setError(errEl, "Email and password are required."); return; }

    setBusy(btn, true, "Logging in…");
    try {
      const base = await api.post("/auth/login", { email, password }, { auth: false });
      await establishSession(base);
      navigate("dashboard");
    } catch (err) {
      Auth.clear();
      setError(errEl, err.message || "Login failed.");
    } finally {
      setBusy(btn, false);
    }
  });

  const magicBtn = $("#btn-magic-link");
  if (magicBtn) {
    magicBtn.addEventListener("click", async () => {
      const errEl = $("#login-error");
      const hintEl = $("#magic-hint");
      setError(errEl, "");
      hintEl.hidden = true;
      const email = $("#login-email").value.trim();
      if (!email || !email.includes("@")) {
        setError(errEl, "Enter your email above, then tap “Continue with email”.");
        return;
      }
      setBusy(magicBtn, true, "Sending link…");
      try {
        const immediate = await requestMagicLink(email);
        if (immediate) {
          showToast("Signed in with email.", "success");
          navigate("dashboard");
        } else {
          hintEl.textContent = "Check your inbox for a sign-in link.";
          hintEl.hidden = false;
        }
      } catch (err) {
        setError(errEl, err.message || "Could not send the link.");
      } finally {
        setBusy(magicBtn, false);
      }
    });
  }
}

function bindSignupForm() {
  const form = $("#form-signup");
  if (!form) return;

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const errEl = $("#signup-error");
    const btn = $("#signup-submit");
    setError(errEl, "");

    const name = $("#signup-name").value.trim();
    const email = $("#signup-email").value.trim();
    const password = $("#signup-password").value;

    if (!name) { setError(errEl, "Username is required."); return; }
    if (!email || !email.includes("@")) { setError(errEl, "Enter a valid email address."); return; }
    if (password.length < 6) { setError(errEl, "Password must be at least 6 characters."); return; }

    setBusy(btn, true, "Creating account…");
    try {
      await api.post("/auth/signup", { name: name, email: email, password: password }, { auth: false });
      const base = await api.post("/auth/login", { email: email, password: password }, { auth: false });
      await establishSession(base);
      navigate("dashboard");
      showToast("Welcome to Kharcha Tracker!", "success");
    } catch (err) {
      setError(errEl, err.message || "Sign up failed.");
    } finally {
      setBusy(btn, false);
    }
  });
}

export function mountLogin() {
  const icon = $("#magic-icon");
  if (icon) icon.innerHTML = Icons.mail;
  bindLoginForm();
}

export function mountSignup() {
  bindSignupForm();
}
