/* Kharcha API client — fetch wrapper with Bearer auth + refresh hook. */
"use strict";

const API_BASE = "/api/v1";

const KEY_ACCESS = "kharcha_access_token";
const KEY_REFRESH = "kharcha_refresh_token";
const KEY_USER = "kharcha_user";

class ApiError extends Error {
  constructor(status, message, code) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code || null;
  }
}

const Auth = {
  get access() { return localStorage.getItem(KEY_ACCESS); },
  get refresh() { return localStorage.getItem(KEY_REFRESH); },
  get user() {
    try { return JSON.parse(localStorage.getItem(KEY_USER) || "null"); }
    catch (e) { return null; }
  },
  setSession({ access, refresh, user }) {
    if (access) localStorage.setItem(KEY_ACCESS, access);
    if (refresh) localStorage.setItem(KEY_REFRESH, refresh);
    else localStorage.removeItem(KEY_REFRESH);
    if (user) localStorage.setItem(KEY_USER, JSON.stringify(user));
  },
  saveUser(user) { localStorage.setItem(KEY_USER, JSON.stringify(user)); },
  clear() {
    localStorage.removeItem(KEY_ACCESS);
    localStorage.removeItem(KEY_REFRESH);
    localStorage.removeItem(KEY_USER);
  }
};

/*
 * Exchange the refresh token for a new access token (server rotates the
 * refresh token too, so persist both). Returns true on success.
 */
let refreshInFlight = null;
async function tryRefresh() {
  const refresh = Auth.refresh;
  if (!refresh) return false;
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    try {
      const res = await fetch(API_BASE + "/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refresh })
      });
      if (!res.ok) return false;
      const data = await res.json();
      if (!data || !data.auth_token) return false;
      Auth.setSession({ access: data.auth_token, refresh: data.refresh_token });
      return true;
    } catch (e) {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

/* Decode a JWT payload's exp (seconds) without verifying the signature. */
function tokenExpirySeconds(token) {
  try {
    const part = token.split(".")[1];
    if (!part) return null;
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/");
    const padded = b64 + "=".repeat((4 - (b64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded));
    return typeof payload.exp === "number" ? payload.exp : null;
  } catch (e) {
    return null;
  }
}

/* True when the access token is missing, unreadable, or expires soon. */
function accessTokenNeedsRefresh(skewSeconds) {
  const token = Auth.access;
  if (!token) return false;
  const exp = tokenExpirySeconds(token);
  if (exp === null) return true;
  return Date.now() / 1000 > exp - (skewSeconds || 120);
}

async function apiFetch(path, opts) {
  opts = opts || {};
  const method = opts.method || "GET";
  const body = opts.body;
  const auth = opts.auth !== false;
  const retried = opts.retried === true;

  /* Proactive refresh: renew shortly before the access token expires. */
  if (auth && !retried && Auth.refresh && accessTokenNeedsRefresh(opts.skewSeconds)) {
    const ok = await tryRefresh();
    if (!ok && accessTokenNeedsRefresh(opts.skewSeconds)) {
      Auth.clear();
      window.dispatchEvent(new CustomEvent("auth:expired"));
      throw new ApiError(401, "Session expired. Please log in again.");
    }
  }

  const headers = {};
  if (body !== undefined && body !== null) headers["Content-Type"] = "application/json";
  if (auth && Auth.access) headers["Authorization"] = "Bearer " + Auth.access;

  let res;
  try {
    res = await fetch(API_BASE + path, {
      method: method,
      headers: headers,
      body: body !== undefined && body !== null ? JSON.stringify(body) : undefined
    });
  } catch (e) {
    throw new ApiError(0, "Network error — could not reach the server.");
  }

  if (res.status === 401 && auth && !retried) {
    const refreshed = await tryRefresh();
    if (refreshed) return apiFetch(path, Object.assign({}, opts, { retried: true }));
    Auth.clear();
    if (location.hash !== "#/login" && location.hash !== "#/signup") {
      window.dispatchEvent(new CustomEvent("auth:expired"));
    }
    throw new ApiError(401, "Session expired. Please log in again.");
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = text; }
  }

  if (!res.ok) {
    let msg = "Request failed (" + res.status + ")";
    let code = null;
    if (data) {
      if (typeof data.detail === "string") {
        msg = data.detail;
      } else if (data.detail && typeof data.detail === "object") {
        code = data.detail.code || null;
        msg = data.detail.message || data.detail.code || JSON.stringify(data.detail);
      } else if (data.detail) {
        msg = JSON.stringify(data.detail);
      } else if (typeof data.message === "string") {
        msg = data.message;
      }
      if (!code && typeof data.code === "string") code = data.code;
    }
    const err = new ApiError(res.status, msg, code);
    if (res.status === 403 && code === "SUBSCRIPTION_REQUIRED") {
      window.dispatchEvent(new CustomEvent("subscription:required"));
    }
    throw err;
  }
  if (opts.withMeta) return { data: data, headers: res.headers };
  return data;
}

const api = {
  get: function (path, opts) { return apiFetch(path, opts); },
  getMeta: function (path, opts) {
    return apiFetch(path, Object.assign({}, opts, { withMeta: true }));
  },
  post: function (path, body, opts) {
    return apiFetch(path, Object.assign({}, opts, { method: "POST", body: body }));
  },
  patch: function (path, body, opts) {
    return apiFetch(path, Object.assign({}, opts, { method: "PATCH", body: body }));
  },
  del: function (path, opts) { return apiFetch(path, Object.assign({}, opts, { method: "DELETE" })); }
};
