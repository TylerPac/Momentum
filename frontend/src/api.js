const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export const SIGN_IN_URL = '/oauth2/authorization/shared';

export async function apiFetch(path, options = {}) {
  const url = path.startsWith('/api') ? path : `/api${path}`;

  const headers = new Headers(options.headers || {});
  const method = (options.method || 'GET').toUpperCase();
  const csrf = csrfToken();
  if (csrf && !SAFE_METHODS.includes(method)) {
    headers.set('X-XSRF-TOKEN', csrf);
  }

  const isFormData = (typeof FormData !== 'undefined') && (options.body instanceof FormData);
  if (!headers.has('Content-Type') && options.body != null && !isFormData) {
    headers.set('Content-Type', 'application/json');
  }

  const res = await fetch(url, {
    ...options,
    headers,
    credentials: 'same-origin',
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    let data = null;
    try {
      data = await res.json();
      if (data?.error) message = data.error;
      else if (data?.message) message = data.message;
      else if (data?.detail) message = data.detail;
    } catch {
      // ignore
    }
    const err = new Error(message);
    err.status = res.status;
    err.data = data;
    throw err;
  }

  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return res.json();
  }

  return null;
}

// Ends this app's session, then the shared sign-in, and returns to the home page
export async function signOut() {
  const result = await apiFetch('/auth/logout', { method: 'POST' });
  window.location.assign(result?.logoutUrl || '/');
}
