/* Shared DOM selector helpers — import these instead of defining copies. */
"use strict";

export function $(sel, root) { return (root || document).querySelector(sel); }
export function $all(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }
