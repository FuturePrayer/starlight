<template>
  <section class="directory-tree-card">
    <div v-if="title || showToolbar || description" class="directory-tree-card__header">
      <div class="directory-tree-card__headline">
        <div v-if="title" class="directory-tree-card__title">{{ title }}</div>
        <div v-if="description" class="directory-tree-card__desc">{{ description }}</div>
      </div>
      <div v-if="showToolbar" class="directory-tree-card__toolbar">
        <button class="sl-btn sl-btn--ghost sl-btn--sm directory-tree-card__toolbar-btn" type="button" title="全部展开" aria-label="全部展开" @click="expandAll">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="7 6 12 11 17 6"/>
            <polyline points="7 13 12 18 17 13"/>
          </svg>
        </button>
        <button class="sl-btn sl-btn--ghost sl-btn--sm directory-tree-card__toolbar-btn" type="button" title="全部收起" aria-label="全部收起" @click="collapseAll">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="7 11 12 6 17 11"/>
            <polyline points="7 18 12 13 17 18"/>
          </svg>
        </button>
      </div>
    </div>

    <div v-if="selectionSummary" class="directory-tree-card__summary">{{ selectionSummary }}</div>
    <div v-if="!items.length" class="directory-tree-card__empty">{{ emptyText }}</div>
    <div v-else class="directory-tree-card__body">
      <DirectoryTreeNode
        v-for="item in items"
        :key="item.id ?? `${item.type}-${item.label || item.name}`"
        :item="item"
        :model-value="normalizedModelValue"
        :multiple="multiple"
        :expanded-ids="currentExpandedIds"
        :selectable-types="selectableTypes"
        :node-actions="nodeActions"
        :visual-selected-ids="visualSelectedIds"
        :partial-selected-ids="partialSelectedIds"
        :inherited-selected-ids="inheritedSelectedIds"
        @toggle-expand="toggleExpand"
        @select="handleSelect"
        @action="emit('action', $event)"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import DirectoryTreeNode from '@/components/DirectoryTreeNode.vue'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  items: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: [String, Number, Array, null],
    default: null
  },
  multiple: {
    type: Boolean,
    default: false
  },
  /** 多选模式下，勾选父级分类时是否自动级联勾选所有子级 */
  cascadeChildren: {
    type: Boolean,
    default: false
  },
  selectableTypes: {
    type: Array,
    default: () => ['category', 'note']
  },
  expandedIds: {
    type: Array,
    default: undefined
  },
  emptyText: {
    type: String,
    default: '暂无可显示的目录'
  },
  showToolbar: {
    type: Boolean,
    default: true
  },
  nodeActions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'update:expandedIds', 'select', 'action'])

const internalExpandedIds = ref([])

const currentExpandedIds = computed(() => Array.isArray(props.expandedIds) ? props.expandedIds : internalExpandedIds.value)
const normalizedMultipleValue = computed(() => normalizeMultipleSelection(props.modelValue))
const normalizedModelValue = computed(() => (props.multiple ? normalizedMultipleValue.value : props.modelValue))

const selectionState = computed(() => buildVisualSelectionState(props.items, normalizedMultipleValue.value))
const visualSelectedIds = computed(() => selectionState.value.visualSelectedIds)
const partialSelectedIds = computed(() => selectionState.value.partialSelectedIds)
const inheritedSelectedIds = computed(() => selectionState.value.inheritedSelectedIds)

const selectionSummary = computed(() => {
  if (props.multiple) {
    const count = normalizedMultipleValue.value.length
    return count ? `已选择 ${count} 项` : ''
  }
  if (props.modelValue === '' || props.modelValue === 0 || props.modelValue) {
    const node = findNodeById(props.items, props.modelValue)
    return node ? `当前选中：${node.label || node.name}` : ''
  }
  return ''
})

watch(
  () => [props.items, normalizedModelValue.value],
  () => {
    const selectedIds = props.multiple
      ? normalizedMultipleValue.value
      : ((props.modelValue === '' || props.modelValue === 0 || props.modelValue) ? [props.modelValue] : [])

    if (!selectedIds.length) {
      return
    }

    const ancestorIds = new Set(currentExpandedIds.value)
    for (const selectedId of selectedIds) {
      for (const ancestorId of findAncestorIds(props.items, selectedId)) {
        ancestorIds.add(ancestorId)
      }
    }
    updateExpandedIds([...ancestorIds])
  },
  { immediate: true, deep: true }
)

function updateExpandedIds(nextIds) {
  const normalized = Array.from(new Set(nextIds)).filter(id => id !== undefined && id !== null)
  if (Array.isArray(props.expandedIds)) {
    emit('update:expandedIds', normalized)
    return
  }
  internalExpandedIds.value = normalized
}

function toggleExpand(id) {
  if (id === undefined || id === null) return
  if (currentExpandedIds.value.includes(id)) {
    updateExpandedIds(currentExpandedIds.value.filter(itemId => itemId !== id))
    return
  }
  updateExpandedIds([...currentExpandedIds.value, id])
}

function expandAll() {
  updateExpandedIds(collectExpandableIds(props.items))
}

function collapseAll() {
  updateExpandedIds([])
}

function handleSelect(item) {
  if (!props.selectableTypes.includes(item?.type)) return

  if (props.multiple) {
    const next = new Set(normalizedMultipleValue.value)
    const isCurrentlySelected = next.has(item.id)

    if (isCurrentlySelected) {
      next.delete(item.id)
    } else {
      next.add(item.id)
    }
    emit('update:modelValue', normalizeMultipleSelection([...next]))
  } else {
    emit('update:modelValue', item.id)
  }
  emit('select', item)
}

function normalizeMultipleSelection(value) {
  const uniqueIds = Array.from(new Set(Array.isArray(value) ? value : []))
    .filter(id => id !== undefined && id !== null && id !== '')

  if (!props.cascadeChildren || !uniqueIds.length) {
    return uniqueIds
  }

  const selectedIdSet = new Set(uniqueIds)
  return uniqueIds.filter(id => !findAncestorIds(props.items, id).some(ancestorId => selectedIdSet.has(ancestorId)))
}

function buildVisualSelectionState(items = [], explicitIds = []) {
  const visualIdSet = new Set(explicitIds)
  const inheritedIdSet = new Set()

  if (props.cascadeChildren) {
    for (const selectedId of explicitIds) {
      const node = findNodeById(items, selectedId)
      if (node?.type !== 'category') continue
      collectDescendantIds(node.children, descendantId => {
        if (!visualIdSet.has(descendantId)) {
          inheritedIdSet.add(descendantId)
        }
        visualIdSet.add(descendantId)
      })
    }
  }

  const partialIdSet = new Set()

  function walk(nodes = []) {
    let subtreeHasSelection = false

    for (const node of nodes) {
      const selfSelected = visualIdSet.has(node?.id)
      const childHasSelection = node?.children?.length ? walk(node.children) : false

      if (!selfSelected && childHasSelection && node?.type === 'category') {
        partialIdSet.add(node.id)
      }

      if (selfSelected || childHasSelection) {
        subtreeHasSelection = true
      }
    }

    return subtreeHasSelection
  }

  walk(items)

  return {
    visualSelectedIds: [...visualIdSet],
    partialSelectedIds: [...partialIdSet],
    inheritedSelectedIds: [...inheritedIdSet]
  }
}

function collectDescendantIds(items = [], onVisit) {
  for (const item of items || []) {
    if (item?.id !== undefined && item?.id !== null) {
      onVisit(item.id, item)
    }
    if (item?.children?.length) {
      collectDescendantIds(item.children, onVisit)
    }
  }
}

function collectExpandableIds(items = [], collector = []) {
  for (const item of items || []) {
    if (item?.children?.length) {
      collector.push(item.id)
      collectExpandableIds(item.children, collector)
    }
  }
  return collector
}

function findNodeById(items = [], targetId) {
  for (const item of items || []) {
    if (item?.id === targetId) {
      return item
    }
    if (item?.children?.length) {
      const found = findNodeById(item.children, targetId)
      if (found) {
        return found
      }
    }
  }
  return null
}

function findAncestorIds(items = [], targetId, path = []) {
  for (const item of items || []) {
    if (item?.id === targetId) {
      return path
    }
    if (item?.children?.length) {
      const foundPath = findAncestorIds(item.children, targetId, [...path, item.id])
      if (foundPath.length) {
        return foundPath
      }
    }
  }
  return []
}
</script>

<style scoped>
.directory-tree-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  background: linear-gradient(180deg, var(--sl-card) 0%, var(--sl-bg-secondary) 100%);
  box-shadow: var(--sl-shadow-card);
}

.directory-tree-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.directory-tree-card__headline {
  min-width: 0;
}

.directory-tree-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text);
}

.directory-tree-card__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sl-text-tertiary);
}

.directory-tree-card__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.directory-tree-card__toolbar-btn {
  width: 30px;
  min-width: 30px;
  height: 30px;
  padding: 0;
  justify-content: center;
}

.directory-tree-card__summary {
  font-size: 12px;
  color: var(--sl-text-secondary);
}

.directory-tree-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.directory-tree-card__empty {
  padding: 18px 12px;
  border-radius: var(--sl-radius);
  background: var(--sl-hover-bg);
  text-align: center;
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

@media (max-width: 640px) {
  .directory-tree-card {
    padding: 12px;
  }

  .directory-tree-card__header {
    flex-direction: column;
  }

  .directory-tree-card__toolbar {
    width: 100%;
  }

  .directory-tree-card__toolbar .sl-btn {
    flex: 0 0 auto;
  }
}
</style>

