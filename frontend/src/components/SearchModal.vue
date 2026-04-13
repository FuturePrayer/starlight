<template>
  <PopupLayer
    title="搜索笔记"
    description="支持空格分隔多个关键词进行搜索"
    width="min(640px, calc(100vw - 32px))"
    :close-on-esc="!showCategoryPicker"
    :close-on-backdrop="!showCategoryPicker"
    @close="emit('close')"
  >
    <!-- 搜索输入框 -->
    <div class="search-modal__input-wrap">
      <svg class="search-modal__icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8"/>
        <line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
      <input
        ref="searchInputRef"
        v-model="searchQuery"
        class="sl-input search-modal__input"
        placeholder="搜索笔记标题与内容…"
        @input="handleSearchInput"
      />
      <button v-if="searchQuery" class="search-modal__clear sl-btn sl-btn--ghost sl-btn--sm" @click="clearSearch">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>

    <!-- 搜索范围 -->
    <div class="search-modal__scope">
      <span class="search-modal__scope-label">搜索范围</span>
      <div class="search-modal__scope-options">
        <button
          v-if="hasCurrentNote"
          type="button"
          :class="['search-modal__radio', { active: searchScope === 'current' }]"
          @click="handleScopeChange('current')"
        >
          当前笔记
        </button>
        <button
          type="button"
          :class="['search-modal__radio', { active: searchScope === 'categories' }]"
          @click="handleCategoryScopeClick"
        >
          指定分类
        </button>
        <button
          type="button"
          :class="['search-modal__radio', { active: searchScope === 'all' }]"
          @click="handleScopeChange('all')"
        >
          所有笔记
        </button>
      </div>
    </div>

    <div v-if="searchScope === 'categories'" class="search-modal__scope-summary">
      <button type="button" class="sl-btn sl-btn--ghost sl-btn--sm" @click="openCategoryPicker">
        {{ selectedCategoryIds.length ? '修改目录' : '选择目录' }}
      </button>
      <span class="search-modal__scope-current">{{ selectedCategorySummary }}</span>
      <button
        v-if="selectedCategoryIds.length"
        type="button"
        class="sl-btn sl-btn--ghost sl-btn--sm"
        @click="clearSelectedCategories"
      >
        清空
      </button>
    </div>

    <!-- 搜索结果 -->
    <div class="search-modal__results">
      <div v-if="searchLoading && !searchResults.length" class="search-modal__hint">搜索中…</div>
      <div
        v-else-if="searchScope === 'categories' && searchQuery && !searchLoading && !selectedCategoryIds.length"
        class="search-modal__hint"
      >
        请先选择要搜索的目录范围
      </div>
      <div v-else-if="searchQuery && !searchLoading && !searchResults.length && searchQueried" class="search-modal__hint">未找到匹配的笔记</div>
      <div v-else-if="!searchQuery" class="search-modal__hint">输入关键词搜索笔记</div>
      <div v-else class="search-modal__list">
        <div
          v-for="item in searchResults"
          :key="item.id"
          :class="['search-modal__item', { active: currentNoteId === item.id }]"
          @click="handleSelectResult(item.id)"
        >
          <div class="search-modal__item-title" v-html="item.title"></div>
          <div class="search-modal__item-snippet" v-html="item.snippet"></div>
          <div class="search-modal__item-meta">{{ formatTime(item.updatedAt) }}</div>
        </div>
        <button
          v-if="searchHasMore"
          class="sl-btn sl-btn--sm search-modal__load-more"
          :disabled="searchLoading"
          @click="loadMoreSearch"
        >
          {{ searchLoading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </div>
  </PopupLayer>

  <PopupLayer
    v-if="showCategoryPicker"
    title="选择搜索目录"
    description="勾选分类后，将在该分类及其子分类中搜索；子分类与笔记的勾选仅用于显示包含范围。"
    width="min(720px, calc(100vw - 32px))"
    @close="closeCategoryPicker"
  >
    <div class="search-modal__category-picker">
      <DirectoryTree
        :items="treeItems"
        v-model="draftCategoryIds"
        v-model:expanded-ids="draftExpandedIds"
        :multiple="true"
        :cascade-children="true"
        :selectable-types="['category']"
        :show-toolbar="true"
        title="目录范围"
        empty-text="暂无分类"
      />
    </div>

    <template #footer>
      <button class="sl-btn" type="button" @click="closeCategoryPicker">取消</button>
      <button class="sl-btn" type="button" @click="clearDraftCategories">清空</button>
      <button class="sl-btn sl-btn--primary" type="button" @click="applyCategorySelection">确认</button>
    </template>
  </PopupLayer>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import PopupLayer from '@/components/PopupLayer.vue'
import DirectoryTree from '@/components/DirectoryTree.vue'
import { noteApi } from '@/api'
import { formatTime } from '@/utils/markdown'
import { findTreeNodeById } from '@/utils/directoryTree'

const props = defineProps({
  /** 当前打开的笔记 ID */
  currentNoteId: {
    type: String,
    default: null
  },
  /** 目录树数据 */
  treeItems: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['close', 'open-note'])

const searchInputRef = ref(null)
const searchQuery = ref('')
const searchResults = ref([])
const searchHasMore = ref(false)
const searchLoading = ref(false)
const searchQueried = ref(false)
const searchOffset = ref(0)
const searchScope = ref('all')
const selectedCategoryIds = ref([])
const draftCategoryIds = ref([])
const draftExpandedIds = ref([])
const showCategoryPicker = ref(false)
const SEARCH_PAGE_SIZE = 20
let searchDebounceTimer = null

const hasCurrentNote = computed(() => Boolean(props.currentNoteId))

const selectedCategoryLabels = computed(() => selectedCategoryIds.value
  .map(id => findTreeNodeById(props.treeItems, id)?.label || findTreeNodeById(props.treeItems, id)?.name || '')
  .filter(Boolean))

const selectedCategorySummary = computed(() => {
  if (!selectedCategoryIds.value.length) {
    return '未选择目录'
  }
  if (selectedCategoryLabels.value.length <= 3) {
    return `已选：${selectedCategoryLabels.value.join('、')}`
  }
  return `已选：${selectedCategoryLabels.value.slice(0, 3).join('、')} 等 ${selectedCategoryLabels.value.length} 个目录`
})

watch(searchScope, () => {
  if (searchQuery.value.trim()) {
    executeSearch(true)
  }
})

onMounted(() => {
  nextTick(() => searchInputRef.value?.focus())
})

onUnmounted(() => {
  clearTimeout(searchDebounceTimer)
})

function handleSearchInput() {
  clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    executeSearch(true)
  }, 300)
}

function handleScopeChange(scope) {
  searchScope.value = scope
}

function handleCategoryScopeClick() {
  searchScope.value = 'categories'
  openCategoryPicker()
}

function openCategoryPicker() {
  draftCategoryIds.value = [...selectedCategoryIds.value]
  showCategoryPicker.value = true
}

function closeCategoryPicker() {
  showCategoryPicker.value = false
}

function clearDraftCategories() {
  draftCategoryIds.value = []
}

function applyCategorySelection() {
  selectedCategoryIds.value = [...draftCategoryIds.value]
  showCategoryPicker.value = false
  if (searchScope.value === 'categories' && searchQuery.value.trim()) {
    executeSearch(true)
  }
}

function clearSelectedCategories() {
  selectedCategoryIds.value = []
  if (searchScope.value === 'categories' && searchQuery.value.trim()) {
    executeSearch(true)
  }
}

async function executeSearch(reset) {
  const q = searchQuery.value.trim()
  if (!q) {
    searchResults.value = []
    searchHasMore.value = false
    searchQueried.value = false
    searchOffset.value = 0
    return
  }
  // scope=categories 但没选分类时不搜索
  if (searchScope.value === 'categories' && !selectedCategoryIds.value.length) {
    searchResults.value = []
    searchHasMore.value = false
    searchQueried.value = true
    searchOffset.value = 0
    return
  }
  if (reset) {
    searchOffset.value = 0
    searchResults.value = []
  }
  searchLoading.value = true
  try {
    const options = {
      scope: searchScope.value,
      categoryIds: searchScope.value === 'categories' ? selectedCategoryIds.value.join(',') : null,
      noteId: searchScope.value === 'current' ? props.currentNoteId : null
    }
    const data = await noteApi.search(q, searchOffset.value, SEARCH_PAGE_SIZE, options)
    if (reset) {
      searchResults.value = data.items
    } else {
      searchResults.value = [...searchResults.value, ...data.items]
    }
    searchHasMore.value = data.hasMore
    searchOffset.value = searchOffset.value + data.items.length
    searchQueried.value = true
  } catch (err) {
    console.error('搜索失败:', err)
  } finally {
    searchLoading.value = false
  }
}

function loadMoreSearch() {
  executeSearch(false)
}

function clearSearch() {
  searchQuery.value = ''
  searchResults.value = []
  searchHasMore.value = false
  searchQueried.value = false
  searchOffset.value = 0
}

function handleSelectResult(id) {
  emit('open-note', id)
  emit('close')
}
</script>

<style scoped>
.search-modal__input-wrap {
  position: relative;
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.search-modal__icon {
  position: absolute;
  left: 10px;
  color: var(--sl-text-tertiary);
  pointer-events: none;
}
.search-modal__input {
  padding-left: 34px;
  padding-right: 34px;
  height: 36px;
  font-size: 14px;
  width: 100%;
}
.search-modal__clear {
  position: absolute;
  right: 4px;
  padding: 0 6px;
  height: 28px;
}

/* 搜索范围 */
.search-modal__scope {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.search-modal__scope-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text-secondary);
  white-space: nowrap;
}
.search-modal__scope-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.search-modal__radio {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--sl-text);
  padding: 4px 10px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
  background: transparent;
  transition: all 0.15s;
}
.search-modal__radio:hover {
  background: var(--sl-hover-bg);
}
.search-modal__radio.active {
  border-color: var(--sl-primary);
  background: color-mix(in srgb, var(--sl-primary) 8%, var(--sl-card));
  color: var(--sl-primary);
}

.search-modal__scope-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: -2px 0 12px;
  flex-wrap: wrap;
}

.search-modal__scope-current {
  font-size: 12px;
  line-height: 1.6;
  color: var(--sl-text-tertiary);
}

/* 分类选择 */
.search-modal__category-picker {
  max-height: min(56vh, 520px);
  overflow-y: auto;
  border-radius: var(--sl-radius);
}
.search-modal__category-picker :deep(.directory-tree-card) {
  margin: 2px 0;
}

/* 搜索结果 */
.search-modal__results {
  min-height: 60px;
  max-height: 320px;
  overflow-y: auto;
}
.search-modal__hint {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sl-text-tertiary);
}
.search-modal__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.search-modal__item {
  padding: 10px 12px;
  border-radius: var(--sl-radius);
  cursor: pointer;
  transition: background 0.1s;
}
.search-modal__item:hover {
  background: var(--sl-hover-bg);
}
.search-modal__item.active {
  background: var(--sl-selection);
}
.search-modal__item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--sl-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-modal__item-title :deep(mark) {
  background: color-mix(in srgb, var(--sl-primary) 25%, transparent);
  color: var(--sl-primary);
  border-radius: 2px;
  padding: 0 1px;
}
.search-modal__item-snippet {
  font-size: 12px;
  color: var(--sl-text-secondary);
  line-height: 1.5;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.search-modal__item-snippet :deep(mark) {
  background: color-mix(in srgb, var(--sl-primary) 20%, transparent);
  color: var(--sl-primary);
  border-radius: 2px;
  padding: 0 1px;
}
.search-modal__item-meta {
  font-size: 11px;
  color: var(--sl-text-tertiary);
  margin-top: 4px;
}
.search-modal__load-more {
  align-self: center;
  margin: 8px 0;
}

@media (max-width: 768px) {
  .search-modal__scope {
    flex-direction: column;
    align-items: flex-start;
  }
  .search-modal__scope-summary {
    align-items: flex-start;
  }
  .search-modal__results {
    max-height: 260px;
  }
  .search-modal__category-picker {
    max-height: min(52vh, 420px);
  }
}
</style>

