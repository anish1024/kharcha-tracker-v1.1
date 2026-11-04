/* Shared application state: current user, shared caches and feature
   selections. Not a general-purpose state framework — features keep their
   own transient state private. */
"use strict";

export const state = {
  currentUser: null,

  dashboard: {
    groupId: "",
    range: "daily"
  },

  groupsCache: [],
  membersCache: {},
  expensesCache: {},
  settleCache: {},
  notesCache: {},

  expenses: {
    groupId: ""
  },

  activity: {
    groups: [],
    groupId: "",
    page: 0,
    total: 0
  }
};

/* Shared member lookup over state.membersCache. Used by groups, expenses
   and settlement; lives here so those features never import each other. */
export function memberName(groupId, userId) {
  const members = state.membersCache[groupId] || [];
  const match = members.find((m) => m.id === userId);
  return match ? (match.name || match.email || "?") : "Unknown";
}
