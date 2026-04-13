<template>
  <div class="tree-node">
    <div
      :class="['tree-row', { active: isSelected, category: isCategory }]"
      :style="rowStyle"
      :title="item.name"
      @click="handleClick"
    >
      <span class="tree-icon">
        <template v-if="isCategory">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
        </template>
        <template v-else>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        </template>
      </span>
      <span class="tree-label">{{ item.name }}</span>
      <span v-if="!isCategory && item.pinnedFlag" class="tree-flags">
        <span class="tree-flag" title="已置顶">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 17v5"/><path d="M5 6V3h14v3l-4 4v3l2 2v1H7v-1l2-2v-3z"/></svg>
        </span>
      </span>
      <button v-if="isCategory" class="tree-expand sl-btn sl-btn--ghost sl-btn--sm" @click.stop="emit('toggle-category', item.id)">
        <svg :class="['chevron', { open: expanded }]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
      </button>
    </div>
    <div v-if="isCategory && expanded && item.children?.length" class="tree-children">
      <SiteTreeNode
        v-for="child in item.children"
        :key="child.id"
        :item="child"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        :depth="depth + 1"
        @select-note="$emit('select-note', $event)"
        @toggle-category="$emit('toggle-category', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: { type: Object, required: true },
  selectedId: { type: String, default: null },
  expandedIds: { type: Array, default: () => [] },
  depth: { type: Number, default: 0 }
})
const emit = defineEmits(['select-note', 'toggle-category'])

const isCategory = computed(() => props.item.type === 'category')
const isSelected = computed(() => props.item.id === props.selectedId)
const expanded = computed(() => props.expandedIds.includes(props.item.id))
const rowStyle = computed(() => {
  const visualDepth = Math.min(Math.max(Number(props.depth) || 0, 0), 4)
  return {
    paddingLeft: `${10 + (visualDepth * 12)}px`
  }
})

function handleClick() {
  if (isCategory.value) {
    emit('toggle-category', props.item.id)
  } else {
    emit('select-note', props.item.id)
  }
}
</script>

<style scoped>
.tree-node { user-select: none; }
.tree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--sl-radius);
  cursor: pointer;
  font-size: 13px;
  color: var(--sl-text-secondary);
  transition: background 0.1s, color 0.1s;
}
.tree-row:hover { background: var(--sl-hover-bg); color: var(--sl-text); }
.tree-row.active { background: var(--sl-selection); color: var(--sl-primary); font-weight: 500; }
.tree-icon { display: flex; flex-shrink: 0; }
.tree-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-flags { display: inline-flex; align-items: center; gap: 4px; color: var(--sl-text-tertiary); }
.tree-flag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-primary);
}
.tree-expand { padding: 0 4px; }
.chevron { transition: transform 0.15s; }
.chevron.open { transform: rotate(90deg); }
.tree-children { display: flex; flex-direction: column; gap: 2px; }
</style>

