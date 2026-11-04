# AGENT.md — Kharcha Tracker view: login

## View file

- `views/auth/login.html`

## Root element

- `<section id="view-login" class="view view--auth">`

## Dominant DOM IDs

- `#auth-view-container` (persistent mount container, outside app-shell)

## Feature module

- `js/features/auth.js` (login, signup, magic-link)

## Notes for targeted fixes

- Preserve `[hidden]` behaviour and CSS.
- Login/Signup are auth views — no app-shell chrome.
- No `#form-profile` submit handler exists in source; do not invent one.

## Files to touch for login bugs

1. `views/auth/login.html`
2. `js/features/auth.js`
3. `js/ui/toast.js`, `js/ui/forms.js`, `js/ui/modal.js` (shared)
4. `js/core/router.js` (route registration) via `js/app.js`
5. `js/api.js` (Auth)
