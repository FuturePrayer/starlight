<template>
  <div class="site-page">
    <!-- Mobile sidebar toggle -->
    <button class="site-sidebar-toggle" @click="sidebarOpen = !sidebarOpen" v-if="isMobile">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
    </button>

    <!-- Sidebar -->
    <aside :class="['site-sidebar', { open: sidebarOpen, resizing: isSidebarResizing }]" :style="sidebarStyle" @click.self="sidebarOpen = false">
      <div class="site-sidebar-inner">
        <div v-if="isMobile" class="site-sidebar-mobile-chrome sl-card">
          <div>
            <div class="site-sidebar-mobile-chrome__title">{{ mobileSidebarTitle }}</div>
            <div class="site-sidebar-mobile-chrome__meta">{{ siteTitle }} · {{ noteCount }} 篇文章</div>
          </div>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" type="button" @click="sidebarOpen = false" title="关闭侧边栏">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="site-sidebar-header">
          <div class="site-badge">星迹书阁</div>
          <h2 class="site-title">{{ siteTitle }}</h2>
          <p class="site-hint">{{ ownerName }} · {{ noteCount }} 篇文章</p>
        </div>

        <!-- Tabs: 目录 / 大纲 -->
        <div class="sidebar-tabs">
          <button :class="['tab-btn', { active: sidebarTab === 'tree' }]" @click="sidebarTab = 'tree'">目录</button>
          <button :class="['tab-btn', { active: sidebarTab === 'outline' }]" @click="sidebarTab = 'outline'">大纲</button>
        </div>

        <!-- Scrollable content -->
        <div class="sidebar-scroll">
          <!-- Tree panel -->
          <div v-show="sidebarTab === 'tree'" class="tree-panel">
            <MobileTreeBrowser
              v-if="isMobile"
              :items="siteTree.items"
              :pinned-items="siteTree.pinnedItems"
              :path="mobileTreePath"
              :selected-id="currentNoteId"
              root-title="全部文章"
              empty-text="暂无文章"
              @navigate="handleMobileSiteNavigate"
              @select-note="handleSelectNote"
            />
            <template v-else>
              <!-- Pinned section -->
              <section v-if="siteTree.pinnedItems?.length" class="quick-section">
                <div class="quick-section__header">
                  <span class="quick-section__title">置顶</span>
                  <span class="quick-section__hint">目录最上方</span>
                </div>
                <div class="quick-section__list">
                  <SiteTreeNode
                    v-for="item in siteTree.pinnedItems"
                    :key="`pinned-${item.id}`"
                    :item="item"
                    :selected-id="currentNoteId"
                    :expanded-ids="expandedCats"
                    @select-note="handleSelectNote"
                    @toggle-category="toggleCatExpand"
                  />
                </div>
              </section>
              <!-- Main tree -->
              <SiteTreeNode
                v-for="item in siteTree.items"
                :key="item.id"
                :item="item"
                :selected-id="currentNoteId"
                :expanded-ids="expandedCats"
                @select-note="handleSelectNote"
                @toggle-category="toggleCatExpand"
              />
              <div
                v-if="!siteTree.items?.length && !siteTree.pinnedItems?.length && loaded"
                class="empty-hint"
              >
                暂无文章
              </div>
            </template>
          </div>

          <!-- Outline panel -->
          <div v-show="sidebarTab === 'outline'" class="outline-panel">
            <OutlineList
              v-if="currentNoteMarkdown"
              :markdown="currentNoteMarkdown"
              :active-anchor="activeOutlineAnchor"
              @select="handleOutlineSelect"
            />
            <div v-else class="empty-hint">选择一篇文章后查看大纲</div>
          </div>
        </div>
      </div>
    </aside>
    <div v-if="!isMobile" class="site-sidebar-resize-handle" @pointerdown="startSidebarResize" @dblclick="resetSidebarWidth" />

    <!-- Main -->
    <main class="site-main">
      <!-- Note detail view -->
      <template v-if="currentNote">
        <div class="site-topbar">
          <div>
            <h1 class="site-article-title">{{ currentNote.title }}</h1>
            <div class="site-article-meta">
              <span class="sl-badge">{{ formatTime(currentNote.updatedAt) }}</span>
              <span class="sl-badge" v-if="currentNote.createdAt">创建于 {{ formatTime(currentNote.createdAt) }}</span>
            </div>
          </div>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="handleBackToList" title="返回文章列表">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
            返回列表
          </button>
        </div>
        <div ref="siteContentRef" class="site-content" @scroll="handleContentScroll">
          <div ref="siteMarkdownRef" class="markdown-body" v-html="renderedHtml"></div>
        </div>
      </template>

      <!-- Index / list view -->
      <template v-else-if="loaded">
        <div class="site-topbar">
          <div>
            <h1 class="site-article-title">{{ siteTitle }}</h1>
            <div class="site-article-meta">
              <span class="sl-badge">{{ noteCount }} 篇文章</span>
              <span class="sl-badge">{{ ownerName }}</span>
            </div>
          </div>
        </div>
        <div ref="siteContentRef" class="site-content" @scroll="handleContentScroll">
          <!-- Grouped view when has sub-categories -->
          <div v-if="hasSubCategories" class="site-grouped-list">
            <!-- Root category notes -->
            <div v-if="rootNotes.length" class="site-group">
              <div class="site-group-header">{{ siteTitle }}</div>
              <div class="site-card-list">
                <div
                  v-for="item in rootNotes"
                  :key="item.id"
                  class="site-card"
                  @click="handleSelectNote(item.id)"
                >
                  <h3 class="site-card-title">{{ item.title }}</h3>
                  <p class="site-card-summary">{{ item.summary || '暂无摘要' }}</p>
                  <div class="site-card-meta">
                    <span>{{ formatTime(item.updatedAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <!-- Sub-category groups -->
            <template v-for="cat in subCategoryGroups" :key="cat.id">
              <div v-if="cat.notes.length > 0" class="site-group">
                <div class="site-group-header">{{ cat.name }}</div>
                <div class="site-card-list">
                  <div
                    v-for="item in cat.notes"
                    :key="item.id"
                    class="site-card"
                    @click="handleSelectNote(item.id)"
                  >
                    <h3 class="site-card-title">{{ item.title }}</h3>
                    <p class="site-card-summary">{{ item.summary || '暂无摘要' }}</p>
                    <div class="site-card-meta">
                      <span>{{ formatTime(item.updatedAt) }}</span>
                      <span v-if="item.categoryName && item.categoryName !== cat.name" class="site-card-category">{{ item.categoryName }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </template>
            <div v-if="!noteList.length" class="empty-state">
              <div class="empty-icon">📖</div>
              <h2>暂无文章</h2>
              <p>该书阁尚未发布任何文章。</p>
            </div>
          </div>
          <!-- Flat view (no sub-categories) -->
          <div v-else class="site-card-list">
            <div
              v-for="item in noteList"
              :key="item.id"
              class="site-card"
              @click="handleSelectNote(item.id)"
            >
              <h3 class="site-card-title">{{ item.title }}</h3>
              <p class="site-card-summary">{{ item.summary || '暂无摘要' }}</p>
              <div class="site-card-meta">
                <span>{{ formatTime(item.updatedAt) }}</span>
              </div>
            </div>
            <div v-if="!noteList.length" class="empty-state">
              <div class="empty-icon">📖</div>
              <h2>暂无文章</h2>
              <p>该书阁尚未发布任何文章。</p>
            </div>
          </div>
        </div>
      </template>

      <!-- Loading / Error -->
      <template v-else>
        <div ref="siteContentRef" class="site-content" @scroll="handleContentScroll">
          <div v-if="errorMsg" class="empty-state">
            <div class="empty-icon">🔒</div>
            <h2>无法访问</h2>
            <p>{{ errorMsg }}</p>
          </div>
          <div v-else class="empty-state">
            <div class="empty-icon">📖</div>
            <h2>正在加载星迹书阁...</h2>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import { siteApi } from '@/api'
import { renderMarkdown, formatTime } from '@/utils/markdown'
import {
  enhanceMarkdown,
  scrollMarkdownContainerToHash,
  detectActiveHeadingAnchor
} from '@/utils/markdownEnhance'
import MobileTreeBrowser from '@/components/MobileTreeBrowser.vue'
import OutlineList from '@/components/OutlineList.vue'
import SiteTreeNode from '@/components/SiteTreeNode.vue'
import { findTreeNodeById, findTreePathById } from '@/utils/directoryTree'
import { useSidebarWidth } from '@/utils/sidebarLayout'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const toast = useToastStore()

const loaded = ref(false)
const errorMsg = ref('')
const siteTitle = ref('星迹书阁')
const ownerName = ref('')
const noteCount = ref(0)
const noteList = ref([])
const siteTree = ref({ items: [], pinnedItems: [] })
const hasSubCategories = ref(false)
const currentNote = ref(null)
const sidebarOpen = ref(false)
const sidebarTab = ref('tree')
const isMobile = ref(window.innerWidth <= 768)
const mobileTreePath = ref([])
const expandedCats = ref([])
const siteContentRef = ref(null)
const siteMarkdownRef = ref(null)
const activeOutlineAnchor = ref('')
const {
  sidebarStyle,
  isResizing: isSidebarResizing,
  startResize: startSidebarResize,
  resetWidth: resetSidebarWidth,
  syncSidebarWidth
} = useSidebarWidth({
  storageKey: 'starlight:site-sidebar-width',
  defaultWidth: 360,
  minWidth: 280,
  maxWidth: 460
})

function normalizeHash(value) {
  return decodeURIComponent(String(value || '').replace(/^#/, '').trim())
}

const currentNoteId = computed(() => currentNote.value?.id || route.params.noteId || null)
const currentNoteMarkdown = computed(() => currentNote.value?.markdownContent || '')
const mobileSidebarTitle = computed(() => (sidebarTab.value === 'outline' ? '文章大纲' : '文章目录'))
const renderedHtml = computed(() => renderMarkdown(currentNoteMarkdown.value))

async function enhanceSiteContent() {
  if (!currentNote.value) return
  await nextTick()
  await enhanceMarkdown(siteMarkdownRef.value)
  const scrolled = await applyHashScroll('auto')
  if (!scrolled) {
    activeOutlineAnchor.value = detectActiveHeadingAnchor(siteContentRef.value)
  }
}

async function applyHashScroll(behavior = 'smooth') {
  const anchor = normalizeHash(route.hash)
  activeOutlineAnchor.value = anchor
  return scrollMarkdownContainerToHash(siteContentRef.value, anchor, { behavior })
}

async function updateHash(anchor) {
  const hash = anchor ? `#${anchor}` : ''
  if (route.hash === hash) {
    activeOutlineAnchor.value = anchor || ''
    return
  }
  await router.replace({ path: route.path, query: route.query, hash })
  activeOutlineAnchor.value = anchor || ''
}

async function handleOutlineSelect(item) {
  await updateHash(item.anchor)
  scrollMarkdownContainerToHash(siteContentRef.value, item.anchor)
}

function handleResize() {
  const wasMobile = isMobile.value
  isMobile.value = window.innerWidth <= 768
  syncSidebarWidth()
  if (wasMobile !== isMobile.value) {
    sidebarOpen.value = false
  }
  if (isMobile.value) {
    syncMobileTreePathFromSelection()
  }
}

function handleContentScroll() {
  activeOutlineAnchor.value = detectActiveHeadingAnchor(siteContentRef.value)
}

/** 根分类下的笔记（不属于任何子分类的） */
const rootNotes = computed(() => {
  if (!hasSubCategories.value) return noteList.value
  const subCatIds = collectAllCategoryIds(siteTree.value.items || [])
  return noteList.value.filter(n => !n.categoryId || !subCatIds.has(n.categoryId))
})

/** 子分类分组（用于卡片视图） */
const subCategoryGroups = computed(() => {
  if (!hasSubCategories.value) return []
  return flattenCategoryGroups(siteTree.value.items || [])
})

/** 从树中递归收集所有分类 ID */
function collectAllCategoryIds(items) {
  const ids = new Set()
  for (const item of items) {
    if (item.type === 'category') {
      ids.add(item.id)
      if (item.children?.length) {
        for (const id of collectAllCategoryIds(item.children)) ids.add(id)
      }
    }
  }
  return ids
}

/** 将树形分类扁平化为分组列表，每组包含该分类及其所有后代的笔记 */
function flattenCategoryGroups(items) {
  const groups = []
  for (const item of items) {
    if (item.type !== 'category') continue
    const catIds = new Set()
    catIds.add(item.id)
    collectDescendantCatIds(item.children || [], catIds)
    const notes = noteList.value.filter(n => catIds.has(n.categoryId))
    groups.push({ id: item.id, name: item.name, notes })
  }
  return groups
}

function collectDescendantCatIds(children, ids) {
  for (const c of children) {
    if (c.type === 'category') {
      ids.add(c.id)
      if (c.children?.length) collectDescendantCatIds(c.children, ids)
    }
  }
}

/** 切换分类展开/折叠 */
function toggleCatExpand(catId) {
  const idx = expandedCats.value.indexOf(catId)
  if (idx >= 0) {
    expandedCats.value.splice(idx, 1)
  } else {
    expandedCats.value.push(catId)
  }
}

function expandSitePath(path) {
  const next = new Set(expandedCats.value)
  for (const id of path || []) {
    next.add(id)
  }
  expandedCats.value = [...next]
}

function syncMobileTreePathFromSelection() {
  if (!isMobile.value) return
  if (currentNoteId.value) {
    mobileTreePath.value = findTreePathById(siteTree.value.items, currentNoteId.value)
    expandSitePath(mobileTreePath.value)
    return
  }

  const isPathValid = mobileTreePath.value.every(id => Boolean(findTreeNodeById(siteTree.value.items, id)))
  if (!isPathValid) {
    mobileTreePath.value = []
  }
}

function handleMobileSiteNavigate(path) {
  mobileTreePath.value = [...path]
  expandSitePath(path)
}

/** 加载站点首页数据 */
async function loadSiteIndex() {
  const token = route.params.token
  try {
    const data = await siteApi.getIndex(token)
    // 应用作者主题
    if (data.owner?.theme) {
      const resolved = themeStore.resolveTheme(data.owner.theme)
      themeStore.apply(resolved)
    }
    siteTitle.value = data.siteTitle || '星迹书阁'
    ownerName.value = data.owner?.username || '未知'
    noteCount.value = data.noteCount || 0
    noteList.value = data.notes || []
    siteTree.value = data.siteTree || { items: [], pinnedItems: [] }
    hasSubCategories.value = data.hasSubCategories || false

    // 分类默认折叠（不自动展开）
    expandedCats.value = []

    loaded.value = true
    errorMsg.value = ''

    // 首页笔记自动跳转：仅在首页视图（无 noteId 参数）时生效
    if (!route.params.noteId && data.indexNoteId) {
      handleSelectNote(data.indexNoteId)
    }
  } catch (err) {
    errorMsg.value = err.message
    toast.error(err.message)
  }
}

/** 加载文章详情 */
async function loadNote(noteId) {
  const token = route.params.token
  try {
    const data = await siteApi.getNote(token, noteId)
    if (data.owner?.theme) {
      const resolved = themeStore.resolveTheme(data.owner.theme)
      themeStore.apply(resolved)
    }
    siteTitle.value = data.siteTitle || siteTitle.value
    currentNote.value = data.note
    if (data.siteTree) siteTree.value = data.siteTree
    if (data.hasSubCategories !== undefined) hasSubCategories.value = data.hasSubCategories
    errorMsg.value = ''
  } catch (err) {
    errorMsg.value = err.message
    toast.error(err.message)
  }
}

/** 选择文章 */
function handleSelectNote(noteId) {
  const token = route.params.token
  router.push({ path: `/site/${token}/${noteId}`, hash: '' })
  sidebarOpen.value = false
}

/** 返回列表 */
function handleBackToList() {
  currentNote.value = null
  const token = route.params.token
  router.push({ path: `/site/${token}`, hash: '' })
}

// 监听路由参数变化
watch(() => route.params.noteId, async (noteId) => {
  if (noteId) {
    await loadNote(noteId)
    await enhanceSiteContent()
    syncMobileTreePathFromSelection()
  } else {
    currentNote.value = null
  }
}, { immediate: false })

watch(
  [() => siteTree.value.items, currentNoteId, isMobile],
  () => {
    syncMobileTreePathFromSelection()
  },
  { deep: true }
)

watch([renderedHtml, () => themeStore.currentId], async () => {
  await enhanceSiteContent()
}, { flush: 'post' })

watch(() => route.hash, async hash => {
  activeOutlineAnchor.value = normalizeHash(hash)
  await nextTick()
  await applyHashScroll('smooth')
})

onMounted(async () => {
  themeStore.loadCached()
  activeOutlineAnchor.value = normalizeHash(route.hash)
  handleResize()
  window.addEventListener('resize', handleResize)
  await loadSiteIndex()
  if (route.params.noteId) {
    await loadNote(route.params.noteId)
    await enhanceSiteContent()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.site-page {
  --sl-sidebar-width: 340px;
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--sl-bg);
}

/* ──── Sidebar ──── */
.site-sidebar {
  width: var(--sl-sidebar-width);
  min-width: var(--sl-sidebar-width);
  max-width: var(--sl-sidebar-width);
  background: var(--sl-sidebar-bg);
  border-right: 1px solid var(--sl-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}
.site-sidebar.resizing { transition: none; }
.site-sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 16px 0;
  gap: 16px;
  overflow: hidden;
}
.site-sidebar-mobile-chrome {
  display: none;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--sl-radius-lg);
  border: 1px solid var(--sl-border);
  background: linear-gradient(180deg, var(--sl-card) 0%, color-mix(in srgb, var(--sl-card) 70%, var(--sl-hover-bg)) 100%);
  box-shadow: var(--sl-shadow-card);
}
.site-sidebar-mobile-chrome__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sl-text);
}
.site-sidebar-mobile-chrome__meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--sl-text-tertiary);
}
.site-sidebar-header {}
.site-badge {
  display: inline-flex;
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-primary);
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--sl-primary-light);
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}
.site-title {
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 4px;
  color: var(--sl-text);
}
.site-hint {
  font-size: 12px;
  color: var(--sl-text-tertiary);
  margin: 0;
  line-height: 1.4;
}

/* ──── Tabs ──── */
.sidebar-tabs {
  display: flex;
  background: var(--sl-active-bg);
  border-radius: var(--sl-radius);
  padding: 3px;
  flex-shrink: 0;
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

/* ──── Scrollable content ──── */
.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding-bottom: 16px;
}
.site-sidebar-resize-handle {
  position: relative;
  width: 10px;
  margin-left: -1px;
  flex-shrink: 0;
  cursor: col-resize;
  touch-action: none;
  background: transparent;
}
.site-sidebar-resize-handle::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: var(--sl-border);
  transition: background 0.15s, box-shadow 0.15s;
}
.site-sidebar-resize-handle:hover::before {
  background: var(--sl-primary);
  box-shadow: 0 0 0 3px var(--sl-primary-light);
}
.site-sidebar-resize-handle:active::before {
  background: var(--sl-primary);
  box-shadow: 0 0 0 4px var(--sl-primary-light);
}
.tree-panel,
.outline-panel {
  padding: 4px 0;
}
.empty-hint {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--sl-text-tertiary);
}

/* ──── Pinned section ──── */
.quick-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--sl-border);
}
.quick-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.quick-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text);
}
.quick-section__hint {
  font-size: 11px;
  color: var(--sl-text-tertiary);
}
.quick-section__list {
  display: flex;
  flex-direction: column;
}

/* ──── Main ──── */
.site-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--sl-bg-secondary);
}
.site-topbar {
  padding: 16px 24px;
  border-bottom: 1px solid var(--sl-border);
  background: var(--sl-card);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.site-article-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--sl-text);
}
.site-article-meta {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.site-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

/* ──── Grouped list (index page with sub-categories) ──── */
.site-grouped-list {
  display: flex;
  flex-direction: column;
  gap: 32px;
  max-width: 960px;
}
.site-group-header {
  font-size: 16px;
  font-weight: 600;
  color: var(--sl-text);
  padding-bottom: 10px;
  border-bottom: 2px solid var(--sl-primary-light);
  margin-bottom: 12px;
}

/* ──── Card list (index page) ──── */
.site-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  max-width: 960px;
}
.site-card {
  background: var(--sl-card);
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  padding: 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.15s;
  box-shadow: var(--sl-shadow-card);
}
.site-card:hover {
  border-color: var(--sl-primary);
  box-shadow: var(--sl-shadow-flyout);
  transform: translateY(-2px);
}
.site-card-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--sl-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.site-card-summary {
  font-size: 13px;
  color: var(--sl-text-secondary);
  line-height: 1.5;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.site-card-meta {
  font-size: 12px;
  color: var(--sl-text-tertiary);
  display: flex;
  gap: 8px;
}
.site-card-category {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 500;
}

/* ──── Empty state ──── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60%;
  text-align: center;
  color: var(--sl-text-secondary);
}
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h2 { font-size: 18px; font-weight: 600; margin-bottom: 8px; }
.empty-state p { font-size: 13px; color: var(--sl-text-tertiary); }

/* ──── Mobile toggle ──── */
.site-sidebar-toggle {
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 60;
  width: 36px;
  height: 36px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
  background: color-mix(in srgb, var(--sl-card) 88%, transparent);
  box-shadow: var(--sl-shadow-card);
  backdrop-filter: saturate(1.08) blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--sl-text);
}

/* ──── Mobile responsive ──── */
@media (max-width: 768px) {
  .site-sidebar {
    position: fixed;
    inset: 0;
    z-index: 55;
    width: 100vw;
    min-width: unset;
    max-width: none;
    background: color-mix(in srgb, var(--sl-sidebar-bg) 94%, var(--sl-bg-secondary));
    backdrop-filter: saturate(1.08) blur(18px);
    border-right: none;
    transform: translateX(-100%);
    transition: transform 0.28s ease;
  }
  .site-sidebar.open { transform: translateX(0); }
  .site-sidebar-inner {
    width: 100%;
    height: 100%;
    padding: calc(env(safe-area-inset-top, 0px) + 14px) 14px calc(env(safe-area-inset-bottom, 0px) + 12px);
    gap: 12px;
  }
  .site-sidebar.open .site-sidebar-inner {
    max-width: 100vw;
    background: transparent;
    box-shadow: none;
  }
  .site-sidebar-mobile-chrome {
    display: flex;
  }
  .site-sidebar-header,
  .sidebar-tabs,
  .tree-panel,
  .outline-panel {
    border-radius: var(--sl-radius-lg);
  }
  .site-sidebar-header,
  .sidebar-tabs {
    padding: 12px;
    background: color-mix(in srgb, var(--sl-card) 92%, transparent);
    border: 1px solid var(--sl-border);
    box-shadow: var(--sl-shadow-card);
  }
  .sidebar-tabs {
    padding: 4px;
  }
  .sidebar-scroll {
    padding-right: 2px;
  }
  .site-sidebar-resize-handle {
    display: none;
  }
  .site-topbar { padding: 16px 16px 16px 56px; }
  .site-content { padding: 16px; }
  .site-card-list {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .site-grouped-list {
    gap: 24px;
  }
}
</style>

