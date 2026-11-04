/* Shared amount/date formatting helpers. */
"use strict";

export function formatAmount(value, currency) {
  const n = Number(value || 0);
  return (currency ? currency + " " : "") + n.toFixed(2);
}

export function formatDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}
