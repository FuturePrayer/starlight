import { request } from './client'

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

export const siteApi = {
  getInfo: (categoryId) => request(`/api/categories/${categoryId}/site`),
  enable: (categoryId, siteTitle, mergeSubSites = false) => request(`/api/categories/${categoryId}/site`, { method: 'POST', body: JSON.stringify({ siteTitle, mergeSubSites }) }),
  disable: (categoryId) => request(`/api/categories/${categoryId}/site`, { method: 'DELETE' }),
  updateTitle: (categoryId, siteTitle) => request(`/api/categories/${categoryId}/site`, { method: 'PUT', body: JSON.stringify({ siteTitle }) }),
  getIndex: (token) => request(`/api/site/${token}`),
  getNote: (token, noteId) => request(`/api/site/${token}/notes/${noteId}`),
  qrCode: (token) => request(`/api/site/${token}/qrcode`)
}
