# Kharcha Tracker Frontend Map

## 1. Purpose and usage

This file is the navigation index for the Kharcha Tracker frontend.
Use it to identify the small set of files relevant to a targeted change.
The implementation and migration rules are defined in
`kharcha-frontend-refactoring.md` (the approved specification in this
directory).

**Navigation rule:** For a targeted screen change, start with the mapped
HTML view, then inspect its mapped feature module. Inspect the relevant
CSS section only when the change affects presentation. Follow the
cross-feature dependencies below when a behaviour is shared.

## 2. Migration status

| Item | Status / value |
|---|---|
| Overall status | PHASE 2 COMPLETE — views extracted, shell + async router running; feature extraction pending |
| Last completed phase | Phase 2 (HTML view extraction and view loader) |
| Current phase | Phase 3 (core/UI utilities) — not started |
| Last updated | 2026-11-03 |
| Source baseline | commit `f1a9b55` on branch `refactor-frontend-service`; `index.html`, `css/app.css`, `js/app.js`, `js/api.js`, `js/charts.js`, `js/icons.js` |
| Baseline verification | all four app JS files pass `node --check`; page boots via file:// in headless Chromium, renders 15 nav links, `#app-shell` hidden, `#view-login` visible |
| Phase 2 verification | all eight views load from separate files; direct navigation and refresh work; rapid navigation leaves only the final view; views are reused; login screenshots byte-identical to baseline |

Status meanings:

- `CURRENT`: verified location in the implementation being inspected.
- `MIGRATED`: responsibility has been moved and verified in its target.
- `PENDING`: planned or not yet verified; do not assume it exists.

## 3. Route and view map

Source: hash routes parsed by `currentRoute()` in `js/core/router.js`. Each
screen now lives in its own file under `views/` and is fetched on first use.

| Route | Root DOM ID | View HTML file | Container | Feature module(s) | Status |
|---|---|---|---|---|---|
| `login` | `view-login` | `views/auth/login.html` | `auth-view-container` | `features/auth.js` | HTML MIGRATED; feature MIGRATED |
| `signup` | `view-signup` | `views/auth/signup.html` | `auth-view-container` | `features/auth.js` | HTML MIGRATED; feature MIGRATED |
| `dashboard` | `view-dashboard` | `views/dashboard/dashboard.html` | `view-container` | `features/dashboard.js` | HTML MIGRATED; feature MIGRATED |
| `groups` | `view-groups` | `views/groups/groups.html` | `view-container` | `features/groups.js`, `features/invitations.js`, `features/settlement.js`, `features/expenses.js` | HTML MIGRATED; features MIGRATED |
| `expenses` | `view-expenses` | `views/expenses/expenses.html` | `view-container` | `features/expenses.js` | HTML MIGRATED; feature MIGRATED |
| `activity` | `view-activity` | `views/activity/activity.html` | `view-container` | `features/activity.js` | HTML MIGRATED; feature MIGRATED |
| `settings` | `view-settings` | `views/settings/settings.html` | `view-container` | `features/settings.js`, `features/subscription.js` | HTML MIGRATED; features MIGRATED |
| `subscription` | `view-subscription` | `views/subscription/subscription.html` | `view-container` | `features/subscription.js` | HTML MIGRATED; feature MIGRATED |

`magic` is a special authentication action, not a separate HTML view.
`handleMagicLink()` is registered with the router via `setMagicHandler()`;
it mounts/uses the login view, verifies the token, establishes the session
and navigates to `dashboard`.

Unknown routes redirect to `dashboard` when authenticated (a token
exists) and to `login` otherwise. Login/Signup redirect to `dashboard`
when a token exists and `Auth.user` is set.

Important screen boundaries:

- Invitations are a section within Groups, not a separate route.
- Group details are rendered within Groups group cards, not a separate
  route.
- Settlement UI is rendered within Groups.
- Notifications belong to the persistent application shell, not a
  standalone route.

## 4. Persistent application shell

Verified against the current `index.html` (Phase 2). The shell now contains
both mount containers, and the route views live in `views/`.

| Element / responsibility | ID or selector | Status / notes |
|---|---|---|
| Authentication view mount container | `auth-view-container` | CURRENT in `index.html`; receives login/signup views |
| Authenticated application shell | `app-shell` | CURRENT in `index.html`, `class="app-shell"`, starts `hidden` |
| Main content element | `main` / `#main` | CURRENT; contains `#view-container` |
| Protected view mount container | `view-container` | CURRENT in `index.html`; receives protected views |
| Sidebar navigation | `#sidebar` | CURRENT; populated by `renderNav()` |
| Top navigation | `#topnav` | CURRENT; populated by `renderNav()` |
| Bottom tab navigation | `#tabbar` | CURRENT; populated by `renderNav()` |
| Toast root | `#toast` | CURRENT |
| Modal root | `#modal-root` | CURRENT; starts `hidden` |
| Header / topbar | `header.topbar` | CURRENT (no ID) |
| Brand link | `a.brand` | CURRENT; links to `#/dashboard` |
| User identity name | `#user-chip-name` | CURRENT |
| User avatar initials | `#user-avatar` | CURRENT |
| Notification bell | `#btn-bell` | CURRENT; starts `hidden`, icon injected at boot |
| Logout (header) | `#btn-logout` | CURRENT; icon injected at boot |
| Notification panel | `#notif-panel` | CURRENT; starts `hidden` |
| Notification list | `#notif-list` | CURRENT |
| Mark-all-read | `#notif-mark-read` | CURRENT |
| Sidebar footer email | `#nav-foot-email` | DYNAMIC; created by `renderNav()` |

## 5. JavaScript module map

| File | Responsibility | Status |
|---|---|---|
| `js/app.js` | Startup, shell wiring, route registration, session bootstrap, router start (70 lines) | MIGRATED (composition/startup only) |
| `js/api.js` | HTTP client, API calls and token refresh | CURRENT; exports `ApiError`, `Auth`, `api` |
| `js/charts.js` | SVG chart generation (`Charts`) | CURRENT; exports `Charts` |
| `js/icons.js` | SVG icon definitions (`Icons`) | CURRENT; exports `Icons` |
| `js/core/dom.js` | DOM helpers such as `$` and `$all` | MIGRATED |
| `js/core/state.js` | Current user and shared application caches | MIGRATED |
| `js/core/view-loader.js` | Fetch, validate, mount and cache route HTML | CURRENT |
| `js/core/router.js` | Hash routing, route registry, guards and navigation sequencing | CURRENT |
| `js/ui/modal.js` | Generic modal open/close and Escape handling | MIGRATED |
| `js/ui/toast.js` | Toast display | MIGRATED |
| `js/ui/forms.js` | Shared form error and busy-state helpers | MIGRATED |
| `js/features/auth.js` | Login, signup, magic-link and session flows | MIGRATED |
| `js/features/dashboard.js` | Dashboard data, rendering and bindings | MIGRATED |
| `js/features/groups.js` | Group cards, group operations and notes | MIGRATED |
| `js/features/expenses.js` | Expense rendering, forms, splits and CRUD | MIGRATED |
| `js/features/settlement.js` | Settlement and payout UI/actions | MIGRATED |
| `js/features/invitations.js` | Invitation list and actions | MIGRATED |
| `js/features/notifications.js` | Notification rendering and actions | MIGRATED |
| `js/features/activity.js` | Activity loading, filters, pagination and CSV export | MIGRATED |
| `js/features/settings.js` | Settings screen initialization | MIGRATED |
| `js/features/subscription.js` | Subscription status and plan actions | MIGRATED |
| `js/utils/formatters.js` | Shared date/amount formatting | MIGRATED |
| `js/utils/html.js` | HTML escaping helper(s) | MIGRATED |

Current `js/app.js` is a module that imports router/API/chart/icon modules
and runs `boot()` on `DOMContentLoaded`. Important anchors:

- Router constants now live in `js/core/router.js` (`NAV_ITEMS`,
  `ROUTE_TITLES`, `PROTECTED_ROUTES`).
- Per-route registration happens in `boot()` via `registerRoute(route, {mount, onEnter})`.
- Route enter hooks are in their respective feature modules.
- Per-route mount functions are in their respective feature modules.
- Startup: `boot()` is the last function in the file.
- Section markers in `app.js`: Shell icons, global listeners, route
  registration, session bootstrap, router start.
- `bindCrossScreenExpenseActions()` is bound once at boot on `#main`.

## 6. Shared state ownership

All shared state has been migrated to `core/state.js` during Phase 3. Line numbers refer to the current (post-Phase-6) state of the implementation.

| State / data | Current variable (current line) | Owner / notes |
|---|---|---|
| Current authenticated user | `currentUser` (16) | `core/state.js` (target) |
| Magic-link in-flight guard | `magicInFlight` (128) | `features/auth.js` (target) |
| Toast timer | `toastTimer` (31) | `ui/toast.js` (target) |
| Dashboard selected group | `dashGroupId` (424) | `state.dashboard.groupId` (target) |
| Dashboard chart range | `dashRange` (425) | `state.dashboard.range` (target) |
| Groups cache | `groupsCache` (682) | `core/state.js` (target) |
| Group members cache | `membersCache` (683) | `core/state.js` (target) |
| Group expenses cache | `expensesCache` (684) | `core/state.js` (target) |
| Settlement preview cache | `settleCache` (685) | `core/state.js` (target) |
| Group notes cache | `notesCache` (687) | `core/state.js` (target) |
| Expenses screen selected group | `expensesGroupId` (1202) | `state.expenses.groupId` (target) |
| Last-used expense group key | `LAST_EXPENSE_GROUP_KEY` (1205) | `features/expenses.js` (target); localStorage key must not change |
| Activity group list | `activityGroups` (2308) | `state.activity.groups` (target) |
| Activity selected group | `activityGroupId` (2309) | `state.activity.groupId` (target) |
| Activity page | `activityPage` (2310) | `state.activity.page` (target) |
| Activity total | `activityTotal` (2311) | `state.activity.total` (target) |
| Authentication/session token handling | `Auth` in `js/api.js` | Existing owner; preserve keys and refresh behaviour |
| View mounted/loading state | `mountedViews`, `inFlightViews` in `js/core/view-loader.js` | CURRENT owner |
| Active route/navigation sequence | `navigationSequence`, `routeFeatures`, `mountedFeatures` in `js/core/router.js` | CURRENT owner |

localStorage keys owned by `api.js`: `kharcha_access_token`, `kharcha_refresh_token`, `kharcha_user`. Additional key owned by expenses: `kharcha_last_expense_group`.

## 7. API client

- **Current file:** `js/api.js`
- **Responsibility:** HTTP/API access and existing token-refresh logic.
- **Rule:** Feature modules import the API client; they must not duplicate
  HTTP or token-refresh implementations.
- **Status:** CURRENT SOURCE FILE; exports `ApiError`, `Auth` and `api` as
  an ES module (added in Phase 1).
- **Public surface:** `api` (`get`, `getMeta`, `post`, `patch`, `del`),
  `Auth` (`access`, `refresh`, `user`, `setSession`, `saveUser`, `clear`),
  `ApiError`. `apiFetch`, `tryRefresh`, `tokenExpirySeconds`,
  `accessTokenNeedsRefresh` are internal.
- **Base path:** `/api/v1`.
- **Events dispatched:** `auth:expired`, `subscription:required`.

Endpoint families used by the frontend (verified in `js/app.js`):

| Family | Methods / paths |
|---|---|
| Session | `GET /users/me` |
| Auth | `POST /auth/login`, `POST /auth/signup`, `POST /auth/magic-link`, `POST /auth/magic-link/verify` |
| Subscription | `GET /users/me/subscription`, `POST /subscription` |
| Groups | `GET /groups`, `GET /groups?sort=activity`, `POST /groups`, `DELETE /groups/{id}` |
| Group membership | `GET /groups/{id}/members`, `DELETE /groups/{id}/members/me` |
| Invitations | `POST /groups/{id}/invitations`, `POST /groups/{id}/invite/accept`, `POST /groups/{id}/invite`, `GET /users/me/invitations`, `POST /invitations/{id}/accept`, `POST /invitations/{id}/decline` |
| Expenses | `GET /groups/{id}/expenses`, `POST /expenses`, `PATCH /expenses/{id}`, `DELETE /expenses/{id}` |
| Notes | `GET /groups/{id}/notes`, `POST /groups/{id}/notes`, `PATCH /groups/{id}/notes/{noteId}`, `DELETE /groups/{id}/notes/{noteId}` |
| Settlement | `GET /groups/{id}/settle`, `POST /groups/{id}/settle`, `POST /groups/{id}/settle/me`, `POST /groups/{id}/payments` |
| Payouts | `POST /groups/{id}/payouts/{payoutId}/{paid|settled|remind|failed|cancel|escalate}`, `POST /groups/{id}/payouts/{payoutId}/expenses` |
| Notifications | `GET /users/me/notifications`, `POST /users/me/notifications/read` |

## 8. Charts and icons

| Concern | File | Responsibility | Status |
|---|---|---|---|
| Charts | `js/charts.js` | SVG chart generation; exports `Charts` with `barChart`, `stackedBarChart`, `pieChart`, `color`, `colorFor` | CURRENT |
| Icons | `js/icons.js` | SVG icon strings; exports `Icons` with dashboard, groups, settings, logout, bell, plus, copy, check, x, mail, alert, card, link, refresh, key, trash, edit, exit, clock, download, shield | CURRENT |

Screen-specific icon initialization now happens in the owning route's
`mount()` function: login `#magic-icon`; groups `#btn-new-group-icon`,
`#join-icon`, `#invite-icon`; expenses `#expenses-add-icon`; activity
`#activity-export-icon`. Shell icons (`#btn-bell`, `#btn-logout`) are
initialized once in `boot()`.

## 9. CSS map

**Stylesheet:** `css/app.css` (retained as one consolidated stylesheet).

| Screen / concern | Actual CSS heading or selector group | Status |
|---|---|---|
| Tokens, reset and typography | `/* ============================================================` opening block (custom properties) | CURRENT |
| Icons | `/* ============================ Icons ============================ */` | CURRENT |
| Buttons | `/* ============================ Buttons ============================ */` | CURRENT |
| Forms | `/* ============================ Forms ============================ */` | CURRENT |
| Cards | `/* ============================ Cards ============================ */` | CURRENT |
| Auth | `/* ============================ Auth views ============================ */` | CURRENT |
| Shell / navigation | `/* ============================ App shell ============================ */`, `/* Tablet top nav ... */`, `/* Sidebar ... */`, `/* Mobile bottom tab bar */`, `/* tabbar flavour: ... */` | CURRENT |
| Dashboard | `/* ============================ Dashboard ============================ */` | CURRENT |
| Groups | `/* ============================ Groups ============================ */` | CURRENT |
| Subscription / plans | `/* ============================ Plans ============================ */` | CURRENT |
| Toast | `/* ============================ Toast ============================ */` | CURRENT |
| Charts | `/* ============================ Charts ============================ */` | CURRENT |
| Modal | `/* ============================ Modal ============================ */` | CURRENT |
| Responsive | `/* ============================ TABLET ≥640px ... */`, `/* ============================ DESKTOP ≥1024px ... */`, `/* ============================ Narrow phones ... */` | CURRENT |
| Members + expenses | `/* ============================ Members + expenses ============================ */` | CURRENT |
| Group notes | `/* ------------------------------ Group notes (E5) ------------------------------ */` | CURRENT |
| Settlement | `/* ------------------------------ Settlement ------------------------------ */` | CURRENT |
| Corpus fund | `/* E6: corpus fund panel ... */` | CURRENT |
| Activity | `/* ============================ Activity log ============================ */` | CURRENT |

`[hidden] { display: none !important; }` is present and must be preserved.

Phase 2 changed one selector only: `.main > .view` became
`.main > #view-container > .view`, to match the new protected-view
container. No other CSS rules changed.

## 10. Cross-feature dependencies

- The Groups screen contains group cards, invitation content, expense
  content and settlement UI. These are not separate routes.
- The Expenses screen and Groups screen both use expense behaviour. In
  the current source, `bindCrossScreenExpenseActions()` registers a
  delegated click handler on `#main` for expense actions (edit, delete,
  open split, toggle/cancel form, add-expense navigation). It is bound
  once at boot, before any route is mounted, so it still works when the
  Expenses view is opened directly. Preserve exactly one such handler on
  a persistent ancestor.
- `#groups-list` has its own delegated `click` handler (group, note and
  settlement card actions), plus delegated `submit` handlers for notes and
  payout-expense linking, and a delegated `change` handler for ad-hoc
  payment filtering. These are attached by `mountGroups()` only after the
  Groups view is mounted, exactly once (route features are recorded in
  `mountedFeatures`).
- Settings displays subscription summary information by calling
  `GET /users/me/subscription`; the subscription module also updates the
  shared `#stat-plan` element on Dashboard.
- Notifications are part of the persistent shell and must not be
  initialized as a route view.
- Authentication state affects access to protected routes. Keep route
  guards and session behaviour consistent with the existing app.
- Dashboard `#stat-plan` and `GET /users/me/subscription` are shared with
  Settings and Subscription; do not duplicate the request/refresh logic.

## 11. Targeted change workflow

For a change limited to one screen:

1. Find the route in Section 3 and open its HTML file.
2. Open only the feature module(s) mapped to that screen.
3. Follow Section 10 if the behaviour crosses feature boundaries.
4. Inspect the relevant verified CSS section only if presentation
   changes are involved.
5. Inspect `api.js`, `charts.js`, `icons.js` or shared core/UI modules
   only when the mapped feature imports or uses them.
6. Update this map if the change affects file ownership, routes, IDs,
   state or dependencies.

## 12. Update log

| Date | Phase | Files / responsibilities changed | Verification |
|---|---|---|---|
| 2026-11-03 | Phase 0 | Initial source inventory and map created from baseline commit `f1a9b55` | Verified IDs, routes, state, endpoints, CSS sections, syntax check, headless boot |
| 2026-11-03 | Phase 1 | `js/api.js`, `js/charts.js`, `js/icons.js` now export modules; `js/app.js` imports them; `index.html` loads one module script | `node --check`, headless boot, no console errors |
| 2026-11-03 | Phase 2 | Eight views moved to `views/`; shell reduced to containers; `js/core/view-loader.js` and `js/core/router.js` added; `app.js` boot/route registration rewired; one CSS selector updated | All views load from separate files; direct navigation, refresh, guards, view reuse, rapid navigation, form-state persistence, error UI and screenshot parity verified |
| 2026-11-03 | Phase 3 | New core/UI/utils modules: `dom.js`, `state.js`, `forms.js`, `toast.js`, `modal.js`, `html.js`, `formatters.js`; `app.js` imports them | All new modules pass `node --check`; no behavioural changes |
| 2026-11-03 | Phase 4 | Feature modules: `auth.js`, `dashboard.js`, `settings.js`, `subscription.js`; `app.js` removes their logic and registers routes | All four modules pass `node --check`; route registration works; screens render identically |
| 2026-11-04 | Phase 5 | Feature modules: `groups.js`, `invitations.js`, `expenses.js`, `settlement.js`, `notifications.js`, `activity.js`; `app.js` rewritten as composition/startup only | All six modules pass `node --check`; import graph has **no cycles**; `app.js` passes `node --check`; 70 lines only; cross-screen expense delegation bound once; all route views load/render identically to Phase 2 baseline |
| 2026-11-04 | Phase 6 | `graph.yaml` schema updates: `contract`, `class` protocol nodes, `union` type descriptors, `implements` edges; type descriptor validation per §6; cross-language `boundary: service` edges | Schema validation: all type descriptors resolve; no `any`/bare `list`; union options valid; implements targets are contracts; layout protocol-legal; http_method/route paired; cross-language edges declare boundary |
| 2026-11-04 | Phase 7 | `js/features/dashboard.js` — added `$all` to DOM helper import; `js/charts.js` — re-added stacked bar total labels above each bar | `node --check` on affected modules; bars render with value labels; click behavior preserved |
