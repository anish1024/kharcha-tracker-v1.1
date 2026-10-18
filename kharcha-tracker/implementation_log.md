# Implementation Log — Kharcha Tracker Frontend + User/Group Sprint

Companion to `./auth_group_implementation_plan.md`. One entry is appended per completed phase.

---

## Phase 0 — Baseline  ✅  (done)

**Goal:** Get a runnable, green test baseline before any feature work.

**Actions**
- Installed `httpx` (0.28.1) into the shared dev venv (`../.venv`, which already had fastapi 0.142.2, pydantic 2.13.5, pytest 9.1.1, uvicorn 0.54.0). This unblocks `fastapi.testclient.TestClient`.
- Ran the full suite: `python -m pytest -q` from the repo root.

**Result**
- **90 passed**, 67 warnings.
- Warnings are pre-existing `PydanticSerializationUnexpectedValue` notices in `test_flat_file.py` (string-vs-uuid round-trips); they are not failures and predate this sprint.
- No code changes made to application source in this phase.

**Baseline verified green.** Proceeding to Phase 1.

---

## Phase 1 — Static SPA shell + auth (vanilla HTML/CSS/JS)  ✅  (done)

**Goal:** Serve a responsive frontend from the FastAPI app with working signup/login/logout and a hash router.

**Actions**
- Created `frontend_service/index.html` — all six views (login, signup, dashboard, groups, settings, subscription) in one page, hash-router driven; app-shell layout (topbar / sidebar / tabbar) revealed only when authenticated.
- Created `frontend_service/css/app.css` — mobile-first; breakpoints 640 px (tablet: no tabbar, `.topnav` appears) and 1024 px (desktop: `.sidebar` column); grid-template areas for the shell.
- Created `frontend_service/js/icons.js` — inline SVG icon set (dashboard grid, groups people, settings gear, logout, bell, plus, copy, check, x, mail, alert, card, link, refresh, key, trash, exit, clock, shield) built with a `wrap()` helper.
- Created `frontend_service/js/api.js` — `Auth` token store (localStorage keys `kharcha_access_token` / `kharcha_refresh_token` / `kharcha_user`), `apiFetch` Bearer wrapper, 401 → `tryRefresh()` hook (Phase 1 stub: returns false; Phase 3 fills in rotation) → clear + `auth:expired` event → redirect to `#/login`; `ApiError` parses FastAPI `detail`.
- Created `frontend_service/js/app.js` — hash router with protected-route guard, `renderNav()` into `#sidebar`/`#topnav`/`#tabbar`, signup (POST `/auth/signup` then auto-login POST `/auth/login`) and login form handlers, logout, toast, dashboard/settings shells (profile form filled from `GET /users/me`), `auth:expired` listener.
- Mounted static files in `app/main.py`: `_FRONTEND_DIR` + `StaticFiles(html=True)` at `/`, added **after** all API routes so `/api/v1/*` and `/docs` keep matching first.
- **Pre-existing wiring fix (found during E2E smoke test):** live uvicorn served 500s on every auth call — `flat_file_db/` was empty and `get_storage()` constructed `FlatFileStorage()` without a schema registry, so `user_record` was unknown (tests only passed because `conftest.py` injects `APPLICATION_SCHEMA_REGISTRY`). Fixed by building `_FLAT_FILE_SCHEMA_REGISTRY` in `main.py` (all 7 record schemas) and passing it to the constructor. No behaviour change for tests (they still override `get_storage`).
- Added 2 tests in `test_api_main.py`: `GET /` returns HTML containing view/script tags and all four assets serve 200; `GET /index.html` serves 200 HTML.

**Result**
- **92 passed** (90 baseline + 2 new), 67 pre-existing warnings.
- Live uvicorn (`:8017`) smoke-tested with curl: `POST /auth/signup` → 201, `POST /auth/login` → token, `GET /users/me` → 200, duplicate signup → 422, bad login → 401, `PATCH /users/me` → 200; `GET /`, `/css/app.css`, `/js/*.js`, `/api/v1/health`, `/docs` all → 200.

**Phase 1 verified end-to-end.** Proceeding to Phase 2 (wire Groups view to existing group endpoints).

---

## Phase 2 — Groups view wired to existing endpoints  ✅  (done)

**Goal:** Make the Groups view fully functional against the existing group API, and fill the dashboard group count.

**Actions**
- `js/app.js`:
  - `routeHandlers.groups` → `loadGroups()`; `routeHandlers.dashboard` also calls `refreshGroupCount()` to fill `#stat-groups`.
  - `loadGroups()` — `GET /groups`, renders one card per group (name, currency, member count, status pill, copyable invite code) and populates the `#invite-group` select for email invites.
  - `groupCardHtml()` — owner sees **Delete**, non-owner sees **Leave**; everyone gets **New code** (regenerate) and **Copy**.
  - `bindGroupActions()` (bound once at boot):
    - `#btn-new-group` toggles `#form-create-group`; submit → `POST /groups {name, currency}`.
    - `#form-join-group` submit → `POST /groups/{id}/invite/accept {invite_code}`.
    - `#form-invite-email` submit → `POST /groups/{id}/members {email}` (existing endpoint adds the member directly; real email invites are Phase 8).
    - Delegated click handler on `#groups-list` for copy (`navigator.clipboard`), regen (`POST /groups/{id}/invite`), delete (`DELETE /groups/{id}`), leave (`DELETE /groups/{id}/members/me`); destructive actions are `window.confirm`-gated.
- `css/app.css`: added styles for `group-card-head/-name/-meta/-actions`, `group-status` (+ `--open/--locked/--settled` color variants), `invite-code-row`, `invite-code` (monospace, letter-spaced).

**Result**
- **92 passed** (no regressions); `node --check` clean on all four frontend JS files.
- Live uvicorn E2E (two/three users):
  - Alice creates group → 201; generates invite code.
  - Bob joins by code → **200**; both see the group (counts 1/1); group member count = 3 after Carol is added.
  - Bob wrong code → **404**; Alice invites `carol@test.com` by email → **200**.
  - Bob leaves → 200 (his count drops to 0); non-owner delete → **403**; owner delete → **200**.

**Incident:** while smoke-testing, the detached uvicorn launched with a *relative* `../.venv/bin/python` path silently failed to come up (script continued, all curls returned empty). Using the **absolute** interpreter path fixed it; noted for future smoke tests.

**Phase 2 verified end-to-end.** Proceeding to Phase 3 (token refresh).

---

## Phase 3 — Access + refresh tokens with rotation  ✅  (done)

**Goal:** Short-lived access tokens + rotating refresh tokens so sessions survive beyond the access-token lifetime; frontend refreshes automatically.

**Backend (`auth_service.py`, `models/base.py`, `app/main.py`)**
- `TOKEN_EXPIRY_HOURS` 24 → **1**; added `REFRESH_TOKEN_EXPIRY_DAYS = 30`.
- Added `_ACTIVE_REFRESH: dict[str, tuple[UUID, datetime]]` — the single-use rotation registry.
- New helpers: `_issue_access_token` (payload now carries `typ: "access"` + a unique `jti`), `_issue_refresh_token` (`typ: "refresh"` + `jti`, registered in `_ACTIVE_REFRESH`), and `_issue_session` (mints both and returns a populated `Base`).
- `login` now returns `_issue_session(...)`.
- New `refresh_session(storage, refresh_token)`: validates signature/exp, requires `typ == "refresh"`, checks the token is still registered (not already rotated), re-checks the user is active, then **retires the presented token and mints a fresh session** (rotation). Raises `PermissionError` on any failure.
- `get_current_user` now rejects any token whose `typ` is not `access` (a refresh token can never be used as a bearer token).
- `Base` DTO gained optional `refresh_token` / `refresh_token_expiry`.
- New endpoint `POST /api/v1/auth/refresh` → 200 `Base`, 401 on `PermissionError`.
- Added `jti` to access tokens too (unique token id; also avoids same-second collisions).

**Frontend (`js/api.js`, `js/app.js`)**
- `tryRefresh()` now reads `auth_token` / `refresh_token` from the `Base` response, persists both, and de-dupes concurrent refreshes via an in-flight promise.
- Added `tokenExpirySeconds()` (client-side JWT `exp` decode) and `accessTokenNeedsRefresh(skew=120s)`; `apiFetch` performs a **proactive** refresh shortly before expiry, and still refreshes on a 401 then retries once. If refresh fails, session is cleared and `auth:expired` fires.
- `app.js` login/signup now persist the refresh token: `Auth.setSession({ access, refresh })`.

**Tests added (5)**
- `test_login_returns_refresh_token`, `test_refresh_session_rotates_and_retires_old_token`, `test_refresh_session_rejects_bad_tokens`, `test_refresh_token_rejected_as_bearer` (unit), and `test_refresh_token_flow_api` (API).

**Result**
- **97 passed** (92 + 5 new), 67 pre-existing warnings; `node --check` clean.
- Live uvicorn E2E: access 200 → refresh 200 → rotated access 200 → new refresh 200 → **old refresh replay 401** → **refresh-as-bearer 401**.

**Note:** `Base` gained two optional fields; the `kharcha-tracker.yaml` back-update for these (and the new refresh endpoint) is deferred to Phase 9 per the plan.

**Phase 3 verified end-to-end.** Proceeding to Phase 4 (trial + subscription backend).

---

## Phase 4 — Trial + subscription backend (mock payment)  ✅  (done)

**Goal:** 1-year trial on signup, subscription fields on the user, a `subscription_service` (activate + status), and the two new endpoints.

**Constants**
- New `constants/plan.py` — `Plan` enum (`none|monthly|6month|yearly|lifetime`).
- New `constants/plan_status.py` — `PlanStatus` enum (`none|active`).
- `constants/system_config.py` — added `TRIAL_PERIOD_DAYS = 365`, `POST_TRIAL_ENTRY_LIMIT = 3`.
- `constants/__init__.py` — exports `Plan`, `PlanStatus`, and both new config constants.

**Schema**
- `user_record` gained: `trial_ends_at`, `plan` (default `"none"`), `plan_status` (default `"none"`), `plan_ends_at`, `limited_entries_used` (default `0`). All have defaults, so existing records/tests are unaffected.

**Service (`subscription_service.py`, new)**
- `get_status(storage, user_id)` → `{trial_ends_at, plan, plan_status, plan_ends_at, entries_remaining, is_limited, is_expired}` (exactly the shape in plan §5b). Trial active **or** paid plan active ⇒ `is_limited = false`, `entries_remaining = null`; otherwise `is_limited = is_expired = true` and `entries_remaining = max(0, POST_TRIAL_ENTRY_LIMIT − limited_entries_used)`.
- `activate(storage, user_id, plan)` → mock payment: validates the plan, sets `plan`/`plan_status=active`, computes `plan_ends_at` (monthly +30d, 6month +180d, yearly +365d, lifetime → `null`), **stacks renewals** on remaining time, and **resets `limited_entries_used` to 0**.

**Auth**
- `auth_service.signup` now sets `trial_ends_at = now + timedelta(days=TRIAL_PERIOD_DAYS)`.

**Endpoints (`app/main.py`)**
- `GET /api/v1/users/me/subscription` → status dict (404 if user missing).
- `POST /api/v1/subscription` `{plan}` → status dict (422 invalid plan, 404 user missing).

**Tests added (11)**
- `test_subscription_service.py` (10): trial-on-signup, expired→limited, yearly/lifetime activation, plan durations (parametrized monthly/6month/yearly), invalid plan, counter reset, renewal stacking.
- `test_api_main.py::test_subscription_endpoints_api` (1).

**Result**
- **108 passed** (97 + 11 new); 68 warnings (one new Pydantic uuid-serializer warning, same pre-existing class).
- Live uvicorn E2E: fresh user → trial `2027-10-17`/unlimited; `POST /subscription {yearly}` → active, `plan_ends_at` +1yr; `{lifetime}` → `plan_ends_at: null`; invalid plan → **422**; unauthenticated → **401**.

**Phase 4 verified end-to-end.** Proceeding to Phase 5 (subscription view + SSO).

---

## Phase 5 — Subscription view + magic-link SSO  ✅  (done)

**Goal:** Wire the subscription page (4 plans + mock pay) and trial/plan banners, and implement "Continue with email" = signed one-time magic-link login (plan §5a line 29, §5b line 66, §5c line 168).

**Backend (magic-link)**
- `auth_service.py`: added `MAGIC_LINK_EXPIRY_MINUTES = 15` and `_ACTIVE_MAGIC: dict[str, tuple[str, datetime]]` (a single-use registry keyed by JWT `jti`).
- `request_magic_link(storage, email)` → normalizes/validates the email, mints a signed JWT (`typ: "magic"`, `email`, `jti`, 15-min `exp`), registers the `jti`, and returns `{email, magic_token, magic_link (#/magic?token=…), expires_at}`. No mail transport — the token is returned in the response (dev behaviour per the plan).
- `verify_magic_link(storage, token)` → decodes the JWT, requires `typ == "magic"`, **consumes the `jti` immediately** (single-use), checks expiry, then finds the user **or auto-creates** one from the email local-part (random 24-char password) and returns a full `_issue_session(...)`.
- `app/main.py`: `POST /api/v1/auth/magic-link` → 202 `{...}` (422 invalid email); `POST /api/v1/auth/magic-link/verify` → 200 `Base` (401 bad/used/expired token).

**Frontend**
- `index.html` login view: added an `or` divider, a **Continue with email** button (`#btn-magic-link`, mail icon) that reuses the email already typed in `#login-email`, and a `#magic-hint` line.
- Subscription cards: corrected the 6-month plan's `data-plan` from `half_yearly` → **`6month`** so it matches the `Plan` enum (card + button).
- `js/app.js`:
  - Extracted `establishSession(base)` (store access/refresh + `GET /users/me`); login/signup now use it.
  - `#/magic?token=…` is a new (public) route: `handleMagicLink()` verifies the token, establishes the session, and lands on the dashboard; failures fall back to login with a toast.
  - `requestMagicLink(email)` POSTs then, in dev, immediately verifies the returned token (one-tap sign-in); if a real link came back it shows "check your inbox".
  - Subscription wiring: `routeHandlers.subscription` → `loadSubscription()` (renders `#sub-current` banner ok/info/warn + fills `#stat-plan`); `bindSubscriptionActions()` (boot) wires every `.plan-buy` → confirm → `POST /subscription {plan}` → re-render + toast.
  - Dashboard calls `refreshPlanStat()` (fills `#stat-plan`); settings calls `refreshSettingsPlan()` (fills `#settings-plan`) with a `planSummary()` human string.
  - Helpers: `planLabel()`, `PLAN_LABELS` (incl. `6month` → "6 Months"), `formatDate()`, `shortPlan()`, `planSummary()`.
- `css/app.css`: added `banner--ok` / `banner--info` / `banner--warn`, `.auth-divider`, `.form-hint`.

**Tests added (5)**
- `test_auth_service.py`: `test_magic_link_logs_in_existing_user`, `test_magic_link_auto_creates_account`, `test_magic_link_is_single_use`, `test_magic_link_rejects_bad_input` (4).
- `test_api_main.py::test_magic_link_flow_api` (1) — request 202 → verify 200 → `/users/me` email matches → replay 401 → bad email 422 / bad token 401.

**Result**
- **113 passed** (108 + 5 new); `node --check` clean on all frontend JS.
- Live uvicorn (absolute-path interpreter, `:8099`) E2E: health 200; `POST /auth/magic-link` → 202 with `magic_token` + `magic_link`; `POST /auth/magic-link/verify` → 200 with access **and** refresh tokens; token replay → **401**; `GET /`, `/css/app.css`, `/js/app.js` → all 200.

**Note:** the new magic-link endpoints are deferred to the `kharcha-tracker.yaml` back-update in Phase 9 (consistent with Phase 3/4). Subscription UI/pay and the browser walk-through will be re-confirmed in the Phase 5 browser verification sweep.

**Phase 5 verified end-to-end.** Proceeding to Phase 6 (entry-limit gate → subscription redirect).

---

## Phase 6 — Entry gating (3-entry limit → subscription)  ✅  (done)

**Goal:** Enforce the post-trial free-entry limit on the entry path (`POST /expenses`) and make the frontend route to `#/subscription` on `403 SUBSCRIPTION_REQUIRED` (plan §5b lines 103–107).

**Backend**
- `subscription_service.py`:
  - New `SubscriptionRequiredError(PermissionError)` with `code = "SUBSCRIPTION_REQUIRED"`.
  - New `consume_entry(storage, user_id)` — the gate: unlimited (trial active **or** paid plan active) → no-op; else if `limited_entries_used < POST_TRIAL_ENTRY_LIMIT` → increment + persist + return status; else → raise `SubscriptionRequiredError`. Returns the same status shape as `get_status`.
- `app/main.py` `POST /api/v1/expenses`: now depends on `get_storage`, calls `consume_entry`, and on `SubscriptionRequiredError` raises `HTTPException(403, detail={"code": "SUBSCRIPTION_REQUIRED", "message": ...})`. The endpoint body is still a stub (`return {}`); only the gate is real, as the plan specifies.

**Frontend**
- `js/api.js`: `ApiError` gained a `code` field; the error parser now reads `detail.code` / `detail.message` from a structured `detail` object, and on `403` + `code === "SUBSCRIPTION_REQUIRED"` dispatches a `subscription:required` window event.
- `js/app.js`: new `onSubscriptionRequired()` shows a toast and navigates to `#/subscription`; registered on `window` in `boot()` alongside `auth:expired`.

**Tests added (4)**
- `test_subscription_service.py`: `test_consume_entry_unlimited_during_trial`, `test_consume_entry_blocks_after_limit` (200/200/200 then raises; counter capped), `test_consume_entry_unlimited_after_activation`.
- `test_api_main.py::test_entry_gate_api`: unlimited during trial → expire trial → 3 entries 200 → 4th **403** with `detail.code == "SUBSCRIPTION_REQUIRED"` → activate monthly → 200.

**Result**
- **117 passed** (113 + 4 new); `node --check` clean.
- Live uvicorn E2E (trial expired by editing the flat-file store while the server was **down**, then restarting — the running server caches records in memory, so mid-run file edits are invisible): status `entries_remaining: 3`, `is_limited: true`, `is_expired: true`; four `POST /expenses` → **200 200 200 403**; 403 body `{"detail":{"code":"SUBSCRIPTION_REQUIRED","message":"…"}}`; `POST /subscription {monthly}` → 200; next entry → **200**.

**Note:** the gating endpoint (`POST /expenses`) is a pre-existing stub — only its gate logic was added, per the plan (§ line 36). JWT tokens survived the server restart, confirming the signing key is process-stable.

**Phase 6 verified end-to-end.** Proceeding to Phase 7 (invitations + owner notifications).

## Phase 7 — Invitations + owner notifications  ✅  (done)

**Goal:** Email invitations become a real pending/accept/decline handshake, and the group owner is notified when an invitee responds (plan §5c lines 113–130).

**Backend**
- `constants/invitation_status.py` (new): `InvitationStatus` = `pending | accepted | rejected | expired`.
- `constants/notification_kind.py`: added `GROUP_INVITE_ACCEPTED`, `GROUP_INVITE_DECLINED`.
- `constants/system_config.py`: added `INVITATION_TTL_DAYS = 7`; `constants/__init__.py` re-exports `InvitationStatus` + `INVITATION_TTL_DAYS`.
- `storage_service/schema/invitation_record.py` (new): `InvitationRecord` (`group_id`, `inviter_id`, `invitee_email`, `invite_code`, `status`, `expires_at`); exported from `schema/__init__.py`; `flat_file.py` `_DEFAULT_TABLE_FILES` += `"invitation_record": "invitation_records.json"`.
- `models/invitation.py` (new): `Invitation` DTO; exported from `models/__init__.py`.
- `notification_service.py` (implemented from stub): `create_notification`, `list_notifications` (newest-first, `unread_only`), `mark_all_read`.
- `invitation_service.py` (new): `create_invitation` (validates email + inviter membership, idempotently reuses a live pending invite, 7-day TTL), `list_invitations_for_email` (pending only, drops expired unless `include_resolved`), `list_invitations_for_group`, `accept_invitation` (adds member via `group_service.add_member`, notifies `group.created_by` with `GROUP_INVITE_ACCEPTED`), `decline_invitation` (→ `rejected`, notifies `GROUP_INVITE_DECLINED`).
- `app/main.py`: registry += `invitation_record`; endpoints `POST /api/v1/groups/{group_id}/invitations`, `GET /api/v1/groups/{group_id}/invitations`, `GET /api/v1/users/me/invitations`, `POST /api/v1/invitations/{id}/accept`, `POST /api/v1/invitations/{id}/decline`; the two previously-stubbed notification endpoints now call `notification_service` (`GET /users/me/notifications`, `POST /users/me/notifications/read`). The existing `POST /groups/{id}/members` (direct add) is unchanged.

**Frontend**
- `index.html`: notifications dropdown `#notif-panel` (with `#notif-list` + `#notif-mark-read`) inside the topbar; the `#invites-list` "My invitations" card already existed.
- `css/app.css`: `.icon-btn--badge` (bell unread count via `data-count`), `.notif-panel` / `.notif-head` / `.notif-list` / `.notif-item` (+ `--unread`).
- `js/app.js`: invite-by-email now POSTs `/groups/{id}/invitations` (pending, not instant membership); `loadInvitations` / `refreshInviteCount` / `bindInvitationActions` (Accept/Decline with confirm-free toast); notifications via `loadNotifications` / `renderNotifications` / `setBellBadge` / `markNotificationsRead` / `toggleNotifPanel` and a `NOTIFICATION_LABELS` map; bell is revealed in `applyUserChrome`, loaded on boot + dashboard, panel closes on outside click; dashboard now fills `#stat-invites`.

**Tests added (12)**
- `test_invitation_service.py` (11): create pending / idempotent / membership required / input validation; accept joins + notifies owner; decline rejects + notifies without joining; accept requires matching email; double-accept rejected; expired rejected; resolved invites hidden from the pending list; notification create/list/mark-read lifecycle.
- `test_api_main.py::test_invitation_flow_api` (1): full HTTP flow — invite by email → invitee lists → accept → membership + owner `GROUP_INVITE_ACCEPTED` → mark-read empties unread; a second invite declined → owner `GROUP_INVITE_DECLINED`; re-accepting the declined invite → 422.

**Result**
- **129 passed** (117 + 12 new); `node --check` clean on `app.js` / `api.js` / `icons.js`.
- Live uvicorn E2E: invite → `pending`; guest sees 1 invitation; accept → `accepted` + owner `[GROUP_INVITE_ACCEPTED]`; `mark read` → `1`, unread → `0`; decline → `rejected` + owner2 `[GROUP_INVITE_DECLINED]`; `index.html` / `js/app.js` / `css/app.css` all 200.

**Phase 7 verified end-to-end.** Proceeding to Phase 8 (derived invite-code §5d).

## Phase 8 — Derived invite-code  ✅  (done)

**Goal:** Replace the random stored invite code with the deterministic pass-key `base32(sha256("<group_id>:<inviter_id>"))[:8]` (plan §5d), checkable without rotation.

**Backend (`group_service.py`)**
- New `derive_invite_code(group_id, inviter_id) -> str`: `base64.b32encode(sha256(f"{group_id}:{inviter_id}").digest()).decode()[:8].upper()` — stable and unique per (group, member) pair.
- `create_group`: stores `derive_invite_code(group_id, created_by)` instead of `secrets.token_hex(4)`.
- `generate_invite_code`: now returns `derive_invite_code(group_id, actor_user_id)` (the requesting member's own code); no mutation/rotation, no audit "UPDATE" write. Group-exists / not-SETTLED validation unchanged (membership is enforced at the endpoint via `verify_membership`).
- `join_group_by_invite`: recomputes `derive_invite_code(group_id, m)` for **every current member `m`** and admits the caller if the (case/whitespace-normalised) submitted code matches any, provided the group is `OPEN`. No stored-code comparison.
- Dropped the now-unused `import secrets`.

**Tests added (8)**
- `test_invite_code.py` (7): deterministic + correctly shaped (`base32(sha256(...))[:8].upper()`, 8 chars, base32 alphabet); varies by group and by member; `create_group` uses the derived code; `generate_invite_code` returns the actor's derivation; join succeeds with a *non-creator* member's derived code; join normalises case/whitespace; wrong / empty / foreign-group codes are rejected.
- `test_api_main.py::test_invite_code_join_api` (1): `POST /groups/{id}/invite` returns an 8-char code; a second user joins via `POST /groups/{id}/invite/accept` (lowercased) → member; bogus code → 404.

**Result**
- **137 passed** (129 + 8 new).
- Live uvicorn E2E: created group → stored code `VRCS2JCW` == `POST /invite` code == invitation DTO's `invite_code`; second user joined with the lowercased code → `members: 2`; two bogus codes → `404`.

**Phase 8 verified end-to-end.** Proceeding to Phase 9 (back-update `kharcha_tracker.yaml` + `README.md`, full E2E, entire suite green).

## Phase 9 — YAML + README back-update, final E2E  ✅  (done)

**Goal:** Back-update `kharcha-tracker.yaml` with implementation/regeneration
metadata for everything built in Phases 3–8, refresh `README.md`, run a full
end-to-end pass, and keep the entire suite green.

### YAML back-update (`kharcha-tracker.yaml`: 99 → 125 nodes)

- **26 new nodes**: enums `plan`, `plan_status`, `invitation_status`; DTO
  `invitation`; record `invitation_record`; constant module `auth_token_config`;
  function nodes `derive_invite_code`, `refresh_session`, `request_magic_link`,
  `verify_magic_link`, `create_notification`, `mark_all_read`;
  `subscription_service` (+`get_status`/`activate`/`consume_entry`);
  `invitation_service` (+`create_invitation`/`list_invitations_for_email`/
  `list_invitations_for_group`/`accept_invitation`/`decline_invitation`); and
  the frontend — `frontend_js` folder module under `frontend_service` hosting
  `browser-js` leaves `app_js` / `api_js` / `icons_js`.
- **Extended**: `notification_kind` (+`GROUP_INVITE_ACCEPTED`/
  `GROUP_INVITE_DECLINED`), `system_config` (+`TRIAL_PERIOD_DAYS`,
  `POST_TRIAL_ENTRY_LIMIT`, `INVITATION_TTL_DAYS`), `base`/`user_record`,
  `group_service`, `auth_service`, `notification_service`, and `main`.
  `frontend_service.status` `unsplit` → `implemented`.
- Each generated node received an `implementation` block (path, module,
  symbols/functions, source contract, regeneration guidance).
- **Fix during verification**: `main.implementation.functions` initially listed
  `refresh_session` / `get_subscription`; corrected to the real handler names
  `refresh_tokens` / `get_subscription_status` (the `auth_service` node keeps its
  genuine `refresh_session`).

**Judgement call:** the blueprint tree has no fully-conformant home for the three
`browser-js` files, so they were grouped under an intermediate `frontend_js`
`module` (folder) node; documented rather than invented into `data-model`/
`contract` (both of which are `layout: file`).

**Validation:** YAML parses clean; 125 nodes, no duplicate ids, no dangling
`parent` / `dependencies` / `maps_to` references. (`type: int` on constants was
left as-is — it matches the file's existing convention and AGENTS.md §6 only
governs params/returns/model_fields/contract/class fields, not `constants[].type`.)

### README.md

- §2 filesystem tree: added `invitation.py`, `invitation_record.py`, the three
  new constants modules, `subscription_service.py`, `invitation_service.py`, and
  the `frontend_service` files; `(stub)` markers dropped where implemented.
- `Constants implementation`: + `plan`/`plan_status`/`invitation_status` and a
  note on the trial/subscription/invitation constants.
- §3 / §6 model trees: `invitation.py` + `invitation_record.py`; `main.py`
  import block + `Invitation`.
- §7 rewritten — services are now implemented for auth, groups, expenses,
  notifications, subscriptions, invitations; only the settlement engine and the
  `sql_lite`/`postgres` adapters remain stubbed.
- §8 API routes: + `auth/refresh`, `auth/magic-link(+verify)`,
  `users/me/subscription`, `subscription`, `users/me/invitations`, group
  `invitations`, `invitations/{id}/accept|decline`; new subsections for the
  frontend SPA layout and feature notes (sessions, magic-link, trial/gating,
  derived codes, invitations/notifications).
- §15 sequence: inserted subscription/invitation services + frontend SPA and a
  "Status" line pointing at §7.

### Tests + live E2E

- **137 passed**, 70 warnings (unchanged — Phase 9 added no code, only docs/Blueprint).
- Final uvicorn smoke (`p9_smoke.py`, port 8099): static `/`, `/css/app.css`,
  `/js/app.js` → 200; refresh rotation 200 + reuse → 401; magic-link issues a
  token (202) and `verify` auto-creates a session (200); subscription `none` →
  activate `yearly` (200); derived code `ZFA4QZM2` == stored == generated, and
  joined with the lowercased code → 2 members; invite-by-email → `pending`,
  non-addressee accept → 403, invitee accept → `accepted`, owner notification
  `GROUP_INVITE_ACCEPTED`, mark-all-read → 0 unread; `POST /expenses` while
  trialing → 200.

**Phase 9 complete — all 9 phases verified; blueprint, README, suite, and live
E2E are consistent.**
