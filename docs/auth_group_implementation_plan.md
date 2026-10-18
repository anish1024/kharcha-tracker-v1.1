# Kharcha Tracker — Frontend + User/Group Sprint Implementation Plan

> Status: **PROPOSED → APPROVED (defaults accepted)**
> Scope: Plain vanilla HTML/CSS/JS frontend + the supporting backend changes required to make it work end-to-end.
> Rule: Code is implemented **phase by phase**; after each phase a progress entry is appended to `./implementation_log.md`.

---

## 1. Current state (verified)

- **Backend (FastAPI) — implemented:**
  - `auth`: `POST /auth/signup`, `POST /auth/login`, `POST /auth/password-reset/request`, `POST /auth/password-reset/confirm`.
  - `users/me`: `GET`, `PATCH`.
  - `groups`: `POST /groups`, `GET /groups`, `GET/DELETE /groups/{id}`, `POST /groups/{id}/invite` (currently a **random** code), `POST /groups/{id}/invite/accept`, `POST /groups/{id}/members` (add by email, instant), `DELETE /groups/{id}/members/me`, `POST /groups/{id}/lock|unlock`, `GET /groups/{id}/activity`.
  - Auth = hand-rolled **HS256 JWT**, 24h expiry, **no refresh**, Bearer header.
- **Stubs (no logic):** all `expenses`, `settlement`, `payouts`, `payments`, `notifications` endpoints; `notification_service.py` is empty.
- **Storage:** `flat_file` (one JSON array per table) is the working default; `sql_lite` / `postgres` are stubs.
- **Models / DTOs:** `User` (id, name, password, email, avatar_url); `Group` (id, name, created_by_user_id, invite_code, members[], status, currency, time_created, last_updated); `Base` (auth_user_id, auth_token, auth_token_expiry).
- **Record schemas:** `BaseRecord` (is_deleted, created_by, created_at, modified_by, modified_at) + `user_record`, `group_record`, `notification_record`, `audit_log`, etc.
- **Frontend:** the `frontend_service` node exists in `kharcha_tracker.yaml` as `unsplit` (empty). **No frontend code exists anywhere.**
- **Tests:** `tests/` (auth, group, flat_file, api_main) exist but the API suite cannot run because `TestClient` requires `httpx` (not installed). Phase 0 installs it.

---

## 2. Pushbacks / decisions (sign-off required)

Points where the brief is ambiguous or infeasible as written, each with a **recommended default (✔)**.

1. **SSO — ✔ Recommended: passwordless magic-link.**
   True OAuth SSO (Google/Apple/Facebook) needs IdP client-id/secret + a registered callback URL; none exist in this repo, so real OAuth cannot work here. **Recommended:** implement **"Continue with email" = signed one-time magic-link login** (email in → server issues a short-lived signed link → in local/dev mode the link is returned in the response / logged instead of emailed → clicking it logs the user in and auto-creates the account if new). This is self-contained and is the practical form of "SSO using email id." Login stays email+password; sign-up = email + username + password, exactly as specified. *Alternative:* wire real OAuth later once IdP credentials are supplied (a hook is left in place).
2. **Payments — ✔ Recommended: mock.**
   No payment gateway / merchant key exists. Subscription "Pay" is a **mock** that marks the plan active and records the purchase. Real Razorpay/Stripe is a separate sprint requiring credentials.
3. **Blueprint compliance — ✔ Recommended: extend + back-update the YAML.**
   Trial, subscription, invitations, refresh tokens, and two new notification kinds are **not** in the current closed `kharcha_tracker.yaml`. Per `AGENTS.md §0` these are not added silently — they are proposed as a scoped extension (§6) and the YAML is **back-updated at the end** per the README's progressive-regeneration contract.
4. **"3 entries after expiry" — ✔ Recommended: gate, don't build expense persistence.**
   Expense persistence is a stub this sprint. Implement the **gating logic** (count + block + `403 SUBSCRIPTION_REQUIRED` → frontend routes to `/subscription`) on the entry path; actual expense save stays a stub.
5. **Invite code — ✔ Recommended: deterministic pass-key derived from `(group_id, inviter_id)`.**
   Replaces the current random code. Details in §5d.

---

## 3. Frontend (plain vanilla HTML/CSS/JS)

- **Location:** `kharcha_v1/kharcha_gateway/frontend_service/` (the existing empty node) → `index.html`, `css/app.css`, `js/`.
- **Serving:** FastAPI `StaticFiles` mount at `/` (registered **after** the `/api/v1` routes so the API wins; every other path → the SPA).
- **Routing:** **hash router** (`#/login`, `#/signup`, `#/dashboard`, `#/groups`, `#/settings`, `#/subscription`) so no server-side route handling is needed beyond serving `index.html`.
- **Views:** `login`, `signup`, `dashboard`, `groups`, `settings`, `subscription`.
- **Responsive (mobile-first):**
  - `< 640px` → mobile
  - `640–1024px` → tablet
  - `> 1024px` → desktop
  - Nav adapts: **bottom tab bar (mobile) → left sidebar (desktop) → top bar + condensed nav (tablet).**
- **Icons:** inline SVG, zero external dependencies.
  - **Dashboard → grid (four-squares) icon**
  - **Groups → people / users icon**
  - **Settings → gear icon** (as specified)
  - Notifications → bell (added for §5c)
- **API client module (`js/api.js`):**
  - `BASE_URL` (default same-origin `/api/v1`).
  - Token kept in `localStorage` (`kharcha_access`, `kharcha_refresh`).
  - Every request auto-attaches `Authorization: Bearer <access>`.
  - On `401` → attempt `POST /auth/refresh` once → retry the original request; if refresh fails → clear tokens → route to `#/login`.
- **Auth guard on load:** if an access token exists, call `GET /users/me`; on success → `#/dashboard`, on failure → `#/login`.

### View responsibilities
- **Login:** email + password form, "Sign up" link, "Continue with email" (magic-link) button.
- **Sign-up:** email + username + password. On success → auto-login → `#/dashboard`.
- **Dashboard:** landing after login. Nav links (Dashboard / Groups / Settings). A **trial/plan status card** (from `GET /users/me/subscription`); content area is the app shell.
- **Groups:** list groups the user is in. Per group: show/copy invite code, "Generate new code", "Invite by email". Global: "Create group" (name + currency), "Join by invite code" (group id + code), "My invitations" (accept / cancel).
- **Settings:** profile (name, avatar) via `PATCH /users/me`, plan/subscription status + link to subscription, logout.
- **Subscription:** four plans with mock pay — **Yearly ₹100, Monthly ₹50, 6-month ₹70, Lifetime ₹300**.

---

## 4. Backend changes (summary)

- **Auth / prolonged sessions** → §5a (access + refresh tokens, `POST /auth/refresh`).
- **Trial + subscription + 3-entry gate** → §5b.
- **Invitations + owner notification** → §5c.
- **Invite-code algorithm** → §5d.
- **Notifications:** minimal `notification_service` (`create`, `list_for_user`) so the owner actually receives accept/reject events and `GET /users/me/notifications` works.
- **`main.py`:** add the `StaticFiles` mount for the frontend.

---

## 5. Elaborations (requested)

### 5a. Token expiry during prolonged sessions (access + refresh tokens)
Today: one JWT, 24h, then the user is force-logged-out mid-session.

**Plan (stateless, no DB needed):**
- **Access token** — short-lived (≈ **60 min**), `type: access`, carries `sub` / `email`. Used for all API calls.
- **Refresh token** — long-lived (≈ **30 days**), `type: refresh`, carries a `jti`. Stored client-side with the access token.
- **New endpoint** `POST /api/v1/auth/refresh` → given a valid, un-expired, un-rotated refresh token, returns a **new access token + a rotated new refresh token** (rotation invalidates the old refresh `jti`).
- **Frontend behavior:** when the access token is within ~2 min of `exp`, *or* any call returns `401`, the client calls `/auth/refresh` once and retries the original call. If refresh also fails (expired / rotated away) → clear tokens → `#/login`.
- **Result:** a user who leaves the tab open for days stays **seamlessly logged in**, while a leaked access token stays dangerous for only ~60 min, and refresh provides a revocation point.

### 5b. Trial, subscription, and the 3-entry limit
- **On signup** set `trial_ends_at = now + 1 year`. A user is **unlimited while** `trial_ends_at` is in the future **or** a paid plan is active.
- **Subscription fields on the user:** `plan` (none|monthly|6month|yearly|lifetime), `plan_status` (none|active), `plan_ends_at` (lifetime → null).
- **`POST /api/v1/subscription`** `{plan}` → **mock payment** → activate: monthly `+1mo`, 6month `+6mo`, yearly `+1yr`, lifetime → active forever. Resets the entry counter.
- **`GET /api/v1/users/me/subscription`** → `{trial_ends_at, plan, plan_status, plan_ends_at, entries_remaining, is_limited, is_expired}` — drives the dashboard banner and settings.
- **Entry gate** on the entry path (`POST /expenses`):
  - unlimited → allow;
  - else if `limited_entries_used < 3` → allow and increment;
  - else → `403 {code: "SUBSCRIPTION_REQUIRED"}` and the frontend **navigates to `#/subscription`**.
  - `3` is a named constant (`POST_TRIAL_ENTRY_LIMIT`), easy to change.

### 5c. Invitations + owner notification
The brief's "send invite codes to other users (email)" + "view/accept/cancel invite codes from other users" + "owner is always notified on accept/reject" requires a **pending-invitation** model (the current `add_member` adds instantly, no pending state).

- **`invitation_record`:** `group_id`, `inviter_id`, `invitee_email`, `invite_code`, `status` (pending|accepted|rejected|expired), `created_at`, `expires_at`.
- **"Invite by email"** (on a group) → creates a **pending invitation** for that email (not instant membership).
- **Invitee** sees them under **My invitations** → **Accept** (becomes a member) or **Cancel/Decline**.
- **On accept or cancel → a `NotificationRecord` is created for the group owner** (`record.created_by`) with new kinds `GROUP_INVITE_ACCEPTED` / `GROUP_INVITE_DECLINED`. The owner sees it via `GET /users/me/notifications` (a bell in the nav).

### 5d. Invite-code algorithm (simplest, as requested)
**Goal:** a unique pass-key **created from `group_id` + user (inviter) id**, checkable without storing it.

```python
def derive_invite_code(group_id: UUID, inviter_id: UUID) -> str:
    raw = f"{group_id}:{inviter_id}"
    digest = hashlib.sha256(raw.encode()).digest()
    return base32(digest)[:8].upper()   # Crockford alphabet: no I, L, O, U, 0/1/O ambiguity
```

- **Generate:** `POST /groups/{id}/invite` → returns `derive_invite_code(group_id, actor_user_id)`. Each member's code is stable and unique to their (group, member) pair.
- **Validate / join:** `POST /groups/{id}/invite/accept` with the code → server recomputes `derive_invite_code(group_id, m)` for **each current member `m`**; if the submitted code matches any (and the group is `OPEN`), the user is admitted. **No stored/rotated code needed.**
- **Tradeoff (flagged):** because it is deterministic, a code is only as strong as the `group_id` you must also supply (a 128-bit UUID — not guessable). If one-time / revocable codes are wanted later, add a per-code `HMAC(secret, …)` + salt (a one-line change). Keeping it deterministic now per the "simplest algorithm" requirement.

---

## 6. Blueprint (`kharcha_tracker.yaml`) extension to back-update (AGENTS.md §0 — scoped, approved)

New / changed nodes & fields:
- `user_record`: + `trial_ends_at`, `plan`, `plan_status`, `plan_ends_at`, `limited_entries_used`.
- New `invitation_record` (db_schema) + `invitation` domain model (dto).
- New / extended constant enums:
  - `notification_kind` += `GROUP_INVITE_ACCEPTED`, `GROUP_INVITE_DECLINED`.
  - New `plan` enum (none|monthly|6month|yearly|lifetime).
  - New `plan_status` enum (none|active).
- New service functions:
  - `auth_service.refresh_token`.
  - `subscription_service` (activate, get_status).
  - `group_service` (create / list / accept / decline invitation, `derive_invite_code`, owner-notify).
  - `notification_service` (create, list_for_user).
- New endpoints:
  - `POST /auth/refresh`
  - `POST /subscription`
  - `GET  /users/me/subscription`
  - `GET  /users/me/invitations`
  - `POST /users/me/invitations/{id}/accept`
  - `POST /users/me/invitations/{id}/cancel`
- `main.py`: + `StaticFiles` mount for the frontend.
- `frontend_service`: split from `unsplit` → the concrete HTML/CSS/JS files.

---

## 7. Step-by-step implementation plan

Each phase is implemented and verified, then a progress entry is appended to `./implementation_log.md`.

- **Phase 0 — Baseline:** `pip install httpx` into the venv; confirm the existing test suite runs green.
- **Phase 1 — Frontend shell:** `index.html` + `app.css` (responsive) + router + API client; **login + signup** views wired to the existing auth endpoints. *Verify:* run uvicorn → browser → sign up → auto-login → land on a dashboard shell.
- **Phase 2 — Nav + Groups view:** dashboard nav (Dashboard / Groups / Settings icons) + **Groups** (list, create, show/copy invite code, join by code, invite by email) wired to existing group endpoints. *Verify:* create/list a group, generate a code, a second user joins by code.
- **Phase 3 — Token refresh:** access + refresh JWTs, `POST /auth/refresh` (rotation), frontend auto-refresh on 401 / near-expiry. *Verify:* pytest + manual token flow.
- **Phase 4 — Trial + subscription (backend):** user fields, `subscription_service`, `POST /subscription` (mock), `GET /users/me/subscription`. *Verify:* pytest + curl.
- **Phase 5 — Subscription view + SSO:** subscription page (4 plans + mock pay), trial/plan banners, **"Continue with email" magic-link** login. *Verify:* browser.
- **Phase 6 — Entry gating:** 3-entry limit on the entry path → `403 SUBSCRIPTION_REQUIRED` → route to `#/subscription`. *Verify:* pytest + curl.
- **Phase 7 — Invitations + owner notifications:** invitation model, accept/cancel, owner `NotificationRecord`, notifications bell + `GET /users/me/notifications`. *Verify:* pytest with two users; owner sees accept and decline.
- **Phase 8 — Derived invite-code:** §5d algorithm + validation + unit tests. *Verify:* pytest.
- **Phase 9 — Back-update + hardening:** update `kharcha_tracker.yaml` (new nodes / fields / endpoints) + `README.md`; full end-to-end pass; **entire test suite green**.

---

## 8. Verification strategy
- **Unit / API tests:** pytest (`kharcha_v1/tests/`); new tests added per phase for refresh, subscription, gating, invitations, and invite-code derivation.
- **Manual E2E:** `uvicorn kharcha_v1.kharcha_gateway.app.main:app --reload` (with `KHARCHA_STORAGE=flat_file`) → browser on mobile / tablet / desktop widths to confirm responsiveness and the full login → dashboard → groups → invite → accept flow.
