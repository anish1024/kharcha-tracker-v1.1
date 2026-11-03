# Kharcha Tracker Frontend Refactoring — Progress Report

Specification: `kharcha-frontend-refactoring.md`
Map: `FRONTEND_MAP.md`
Branch: `refactor-frontend-service`
Baseline commit: `f1a9b55`

Phase reports use the Section 16 completion-report format.

---

## Phase 0 — Baseline and inventory

**Phase:** 0 — Baseline verification and source inventory

**Files created:**
- `FRONTEND_MAP.md`

**Files modified:** none

**Functions moved:** none

**Functions removed from old location:** none

**Imports/exports changed:** none

**Behaviour verified:**
- Baseline source matches the specification's description: one `index.html`
  (459 lines) containing all eight views inline, a single `js/app.js`
  (2,748 lines), plus `js/api.js` (179), `js/charts.js` (188) and
  `js/icons.js` (80).
- All four JS files parse under `node --check`.
- The untouched app boots from `file://`: `#app-shell` hidden, login view
  visible, 15 nav links rendered.
- Route list, guards, DOM IDs, `data-*` attributes, shared state variables,
  API endpoint families, localStorage keys and CSS section headings
  recorded in `FRONTEND_MAP.md` from actual source.

**Tests run:**
- `node --check` on `app.js`, `api.js`, `charts.js`, `icons.js`.
- Headless Chromium boot of `index.html` via `file://` with
  `--allow-file-access-from-files`; inspected rendered DOM.
- Desktop (1440×900) and mobile (390×844) baseline screenshots captured
  for later byte comparison.
- Source inspection of routes, handlers, state, endpoints and CSS headings.

**Known issues:**
- Headless Chromium in this environment cannot load `http://` URLs (hangs),
  so browser verification uses `file://` with file access enabled. This
  exercises the same ES modules and `fetch()` paths but not server MIME
  types.
- Existing source gap noted for later phases: there is no `#form-profile`
  submit handler; the spec does not require inventing one.
- Unrelated pre-existing working-tree changes outside this directory
  (`kharcha-android/.../ApiConstants.kt`, `kharcha-ui-refactor-html.md`)
  were left untouched.

**Phase exit criteria:** PASS

---

## Phase 1 — Native ES modules

**Phase:** 1 — Convert script loading to native ES modules

**Files created:** none

**Files modified:**
- `js/api.js` — added `export { ApiError, Auth, api };`
- `js/charts.js` — added `export { Charts };`
- `js/icons.js` — added `export { Icons };`
- `js/app.js` — added `import { Icons } from "./icons.js";`,
  `import { Auth, api } from "./api.js";`, `import { Charts } from "./charts.js";`
- `index.html` — replaced four classic `<script src>` tags with one
  `<script type="module" src="js/app.js"></script>`

**Functions moved:** none

**Functions removed from old location:** none

**Imports/exports changed:**
- `api.js` now exports `ApiError`, `Auth`, `api`.
- `charts.js` now exports `Charts`.
- `icons.js` now exports `Icons`.
- `app.js` imports those three modules.
- No internal logic or public method signatures changed.

**Behaviour verified:**
- `app.js` runs as a module; `Icons`, `Auth` and `api` resolve through
  imports rather than globals.
- Login screen still renders, nav still renders, shell remains hidden on
  the unauthenticated route.
- No console errors, syntax errors or failed module loads.

**Tests run:**
- `node --check` on all four JS files (copied to `.mjs` for module parsing).
- Headless Chromium `file://` DOM dump: 15 nav links, `#view-login`
  visible, no `CONSOLE`/`Uncaught`/module-load errors in stderr logging.

**Known issues:**
- `app.js` is still the feature monolith at this phase; only module
  boundaries were introduced.

**Phase exit criteria:** PASS

---

## Phase 2 — HTML view extraction and view loader

**Phase:** 2 — Extract route views, create the persistent shell, add view
loader and async router

**Files created:**
- `views/auth/login.html`
- `views/auth/signup.html`
- `views/dashboard/dashboard.html`
- `views/groups/groups.html`
- `views/expenses/expenses.html`
- `views/activity/activity.html`
- `views/settings/settings.html`
- `views/subscription/subscription.html`
- `js/core/view-loader.js`
- `js/core/router.js`

**Files modified:**
- `index.html` — reduced to the persistent shell: `#auth-view-container`,
  `#app-shell` (`header.topbar`, `#sidebar`, `#main` > `#view-container`,
  `#tabbar`), `#toast`, `#modal-root`, single module entry.
- `js/app.js` — removed the inline router/nav copies, split
  `bindAuthForms()` into `bindLoginForm()` / `bindSignupForm()`, added
  per-route `mount*()` functions, moved cross-screen expense delegation into
  `bindCrossScreenExpenseActions()` bound once at boot, boot now registers
  routes and starts the router.
- `css/app.css` — one selector change: `.main > .view` became
  `.main > #view-container > .view` to match the new container nesting.

**Functions moved:**
- `NAV_ITEMS`, `ROUTE_TITLES`, `PROTECTED_ROUTES`, `navItemHtml`,
  `renderNav`, `setActiveNav`, `router` → `js/core/router.js`.
- View fetch/mount/cache responsibility → `js/core/view-loader.js`.
- Initialization previously performed unconditionally in `boot()` was
  redistributed to per-route `mount*()` functions:
  `mountLogin`, `mountSignup`, `mountDashboard`, `mountGroups`,
  `mountExpenses`, `mountActivity`, `mountSettings`, `mountSubscription`.
- Screen icon injection moved from boot to the owning route's mount:
  login `#magic-icon`; groups `#btn-new-group-icon`, `#join-icon`,
  `#invite-icon`; expenses `#expenses-add-icon`; activity
  `#activity-export-icon`. Shell icons (`#btn-bell`, `#btn-logout`)
  remain at boot.
- `bindAuthForms()` split into `bindLoginForm()` and `bindSignupForm()`
  so each auth view can mount independently.
- Cross-screen `#main` expense click delegation extracted to
  `bindCrossScreenExpenseActions()` and bound once at boot, so expense
  edit/delete/split/add-navigation still work when Expenses is the first
  mounted protected view.

**Functions removed from old location:**
- Inline router/nav functions listed above were deleted from `app.js`.
- The original combined `bindAuthForms()` was removed and replaced by the
  two per-view functions.
- The original unconditional `boot()` binding block for screen-owned icons
  and feature bindings was removed and replaced by route registration.
- The inline `#main` expense handler inside `bindGroupActions()` was
  removed; it now lives in `bindCrossScreenExpenseActions()`.

**Imports/exports changed:**
- `js/app.js` now imports `registerRoute`, `setMagicHandler`, `renderNav`,
  `startRouter`, `navigate`, `currentRoute` from `./core/router.js`.
- `js/core/router.js` imports `Icons` from `../icons.js`, `Auth` from
  `../api.js`, and `ensureViewMounted` from `./view-loader.js`.
- `js/core/view-loader.js` exports `getMountedView`, `getViewSpec`,
  `ensureViewMounted`.

**Behaviour verified:**
- All eight route views are fetched from separate HTML files and mounted
  into the correct container.
- Each extracted view's DOM structure (tags, attributes, text, tails)
  matches the original inline section exactly; verified with an `lxml`
  structural signature comparison (login 23, signup 21, dashboard 43,
  groups 61, expenses 19, activity 26, settings 29, subscription 41
  elements — all identical).
- Persistent shell markup (`header.topbar`, `#sidebar`, `#tabbar`,
  `#toast`, `#modal-root`) matches the baseline; `#app-shell` attributes
  unchanged.
- Direct navigation and refresh on every protected route mount that view
  exactly once (duplicate count = 1 for each).
- Unauthenticated protected routes redirect to Login.
- Authenticated Login/Signup redirect to Dashboard.
- Rapid route changes (login → signup → login and groups → activity →
  expenses) leave only the final view visible.
- Previously mounted views are reused; no duplicate view elements.
- Form input state survives navigating away and back
  (`#login-email` value retained).
- A failed view fetch renders a visible error box with `data-view-error`
  and a Retry button; failed loads are not cached.
- Login screenshots at 1440×900 and 390×844 are byte-identical to the
  Phase 0 baseline (same MD5).
- No console errors during route sweeps.

**Tests run:**
- `node --check` on `app.js` and both `js/core/*.js` files.
- Headless Chromium `file://` DOM dumps for: default boot, `#/signup`,
  `#/dashboard` unauthenticated, all six protected routes with fake auth,
  direct reload on each protected route, rapid-route sequences, preserved
  form state, and a deliberately missing view file.
- Structural DOM comparison of all eight views against baseline
  `index.html`.
- Desktop/mobile login screenshot MD5 comparison against baseline.

**Known issues:**
- Live backend API calls were not exercised (no backend running in this
  environment); network-dependent rendering and mutation flows remain to
  be regression-tested in later phases.
- `js/app.js` still contains all feature logic (dashboard, groups,
  expenses, settlement, invitations, notifications, activity,
  subscription). Feature extraction is Phase 4/5 work.
- `js/core/dom.js`, `js/core/state.js`, `js/ui/*`, `js/features/*`,
  `js/utils/*` are not created yet.
- No `#form-profile` submit handler exists in the original source; left
  as-is per the specification's "do not invent" rule.

**Phase exit criteria:** PASS
