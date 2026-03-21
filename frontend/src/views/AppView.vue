<template>
  <div class="app-shell">
    <!-- Mobile sidebar toggle -->
    <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen" v-if="isMobile">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
    </button>

    <!-- Sidebar -->
    <aside :class="['sidebar', { open: sidebarOpen }]" @click.self="sidebarOpen = false">
      <div class="sidebar-inner">
        <!-- Profile section -->
        <div class="sidebar-profile">
          <div class="profile-info">
            <div class="profile-avatar">{{ authStore.username?.charAt(0)?.toUpperCase() || '?' }}</div>
            <div>
              <div class="profile-name">{{ authStore.username }}</div>
              <div class="profile-role">{{ authStore.isAdmin ? '管理员' : '用户' }}</div>
            </div>
          </div>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="handleLogout" title="退出登录">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
          </button>
        </div>

        <!-- Theme selector -->
        <div class="sidebar-section">
          <label class="sl-label">主题</label>
          <select class="sl-select" :value="themeStore.currentId" @change="handleThemeChange($event.target.value)">
            <option v-for="t in allThemes" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </div>

        <!-- Action buttons -->
        <div class="sidebar-actions">
          <button class="sl-btn sl-btn--primary" @click="handleNewNote" style="flex:1">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            新建笔记
          </button>
          <button class="sl-btn" @click="showCategoryModal = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
          </button>
          <button v-if="authStore.isAdmin" class="sl-btn" @click="showAdminModal = true" title="管理员设置">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
          </button>
        </div>

        <!-- Sidebar tabs -->
        <div class="sidebar-tabs">
          <button :class="['tab-btn', { active: sidebarTab === 'tree' }]" @click="sidebarTab = 'tree'">目录</button>
          <button :class="['tab-btn', { active: sidebarTab === 'outline' }]" @click="sidebarTab = 'outline'">大纲</button>
        </div>

        <!-- Tree / Outline panels -->
        <div class="sidebar-scroll">
          <div v-show="sidebarTab === 'tree'" class="tree-panel">
            <TreeNode
              v-for="item in noteStore.tree.items"
              :key="item.id"
              :item="item"
              :selected-id="noteStore.currentNote?.id"
              @select-note="handleOpenNote"
              @select-category="selectedCategoryId = $event"
            />
            <div v-if="!noteStore.tree.items?.length" class="empty-hint">还没有笔记，点击上方按钮创建</div>
          </div>
          <div v-show="sidebarTab === 'outline'" class="outline-panel">
            <OutlineList :markdown="outlineSource" />
          </div>
        </div>
      </div>
    </aside>

    <!-- Main content -->
    <main class="main-content">
      <!-- Top bar -->
      <div class="topbar">
        <div class="topbar-left">
          <h1 class="topbar-title">{{ noteStore.currentNote?.title || 'Starlight' }}</h1>
          <div class="topbar-meta" v-if="noteStore.currentNote">
            <span class="sl-badge">{{ noteStore.editMode ? '编辑中' : '查看' }}</span>
            <span class="sl-badge" v-if="noteStore.currentNote.updatedAt">{{ formatTime(noteStore.currentNote.updatedAt) }}</span>
          </div>
        </div>
        <div class="topbar-actions" v-if="!isMobile">
          <template v-if="noteStore.editMode">
            <button class="sl-btn" @click="togglePreview">{{ previewVisible ? '关闭预览' : '打开预览' }}</button>
            <button class="sl-btn sl-btn--primary" @click="handleSave">保存</button>
            <button class="sl-btn" @click="handleFinish">完成</button>
          </template>
          <template v-else-if="noteStore.currentNote">
            <button class="sl-btn" @click="handleShare" v-if="noteStore.currentNote.id">分享</button>
            <button class="sl-btn sl-btn--primary" @click="noteStore.setEditMode(true)">编辑</button>
            <button class="sl-btn sl-btn--danger" @click="handleDelete" v-if="noteStore.currentNote.id" title="删除笔记">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            </button>
          </template>
        </div>
      </div>

      <!-- Mobile floating action bar -->
      <div v-if="isMobile && noteStore.editMode" class="mobile-fab">
        <div :class="['fab-menu', { expanded: mobileActionsOpen }]">
          <button class="fab-toggle sl-btn sl-btn--primary" @click="mobileActionsOpen = !mobileActionsOpen">
            {{ mobileActionsOpen ? '收起' : '操作' }}
          </button>
          <template v-if="mobileActionsOpen">
            <button class="sl-btn" @click="togglePreview">{{ previewVisible ? '恢复输入' : '预览' }}</button>
            <button class="sl-btn sl-btn--primary" @click="handleSave">保存</button>
            <button class="sl-btn" @click="handleFinish">完成</button>
          </template>
        </div>
      </div>
      <div v-if="isMobile && !noteStore.editMode && noteStore.currentNote" class="mobile-fab">
        <div class="fab-menu expanded">
          <button class="sl-btn" @click="handleShare" v-if="noteStore.currentNote.id">分享</button>
          <button class="sl-btn sl-btn--primary" @click="noteStore.setEditMode(true)">编辑</button>
        </div>
      </div>

      <!-- View mode -->
      <div v-if="!noteStore.editMode" class="viewer-area">
        <div v-if="noteStore.currentNote" class="markdown-body" v-html="renderedHtml"></div>
        <div v-else class="empty-state">
          <div class="empty-icon">✨</div>
          <h2>欢迎使用 Starlight</h2>
          <p>从左侧选择笔记，或新建一篇星光笔记。</p>
        </div>
      </div>

      <!-- Edit mode -->
      <div v-else class="editor-area" :class="{ 'with-preview': previewVisible && !isMobile }">
        <div class="editor-pane" :class="{ hidden: isMobile && previewVisible }">
          <div class="editor-fields">
            <div class="field-row">
              <div class="field-col" style="flex:2">
                <label class="sl-label">标题</label>
                <input v-model="editorTitle" class="sl-input" placeholder="笔记标题" @input="markDirty" />
              </div>
              <div class="field-col" style="flex:1">
                <label class="sl-label">分类</label>
                <select v-model="editorCategory" class="sl-select" @change="markDirty">
                  <option value="">无分类</option>
                  <option v-for="opt in categoryOptions" :key="opt.id" :value="opt.id">{{ opt.label }}</option>
                </select>
              </div>
            </div>
          </div>
          <textarea
            ref="editorTextarea"
            v-model="editorContent"
            class="editor-textarea"
            placeholder="# 从这里开始记录你的星光..."
            @input="handleEditorInput"
          ></textarea>
        </div>
        <div v-if="previewVisible" class="preview-pane" :class="{ 'mobile-full': isMobile }">
          <div class="markdown-body" v-html="livePreviewHtml"></div>
        </div>
      </div>
    </main>

    <!-- Modals -->
    <ShareModal
      v-if="showShareModal"
      :note-id="noteStore.currentNote?.id"
      @close="showShareModal = false"
    />
    <CategoryModal
      v-if="showCategoryModal"
      :tree-items="noteStore.tree.items"
      @close="showCategoryModal = false"
      @created="handleCategoryCreated"
    />
    <AdminModal
      v-if="showAdminModal"
      @close="showAdminModal = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useNoteStore } from '@/stores/note'
import { useToastStore } from '@/stores/toast'
import { renderMarkdown, formatTime } from '@/utils/markdown'
import TreeNode from '@/components/TreeNode.vue'
import OutlineList from '@/components/OutlineList.vue'
import ShareModal from '@/components/ShareModal.vue'
import CategoryModal from '@/components/CategoryModal.vue'
import AdminModal from '@/components/AdminModal.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const noteStore = useNoteStore()
const toast = useToastStore()

const sidebarOpen = ref(false)
const sidebarTab = ref('tree')
const previewVisible = ref(true)
const mobileActionsOpen = ref(false)
const selectedCategoryId = ref(null)
const allThemes = ref([])
const showShareModal = ref(false)
const showCategoryModal = ref(false)
const showAdminModal = ref(false)

const editorTitle = ref('')
const editorContent = ref('')
const editorCategory = ref('')

const isMobile = ref(window.innerWidth <= 768)
let autosaveTimer = null

const renderedHtml = computed(() => {
  if (!noteStore.currentNote) return ''
  return renderMarkdown(noteStore.currentNote.markdownContent)
})

const livePreviewHtml = computed(() => renderMarkdown(editorContent.value))

const outlineSource = computed(() => {
  if (noteStore.editMode) return editorContent.value
  return noteStore.currentNote?.markdownContent || ''
})

const categoryOptions = computed(() => {
  const result = []
  function walk(items, prefix = '') {
    for (const item of items || []) {
      if (item.type === 'category') {
        result.push({ id: item.id, label: prefix + item.name })
        if (item.children?.length) walk(item.children, prefix + '— ')
      }
    }
  }
  walk(noteStore.tree.items)
  return result
})

// Sync editor fields when note changes
watch(() => noteStore.currentNote, (note) => {
  if (note) {
    editorTitle.value = note.title || ''
    editorContent.value = note.markdownContent || ''
    editorCategory.value = note.categoryId || ''
  }
}, { immediate: true })

watch(() => noteStore.editMode, (mode) => {
  if (mode && noteStore.currentNote) {
    editorTitle.value = noteStore.currentNote.title || ''
    editorContent.value = noteStore.currentNote.markdownContent || ''
    editorCategory.value = noteStore.currentNote.categoryId || ''
  }
})

function markDirty() { noteStore.dirty = true }
function handleEditorInput() { noteStore.dirty = true }

function togglePreview() { previewVisible.value = !previewVisible.value }

async function handleSave() {
  try {
    await noteStore.saveNote({
      title: editorTitle.value,
      markdownContent: editorContent.value,
      categoryId: editorCategory.value || null
    })
    toast.success('已保存')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleFinish() {
  try {
    await noteStore.saveNote({
      title: editorTitle.value,
      markdownContent: editorContent.value,
      categoryId: editorCategory.value || null
    })
    noteStore.setEditMode(false)
    toast.success('已保存')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleOpenNote(id) {
  try {
    await noteStore.openNote(id)
    sidebarOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}

function handleNewNote() {
  noteStore.startNewNote(selectedCategoryId.value)
  editorTitle.value = ''
  editorContent.value = ''
  editorCategory.value = selectedCategoryId.value || ''
  sidebarOpen.value = false
}

function handleShare() {
  if (noteStore.currentNote?.id) showShareModal.value = true
}

async function handleDelete() {
  if (!noteStore.currentNote?.id) return
  if (!confirm('确定删除这篇笔记吗？此操作不可撤销。')) return
  try {
    await noteStore.deleteNote(noteStore.currentNote.id)
    toast.success('已删除')
  } catch (err) {
    toast.error(err.message)
  }
}

function handleCategoryCreated() {
  showCategoryModal.value = false
  toast.success('分类已创建')
}

async function handleThemeChange(id) {
  try {
    await themeStore.selectTheme(id)
    toast.success('主题已切换')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

function handleResize() {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) previewVisible.value = true
}

onMounted(async () => {
  themeStore.loadCached()
  window.addEventListener('resize', handleResize)

  try {
    await authStore.fetchMe()
    allThemes.value = await themeStore.loadThemes()
    await noteStore.refreshTree()
    // Open first note if available
    if (noteStore.allNotes.length) {
      await noteStore.openNote(noteStore.allNotes[0].id)
    }
    // Autosave every 30s
    autosaveTimer = setInterval(async () => {
      if (noteStore.editMode && noteStore.dirty) {
        try {
          await noteStore.saveNote({
            title: editorTitle.value,
            markdownContent: editorContent.value,
            categoryId: editorCategory.value || null
          })
          toast.info('自动保存完成')
        } catch {}
      }
    }, 30000)
  } catch {
    router.replace('/login')
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  clearInterval(autosaveTimer)
})
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--sl-bg);
}

/* --- Sidebar --- */
.sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--sl-sidebar-bg);
  border-right: 1px solid var(--sl-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  gap: 14px;
  overflow: hidden;
}
.sidebar-profile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--sl-border);
}
.profile-info { display: flex; align-items: center; gap: 10px; }
.profile-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--sl-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}
.profile-name { font-size: 13px; font-weight: 600; }
.profile-role { font-size: 11px; color: var(--sl-text-tertiary); }

.sidebar-section { display: flex; flex-direction: column; }
.sidebar-actions { display: flex; gap: 6px; }
.sidebar-tabs {
  display: flex;
  background: var(--sl-active-bg);
  border-radius: var(--sl-radius);
  padding: 3px;
}
.tab-btn {
  flex: 1;
  background: transparent;
  border: none;
  padding: 5px 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--sl-text-secondary);
  cursor: pointer;
  border-radius: 5px;
  transition: all 0.15s;
}
.tab-btn.active {
  background: var(--sl-card);
  color: var(--sl-text);
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.empty-hint {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--sl-text-tertiary);
}

/* --- Main --- */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--sl-bg-secondary);
  position: relative;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--sl-border);
  gap: 16px;
  background: var(--sl-card);
  min-height: 60px;
}
.topbar-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 500px;
}
.topbar-meta { display: flex; gap: 8px; margin-top: 4px; }
.topbar-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* --- Viewer --- */
.viewer-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: var(--sl-text-secondary);
}
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h2 { font-size: 20px; font-weight: 600; margin-bottom: 8px; }
.empty-state p { font-size: 14px; color: var(--sl-text-tertiary); }

/* --- Editor --- */
.editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-area.with-preview {
  flex-direction: row;
}
.editor-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-pane.hidden { display: none; }
.editor-fields { padding: 16px 20px 8px; }
.field-row { display: flex; gap: 12px; }
.field-col { display: flex; flex-direction: column; }
.editor-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  padding: 12px 20px;
  font-family: var(--sl-font-mono);
  font-size: 14px;
  line-height: 1.65;
  background: transparent;
  color: var(--sl-text);
}
.editor-textarea::placeholder { color: var(--sl-text-tertiary); }
.preview-pane {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  border-left: 1px solid var(--sl-border);
  background: var(--sl-bg);
}
.preview-pane.mobile-full {
  border-left: none;
  position: absolute;
  inset: 0;
  top: 60px;
  z-index: 10;
  background: var(--sl-bg-secondary);
}

/* --- Mobile --- */
.sidebar-toggle {
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 60;
  width: 36px;
  height: 36px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
  background: var(--sl-card);
  box-shadow: var(--sl-shadow-card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--sl-text);
}
.mobile-fab {
  position: fixed;
  bottom: 16px;
  right: 16px;
  z-index: 50;
}
.fab-menu {
  display: flex;
  gap: 6px;
  background: var(--sl-card);
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  padding: 6px;
  box-shadow: var(--sl-shadow-flyout);
}
.fab-toggle { border-radius: var(--sl-radius); }

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    inset: 0;
    z-index: 55;
    width: 100%;
    min-width: unset;
    background: var(--sl-backdrop);
    border-right: none;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .sidebar.open { transform: translateX(0); }
  .sidebar.open .sidebar-inner {
    width: 300px;
    max-width: 85vw;
    background: var(--sl-sidebar-bg);
    height: 100%;
    box-shadow: var(--sl-shadow-flyout);
  }
  .topbar { padding: 16px 16px 16px 56px; }
  .viewer-area { padding: 16px; }
  .editor-fields { padding: 12px 16px 8px; }
  .field-row { flex-direction: column; gap: 8px; }
  .editor-textarea { padding: 12px 16px; }
}
</style>

