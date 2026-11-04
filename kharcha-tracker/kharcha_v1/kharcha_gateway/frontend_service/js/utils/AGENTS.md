# AGENT.md — Kharcha Tracker utils modules

## `js/utils/html.js`

- Exports `esc(value)` — canonical HTML text/attribute escaping
- Replaces `& < > " '` with entities
- Used by all feature render functions that interpolate data into HTML
- Import: `import { esc } from "../utils/html.js"`

## `js/utils/formatters.js`

- Exports `formatAmount(amount, currency)` — formats amount with 2 decimals
  and appends currency symbol (₹ by default, or passed currency)
- Exports `formatDate(dateString)` — formats to `YYYY-MM-DD` for inputs
  and display
- Used by dashboard stats, expense rows, activity table, etc.
- Import: `import { formatAmount, formatDate } from "../utils/formatters.js"`

## Cross-module notes

- Must not import feature modules.
- Single responsibility — only text/formatting utilities here.