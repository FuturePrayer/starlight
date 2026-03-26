<template>
  <div class="tree-node">
    <div
      :class="['tree-row', { active: isSelected, category: isCategory }]"
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
      <button v-if="isCategory" class="tree-expand sl-btn sl-btn--ghost sl-btn--sm" @click.stop="emit('toggle-category', item.id)">
        <svg :class="['chevron', { open: expanded }]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
      </button>
    </div>
    <div v-if="isCategory && expanded && item.children?.length" class="tree-children">
      <TreeNode
        v-for="child in item.children"
        :key="child.id"
        :item="child"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        @select-note="$emit('select-note', $event)"
        @select-category="$emit('select-category', $event)"
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
  expandedIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['select-note', 'select-category', 'toggle-category'])

const isCategory = computed(() => props.item.type === 'category')
const isSelected = computed(() => props.item.id === props.selectedId)
const expanded = computed(() => props.expandedIds.includes(props.item.id))

function handleClick() {
  if (isCategory.value) {
    emit('select-category', props.item.id)
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
.tree-expand { padding: 0 4px; }
.chevron { transition: transform 0.15s; }
.chevron.open { transform: rotate(90deg); }
.tree-children { margin-left: 14px; padding-left: 10px; border-left: 1px solid var(--sl-border); }
</style>

