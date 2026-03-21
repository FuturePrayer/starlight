<template>
  <router-view />
  <Transition name="toast">
    <div v-if="toast.visible" :class="['sl-toast', `sl-toast--${toast.type}`]">
      <span class="sl-toast__icon">
        <template v-if="toast.type === 'error'">✕</template>
        <template v-else-if="toast.type === 'success'">✓</template>
        <template v-else>ℹ</template>
      </span>
      {{ toast.message }}
    </div>
  </Transition>
</template>

<script setup>
import { useToastStore } from '@/stores/toast'
const toast = useToastStore()
</script>

<style scoped>
.sl-toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: var(--sl-radius);
  background: var(--sl-card);
  border: 1px solid var(--sl-border);
  box-shadow: var(--sl-shadow-flyout);
  font-size: 13px;
  color: var(--sl-text);
  max-width: 90vw;
}
.sl-toast__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.sl-toast--info .sl-toast__icon { background: var(--sl-primary); color: #fff; }
.sl-toast--error .sl-toast__icon { background: var(--sl-danger); color: #fff; }
.sl-toast--success .sl-toast__icon { background: var(--sl-success); color: #fff; }

.toast-enter-active { animation: sl-slide-up 0.3s ease; }
.toast-leave-active { animation: sl-fade-in 0.2s ease reverse; }
</style>
