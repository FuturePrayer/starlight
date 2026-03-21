const BASE = ''

async function request(url, options = {}) {
  const res = await fetch(BASE + url, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  })
  const payload = await res.json().catch(() => ({ success: false, message: '响应解析失败' }))
  if (!res.ok || !payload.success) {
    const err = new Error(payload.message || '请求失败')
    err.status = res.status
    throw err
  }
  return payload.data
}

export const authApi = {
  registrationStatus: () => request('/api/auth/registration-status'),
  register: (email, password) => request('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  login: (username, password) => request('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => request('/api/auth/logout', { method: 'POST', body: '{}' }),
  me: () => request('/api/auth/me')
}

export const noteApi = {
  tree: () => request('/api/tree'),
  list: () => request('/api/notes'),
  get: (id) => request(`/api/notes/${id}`),
  create: (data) => request('/api/notes', { method: 'POST', body: JSON.stringify(data) }),
  update: (id, data) => request(`/api/notes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id) => request(`/api/notes/${id}`, { method: 'DELETE' })
}

export const categoryApi = {
  create: (name, parentId) => request('/api/categories', { method: 'POST', body: JSON.stringify({ name, parentId }) })
}

export const shareApi = {
  list: (noteId) => request(`/api/notes/${noteId}/shares`),
  create: (noteId, data) => request(`/api/notes/${noteId}/shares`, { method: 'POST', body: JSON.stringify(data) }),
  open: (token, password) => {
    let url = `/api/shares/${token}`
    if (password) url += `?password=${encodeURIComponent(password)}`
    return request(url)
  }
}

export const themeApi = {
  list: () => request('/api/themes'),
  select: (themeId) => request('/api/themes/select', { method: 'POST', body: JSON.stringify({ themeId }) })
}

export const adminApi = {
  getSettings: () => request('/api/admin/settings'),
  saveSettings: (data) => request('/api/admin/settings', { method: 'POST', body: JSON.stringify(data) })
}

