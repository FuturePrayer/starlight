import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { noteApi, categoryApi } from '@/api'

export const useNoteStore = defineStore('note', () => {
  const tree = ref({ items: [] })
  const currentNote = ref(null)
  const editMode = ref(false)
  const dirty = ref(false)

  async function refreshTree() {
    tree.value = await noteApi.tree()
  }

  async function openNote(id) {
    const note = await noteApi.get(id)
    currentNote.value = note
    editMode.value = false
    dirty.value = false
    return note
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
    await refreshTree()
    return note
  }

  async function deleteNote(id) {
    await noteApi.delete(id)
    if (currentNote.value?.id === id) {
      currentNote.value = null
    }
    await refreshTree()
  }

  async function createCategory(name, parentId) {
    const cat = await categoryApi.create(name, parentId)
    await refreshTree()
    return cat
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
  }

  function setEditMode(val) {
    editMode.value = val
  }

  function flatNotes(items, collector = []) {
    for (const item of items || []) {
      if (item.type === 'note') collector.push(item)
      if (item.children?.length) flatNotes(item.children, collector)
    }
    return collector
  }

  const allNotes = computed(() => flatNotes(tree.value?.items))

  return {
    tree, currentNote, editMode, dirty,
    refreshTree, openNote, saveNote, deleteNote,
    createCategory, startNewNote, setEditMode, allNotes
  }
})

