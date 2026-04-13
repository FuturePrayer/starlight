<template>
  <div class="mobile-tree-browser">
    <div class="mobile-tree-browser__header sl-card">
      <div class="mobile-tree-browser__header-top">
        <button
          class="sl-btn sl-btn--ghost sl-btn--sm"
          :disabled="!path.length"
          @click="handleBack"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          返回上一级
        </button>
        <div class="mobile-tree-browser__title">{{ currentTitle }}</div>
      </div>
      <div class="mobile-tree-browser__crumbs" aria-label="当前目录路径">
        <button
          v-for="(crumb, index) in breadcrumbs"
          :key="crumb.key"
          :class="['mobile-tree-browser__crumb', { active: index === breadcrumbs.length - 1 }]"
          @click="handleBreadcrumb(index)"
        >
          {{ crumb.label }}
        </button>
      </div>
    </div>

    <section v-if="showPinnedSection" class="mobile-tree-section sl-card">
      <div class="mobile-tree-section__header">
        <span class="mobile-tree-section__title">置顶</span>
        <span class="mobile-tree-section__hint">目录最上方</span>
      </div>
      <div class="mobile-tree-list">
        <div
          v-for="item in pinnedItems"
          :key="`pinned-${item.id}`"
          :class="['mobile-tree-row', { active: isActive(item), category: isCategory(item) }]"
          @click="handleRowClick(item)"
        >
          <div class="mobile-tree-row__body">
            <span class="mobile-tree-row__icon" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            </span>
            <div class="mobile-tree-row__content">
              <div class="mobile-tree-row__title" :title="getLabel(item)">{{ getLabel(item) }}</div>
              <div class="mobile-tree-row__meta">
                <span v-if="item.pinnedFlag" class="mobile-tree-badge">置顶</span>
              </div>
            </div>
          </div>
          <div v-if="$slots.actions" class="mobile-tree-row__actions" @click.stop>
            <slot name="actions" :item="item" :is-category="false" />
          </div>
        </div>
      </div>
    </section>

    <section class="mobile-tree-section sl-card">
      <div class="mobile-tree-section__header">
        <span class="mobile-tree-section__title">{{ path.length ? '当前层级' : rootTitle }}</span>
        <span class="mobile-tree-section__hint">{{ currentItems.length }} 项</span>
      </div>

      <div v-if="currentItems.length" class="mobile-tree-list">
        <div
          v-for="item in currentItems"
          :key="item.id"
          :class="['mobile-tree-row', { active: isActive(item), category: isCategory(item) }]"
          @click="handleRowClick(item)"
        >
          <div class="mobile-tree-row__body">
            <span class="mobile-tree-row__icon" aria-hidden="true">
              <template v-if="isCategory(item)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
              </template>
              <template v-else>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              </template>
            </span>
            <div class="mobile-tree-row__content">
              <div class="mobile-tree-row__title" :title="getLabel(item)">{{ getLabel(item) }}</div>
              <div class="mobile-tree-row__meta">
                <span v-if="isCategory(item) && item.siteToken" class="mobile-tree-badge mobile-tree-badge--success">已公开</span>
                <span v-else-if="isCategory(item) && item.inheritedSiteToken" class="mobile-tree-badge mobile-tree-badge--muted">继承书阁</span>
                <span v-if="!isCategory(item) && item.pinnedFlag" class="mobile-tree-badge">置顶</span>
                <span class="mobile-tree-meta-text">{{ getMetaText(item) }}</span>
              </div>
            </div>
          </div>

          <div v-if="$slots.actions" class="mobile-tree-row__actions" @click.stop>
            <slot name="actions" :item="item" :is-category="isCategory(item)" />
          </div>

          <button
            v-if="isCategory(item)"
            class="mobile-tree-row__enter sl-btn sl-btn--ghost sl-btn--sm"
            type="button"
            @click.stop="handleEnter(item)"
            :title="`进入 ${getLabel(item)}`"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </div>

      <div v-else class="mobile-tree-empty">{{ emptyText }}</div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { findTreeNodeById, getTreeItemsAtPath, getTreeNodeLabel } from '@/utils/directoryTree'

const props = defineProps({
  items: { type: Array, default: () => [] },
  pinnedItems: { type: Array, default: () => [] },
  path: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null },
  selectedCategoryId: { type: [String, Number], default: null },
  emptyText: { type: String, default: '暂无内容' },
  rootTitle: { type: String, default: '全部' }
})

const emit = defineEmits(['navigate', 'select-note', 'select-category'])

const currentItems = computed(() => getTreeItemsAtPath(props.items, props.path))
const currentTitle = computed(() => {
  if (!props.path.length) return props.rootTitle
  return getLabel(findTreeNodeById(props.items, props.path[props.path.length - 1])) || props.rootTitle
})
const breadcrumbs = computed(() => {
  const root = [{ key: 'root', label: props.rootTitle, path: [] }]
  const next = props.path.map((id, index) => {
    const node = findTreeNodeById(props.items, id)
    return {
      key: `crumb-${id}`,
      label: getLabel(node) || `层级 ${index + 1}`,
      path: props.path.slice(0, index + 1)
    }
  })
  return root.concat(next)
})
const showPinnedSection = computed(() => !props.path.length && props.pinnedItems.length > 0)

function isCategory(item) {
  return item?.type === 'category'
}

function getLabel(item) {
  return getTreeNodeLabel(item)
}

function isActive(item) {
  if (isCategory(item)) {
    return item?.id === props.selectedCategoryId
  }
  return item?.id === props.selectedId
}

function getMetaText(item) {
  if (!item) return ''
  if (isCategory(item)) {
    const childCount = item.children?.length || 0
    return childCount ? `${childCount} 项内容` : '空分类'
  }
  if (item.deletedAt && item.purgeAt) {
    return '待清理'
  }
  return '打开笔记'
}

function handleEnter(item) {
  if (!isCategory(item)) return
  emit('select-category', item.id)
  emit('navigate', [...props.path, item.id])
}

function handleRowClick(item) {
  if (isCategory(item)) {
    handleEnter(item)
    return
  }
  emit('select-note', item.id)
}

function handleBack() {
  if (!props.path.length) return
  const nextPath = props.path.slice(0, -1)
  const nextSelectedCategoryId = nextPath[nextPath.length - 1] || null
  emit('select-category', nextSelectedCategoryId)
  emit('navigate', nextPath)
}

function handleBreadcrumb(index) {
  const crumb = breadcrumbs.value[index]
  if (!crumb) return
  const nextSelectedCategoryId = crumb.path[crumb.path.length - 1] || null
  emit('select-category', nextSelectedCategoryId)
  emit('navigate', crumb.path)
}
</script>

<style scoped>
.mobile-tree-browser {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 6px;
}

.mobile-tree-browser__header,
.mobile-tree-section {
  padding: 12px;
  border-radius: var(--sl-radius-lg);
}

.mobile-tree-browser__header {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--sl-border);
  background: linear-gradient(180deg, var(--sl-card) 0%, var(--sl-hover-bg) 100%);
  box-shadow: var(--sl-shadow-card);
  backdrop-filter: saturate(1.04) blur(12px);
}

.mobile-tree-browser__header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mobile-tree-browser__title {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text);
  text-align: right;
}

.mobile-tree-browser__crumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mobile-tree-browser__crumb {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--sl-border);
  border-radius: 999px;
  background: var(--sl-card);
  color: var(--sl-text-secondary);
  font-size: 12px;
  line-height: 1;
  padding: 6px 10px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.mobile-tree-browser__crumb:hover {
  color: var(--sl-text);
  background: var(--sl-card-hover);
  border-color: var(--sl-border-strong);
}

.mobile-tree-browser__crumb.active {
  color: var(--sl-primary);
  background: var(--sl-selection);
  border-color: transparent;
}

.mobile-tree-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--sl-border);
  background: color-mix(in srgb, var(--sl-card) 94%, transparent);
  box-shadow: var(--sl-shadow-card);
}

.mobile-tree-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mobile-tree-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text);
}

.mobile-tree-section__hint {
  font-size: 11px;
  color: var(--sl-text-tertiary);
}

.mobile-tree-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mobile-tree-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  padding: 10px 10px 10px 12px;
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
  background: var(--sl-card);
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
  cursor: pointer;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.02);
}

.mobile-tree-row:hover {
  border-color: var(--sl-border-strong);
  background: var(--sl-card-hover);
}

.mobile-tree-row.active {
  border-color: var(--sl-primary);
  background: var(--sl-selection);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--sl-primary) 18%, transparent);
}

.mobile-tree-row.category {
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.01);
}

.mobile-tree-row__body {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.mobile-tree-row__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  color: var(--sl-text-secondary);
  flex-shrink: 0;
  margin-top: 1px;
}

.mobile-tree-row__content {
  min-width: 0;
  flex: 1;
}

.mobile-tree-row__title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
  color: var(--sl-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.mobile-tree-row__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 5px;
  font-size: 11px;
  color: var(--sl-text-tertiary);
}

.mobile-tree-meta-text {
  color: var(--sl-text-tertiary);
}

.mobile-tree-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-primary);
}

.mobile-tree-badge--success {
  background: color-mix(in srgb, var(--sl-success) 14%, transparent);
  color: var(--sl-success);
}

.mobile-tree-badge--muted {
  color: var(--sl-text-secondary);
}

.mobile-tree-row__actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.mobile-tree-row__enter {
  align-self: center;
  padding: 0 6px;
  color: var(--sl-text-secondary);
  flex-shrink: 0;
}

.mobile-tree-row__enter:hover {
  color: var(--sl-primary);
}

.mobile-tree-empty {
  padding: 20px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

@media (max-width: 768px) {
  .mobile-tree-browser {
    gap: 10px;
  }

  .mobile-tree-browser__header,
  .mobile-tree-section {
    padding: 11px;
  }

  .mobile-tree-browser__header-top {
    align-items: flex-start;
  }

  .mobile-tree-browser__title {
    max-width: 48vw;
    line-height: 1.35;
  }
}
</style>

