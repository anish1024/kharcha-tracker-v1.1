# AGENTS.md — Kharcha Tracker Frontend

This file is the agent guide for the `frontend_service/` directory.
It is intentionally compact — read it when you need to make a targeted
change without loading the whole project.

## Start here

- `README.md` — project overview and route map.
- `kharcha-frontend-refactoring.md` — the approved specification (what
  was agreed, what the architecture is, what the phases are).
- `FRONTEND_MAP.md` — the verified navigation index (DOM IDs, state
  fields, endpoints, CSS sections, cross-feature dependencies).
- `PROGRESS.md` — phase-by-phase completion reports.

## Where to find things

| Concern | File |
|---|---|
| Entry point / shell / routes | `js/app.js` |
| HTTP + Auth | `js/api.js` |
| SVG charts | `js/charts.js` |
| SVG icons | `js/icons.js` |
| DOM helpers (`$`, `$all`) | `js/core/dom.js` |
| Shared state | `js/core/state.js` |
| View loader | `js/core/view-loader.js` |
| Router | `js/core/router.js` |
| Toast | `js/ui/toast.js` |
| Form helpers | `js/ui/forms.js` |
| Modal | `js/ui/modal.js` |
| HTML escaping | `js/utils/html.js` |
| Formatting | `js/utils/formatters.js` |
| Auth feature | `js/features/auth.js` |
| Dashboard feature | `js/features/dashboard.js` |
| Groups feature | `js/features/groups.js` |
| Expenses feature | `js/features/expenses.js` |
| Settlement feature | `js/features/settlement.js` |
| Invitations feature | `js/features/invitations.js` |
| Notifications feature | `js/features/notifications.js` |
| Activity feature | `js/features/activity.js` |
| Settings feature | `js/features/settings.js` |
| Subscription feature | `js/features/subscription.js` |
| Route HTML views | `views/<route>/<route>.html` |
| Stylesheet | `css/app.css` |

## Common tasks

### Fix a bug on one screen

1. Open the route's HTML view (see `FRONTEND_MAP.md` §3).
2. Open the owning feature module (see `FRONTEND_MAP.md` §5).
3. If the behaviour crosses feature boundaries, read `FRONTEND_MAP.md` §10.

### Add a new route

1. Add the view HTML to `views/<route>/<route>.html`.
2. Create a feature module under `js/features/<route>.js` with `mount`
   and `onEnter` exports.
3. Register the route in `js/app.js` via `registerRoute()`.
4. Update `FRONTEND_MAP.md` §3 and §5.

### Add a new shared utility

1. Create the module under `js/utils/` or `js/core/`.
2. Export it.
3. Import it in the consuming feature modules.
4. Update `FRONTEND_MAP.md` §5.

### Change the stylesheet

1. Open `css/app.css`.
2. Find the section heading for the affected screen (see `FRONTEND_MAP.md` §9).
3. Preserve `[hidden] { display: none !important; }`.

## Rules

- Do not invent new top-level or `interface.*` fields in `graph.yaml`.
- Do not introduce template languages, bundlers or frameworks.
- Do not duplicate HTTP or token-refresh logic — `api.js` owns that.
- Do not create circular imports between feature modules.
- Do not invent a profile-update API — there is no `#form-profile`
  submit handler in the original source.
- Verify changes with `node --check` on every touched JS file.