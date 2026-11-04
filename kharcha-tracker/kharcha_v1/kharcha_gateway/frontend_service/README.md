# Kharcha Tracker — Frontend Service

This package holds the vanilla HTML/CSS/JS frontend that talks to the
Kharcha Tracker backend API. It is being refactored incrementally from a
single 2,748-line `js/app.js` into ES modules — see
`kharcha-frontend-refactoring.md` (the approved spec) and
`PROGRESS.md` (phase-by-phase completion reports).

## Quick start for an agent

For any targeted fix:

1. Start here (`README.md` → this file) to orient.
2. Jump to the owning module and read its `AGENT.md` for DOM IDs,
   state fields, endpoints, CSS sections and file paths.
3. For a UI change, open the view HTML first, then the owning feature.
4. For chart changes, read `js/charts.js` plus the owning feature.

## Route → view → feature map

| Route | View HTML | Feature module |
|---|---|---|
| login / signup / magic | `views/auth/login.html`, `views/auth/signup.html` | `js/features/auth.js` |
| dashboard | `views/dashboard/dashboard.html` | `js/features/dashboard.js` |
| groups | `views/groups/groups.html` | `js/features/groups.js`, `settlement.js`, `invitations.js`, `expenses.js` |
| expenses | `views/expenses/expenses.html` | `js/features/expenses.js` |
| activity | `views/activity/activity.html` | `js/features/activity.js` |
| settings | `views/settings/settings.html` | `js/features/settings.js`, `subscription.js` |
| subscription | `views/subscription/subscription.html` | `js/features/subscription.js` |

## Directory layout

```
frontend_service/
├── index.html
├── css/app.css
├── views/        ← one HTML file per route (mounted into shell)
├── js/
│   ├── app.js            (composition/startup only)
│   ├── api.js            (HTTP + Auth)
│   ├── charts.js         (SVG charts)
│   ├── icons.js          (SVG icons)
│   ├── core/             (dom, state, view-loader, router) — each has AGENT.md
│   ├── ui/               (toast, forms, modal) — each has AGENT.md
│   ├── utils/            (html, formatters) — each has AGENT.md
│   └── features/         (one module per feature, each has AGENT.md)
└── ...
```

## Verification commands

```bash
node --check <file>.js
```

Headless browser verification uses `file://` with
`--allow-file-access-from-files` in this environment.
