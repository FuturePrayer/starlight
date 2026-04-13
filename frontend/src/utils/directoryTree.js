function compareByLabel(left, right) {
  return String(left?.label || left?.name || '').localeCompare(String(right?.label || right?.name || ''), 'zh-CN', {
    numeric: true,
    sensitivity: 'base'
  })
}

function cloneCategoryNode(item) {
  const children = (item.children || [])
    .filter(child => child?.type === 'category')
    .map(cloneCategoryNode)
    .sort(compareByLabel)

  return {
    id: item.id,
    type: 'category',
    label: item.name,
    children
  }
}

function createDirectoryNode(path, label, markdownFileCount = 0) {
  return {
    id: path,
    path,
    type: 'category',
    label,
    metaText: `${markdownFileCount} 个 Markdown 文件`,
    markdownFileCount,
    children: []
  }
}

function ensureDirectoryNode(nodesByPath, path, fallbackLabel) {
  if (!nodesByPath.has(path)) {
    nodesByPath.set(path, createDirectoryNode(path, fallbackLabel, 0))
  }
  return nodesByPath.get(path)
}

function sortTree(items = []) {
  return [...items]
    .map(item => ({
      ...item,
      children: item.children?.length ? sortTree(item.children) : []
    }))
    .sort(compareByLabel)
}

export function getTreeNodeLabel(item) {
  return String(item?.label || item?.name || item?.title || '').trim()
}

export function buildCategorySelectionTree(items = []) {
  return sortTree(
    (items || [])
      .filter(item => item?.type === 'category')
      .map(cloneCategoryNode)
  )
}

export function buildGitDirectoryTree(directories = []) {
  const sortedDirectories = [...(directories || [])].sort((left, right) => {
    const leftDepth = String(left?.path || '').split('/').filter(Boolean).length
    const rightDepth = String(right?.path || '').split('/').filter(Boolean).length
    if (leftDepth !== rightDepth) return leftDepth - rightDepth
    return String(left?.path || '').localeCompare(String(right?.path || ''), 'zh-CN', {
      numeric: true,
      sensitivity: 'base'
    })
  })

  const nodesByPath = new Map()
  const roots = []

  for (const item of sortedDirectories) {
    const rawPath = String(item?.path || '')
    const segments = rawPath.split('/').filter(Boolean)
    const displayLabel = rawPath
      ? (segments[segments.length - 1] || item?.label || rawPath)
      : '仓库根目录'

    const currentNode = ensureDirectoryNode(nodesByPath, rawPath, displayLabel)
    currentNode.label = displayLabel
    currentNode.metaText = `${Number(item?.markdownFileCount || 0)} 个 Markdown 文件`
    currentNode.markdownFileCount = Number(item?.markdownFileCount || 0)

    if (!rawPath) {
      if (!roots.includes(currentNode)) {
        roots.push(currentNode)
      }
      continue
    }

    const parentPath = segments.length > 1 ? segments.slice(0, -1).join('/') : null
    if (parentPath !== null) {
      const parentLabel = segments[segments.length - 2] || '目录'
      const parentNode = ensureDirectoryNode(nodesByPath, parentPath, parentLabel)
      if (!parentNode.children.some(child => child.id === currentNode.id)) {
        parentNode.children.push(currentNode)
      }
      continue
    }

    if (!roots.includes(currentNode)) {
      roots.push(currentNode)
    }
  }

  if (!roots.length && nodesByPath.size) {
    roots.push(...nodesByPath.values())
  }

  return sortTree(roots)
}

export function findTreeNodeById(items = [], targetId) {
  for (const item of items || []) {
    if (item?.id === targetId) {
      return item
    }
    if (item?.children?.length) {
      const found = findTreeNodeById(item.children, targetId)
      if (found) {
        return found
      }
    }
  }
  return null
}

export function getTreeItemsAtPath(items = [], pathIds = []) {
  let currentItems = items || []
  for (const id of pathIds || []) {
    const nextCategory = currentItems.find(item => item?.type === 'category' && item.id === id)
    if (!nextCategory) {
      return items || []
    }
    currentItems = nextCategory.children || []
  }
  return currentItems
}

export function findTreePathById(items = [], targetId, { includeTargetCategory = false } = {}) {
  function walk(nodes, categoryPath = []) {
    for (const item of nodes || []) {
      if (item?.id === targetId) {
        if (item?.type === 'category' && includeTargetCategory) {
          return [...categoryPath, item.id]
        }
        return categoryPath
      }

      if (item?.type === 'category' && item.children?.length) {
        const result = walk(item.children, [...categoryPath, item.id])
        if (result) {
          return result
        }
      }
    }
    return null
  }

  return walk(items || [], []) || []
}

export function summarizeTreeSubtree(source, targetId = undefined) {
  const root = typeof targetId === 'undefined' ? source : findTreeNodeById(source, targetId)
  if (!root) {
    return {
      categoryCount: 0,
      noteCount: 0,
      categoryIds: [],
      noteIds: []
    }
  }

  const roots = Array.isArray(root) ? root : [root]
  const categoryIds = []
  const noteIds = []
  let categoryCount = 0
  let noteCount = 0

  function walk(items) {
    for (const item of items || []) {
      if (item?.type === 'category') {
        categoryCount += 1
        if (item.id !== undefined && item.id !== null) {
          categoryIds.push(item.id)
        }
      } else if (item?.type === 'note') {
        noteCount += 1
        if (item.id !== undefined && item.id !== null) {
          noteIds.push(item.id)
        }
      }
      if (item?.children?.length) {
        walk(item.children)
      }
    }
  }

  walk(roots)

  return {
    categoryCount,
    noteCount,
    categoryIds,
    noteIds
  }
}

