<template>
  <div ref="rootRef" class="category-action-menu">
    <button
      class="sl-btn sl-btn--ghost sl-btn--sm category-action-menu__trigger"
      type="button"
      title="更多操作"
      aria-label="更多操作"
      @click.stop="toggleMenu"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="5" r="1.5"/>
        <circle cx="12" cy="12" r="1.5"/>
        <circle cx="12" cy="19" r="1.5"/>
      </svg>
    </button>

    <div v-if="open" class="category-action-menu__panel sl-card" role="menu" @click.stop>
      <template v-if="mode === 'tree'">
        <button class="category-action-menu__item" type="button" role="menuitem" @click.stop="handleAction('edit')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 113 3L7 19l-4 1 1-4z"/>
          </svg>
          <span>重命名</span>
        </button>
        <button class="category-action-menu__item" type="button" role="menuitem" @click.stop="handleAction('site')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/>
          </svg>
          <span>星迹书阁</span>
        </button>
        <button class="category-action-menu__item category-action-menu__item--danger" type="button" role="menuitem" @click.stop="handleAction('delete')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
          </svg>
          <span>移入回收站</span>
        </button>
      </template>

      <template v-else>
        <button
          class="category-action-menu__item"
          type="button"
          role="menuitem"
          :disabled="restorable === false"
          @click.stop="handleAction('restore')"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12a9 9 0 109-9 9.75 9.75 0 00-6.74 2.74L3 8"/><path d="M3 3v5h5"/>
          </svg>
          <span>恢复</span>
        </button>
        <button class="category-action-menu__item category-action-menu__item--danger" type="button" role="menuitem" @click.stop="handleAction('purge')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
          </svg>
          <span>彻底删除</span>
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  mode: { type: String, default: 'tree' },
  restorable: { type: Boolean, default: true }
})

const emit = defineEmits(['edit', 'site', 'delete', 'restore', 'purge'])

const open = ref(false)
const rootRef = ref(null)

function toggleMenu() {
  open.value = !open.value
}

function closeMenu() {
  open.value = false
}

function handleAction(action) {
  emit(action)
  closeMenu()
}

function handlePointerDown(event) {
  if (!open.value) return
  if (rootRef.value?.contains(event.target)) return
  closeMenu()
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    closeMenu()
  }
}

watch(open, value => {
  if (value) {
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeydown)
    return
  }
  document.removeEventListener('pointerdown', handlePointerDown)
  document.removeEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.category-action-menu {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
}

.category-action-menu__trigger {
  width: 26px;
  min-width: 26px;
  height: 26px;
  padding: 0;
  justify-content: center;
  opacity: 0.7;
}

.category-action-menu__trigger:hover {
  opacity: 1;
}

.category-action-menu__panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  min-width: 148px;
  padding: 6px;
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  background: color-mix(in srgb, var(--sl-card) 96%, transparent);
  box-shadow: var(--sl-shadow-flyout);
  backdrop-filter: saturate(1.06) blur(12px);
}

.category-action-menu__item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: calc(var(--sl-radius) - 2px);
  background: transparent;
  color: var(--sl-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.category-action-menu__item:hover {
  background: var(--sl-hover-bg);
}

.category-action-menu__item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.category-action-menu__item--danger {
  color: var(--sl-danger);
}
</style>
