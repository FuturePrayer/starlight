const BASE = ''

async function request(url, options = {}) {
  const headers = new Headers(options.headers || {})
  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(BASE + url, {
    credentials: 'same-origin',
    headers,
    ...options
  })
  const payload = await res.json().catch(() => ({ success: false, message: '响应解析失败' }))
  if (!res.ok || !payload.success) {
    const err = new Error(payload.message || '请求失败')
    err.status = res.status
    err.data = payload.data
    throw err
  }
  return payload.data
}

function parseContentDispositionFileName(contentDisposition) {
  if (!contentDisposition) return ''
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] || ''
}

async function download(url, options = {}) {
  const res = await fetch(BASE + url, {
    credentials: 'same-origin',
    headers: new Headers(options.headers || {}),
    ...options
  })

  if (!res.ok) {
    const payload = await res.json().catch(() => ({ message: '请求失败' }))
    const err = new Error(payload.message || '请求失败')
    err.status = res.status
    err.data = payload.data
    throw err
  }

  return {
    blob: await res.blob(),
    fileName: parseContentDispositionFileName(res.headers.get('content-disposition'))
  }
}

export const authApi = {
  registrationStatus: () => request('/api/auth/registration-status'),
  register: (email, password) => request('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  login: (username, password) => request('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => request('/api/auth/logout', { method: 'POST', body: '{}' }),
  me: () => request('/api/auth/me'),
  updateProfile: (data) => request('/api/auth/profile', { method: 'PUT', body: JSON.stringify(data) }),
  // TOTP
  totpSetup: () => request('/api/auth/totp/setup', { method: 'POST', body: '{}' }),
  totpConfirm: (secret, code) => request('/api/auth/totp/confirm', { method: 'POST', body: JSON.stringify({ secret, code }) }),
  totpRevoke: () => request('/api/auth/totp', { method: 'DELETE' }),
  totpVerifyLogin: (pendingToken, code) => request('/api/auth/totp/verify-login', { method: 'POST', body: JSON.stringify({ pendingToken, code }) }),
  // Passkey
  passkeyList: () => request('/api/auth/passkey/credentials'),
  passkeyRegisterStart: () => request('/api/auth/passkey/register/start', { method: 'POST', body: '{}' }),
  passkeyRegisterFinish: (data) => request('/api/auth/passkey/register/finish', { method: 'POST', body: JSON.stringify(data) }),
  passkeyDelete: (id) => request(`/api/auth/passkey/credentials/${id}`, { method: 'DELETE' }),
  passkeyLoginStart: () => request('/api/auth/passkey/login/start', { method: 'POST', body: '{}' }),
  passkeyLoginFinish: (data) => request('/api/auth/passkey/login/finish', { method: 'POST', body: JSON.stringify(data) })
}

export const noteApi = {
  tree: () => request('/api/tree'),
  list: () => request('/api/notes'),
  trash: () => request('/api/trash'),
  get: (id) => request(`/api/notes/${id}`),
  getTrash: (id) => request(`/api/trash/${id}`),
  create: (data) => request('/api/notes', { method: 'POST', body: JSON.stringify(data) }),
  update: (id, data) => request(`/api/notes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id) => request(`/api/notes/${id}`, { method: 'DELETE' }),
  restore: (id) => request(`/api/trash/${id}/restore`, { method: 'POST', body: '{}' }),
  purge: (id) => request(`/api/trash/${id}`, { method: 'DELETE' }),
  setPinned: (id, value) => request(`/api/notes/${id}/pinned`, { method: 'PUT', body: JSON.stringify({ value }) }),
  search: (q, offset = 0, limit = 20) => request(`/api/notes/search?q=${encodeURIComponent(q)}&offset=${offset}&limit=${limit}`),
  exportArchive: () => download('/api/notes/export'),
  importArchive: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request('/api/notes/import', { method: 'POST', body: formData })
  }
}

export const categoryApi = {
  create: (name, parentId) => request('/api/categories', { method: 'POST', body: JSON.stringify({ name, parentId }) })
}

export const shareApi = {
  list: (noteId) => request(`/api/notes/${noteId}/shares`),
  create: (noteId, data) => request(`/api/notes/${noteId}/shares`, { method: 'POST', body: JSON.stringify(data) }),
  delete: (noteId, shareId) => request(`/api/notes/${noteId}/shares/${shareId}`, { method: 'DELETE' }),
  open: (token, password) => {
    let url = `/api/shares/${token}`
    if (password) url += `?password=${encodeURIComponent(password)}`
    return request(url)
  },
  qrCode: (token) => request(`/api/shares/${token}/qrcode`)
}

export const themeApi = {
  list: () => request('/api/themes'),
  select: (themeId) => request('/api/themes/select', { method: 'POST', body: JSON.stringify({ themeId }) })
}

export const adminApi = {
  getSettings: () => request('/api/admin/settings'),
  saveSettings: (data) => request('/api/admin/settings', { method: 'POST', body: JSON.stringify(data) })
}

// ──── WebAuthn helpers ────

export function base64urlToBuffer(base64url) {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
  const pad = base64.length % 4 === 0 ? '' : '='.repeat(4 - (base64.length % 4))
  const binary = atob(base64 + pad)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

export function bufferToBase64url(buffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i])
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}
