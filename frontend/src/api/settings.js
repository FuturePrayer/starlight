import { request } from './client'

export const themeApi = {
  list: () => request('/api/themes'),
  select: (themeId) => request('/api/themes/select', { method: 'POST', body: JSON.stringify({ themeId }) })
}

export const adminApi = {
  getSettings: () => request('/api/admin/settings'),
  saveSettings: (data) => request('/api/admin/settings', { method: 'POST', body: JSON.stringify(data) })
}

export const assetApi = {
  settings: () => request('/api/assets/settings'),
  usage: () => request('/api/assets/usage'),
  uploadImage: (file, { noteId } = {}) => {
    const formData = new FormData()
    formData.append('file', file)
    if (noteId) {
      formData.append('noteId', noteId)
    }
    return request('/api/assets/images', { method: 'POST', body: formData })
  },
  cleanup: ({ dryRun = true, scope = 'self' } = {}) => request(`/api/assets/cleanup?dryRun=${dryRun ? 'true' : 'false'}&scope=${encodeURIComponent(scope)}`, { method: 'POST' }),
  adminUsage: (scope = 'self') => request(`/api/admin/assets/usage?scope=${encodeURIComponent(scope)}`),
  adminCleanup: ({ dryRun = true, scope = 'self' } = {}) => request(`/api/admin/assets/cleanup?dryRun=${dryRun ? 'true' : 'false'}&scope=${encodeURIComponent(scope)}`, { method: 'POST' })
}
