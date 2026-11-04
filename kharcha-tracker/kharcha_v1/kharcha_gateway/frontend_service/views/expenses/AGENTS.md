# AGENT.md — Kharcha Tracker view: expenses

## View file

- `views/expenses/expenses.html`

## Root element

- `<section id="view-expenses" class="view">`

## Dominant DOM IDs

- `#expenses-group` — group selector
- `#expenses-add-form` / `#expenses-add-form-*` — add-expense form
- `#expenses-list` — expense list container

## Owning feature module

- `js/features/expenses.js` — `renderExpensesScreen()`, `mount()`,
  `bindCrossScreenExpenseActions()`, `expensesListHtml()`

## State touched

- `state.expenses.groupId`
- `state.expensesCache`
- `state.dashboard.groupId` (cross-screen navigation source)

## Endpoints

- `GET /groups/{id}/expenses`, `POST /expenses`, `PATCH /expenses/{id}`,
  `DELETE /expenses/{id}`
- `POST /expenses/{id}/split` (split logic lives here)

## Files to touch for expenses bugs

1. `views/expenses/expenses.html`
2. `js/features/expenses.js`
3. `js/features/groups.js` (shared expense list markup)
4. `js/charts.js` (expense chart helper usage)
5. `css/app.css` (Members + expenses, Cards, Forms)

## Cross-screen behaviour

- `bindCrossScreenExpenseActions()` bound once at boot on `#main`
- Expense edit/delete/split/add from groups should still work after
  expense screen is mounted.
