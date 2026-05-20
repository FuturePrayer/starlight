import { request } from './client'

export const authApi = {
  registrationStatus: () => request('/api/auth/registration-status'),
  register: (email, password) => request('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  login: (username, password) => request('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => request('/api/auth/logout', { method: 'POST', body: '{}' }),
  me: () => request('/api/auth/me'),
  updateProfile: (data) => request('/api/auth/profile', { method: 'PUT', body: JSON.stringify(data) }),
  totpSetup: () => request('/api/auth/totp/setup', { method: 'POST', body: '{}' }),
  totpConfirm: (secret, code) => request('/api/auth/totp/confirm', { method: 'POST', body: JSON.stringify({ secret, code }) }),
  totpRevoke: () => request('/api/auth/totp', { method: 'DELETE' }),
  totpVerifyLogin: (pendingToken, code) => request('/api/auth/totp/verify-login', { method: 'POST', body: JSON.stringify({ pendingToken, code }) }),
  passkeyList: () => request('/api/auth/passkey/credentials'),
  passkeyRegisterStart: () => request('/api/auth/passkey/register/start', { method: 'POST', body: '{}' }),
  passkeyRegisterFinish: (data) => request('/api/auth/passkey/register/finish', { method: 'POST', body: JSON.stringify(data) }),
  passkeyDelete: (id) => request(`/api/auth/passkey/credentials/${id}`, { method: 'DELETE' }),
  passkeyLoginStart: () => request('/api/auth/passkey/login/start', { method: 'POST', body: '{}' }),
  passkeyLoginFinish: (data) => request('/api/auth/passkey/login/finish', { method: 'POST', body: JSON.stringify(data) })
}

export const apiKeyApi = {
  list: () => request('/api/auth/api-keys'),
  create: (data) => request('/api/auth/api-keys', { method: 'POST', body: JSON.stringify(data) }),
  update: (id, data) => request(`/api/auth/api-keys/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id) => request(`/api/auth/api-keys/${id}`, { method: 'DELETE' }),
  copyWithTotp: (id, code) => request(`/api/auth/api-keys/${id}/copy/totp`, { method: 'POST', body: JSON.stringify({ code }) }),
  copyPasskeyStart: (id) => request(`/api/auth/api-keys/${id}/copy/passkey/start`, { method: 'POST', body: '{}' }),
  copyPasskeyFinish: (id, data) => request(`/api/auth/api-keys/${id}/copy/passkey/finish`, { method: 'POST', body: JSON.stringify(data) })
}

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
