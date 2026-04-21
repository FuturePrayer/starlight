import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { noteApi, categoryApi } from '@/api'

export const useNoteStore = defineStore('note', () => {
  const tree = ref({ items: [], pinnedItems: [], trashCount: 0 })
  const trashNotes = ref([])
  const trashTree = ref({ items: [], noteCount: 0, categoryCount: 0, totalCount: 0 })
  const currentNote = ref(null)
  const editMode = ref(false)
  const dirty = ref(false)
  const autosaveEnabled = ref(true)
  const lastSavedAt = ref(null)
  const expandedCategoryIds = ref([])
  const trashExpandedCategoryIds = ref([])
  const editSnapshot = ref(null)

  function cloneNote(note) {
    return note ? JSON.parse(JSON.stringify(note)) : null
  }

  function resetEditSession() {
    editMode.value = false
    dirty.value = false
    autosaveEnabled.value = true
    lastSavedAt.value = null
    editSnapshot.value = null
  }

  function sortTreeItems(items = []) {
    return [...items]
      .map(item => ({
        ...item,
        children: item.children?.length ? sortTreeItems(item.children) : (item.children || [])
      }))
      .sort((left, right) => {
        const typeOrder = Number(left.type !== 'category') - Number(right.type !== 'category')
        if (typeOrder !== 0) return typeOrder
        return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN', {
          numeric: true,
          sensitivity: 'base'
        })
      })
  }

  function hasExpandedCategory(id) {
    return expandedCategoryIds.value.includes(id)
  }

  function hasExpandedTrashCategory(id) {
    return trashExpandedCategoryIds.value.includes(id)
  }

  function setCategoryExpanded(id, expanded) {
    if (!id) return
    const next = expandedCategoryIds.value.filter(itemId => itemId !== id)
    if (expanded) next.push(id)
    expandedCategoryIds.value = next
  }

  function toggleCategoryExpanded(id) {
    setCategoryExpanded(id, !hasExpandedCategory(id))
  }

  function setTrashCategoryExpanded(id, expanded) {
    if (id === undefined || id === null) return
    const next = trashExpandedCategoryIds.value.filter(itemId => itemId !== id)
    if (expanded) next.push(id)
    trashExpandedCategoryIds.value = next
  }

  function toggleTrashCategoryExpanded(id) {
    setTrashCategoryExpanded(id, !hasExpandedTrashCategory(id))
  }

  async function refreshTree() {
    const nextTree = await noteApi.tree()
    tree.value = {
      ...nextTree,
      items: sortTreeItems(nextTree?.items || []),
      pinnedItems: nextTree?.pinnedItems || [],
      trashCount: Number(nextTree?.trashCount || 0)
    }
  }

  async function refreshTrash() {
    const [notes, nextTrashTree] = await Promise.all([
      noteApi.trash(),
      noteApi.trashTree()
    ])
    trashNotes.value = notes
    trashTree.value = {
      ...nextTrashTree,
      items: nextTrashTree?.items || [],
      noteCount: Number(nextTrashTree?.noteCount || 0),
      categoryCount: Number(nextTrashTree?.categoryCount || 0),
      totalCount: Number(nextTrashTree?.totalCount || 0)
    }
  }

  async function openNote(id) {
    const note = await noteApi.get(id)
    currentNote.value = note
    resetEditSession()
    return note
  }

  async function openTrashNote(id) {
    const note = await noteApi.getTrash(id)
    currentNote.value = note
    resetEditSession()
    return note
  }

  function clearCurrentNote() {
    currentNote.value = null
    resetEditSession()
  }

  async function saveNote(payload) {
    let note
    if (currentNote.value?.id) {
      note = await noteApi.update(currentNote.value.id, payload)
    } else {
      note = await noteApi.create(payload)
    }
    currentNote.value = note
    dirty.value = false
    lastSavedAt.value = Date.now()
    editSnapshot.value = cloneNote(note)
    await refreshTree()
    await refreshTrash()
    return note
  }

  async function deleteNote(id) {
    await noteApi.delete(id)
    if (currentNote.value?.id === id) {
      currentNote.value = null
    }
    resetEditSession()
    await refreshTree()
    await refreshTrash()
  }

  async function restoreNote(id) {
    const note = await noteApi.restore(id)
    currentNote.value = note
    resetEditSession()
    await refreshTree()
    await refreshTrash()
    return note
  }

  async function purgeNote(id) {
    await noteApi.purge(id)
    if (currentNote.value?.id === id) {
      currentNote.value = null
    }
    resetEditSession()
    await refreshTree()
    await refreshTrash()
  }

  async function setPinned(id, value) {
    const note = await noteApi.setPinned(id, value)
    if (currentNote.value?.id === id) {
      currentNote.value = note
    }
    await refreshTree()
    await refreshTrash()
    return note
  }

  async function createCategory(name, parentId) {
    const cat = await categoryApi.create(name, parentId)
    await refreshTree()
    return cat
  }

  async function updateCategory(id, name, parentId) {
    const cat = await categoryApi.update(id, name, parentId)
    await refreshTree()
    return cat
  }

  async function deleteCategory(id) {
    const result = await categoryApi.delete(id)
    if (currentNote.value?.categoryId === id) {
      currentNote.value = null
      resetEditSession()
    }
    await refreshTree()
    await refreshTrash()
    return result
  }

  async function purgeTrashCategory(id) {
    const result = await categoryApi.purgeTrash(id)
    if (currentNote.value?.categoryId === id) {
      currentNote.value = null
      resetEditSession()
    }
    await refreshTree()
    await refreshTrash()
    return result
  }

  async function restoreTrashCategory(id) {
    const result = await categoryApi.restoreTrash(id)
    await refreshTree()
    await refreshTrash()
    return result
  }

  async function exportArchive() {
    return noteApi.exportArchive()
  }

  async function importArchive(file) {
    const result = await noteApi.importArchive(file)
    await refreshTree()
    return result
  }

  function startNewNote(categoryId = null) {
    currentNote.value = {
      id: null,
      title: '',
      markdownContent: '',
      renderedHtml: '',
      outlineJson: '[]',
      categoryId
    }
    editMode.value = true
    dirty.value = false
    autosaveEnabled.value = true
    lastSavedAt.value = null
    editSnapshot.value = null
  }

  function enterEditMode() {
    if (!currentNote.value) return
    editSnapshot.value = cloneNote(currentNote.value)
    editMode.value = true
    dirty.value = false
    autosaveEnabled.value = true
    lastSavedAt.value = null
  }

  function discardEdit() {
    currentNote.value = cloneNote(editSnapshot.value)
    resetEditSession()
  }

  function finishEditing() {
    resetEditSession()
  }

  function setAutosaveEnabled(value) {
    autosaveEnabled.value = Boolean(value)
  }

  function setEditMode(val) {
    if (val) {
      enterEditMode()
      return
    }
    finishEditing()
  }

  function flatNotes(items, collector = []) {
    for (const item of items || []) {
      if (item.type === 'note') collector.push(item)
      if (item.children?.length) flatNotes(item.children, collector)
    }
    return collector
  }

  const allNotes = computed(() => {
    const merged = [
      ...flatNotes(tree.value?.items, []),
      ...(tree.value?.pinnedItems || [])
    ]
    const unique = new Map()
    for (const item of merged) {
      if (item?.id && !unique.has(item.id)) {
        unique.set(item.id, item)
      }
    }
    return [...unique.values()]
  })

  return {
    tree, trashNotes, trashTree, currentNote, editMode, dirty, autosaveEnabled, lastSavedAt, expandedCategoryIds, trashExpandedCategoryIds,
    refreshTree, refreshTrash, openNote, openTrashNote, clearCurrentNote, saveNote, deleteNote, restoreNote, purgeNote, setPinned,
    createCategory, updateCategory, deleteCategory, restoreTrashCategory, purgeTrashCategory, exportArchive, importArchive,
    startNewNote, enterEditMode, discardEdit, finishEditing,
    setEditMode, setAutosaveEnabled,
    hasExpandedCategory, setCategoryExpanded, toggleCategoryExpanded,
    hasExpandedTrashCategory, setTrashCategoryExpanded, toggleTrashCategoryExpanded,
    allNotes
  }
})

