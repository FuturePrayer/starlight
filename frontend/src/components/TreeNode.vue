<template>
  <div class="tree-node">
    <div
      :class="['tree-row', { active: isSelected, category: isCategory }]"
      :style="rowStyle"
      :title="item.name"
      :data-sidebar-node-id="item.id"
      :data-sidebar-mode="mode"
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
      <span v-if="showSiteFlag && isCategory && item.siteToken" class="tree-flags">
        <span class="tree-flag tree-flag--site" title="星迹书阁已开启">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/></svg>
        </span>
      </span>
      <span v-else-if="showSiteFlag && isCategory && item.inheritedSiteToken" class="tree-flags">
        <span class="tree-flag tree-flag--site-inherited" title="继承自父级星迹书阁">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/></svg>
        </span>
      </span>
      <span v-if="showPinnedFlag && !isCategory && item.pinnedFlag" class="tree-flags">
        <span class="tree-flag" title="已置顶">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 17v5"/><path d="M5 6V3h14v3l-4 4v3l2 2v1H7v-1l2-2v-3z"/></svg>
        </span>
      </span>

      <CategoryActionMenu
        v-if="mode === 'tree' && isCategory"
        mode="tree"
        @edit="emit('edit-category', item.id)"
        @site="emit('open-site', item.id)"
        @delete="emit('delete-category', item.id)"
      />

      <template v-if="mode === 'trash'">
        <button
          v-if="!isCategory"
          class="tree-action-btn sl-btn sl-btn--ghost sl-btn--sm"
          :disabled="item.restorable === false"
          @click.stop="emit('restore-note', item.id)"
          title="恢复笔记"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 109-9 9.75 9.75 0 00-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
        </button>
        <button
          v-if="!isCategory"
          class="tree-delete-btn sl-btn sl-btn--ghost sl-btn--sm"
          @click.stop="emit('purge-note', item.id)"
          title="彻底删除笔记"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
        </button>
        <CategoryActionMenu
          v-if="isCategory"
          mode="trash"
          :restorable="item.restorable !== false"
          @restore="emit('restore-category', item.id)"
          @purge="emit('purge-category', item.id)"
        />
      </template>

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
        :selected-category-id="selectedCategoryId"
        :expanded-ids="expandedIds"
        :mode="mode"
        :depth="depth + 1"
        @select-note="$emit('select-note', $event)"
        @select-category="$emit('select-category', $event)"
        @toggle-category="$emit('toggle-category', $event)"
        @edit-category="$emit('edit-category', $event)"
        @open-site="$emit('open-site', $event)"
        @delete-category="$emit('delete-category', $event)"
        @restore-note="$emit('restore-note', $event)"
        @purge-note="$emit('purge-note', $event)"
        @restore-category="$emit('restore-category', $event)"
        @purge-category="$emit('purge-category', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import CategoryActionMenu from '@/components/CategoryActionMenu.vue'

const props = defineProps({
  item: { type: Object, required: true },
  selectedId: { type: String, default: null },
  selectedCategoryId: { type: String, default: null },
  expandedIds: { type: Array, default: () => [] },
  mode: { type: String, default: 'tree' },
  depth: { type: Number, default: 0 }
})

const emit = defineEmits([
  'select-note', 'select-category', 'toggle-category', 'edit-category', 'open-site', 'delete-category',
  'restore-note', 'purge-note', 'restore-category', 'purge-category'
])

const isCategory = computed(() => props.item.type === 'category')
const isSelected = computed(() => (isCategory.value ? props.item.id === props.selectedCategoryId : props.item.id === props.selectedId))
const expanded = computed(() => props.expandedIds.includes(props.item.id))
const showSiteFlag = computed(() => props.mode === 'tree')
const showPinnedFlag = computed(() => props.mode === 'tree')
const rowStyle = computed(() => {
  const visualDepth = Math.min(Math.max(Number(props.depth) || 0, 0), 4)
  return {
    paddingLeft: `${10 + (visualDepth * 12)}px`
  }
})

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
.tree-flag--site {
  color: var(--sl-success, var(--sl-primary));
}
.tree-flag--site-inherited {
  color: var(--sl-success, var(--sl-primary));
  opacity: 0.4;
}
.tree-expand { padding: 0 4px; }
.tree-delete-btn {
  padding: 0 4px;
  opacity: 0.55;
  color: var(--sl-danger);
  transition: opacity 0.15s, color 0.15s;
}
.tree-action-btn {
  padding: 0 4px;
  opacity: 0.55;
  color: var(--sl-primary);
  transition: opacity 0.15s, color 0.15s;
}
.tree-row:hover :deep(.category-action-menu__trigger),
.tree-row:hover .tree-delete-btn,
.tree-row:hover .tree-action-btn { opacity: 1; }
.tree-delete-btn:hover { opacity: 1 !important; }
.tree-action-btn:hover { opacity: 1 !important; }
.chevron { transition: transform 0.15s; }
.chevron.open { transform: rotate(90deg); }
.tree-children { display: flex; flex-direction: column; gap: 2px; }
.tree-row :deep(.category-action-menu__trigger) { opacity: 0.55; }
</style>
