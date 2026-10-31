# Kharcha Tracker — UI Refactor Proposal: Componentizing `frontend_service` HTML/CSS

**Status:** Proposal (not yet implemented)
**Scope:** `kharcha_v1/kharcha_gateway/frontend_service/` only
**Goal:** Break the monolithic `index.html` / `app.css` into reusable, self-contained components (each with its own HTML and CSS file) that are composed exclusively by `index.html`, while preserving the current DOM contract, routing, and zero-dependency/no-build-step architecture.

---

## 1. Current state

### 1.1 File inventory

```text
frontend_service/
├── index.html        # 459 lines — ALL markup: auth views, app shell, 6 screens
├── css/
│   └── app.css       # 1260 lines — tokens, reset, shell, every screen, every widget
└── js/
    ├── api.js        # 179  — fetch wrapper + token/refresh handling
    ├── charts.js     # 188  — dependency-free SVG bar/pie builders
    ├── icons.js      #  80  — inline SVG icon set
    └── app.js        # 2748 — router, session, caches, ALL rendering + event wiring
```

### 1.2 How it works today

- **Serving:** FastAPI mounts the whole `frontend_service/` directory via
  `StaticFiles(directory=frontend_service, html=True)` at `/`
  (`kharcha_gateway/app/main.py:1203-1208`). **Any new subfolder is served
  automatically — no server-side change is required by this refactor.**
- **Routing:** hash-based router (`app.js:141`) toggles pre-rendered
  `<section id="view-X" class="view" hidden>` blocks by flipping `hidden`.
- **Rendering:** static screens live in `index.html`; dynamic widgets are HTML
  **string builders** in `app.js` (`groupCardHtml`, `expenseRowHtml`,
  `expenseFormHtml`, `settlePanelHtml`, `noteRowHtml`, `invitationCardHtml`,
  `payoutRowHtml`, `splitPopupHtml`, `openModal` …).
- **Events:** mix of direct bindings per form (`#form-create-group`) and
  delegation on stable anchors (`#groups-list`, `#view-expenses`, `#main`,
  `#invites-list`) keyed on `[data-action]` attributes.
- **Constraints (self-imposed by the repo):** framework-free, no dependencies,
  no build step (README §8: "The frontend is framework-free
  HTML/CSS/JavaScript"); `main.py` stays a thin boundary.

### 1.3 Why this hurts

1. `index.html` mixes 8 unrelated screens in one file — a change to the
   Expenses screen is a 459-line-file change with no way to review "just the
   piece I touched".
2. `app.css` interleaves screen-specific and shared rules; media queries for
   different screens are interleaved in three `@media` blocks; finding the rule
   that styles a widget means searching 1260 lines.
3. The same widgets (expense row, group card, plan card, modal) exist as
   string literals in `app.js`, so their markup, styling and behavior live in
   **three** places (string in JS + rules in shared CSS + binding in JS) and
   cannot be previewed, diffed, or reused independently.

---

## 2. Goals / non-goals

### Goals

- **G1 — One component, three files:** every screen and reusable widget gets
  its own folder with `<name>.html` and `<name>.css` (and, in a later phase,
  `<name>.js`).
- **G2 — `index.html` is the only place that composes components.** It becomes
  a thin composition root (head, shell frame, component slots, script list).
  No component is referenced anywhere else.
- **G3 — Components must fit the main DOM.** Injections land in the existing
  app shell (`.app-shell` → `main#main`), light-DOM, with **identical final
  DOM** (same IDs, classes, `data-*` hooks, nesting) as today.
- **G4 — Zero behavior change.** Routing, caches, API calls, `[hidden]`
  view-switching, event delegation all keep working unchanged.
- **G5 — No new dependencies, no build step, no server change.**

### Non-goals (explicitly out of scope)

- Changing class names, selectors, or the visual design (Phase 3 cleanup is
  listed separately and optional).
- Replacing the vanilla JS app with a framework.
- Server-side templating (see §10, rejected alternatives).

---

## 3. Component model

Two kinds of components, one uniform file layout:

| Kind | What it is | How it enters the DOM |
|---|---|---|
| **Screen component** | One route/view (dashboard, groups, expenses, …) — single instance | Fetched at boot, injected into a slot inside `main#main` (or body for auth), **replacing the slot node** so the final DOM is byte-identical to today |
| **Widget component** | Repeated/instantiated fragments (expense row, group card, plan card, modal, settlement panel) | Fetched at boot into a hidden `<template>` registry; existing `*Html()` builders render from the cached template |

Both kinds share the layout:

```text
components/<name>/<name>.html   # the markup fragment (single root element)
components/<name>/<name>.css    # only the rules that style <name>
```

**Rules of the contract**

1. `index.html` is the **single composition point**: it names every component
   via `<div data-component="<name>">` slots (screens) and
   `<div id="template-registry" data-templates="a b c" hidden>` (widgets), and
   lists every `<link rel="stylesheet">` in the head. The loader (`js/components.js`)
   contains **no hardcoded component list** — it reads these markers from the
   DOM. This is what makes "referenced only by index.html" literally true.
2. Screen HTML files contain exactly today's `<section id="view-X" class="view" hidden>`
   root (1:1 move — no markup edits during extraction).
3. **Widget HTML must not contain `id` attributes** (instances repeat). This
   already holds for every current string builder (they use `data-trans-id`,
   `data-group-id`, `data-action`, classes) — the rule just formalizes it.
   Screens may keep their existing `id`s because they are single-instance.
4. CSS files own **only** rules whose selectors they exclusively use. Rules
   used by multiple components (`.btn`, `.field`, `.card`, `.badge`, `.toast`,
   `.empty`, …) stay in a shared primitives file.
5. The canonical `<link>` order in `index.html` defines cascade order
   (§6.3). No inline `<style>` blocks are introduced.

---

## 4. Target structure

```text
frontend_service/
├── index.html                    # ~120 lines: head + shell + slots + scripts
├── css/
│   ├── tokens.css                # :root vars, reset, body/typography, .icon
│   ├── shell.css                 # .app-shell grid, topbar, brand, topnav,
│   │                             #   sidebar, .main, tabbar, nav-item, user-chip,
│   │                             #   avatar, notif panel — the frame that stays in index.html
│   └── components.css            # shared primitives: .btn*, .icon-btn, .form*, .field*,
│                                 #   .card, .card-title, .banner, .badge, .toast,
│                                 #   .form-error/success, .fineprint, .auth-divider*
├── components/
│   ├── auth/                     # view-login + view-signup (2 sections in one fragment)
│   │   ├── auth.html
│   │   └── auth.css              # .view--auth, .auth-card, .auth-brand, .auth-title, …
│   ├── dashboard/                # view-dashboard: stat grid + recent activity card
│   │   ├── dashboard.html
│   │   └── dashboard.css         # .stat-grid/.stat-*, (view header)
│   ├── expense-summary/          # ★ "Expense summary" + "Spending trends" cards
│   │   ├── expense-summary.html  #   (group select, stat cards, segmented range, charts, legend)
│   │   └── expense-summary.css   # .segmented*, .chart-*, .legend-*, .stat-card overrides
│   ├── groups/                   # view-groups: create/join/invite forms, list, invites
│   │   ├── groups.html
│   │   └── groups.css            # .two-col, .list, .group-actions, …
│   ├── group-card/               # widget: one group in #groups-list
│   │   ├── group-card.html
│   │   └── group-card.css        # .group-card*, .group-status*, .invite-code*, .member-chips
│   ├── expenses/                 # view-expenses: group select, add button, list card
│   │   ├── expenses.html
│   │   └── expenses.css          # #expenses-add-block, .group-expense-actions, .card-subtitle
│   ├── expense-form/             # widget: add-expense form (+ edit-form fields)
│   │   ├── expense-form.html
│   │   └── expense-form.css      # .expense-form, .expense-edit-form, .contrib-*
│   ├── expense-row/              # widget: one expense row
│   │   ├── expense-row.html
│   │   └── expense-row.css       # .expense-list, .expense-row*
│   ├── group-notes/              # widget: notes list + note form (inside group card)
│   │   ├── group-notes.html
│   │   └── group-notes.css       # .group-notes*, .note-*, .tag-chip
│   ├── settle-panel/             # widget: settlement preview, payouts, corpus fund, ad-hoc
│   │   ├── settle-panel.html
│   │   └── settle-panel.css      # .settle-*, .net-dues-*, .corpus-*, .payout-*, .adhoc-*
│   ├── activity/                 # view-activity: toolbar, table slot, pager
│   │   ├── activity.html
│   │   └── activity.css          # .activity-toolbar, .activity-table, .event-pill, .pager
│   ├── settings/                 # view-settings: profile, subscription, session
│   │   ├── settings.html
│   │   └── settings.css          # (only view-specific rules; mostly reuses primitives)
│   ├── subscription/             # view-subscription: banner + plan grid
│   │   ├── subscription.html
│   │   └── subscription.css      # .plan-grid, .banner overrides
│   ├── plan-card/                # widget: one plan card
│   │   ├── plan-card.html
│   │   └── plan-card.css         # .plan-card*, .plan-badge, .plan-name/.price/.feats
│   ├── invitation-card/          # widget: pending invite row
│   │   ├── invitation-card.html
│   │   └── invitation-card.css   # .invite-item*
│   └── modal/                    # widget: modal chrome (head/body/overlay)
│       ├── modal.html
│       └── modal.css             # .modal-overlay, .modal, .modal-head/.title/.body
└── js/
    ├── components.js             # NEW — generic slot loader + template registry (~100 lines)
    ├── api.js                    # unchanged
    ├── icons.js                  # unchanged
    ├── charts.js                 # unchanged
    └── app.js                    # router/session/state/bindings; *Html() builders
                                  #   switch to Components.templateHtml() in Phase 2
```

**Note on "expense summary":** today it is one card inside `view-dashboard`
(`index.html:144-172`) plus the sibling "Spending trends" card, all driven by
`loadExpenseSummary()/renderExpenseSummary()/renderExpenseCharts()`
(`app.js:575-718`) over the hooks `#dash-expense-group`, `#dash-expense-stats`,
`#dash-expense-empty`, `#dash-charts`, `#dash-trend-empty`, `#dash-bar-*`,
`#dash-pie-*`, `#dash-range-toggle`. The proposal makes **`expense-summary` a
first-class component** containing both cards (they form one feature: the
per-group summary). It composes inside the dashboard screen's fragment, and is
the natural candidate for reuse elsewhere (e.g. a future group-detail screen).

---

## 5. Composition root — what `index.html` looks like after

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#4f46e5">
  <title>Kharcha Tracker</title>

  <!-- canonical cascade order: tokens → shell → shared → screens → widgets -->
  <link rel="stylesheet" href="css/tokens.css">
  <link rel="stylesheet" href="css/shell.css">
  <link rel="stylesheet" href="css/components.css">
  <link rel="stylesheet" href="components/auth/auth.css">
  <link rel="stylesheet" href="components/dashboard/dashboard.css">
  <link rel="stylesheet" href="components/expense-summary/expense-summary.css">
  <link rel="stylesheet" href="components/groups/groups.css">
  <link rel="stylesheet" href="components/group-card/group-card.css">
  <link rel="stylesheet" href="components/expenses/expenses.css">
  <link rel="stylesheet" href="components/expense-form/expense-form.css">
  <link rel="stylesheet" href="components/expense-row/expense-row.css">
  <link rel="stylesheet" href="components/group-notes/group-notes.css">
  <link rel="stylesheet" href="components/settle-panel/settle-panel.css">
  <link rel="stylesheet" href="components/activity/activity.css">
  <link rel="stylesheet" href="components/settings/settings.css">
  <link rel="stylesheet" href="components/subscription/subscription.css">
  <link rel="stylesheet" href="components/plan-card/plan-card.css">
  <link rel="stylesheet" href="components/invitation-card/invitation-card.css">
  <link rel="stylesheet" href="components/modal/modal.css">
</head>
<body>
  <noscript>
    <p style="padding:24px;font-family:sans-serif">Kharcha Tracker needs JavaScript enabled to run.</p>
  </noscript>

  <!-- ===== auth (outside the app shell, as today) ===== -->
  <div id="slot-auth" data-component="auth"></div>

  <!-- ===== app shell (stays in index.html) ===== -->
  <div id="app-shell" class="app-shell" hidden>
    <header class="topbar">
      … (brand, topnav, bell, user-chip, logout, notif panel — unchanged) …
    </header>
    <aside class="sidebar" id="sidebar" aria-label="Primary"></aside>

    <main class="main" id="main">
      <div id="slot-dashboard"   data-component="dashboard"></div>
      <div id="slot-groups"      data-component="groups"></div>
      <div id="slot-expenses"    data-component="expenses"></div>
      <div id="slot-activity"    data-component="activity"></div>
      <div id="slot-settings"    data-component="settings"></div>
      <div id="slot-subscription" data-component="subscription"></div>
    </main>

    <nav class="tabbar" id="tabbar" aria-label="Primary"></nav>
  </div>

  <div class="toast" id="toast" hidden></div>
  <div id="modal-root" hidden></div>

  <!-- widget templates materialized here at boot (hidden, inert) -->
  <div id="template-registry" hidden
       data-templates="modal expense-row expense-form group-card group-notes settle-panel plan-card invitation-card"></div>

  <script src="js/icons.js"></script>
  <script src="js/api.js"></script>
  <script src="js/charts.js"></script>
  <script src="js/components.js"></script>
  <script src="js/app.js"></script>
  <script>
    /* boot only after component fragments are in place, so every id/data-hook
       referenced by app.js already exists. loadAll() never rejects. */
    Components.loadAll().then(boot);
  </script>
</body>
</html>
```

### 5.1 How injection "fits the main DOM"

- Screens land inside `main#main` where their `<section class="view">` blocks
  live today; auth lands where `view-login`/`view-signup` live today (body,
  outside the shell).
- The loader uses **`slot.replaceWith(...fragmentChildren)`**, i.e. the slot
  node is removed and the fragment's children take its exact place. The
  post-injection DOM is **identical** to today's, which means:
  - the router's `$all(".view")` toggle is unchanged,
  - the delegation anchors (`#main`, `#groups-list`, `#view-expenses`,
    `#invites-list`) still enclose everything,
  - the direct-child rule `.main > .view { max-width: 1080px; … }` and sibling
    rules like `.card + .card` keep matching exactly as before — **no CSS
    selector needs to change because of the slots**.
- `boot()` (icon injection by id, `renderNav`, `bind*`) runs **after**
  `Components.loadAll()` resolves, so `#btn-new-group-icon`, `#join-icon`,
  `#expenses-add-icon`, `#dash-expense-group`, … all exist. This replaces
  today's `document.addEventListener("DOMContentLoaded", boot)`
  (`app.js:2748`).

### 5.2 The loader (`js/components.js`, sketch)

```js
/* Kharcha Tracker — generic component loader. No component names live here;
   index.html is the composition point (data-component slots + data-templates). */
"use strict";
const Components = (() => {
  const htmlCache = new Map();

  async function fetchText(url) {
    const res = await fetch(url, { cache: "force-cache" });
    if (!res.ok) throw new Error("HTTP " + res.status + " for " + url);
    return res.text();
  }

  async function fetchComponent(name) {
    if (htmlCache.has(name)) return htmlCache.get(name);
    const html = await fetchText("components/" + name + "/" + name + ".html");
    htmlCache.set(name, html);
    return html;
  }

  async function loadAll() {
    const names = [...document.querySelectorAll("[data-component]")].map(
      (el) => el.dataset.component
    ).concat(
      (document.getElementById("template-registry")?.dataset.templates || "").split(/\s+/).filter(Boolean)
    );

    const results = await Promise.allSettled(names.map(fetchComponent));
    names.forEach((name, i) => {
      if (results[i].status !== "fulfilled")
        console.error("[components] failed to load", name, results[i].reason);
    });

    /* 1) screens → replace their slot with the fragment's children */
    document.querySelectorAll("[data-component]").forEach((slot) => {
      const name = slot.dataset.component;
      const html = htmlCache.get(name);
      if (!html) {
        slot.innerHTML = '<p class="empty">Could not load the ' + name + " screen.</p>";
        return;
      }
      const frag = document.createElement("template");
      frag.innerHTML = html.trim();
      slot.replaceWith(...frag.content.children);   /* DOM parity, §5.1 */
    });

    /* 2) widgets → hidden <template> registry */
    const registry = document.getElementById("template-registry");
    names.filter((n) => !htmlCache.has(n) || n === n /* all widget names */).forEach(() => {});
    for (const name of (registry?.dataset.templates || "").split(/\s+/).filter(Boolean)) {
      if (!htmlCache.has(name)) continue;
      const tpl = document.createElement("template");
      tpl.id = "tpl-" + name;
      tpl.innerHTML = htmlCache.get(name).trim();
      registry.appendChild(tpl);
    }
  }

  /** Widget instances: cached template string (what the *Html() builders use). */
  function templateHtml(name) {
    const tpl = document.getElementById("tpl-" + name);
    return tpl ? tpl.innerHTML : "";
  }

  return { loadAll, templateHtml };
})();
```

Notes:

- CSS is **not** injected by JS — all `<link>` tags are static in
  `index.html`'s head (canonical order, blocking, cached). JS only fetches the
  small HTML fragments (~0.5–3 KB each).
- `loadAll()` never rejects: a failed component degrades to a visible
  "Could not load …" slot or an empty template, the rest of the app still boots.
- Browser caching: fragments are static files with stable URLs; default HTTP
  caching applies (Starlette `StaticFiles` sends `Last-Modified`).

---

## 6. CSS split strategy

### 6.1 Mapping — where each block of today's `app.css` goes

| Today (`css/app.css`) | Lines (approx.) | Moves to |
|---|---|---|
| `:root` tokens, reset, body, headings, `.muted`, `.empty`, `.icon` | 1–59 | `css/tokens.css` |
| `.btn*`, `.icon-btn` | 69–112 | `css/components.css` |
| `.form*`, `.field*`, `.form-error`, `.form-success` | 114–162 | `css/components.css` |
| `.card*`, `.banner*`, `.badge*`, `.fineprint`, `.auth-divider` | 164–239 | `css/components.css` (`.auth-divider` is shared by auth views) |
| `.view--auth`, `.auth-card`, `.auth-brand`, `.auth-title/subtitle/switch` | 241–291 | `components/auth/auth.css` |
| `.app-shell`, `.topbar`, `.brand`, `.topnav`, `.sidebar`, `.main`, `.tabbar`, `.nav-item`, `.user-chip`, `.avatar`, `.notif-*` | 293–482 | `css/shell.css` |
| `.stat-grid`, `.stat-*` (dashboard stat cards) | 484–497 | `components/dashboard/dashboard.css` |
| `.two-col`, `.list`, `.group-head*`, `.code-box`, `.group-actions`, `.group-card-head/name/meta/actions/status`, `.invite-code-row`, `.member-chips` | 498–595 | `components/groups/groups.css` + `components/group-card/group-card.css` (card-level rules) |
| `.plan-grid` | 596–601 | `components/subscription/subscription.css` |
| `.plan-card`, `.plan-badge`, `.plan-name/price/feats`, `.plan-buy` | 603–639 | `components/plan-card/plan-card.css` |
| `.toast*` | 641–659 | `css/components.css` (global — used from all screens) |
| `.segmented*`, `.chart-*`, `.legend-*` | 661–725 | `components/expense-summary/expense-summary.css` |
| `.modal-*`, `.icon-btn--sm/--danger`, `.contrib-*` (shared with edit form) | 727–821 | `components/modal/modal.css` + `components/expense-form/expense-form.css` (`.contrib-*`) |
| `.group-expenses`, `.card-subtitle`, `.expense-list`, `.expense-row*`, `.group-expense-actions`, `.expense-form` | 916–1015 | `components/expense-row/expense-row.css`, `components/expense-form/expense-form.css`, `components/groups/groups.css` (`.card-subtitle`) |
| `.group-notes*`, `.note-*`, `.tag-chip` | 965–1014 | `components/group-notes/group-notes.css` |
| `.settle-*`, `.net-dues-*`, `.corpus-*`, `.payout-*`, `.adhoc-*`, `.payout-link-form` | 1017–1158 | `components/settle-panel/settle-panel.css` |
| `.activity-toolbar`, `.activity-table`, `.event-pill*`, `.pager*`, `.table-wrap` | 1160–1259 | `components/activity/activity.css` |

### 6.2 Media queries

The three `@media` blocks today (640px / 1024px / 380px, plus
`prefers-reduced-motion`) contain rules for many different screens interleaved.
Rule: **each `@media` rule moves with the component that owns the class it
styles.** e.g.

- `.app-shell` grid / `.tabbar`/`.topnav` show-hide / `.main` overflow /
  `.sidebar` desktop block → `css/shell.css` (they describe the shell frame),
- `.form-row` two-column + `.field--narrow` → `css/components.css`,
- `.stat-grid`, `.two-col`, `.plan-grid` columns → each owning component file,
- `.activity-toolbar`/`.activity-table` desktop table mode →
  `components/activity/activity.css`.

`prefers-reduced-motion` stays global in `css/tokens.css`.

### 6.3 Cascade order (the one rule that matters)

Static `<link>` order in `index.html` is the cascade:

```text
1. tokens.css           (no selectors compete)
2. shell.css
3. components.css       (shared primitives)
4. screens, in nav order:  auth, dashboard, expense-summary, groups,
                           expenses, activity, settings, subscription
5. widgets, each after its host screen(s):
                           group-card, expense-form, expense-row,
                           group-notes, settle-panel, plan-card,
                           invitation-card, modal
```

Widget CSS always comes **after** every screen that can instantiate it
(`expense-row` renders inside both `groups` and `expenses`, so it follows both).
Since today's selectors were all flat class rules with no pseudo-class layering,
and we keep the **same class names**, re-homing rules into files with this
order preserves the effective cascade (identical-specificity ties resolve by
order, and the ordering above mirrors today's order in `app.css`).

### 6.4 What we deliberately do NOT do (yet)

- No BEM renaming, no CSS custom-property rework, no `!important` additions.
- No CSS modules / `@layer` — keep it as plain, concatenatable CSS files that
  `StaticFiles` serves.

---

## 7. JS impact per phase

| Phase | `app.js` changes |
|---|---|
| **1 — screens** | None, except replacing `DOMContentLoaded → boot` with `Components.loadAll().then(boot)` (wiring lives in `index.html`, not `app.js`). All existing `$("#…")` lookups keep working because post-injection DOM is identical and `boot()` runs after injection. |
| **2 — widgets** | The `*Html()` builders switch their static skeleton to `Components.templateHtml(name)` + the same string interpolation for data-driven bits (or full placeholder substitution). Bindings untouched — they already delegate on `[data-action]` / classes and never on `id`s inside widgets (verified: widget markup uses `data-trans-id`, `data-group-id`, `data-action`, `.exp-*`, `.contrib-*` only). |
| **3 — per-component JS (optional)** | Move each screen's `bind*()` + `load*()` + `render*()` + `*Html()` into `components/<name>/<name>.js`, loaded after `app.js` (globals `api`, `Auth`, `Icons`, `Charts`, caches stay shared). `app.js` shrinks to router + session + shared state. |

**Phase 2 detail — widget conversion pattern** (example: `expense-row`):

```js
/* before (app.js:787)                       after */
function expenseRowHtml(exp, group) {        function expenseRowHtml(exp, group) {
  return (                                   const tpl = Components.templateHtml("expense-row");
    '<div class="expense-row" …>             return tpl
      …                                      .replace("__DESC__", esc(exp.expn_desc))
  );                                         .replace("__AMT__",  esc(formatAmount(exp.expn_amnt, group.currency)))
}                                            … // or build the row via a small render helper
                                            }
```

Recommended: keep the existing **builder function names and signatures**
(`expenseRowHtml(exp, group)` etc.) so their callers
(`expensesListHtml`, `groupCardHtml`, `loadGroupExpenses`, `bindGroupActions`)
do not change. Only the body of the builder switches from a literal string to
`Components.templateHtml(...) + interpolation`.

---

## 8. Rollout plan

**Principle:** pure extraction first (zero behavior change), prove the pipeline
on one small component, then screens, then widgets. Every phase ends green on
the verification matrix (§11) before the next starts.

### Phase 0 — Infrastructure (half a day)

1. Create `components/` + `js/components.js` (loader, §5.2).
2. Split `app.css` into `tokens.css` / `shell.css` / `components.css` + the
   screen/widget files — **move rules verbatim, no selector edits** (mapping
   §6.1). `index.html` still contains all markup at this point; the `<head>`
   already lists the new files in canonical order. `app.css` deleted once the
   split is verified.
3. Add slots + `data-templates` registry + `Components.loadAll().then(boot)`
   wiring in `index.html`.
4. **Pilot:** extract `components/plan-card/` + `components/subscription/`
   end-to-end (HTML + CSS + one widget) to prove fetch → inject → template
   registry before touching the bigger screens.
5. Verify with the §11 matrix.

### Phase 1 — Screen components (1–2 days)

Extract in ascending risk:

1. `auth` (2 views, self-contained forms)
2. `settings` (mostly static, one form)
3. `activity` (toolbar + slots; table rows already JS-rendered)
4. `dashboard` **and** `expense-summary` (dashboard composes the
   expense-summary fragment)
5. `groups` (largest static screen)
6. `expenses` (group select + list card; add-form is still the JS string until
   Phase 2)

Each step: move the `<section>…</section>` verbatim into
`components/<name>/<name>.html`, replace with `<div data-component>` slot,
confirm DOM diff is empty (see §11).

### Phase 2 — Widget components (2–3 days)

Convert string builders to templates, in this order (dependency-aware):

1. `modal` (chrome only — `openModal` gains the cached template)
2. `plan-card` (if not fully done as pilot) → `invitation-card`
3. `expense-row` → `expense-form` → `expense-edit` fields (`.contrib-*`)
4. `group-card` (largest; composes `group-notes` + `settle-panel` slots)
5. `group-notes` (note list + note form)
6. `settle-panel` (settlement preview, payouts, corpus fund, ad-hoc form)

Each step keeps the public builder name/signature (§7), so callers and
delegated handlers are untouched.

### Phase 3 — Optional follow-ups (separate change, needs its own review)

- Per-component `components/<name>/<name>.js` (§7 Phase 3) so each component
  truly owns html+css+js.
- Migrate remaining `id`-based lookups inside components to scoped
  `data-*` hooks (e.g. `#dash-expense-group` →
  `root.querySelector("[data-dash-group-select]")`) to decouple `app.js` from
  component internals.
- Evaluate ES modules (`<script type="module">`) as the next architectural
  step — only after components exist as files, since modules need file
  boundaries to be worthwhile.

### Sequencing / effort (rough)

| Phase | Effort (focused work, excl. review) |
|---|---|
| 0 — infra + CSS split + pilot | 0.5–1 day |
| 1 — six screen extractions | 1–2 days |
| 2 — seven widget conversions | 2–3 days |
| 3 — optional | +2–4 days |

---

## 9. Risks and mitigations

| # | Risk | Likelihood | Mitigation |
|---|---|---|---|
| R1 | Cascade changes when rules move between files (ties resolved by order) | Medium | Canonical link order §6.3 mirrors today's in-file order; same selectors, no renames; per-screen visual diff after each phase |
| R2 | Boot ordering: `boot()` runs before fragments arrive → null `$("#…")` | Low | `Components.loadAll().then(boot)`; `loadAll()` never rejects; `boot()` already null-guards most lookups |
| R3 | Slot wrapper breaks `.main > .view` / sibling selectors | Low | `replaceWith(...children)` removes the slot node → DOM identical to today (§5.1) |
| R4 | Duplicate `id`s when a widget instantiates multiple times | Low (already clean) | Contract rule §3.3; enforced by a grep gate in CI/PR: `grep -rn ' id="' components/ --include='*.html'` must only hit single-instance screens |
| R5 | Fetch fails (network hiccup, 404) → broken screen | Low | Per-component `allSettled` isolation; visible fallback in slot; rest of app still boots; browser caching on stable URLs |
| R6 | First paint slower (extra round-trips) | Low | Fragments are small; fetched in parallel; auth screen (first screen for most users) can be inlined in `index.html` as the only exception if measured paint matters |
| R7 | No-JS users see fewer static pages | None (no regression) | App already requires JS (existing `<noscript>` banner); behavior unchanged |
| R8 | Blueprint/README drift — repo docs declare the YAML + README as the source of truth for the file tree | **High if ignored** | §10 — explicit back-update of `kharcha_tracker.yaml` + `README.md` §2/§8 in the same change set |
| R9 | CSS media-query re-homing done sloppily (a rule left in two files or dropped) | Medium | Mechanical check: after split, `cat` of all CSS in canonical order must be semantically identical to today's (selector-by-selector diff script, §11) |

---

## 10. Blueprint & docs back-update (required by repo policy)

`kharcha-tracker/README.md` §16 states the YAML is "both the application
contract and the progressive regeneration manifest" and that the
implementation "must not silently invent a YAML node, filesystem path, …",
and §2 documents the exact `frontend_service` tree. `AGENTS.md` reinforces a
closed schema: new structures must be **proposed, approved, then recorded** —
not added quietly.

Therefore, **each merged phase includes** (in the same change set):

1. `kharcha_tracker.yaml`: extend the `frontend_service` service node with the
   new physical files — e.g. a `frontend_html` folder module (one
   `browser-html`/`browser-css` leaf per component file, mirroring the existing
   `browser-js` leaves for the four JS files) — or record the components as
   explicit `layout: file` children. *The exact node/protocol choice is a
   schema decision that needs explicit user approval per AGENTS.md §0; this
   proposal is the record of that request.*
2. `kharcha-tracker/README.md`: update the §2 tree and the §8 "Frontend SPA"
   block to show `components/` and `js/components.js`.
3. This document (`kharcha-ui-refactor-html.md`) is the design record for the
   extension.

Until the blueprint is back-updated, the new files exist only as an agreed,
documented deviation — never as an invisible one.

---

## 11. Verification / acceptance criteria

Run after **every** phase (not just the end):

**Structural gates (automatable, ~10 lines of shell):**
- `grep -rn ' id="' components/ --include='*.html'` → matches only
  single-instance screens, never widget files.
- Selector audit: for each class used by `app.js`
  (`grep -oE '"\.[a-z-]+"' js/app.js`), the class's CSS rules must exist in
  exactly one of the new files (no orphans, no duplicates) — diff today's
  `app.css` rule set against the concatenation of the new files in canonical
  order.
- Network tab: no 404s on any route; fragment requests are cached on reload.

**Behavioral matrix (manual, 8 routes):**
1. Login / magic link / signup → redirect to dashboard.
2. Dashboard: group select re-renders stat cards + stacked bar + pie + legend
   (daily/weekly/monthly toggle), empty states correct.
3. Groups: create, join by code, invite by email; group card — copy code,
   settle-up panel (net dues, payouts, corpus fund, ad-hoc), notes add/edit/delete,
   leave/delete; invitation list accept/decline.
4. Expenses: group select (last-used group pre-selected), add expense
   (equal + custom split popup), edit modal (contributions re-sum validation),
   delete; locked-group state disables actions.
5. Activity: group + date filters, pagination, CSV export.
6. Settings: profile save, plan shown, logout.
7. Subscription: banner, four plan cards, purchase flow (mocked).
8. Console: zero errors/warnings; `hidden` view-switching identical (only one
   `.view` visible per route).

**Parity check:** with the app running before and after each phase,
`document.querySelector("#main").innerHTML` (per route, after data load) is
identical modulo attribute ordering.

---

## 12. Rejected alternatives (and why)

| Alternative | Rejected because |
|---|---|
| **Web Components / Shadow DOM** | Violates G3: shadow roots would break the existing global cascade and every `app.js` selector that reaches inside widgets; user requirement is components that "fit well the main DOM of index.html" (light DOM) |
| **Server-side includes (Jinja2 in `main.py`)** | Adds a runtime dependency to a deliberately thin FastAPI app, couples markup changes to the Python deploy, and contradicts the "framework-free, no build step" serving model; also StaticFiles already serves arbitrary subfolders for free |
| **Build step (esbuild/Vite, importable components)** | Violates G5 (zero dependencies, no build); the current ops model (copy the folder, `uvicorn`) must keep working |
| **ES modules for HTML/CSS today** | No build tool means `import` of `.html`/`.css` needs fetch anyway — i.e. it converges on the loader in §5.2 with more moving parts; revisit in Phase 3 once components exist as files |
| **Keep status quo, only split `app.css`** | Solves half the problem (CSS locality) but leaves markup in the 459-line monolith and widgets as strings in `app.js` — the stated goal is component files referenced by `index.html` |

---

## 13. Open questions (need a decision before Phase 0)

1. **Naming:** `components/` vs `ui/` vs `views/`+`widgets/`? (Proposal:
   `components/` with flat names.)
2. **Auth as one component** (`auth.html` containing both `view-login` and
   `view-signup`) vs two components (`login`, `signup`)? (Proposal: one —
   they share the card/brand/chrome CSS and are one concern.)
3. **Blueprint schema:** add a new `browser-html` protocol leaf vs. recording
   components as generic `layout: file` children of a `frontend_html` module
   node? (Needs explicit approval per AGENTS.md §0.)
4. **Inline exception:** allow the `auth` screen to stay inlined in
   `index.html` (first paint, no-JS resilience) while everything else is
   fetched? (Proposal: no — uniformity wins; auth fragments are ~2 KB.)

---

*Prepared from a full read of `frontend_service/` (index.html, css/app.css,
js/app.js, js/api.js, js/charts.js, js/icons.js), `kharcha-tracker/README.md`,
`AGENTS.md`, and the `StaticFiles` mount in `kharcha_gateway/app/main.py`.*
