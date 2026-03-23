<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="$emit('close')">
      <div class="modal sl-card">
        <div class="modal-header">
          <h3>个人资料</h3>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="$emit('close')">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-field">
            <label class="sl-label">邮箱（不可修改）</label>
            <input :value="authStore.profile?.email" class="sl-input" disabled />
          </div>
          <div class="form-field" style="margin-top:12px">
            <label class="sl-label">用户名</label>
            <input v-model="newUsername" class="sl-input" placeholder="新用户名" />
          </div>
          <div class="form-field" style="margin-top:12px">
            <label class="sl-label">当前密码 <span class="required">*</span></label>
            <input v-model="currentPassword" type="password" class="sl-input" placeholder="输入当前密码以确认身份" autocomplete="current-password" />
          </div>
          <div class="form-field" style="margin-top:12px">
            <label class="sl-label">新密码（不修改请留空）</label>
            <input v-model="newPassword" type="password" class="sl-input" placeholder="至少 6 位" autocomplete="new-password" />
          </div>
          <button class="sl-btn sl-btn--primary" style="width:100%;margin-top:18px" :disabled="saving" @click="handleSave">
            {{ saving ? '保存中...' : '保存修改' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const emit = defineEmits(['close'])
const authStore = useAuthStore()
const toast = useToastStore()

const newUsername = ref(authStore.profile?.username || '')
const currentPassword = ref('')
const newPassword = ref('')
const saving = ref(false)

async function handleSave() {
  saving.value = true
  try {
    await authStore.updateProfile({
      username: newUsername.value,
      currentPassword: currentPassword.value,
      newPassword: newPassword.value || null
    })
    toast.success('资料已更新')
    emit('close')
  } catch (err) {
    toast.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed; inset: 0;
  background: var(--sl-backdrop);
  display: flex; align-items: center; justify-content: center;
  z-index: 200; padding: 20px;
  animation: sl-fade-in 0.15s ease;
}
.modal {
  width: min(440px, 100%);
  padding: 24px;
  animation: sl-scale-in 0.2s ease;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.modal-header h3 { font-size: 16px; font-weight: 600; margin: 0; }
.required { color: var(--sl-danger); }
</style>

