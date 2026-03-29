<template>
  <div class="site-page">
    <!-- Mobile sidebar toggle -->
    <button class="site-sidebar-toggle" @click="sidebarOpen = !sidebarOpen" v-if="isMobile">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
    </button>

    <!-- Sidebar -->
    <aside :class="['site-sidebar', { open: sidebarOpen }]" @click.self="sidebarOpen = false">
      <div class="site-sidebar-inner">
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
          </div>

          <!-- Outline panel -->
          <div v-show="sidebarTab === 'outline'" class="outline-panel">
            <OutlineList v-if="currentNoteMarkdown" :markdown="currentNoteMarkdown" />
            <div v-else class="empty-hint">选择一篇文章后查看大纲</div>
          </div>
        </div>
      </div>
    </aside>

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
        <div class="site-content">
          <div class="markdown-body" v-html="renderedHtml"></div>
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
        <div class="site-content">
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
        <div class="site-content">
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import { siteApi } from '@/api'
import { renderMarkdown, formatTime } from '@/utils/markdown'
import OutlineList from '@/components/OutlineList.vue'
import SiteTreeNode from '@/components/SiteTreeNode.vue'

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
const expandedCats = ref([])

const currentNoteId = computed(() => currentNote.value?.id || route.params.noteId || null)
const currentNoteMarkdown = computed(() => currentNote.value?.markdownContent || '')
const renderedHtml = computed(() => renderMarkdown(currentNoteMarkdown.value))

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
  router.push(`/site/${token}/${noteId}`)
  sidebarOpen.value = false
}

/** 返回列表 */
function handleBackToList() {
  currentNote.value = null
  const token = route.params.token
  router.push(`/site/${token}`)
}

// 监听路由参数变化
watch(() => route.params.noteId, async (noteId) => {
  if (noteId) {
    await loadNote(noteId)
  } else {
    currentNote.value = null
  }
}, { immediate: false })

onMounted(async () => {
  themeStore.loadCached()
  window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 768 })
  await loadSiteIndex()
  if (route.params.noteId) {
    await loadNote(route.params.noteId)
  }
})
</script>

<style scoped>
.site-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--sl-bg);
}

/* ──── Sidebar ──── */
.site-sidebar {
  width: 300px;
  min-width: 300px;
  background: var(--sl-sidebar-bg);
  border-right: 1px solid var(--sl-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.site-sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 16px 0;
  gap: 16px;
  overflow: hidden;
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
  background: var(--sl-card);
  box-shadow: var(--sl-shadow-card);
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
    width: 100%;
    min-width: unset;
    background: var(--sl-backdrop);
    border-right: none;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .site-sidebar.open { transform: translateX(0); }
  .site-sidebar.open .site-sidebar-inner {
    width: 300px;
    max-width: 85vw;
    background: var(--sl-sidebar-bg);
    height: 100%;
    box-shadow: var(--sl-shadow-flyout);
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

