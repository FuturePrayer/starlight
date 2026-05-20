import { request } from './client'

export const gitApi = {
  getStatus: () => request('/api/git/status'),
  resolveBranches: (repositoryUrl) => request('/api/git/branches', { method: 'POST', body: JSON.stringify({ repositoryUrl }) }),
  createPreview: (repositoryUrl, branchName) => request('/api/git/preview', { method: 'POST', body: JSON.stringify({ repositoryUrl, branchName }) }),
  discardPreview: (token) => request(`/api/git/preview/${token}`, { method: 'DELETE' }),
  importFromPreview: (data) => request('/api/git/import', { method: 'POST', body: JSON.stringify(data) }),
  listSources: () => request('/api/git/sources'),
  deleteSource: (sourceId) => request(`/api/git/sources/${sourceId}`, { method: 'DELETE' }),
  syncNow: (sourceId) => request(`/api/git/sources/${sourceId}/sync`, { method: 'POST', body: '{}' }),
  updateAutoSync: (sourceId, data) => request(`/api/git/sources/${sourceId}/auto-sync`, { method: 'PUT', body: JSON.stringify(data) })
}
