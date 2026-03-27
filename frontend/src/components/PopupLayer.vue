<template>
  <Teleport to="body">
    <div class="sl-popup-backdrop" @click.self="handleBackdropClose">
      <section
        ref="panelRef"
        class="sl-popup sl-card"
        :class="{
          'sl-popup--warning': tone === 'warning',
          'sl-popup--danger': tone === 'danger'
        }"
        :style="{ width }"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="title ? titleId : undefined"
        :aria-describedby="description ? descriptionId : undefined"
        tabindex="-1"
      >
        <div v-if="hasHeader" class="sl-popup-header">
          <div class="sl-popup-header__content">
            <span v-if="eyebrow" class="sl-popup-eyebrow">{{ eyebrow }}</span>
            <div v-if="title || description" class="sl-popup-heading">
              <h3 v-if="title" :id="titleId" class="sl-popup-title">{{ title }}</h3>
              <p v-if="description" :id="descriptionId" class="sl-popup-description">{{ description }}</p>
            </div>
            <slot name="header" />
          </div>
          <div v-if="showClose || $slots['header-actions']" class="sl-popup-header__actions">
            <slot name="header-actions" />
            <button
              v-if="showClose"
              type="button"
              class="sl-popup-close sl-btn sl-btn--ghost sl-btn--sm"
              aria-label="关闭弹出层"
              @click="emit('close')"
            >
              ✕
            </button>
          </div>
        </div>

        <div class="sl-popup-body" :class="{ 'sl-popup-body--flush': bodyFlush }">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="sl-popup-footer">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, useSlots } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  width: { type: String, default: 'min(520px, calc(100vw - 32px))' },
  tone: { type: String, default: 'default' },
  showClose: { type: Boolean, default: true },
  closeOnBackdrop: { type: Boolean, default: true },
  closeOnEsc: { type: Boolean, default: true },
  bodyFlush: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])
const slots = useSlots()
const panelRef = ref(null)
const instanceId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

const titleId = `sl-popup-title-${instanceId}`
const descriptionId = `sl-popup-description-${instanceId}`

const hasHeader = computed(() => Boolean(
  props.eyebrow || props.title || props.description || slots.header || slots['header-actions'] || props.showClose
))

function handleBackdropClose() {
  if (props.closeOnBackdrop) emit('close')
}

function handleKeydown(event) {
  if (event.key === 'Escape' && props.closeOnEsc) {
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  panelRef.value?.focus()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.sl-popup-backdrop {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top, var(--sl-primary-light) 0, transparent 40%),
    var(--sl-backdrop);
  backdrop-filter: blur(14px) saturate(120%);
  animation: sl-fade-in 0.18s ease;
}

.sl-popup {
  --sl-popup-accent: var(--sl-primary);
  --sl-popup-tint: var(--sl-primary-light);
  position: relative;
  display: flex;
  flex-direction: column;
  max-width: 100%;
  max-height: min(88vh, 760px);
  overflow: hidden;
  border-radius: calc(var(--sl-radius-lg) + 2px);
  box-shadow: var(--sl-shadow-flyout);
  background:
    linear-gradient(180deg, var(--sl-popup-tint) 0, transparent 96px),
    var(--sl-card);
  animation: sl-scale-in 0.2s ease;
}

.sl-popup::before {
  content: '';
  position: absolute;
  top: 0;
  left: 24px;
  right: 24px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent 0%, var(--sl-popup-accent) 18%, transparent 100%);
  opacity: 0.9;
}

.sl-popup--warning {
  --sl-popup-accent: var(--sl-warning);
  --sl-popup-tint: var(--sl-hover-bg);
}

.sl-popup--danger {
  --sl-popup-accent: var(--sl-danger);
  --sl-popup-tint: var(--sl-hover-bg);
}

.sl-popup-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 28px 16px;
}

.sl-popup-header__content {
  min-width: 0;
  flex: 1;
}

.sl-popup-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.sl-popup-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-popup-accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.sl-popup-heading {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sl-popup-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--sl-text);
}

.sl-popup-description {
  margin: 0;
  color: var(--sl-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.sl-popup-close {
  flex-shrink: 0;
}

.sl-popup-body {
  min-height: 0;
  overflow-y: auto;
  padding: 0 28px 24px;
}

.sl-popup-body--flush {
  padding: 0;
}

.sl-popup-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 28px 24px;
  border-top: 1px solid var(--sl-border);
  background: linear-gradient(180deg, transparent 0%, var(--sl-hover-bg) 100%);
}

@media (max-width: 768px) {
  .sl-popup-backdrop {
    padding: 16px;
    align-items: flex-end;
  }

  .sl-popup {
    width: min(100%, calc(100vw - 32px)) !important;
    max-height: min(85vh, 720px);
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
  }

  .sl-popup-header {
    padding: 22px 18px 14px;
    gap: 12px;
  }

  .sl-popup-body {
    padding: 0 18px 18px;
  }

  .sl-popup-footer {
    flex-direction: column-reverse;
    align-items: stretch;
    padding: 14px 18px 18px;
  }

  .sl-popup-footer :deep(.sl-btn) {
    width: 100%;
  }
}
</style>

