import { download, request } from './client'

export const noteApi = {
  tree: () => request('/api/tree'),
  list: () => request('/api/notes'),
  trash: () => request('/api/trash'),
  trashTree: () => request('/api/trash/tree'),
  get: (id) => request(`/api/notes/${id}`),
  getTrash: (id) => request(`/api/trash/${id}`),
  create: (data) => request('/api/notes', { method: 'POST', body: JSON.stringify(data) }),
  update: (id, data) => request(`/api/notes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id) => request(`/api/notes/${id}`, { method: 'DELETE' }),
  restore: (id) => request(`/api/trash/${id}/restore`, { method: 'POST', body: '{}' }),
  purge: (id) => request(`/api/trash/${id}`, { method: 'DELETE' }),
  setPinned: (id, value) => request(`/api/notes/${id}/pinned`, { method: 'PUT', body: JSON.stringify({ value }) }),
  search: (q, offset = 0, limit = 20, { scope = 'all', categoryIds = null, noteId = null } = {}) => {
    let url = `/api/notes/search?q=${encodeURIComponent(q)}&offset=${offset}&limit=${limit}&scope=${scope}`
    if (scope === 'categories' && categoryIds) {
      url += `&categoryIds=${encodeURIComponent(categoryIds)}`
    }
    if (scope === 'current' && noteId) {
      url += `&noteId=${encodeURIComponent(noteId)}`
    }
    return request(url)
  },
  exportArchive: () => download('/api/notes/export'),
  exportCategoryArchive: (categoryId) => download(`/api/categories/${categoryId}/export`),
  importArchive: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request('/api/notes/import', { method: 'POST', body: formData })
  }
}

export const categoryApi = {
  create: (name, parentId) => request('/api/categories', { method: 'POST', body: JSON.stringify({ name, parentId }) }),
  update: (id, name, parentId) => request(`/api/categories/${id}`, { method: 'PUT', body: JSON.stringify({ name, parentId }) }),
  delete: (id) => request(`/api/categories/${id}`, { method: 'DELETE' }),
  restoreTrash: (id) => request(`/api/trash/categories/${id}/restore`, { method: 'POST', body: '{}' }),
  purgeTrash: (id) => request(`/api/trash/categories/${id}`, { method: 'DELETE' })
}
