/* Route-to-view registry and HTML view loader.
   Views are fetched once, validated, mounted once and then reused. */

const VIEW_REGISTRY = {
  login: {
    file: "views/auth/login.html",
    container: "auth-view-container",
    rootId: "view-login"
  },
  signup: {
    file: "views/auth/signup.html",
    container: "auth-view-container",
    rootId: "view-signup"
  },
  dashboard: {
    file: "views/dashboard/dashboard.html",
    container: "view-container",
    rootId: "view-dashboard"
  },
  groups: {
    file: "views/groups/groups.html",
    container: "view-container",
    rootId: "view-groups"
  },
  expenses: {
    file: "views/expenses/expenses.html",
    container: "view-container",
    rootId: "view-expenses"
  },
  activity: {
    file: "views/activity/activity.html",
    container: "view-container",
    rootId: "view-activity"
  },
  settings: {
    file: "views/settings/settings.html",
    container: "view-container",
    rootId: "view-settings"
  },
  subscription: {
    file: "views/subscription/subscription.html",
    container: "view-container",
    rootId: "view-subscription"
  }
};

const mountedViews = new Map();
const inFlightViews = new Map();

export function getMountedView(route) {
  return mountedViews.get(route) || null;
}

export function getViewSpec(route) {
  return VIEW_REGISTRY[route] || null;
}

export async function ensureViewMounted(route) {
  const spec = VIEW_REGISTRY[route];
  if (!spec) throw new Error("Unknown view route: " + route);

  const mounted = mountedViews.get(route);
  if (mounted) return mounted;

  const inFlight = inFlightViews.get(route);
  if (inFlight) return inFlight;

  const request = loadView(route, spec)
    .then((root) => {
      mountedViews.set(route, root);
      return root;
    })
    .finally(() => {
      inFlightViews.delete(route);
    });

  inFlightViews.set(route, request);
  return request;
}

async function loadView(route, spec) {
  const container = document.getElementById(spec.container);
  if (!container) {
    throw new Error("View container #" + spec.container + " is missing.");
  }

  const existing = document.getElementById(spec.rootId);
  if (existing) {
    return existing;
  }

  let response;
  try {
    response = await fetch(new URL(spec.file, document.baseURI), {
      credentials: "same-origin",
      headers: { Accept: "text/html" }
    });
  } catch (err) {
    throw new Error("Could not reach the server for the " + route + " screen.");
  }

  if (!response.ok) {
    throw new Error(
      "Could not load the " + route + " screen (HTTP " + response.status + ")."
    );
  }

  const html = await response.text();
  const parsed = new DOMParser().parseFromString(html, "text/html");

  if (parsed.querySelector("parsererror")) {
    throw new Error("The " + route + " screen markup could not be parsed.");
  }
  if (parsed.querySelector("script")) {
    throw new Error("The " + route + " screen markup contains a script tag.");
  }

  const elements = parsed.body ? Array.from(parsed.body.children) : [];
  if (elements.length !== 1) {
    throw new Error("The " + route + " screen markup must contain one root element.");
  }

  const root = elements[0];
  if (root.tagName !== "SECTION" || root.id !== spec.rootId) {
    throw new Error(
      "The " + route + " screen must be a <section> with id \"" + spec.rootId + "\"."
    );
  }

  if (document.getElementById(spec.rootId)) {
    throw new Error("The " + route + " screen is already mounted.");
  }

  container.appendChild(root);
  return root;
}
