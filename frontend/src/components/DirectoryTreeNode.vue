<template>
  <div class="directory-tree-node">
    <div
      :class="['directory-tree-row', `depth-${Math.min(depth, 6)}`, { active: isSelected, 'is-selectable': isSelectable }]"
      @click="handleRowClick"
    >
      <button
        v-if="expandable"
        class="directory-tree-row__toggle sl-btn sl-btn--ghost sl-btn--sm"
        type="button"
        :aria-label="expanded ? '收起' : '展开'"
        @click.stop="emit('toggle-expand', item.id)"
      >
        <svg :class="['directory-tree-row__chevron', { open: expanded }]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>
      <span v-else class="directory-tree-row__toggle directory-tree-row__toggle--placeholder"></span>

      <span class="directory-tree-row__icon" aria-hidden="true">
        <svg v-if="item.type === 'category'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/>
        </svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
      </span>

      <div class="directory-tree-row__content">
        <div class="directory-tree-row__title">{{ item.label || item.name }}</div>
        <div v-if="item.metaText" class="directory-tree-row__meta">{{ item.metaText }}</div>
      </div>

      <span v-if="item.badgeText" :class="['directory-tree-row__badge', item.badgeTone ? `is-${item.badgeTone}` : '']">
        {{ item.badgeText }}
      </span>

      <label v-if="multiple && isSelectable" class="directory-tree-row__selector" @click.stop>
        <input :checked="isSelected" type="checkbox" @change="handleSelectOnly" />
      </label>
      <span v-else-if="!multiple && isSelectable" class="directory-tree-row__selected-mark" aria-hidden="true">
        <span v-if="isSelected" class="directory-tree-row__selected-dot"></span>
      </span>

      <div v-if="visibleActions.length" class="directory-tree-row__actions" @click.stop>
        <button
          v-for="action in visibleActions"
          :key="action.key"
          :class="['sl-btn', 'sl-btn--sm', action.tone === 'danger' ? 'sl-btn--danger' : (action.tone === 'primary' ? 'sl-btn--primary' : 'sl-btn--ghost')]"
          type="button"
          :disabled="isActionDisabled(action)"
          @click.stop="emit('action', { actionKey: action.key, item })"
        >
          {{ action.label }}
        </button>
      </div>
    </div>

    <div v-if="expandable && expanded" class="directory-tree-node__children">
      <DirectoryTreeNode
        v-for="child in item.children"
        :key="child.id ?? `${child.type}-${child.label || child.name}`"
        :item="child"
        :depth="depth + 1"
        :multiple="multiple"
        :model-value="modelValue"
        :expanded-ids="expandedIds"
        :selectable-types="selectableTypes"
        :node-actions="nodeActions"
        @toggle-expand="emit('toggle-expand', $event)"
        @select="emit('select', $event)"
        @action="emit('action', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'DirectoryTreeNode' })

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  depth: {
    type: Number,
    default: 0
  },
  modelValue: {
    type: [String, Number, Array, null],
    default: null
  },
  multiple: {
    type: Boolean,
    default: false
  },
  expandedIds: {
    type: Array,
    default: () => []
  },
  selectableTypes: {
    type: Array,
    default: () => ['category', 'note']
  },
  nodeActions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['toggle-expand', 'select', 'action'])

const expandable = computed(() => Array.isArray(props.item?.children) && props.item.children.length > 0)
const expanded = computed(() => props.expandedIds.includes(props.item?.id))
const isSelectable = computed(() => props.selectableTypes.includes(props.item?.type))
const isSelected = computed(() => {
  if (props.multiple) {
    return Array.isArray(props.modelValue) && props.modelValue.includes(props.item?.id)
  }
  return props.modelValue === props.item?.id
})
const visibleActions = computed(() => {
  const actions = props.nodeActions?.[props.item?.type] || []
  return actions.filter(action => !(typeof action.hidden === 'function' && action.hidden(props.item)))
})

function isActionDisabled(action) {
  return Boolean(typeof action.disabled === 'function' ? action.disabled(props.item) : action.disabled)
}

function handleSelectOnly() {
  if (!isSelectable.value) return
  emit('select', props.item)
}

function handleRowClick() {
  if (isSelectable.value) {
    emit('select', props.item)
    return
  }
  if (expandable.value) {
    emit('toggle-expand', props.item.id)
  }
}
</script>

<style scoped>
.directory-tree-node {
  user-select: none;
}

.directory-tree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 10px;
  border-radius: var(--sl-radius);
  color: var(--sl-text-secondary);
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.directory-tree-row.is-selectable {
  cursor: pointer;
}

.directory-tree-row:hover {
  background: var(--sl-hover-bg);
  color: var(--sl-text);
}

.directory-tree-row.active {
  background: var(--sl-selection);
  color: var(--sl-primary);
}

.directory-tree-row__toggle {
  flex-shrink: 0;
  width: 24px;
  min-width: 24px;
  height: 24px;
  padding: 0;
}

.directory-tree-row__toggle--placeholder {
  display: inline-flex;
}

.directory-tree-row__chevron {
  transition: transform 0.15s;
}

.directory-tree-row__chevron.open {
  transform: rotate(90deg);
}

.directory-tree-row__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.directory-tree-row__content {
  min-width: 0;
  flex: 1;
}

.directory-tree-row__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: inherit;
}

.directory-tree-row__meta {
  margin-top: 2px;
  font-size: 11px;
  color: var(--sl-text-tertiary);
  line-height: 1.5;
}

.directory-tree-row__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-text-tertiary);
  font-size: 11px;
  white-space: nowrap;
}

.directory-tree-row__badge.is-danger {
  background: color-mix(in srgb, var(--sl-danger) 10%, var(--sl-card));
  color: var(--sl-danger);
}

.directory-tree-row__badge.is-primary {
  background: color-mix(in srgb, var(--sl-primary) 10%, var(--sl-card));
  color: var(--sl-primary);
}

.directory-tree-row__selector,
.directory-tree-row__selected-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  min-width: 22px;
  flex-shrink: 0;
}

.directory-tree-row__selected-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.directory-tree-row__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.directory-tree-node__children {
  margin-left: 18px;
  padding-left: 10px;
  border-left: 1px solid var(--sl-border);
}

@media (max-width: 640px) {
  .directory-tree-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .directory-tree-row__content {
    min-width: calc(100% - 84px);
  }

  .directory-tree-row__actions {
    width: 100%;
    margin-left: 32px;
  }

  .directory-tree-row__actions .sl-btn {
    flex: 1;
    justify-content: center;
  }
}
</style>

