# AGENT.md — Kharcha Tracker view: dashboard

## View file

- `views/dashboard/dashboard.html`

## Root element

- `<section id="view-dashboard" class="view">`

## Dominant DOM IDs

- `#stat-plan` — dashboard stat, shared with Settings and Subscription
- `#stat-groups` — group count stat
- `#stat-invites` — pending invites stat
- `#dash-expense-group` — group selector (inside the Spending trends card)
- `#dash-trend-empty` — empty state
- `#dash-charts` — charts container
- `#dash-bar-chart` — stacked bar chart SVG container
- `#dash-pie-chart` — pie chart SVG container
- `#dash-pie-legend` — legend list
- `#dash-pie-total` — bucket total note
- `#dash-range-toggle` — daily/weekly/monthly toggle ([data-range])
- `#trend-snapshot...` (activity)

## Owning feature modules

- `js/features/dashboard.js` — `mount()`, `onEnter()` (charts-only now;
  `loadTrends()` populates the group selector and renders the trends)
- `js/charts.js` — `stackedBarChart()`, `pieChart()` rendering
- `js/features/subscription.js` — refreshes `#stat-plan`

> Note: the former "Expense summary" card (group total + per-category stat
> cards) was moved to the Groups screen — each group card now shows its own
> summary via `expenseSummaryHtml()` in `js/features/expenses.js`.

## State touched

- `state.dashboard.groupId`
- `state.dashboard.range`
- `state.groupsCache`, `state.expensesCache`

## Endpoints

- `GET /groups/{id}/expenses` (via `loadTrends` in dashboard.js)
- `GET /users/me/subscription` (via refreshPlanStat)

## CSS sections

- Dashboard, Charts, Cards, Stat-grid

## Files to touch for dashboard bugs

1. `views/dashboard/dashboard.html`
2. `js/features/dashboard.js`
3. `js/charts.js` (chart SVG rendering)
4. `js/features/subscription.js` (#stat-plan)
5. `css/app.css` (Dashboard section)
