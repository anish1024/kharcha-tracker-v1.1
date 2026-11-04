# AGENT.md — Kharcha Tracker feature: expenses

## Module

- `js/features/expenses.js`

## Exports

- `renderExpensesScreen()`, `mount()`
- `expensesListHtml(group)` — renders expense rows inside group cards
- `bindCrossScreenExpenseActions()` — delegated on `#main`, shared with Groups

## DOM IDs used

- `#expenses-group` — group selector
- `#expenses-list` — expense list container
- `#expenses-list` inside group cards (delegated actions)

## State touched

- `state.expenses.groupId`, `state.expensesCache`

## Endpoints

- `GET /groups/{id}/expenses`, `POST /expenses`, `PATCH /expenses/{id}`,
  `DELETE /expenses/{id}`
- `POST /expenses/{id}/split` (split form logic)
- `POST /groups/{id}/payouts/{payoutId}/expenses`

## CSS sections

- Members + expenses, Cards, Forms, Buttons

## Files to touch for expenses bugs

1. `views/expenses/expenses.html`
2. `js/features/expenses.js`
3. `js/features/groups.js` (shared `expensesListHtml`)
4. `css/app.css`

## Cross-screen notes

- `bindCrossScreenExpenseActions()` bound once at boot on `#main`
- Expense edit/delete from both Groups and Expenses screens
- `expensesListHtml()` reused by groups group cards