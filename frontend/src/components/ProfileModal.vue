<template>
  <PopupLayer title="个人资料" width="min(440px, calc(100vw - 32px))" @close="$emit('close')">
    <div class="form-field">
      <label class="sl-label">邮箱（不可修改）</label>
      <input :value="authStore.profile?.email" class="sl-input" disabled />
    </div>
    <div class="form-field profile-form-field">
      <label class="sl-label">用户名</label>
      <input v-model="newUsername" class="sl-input" placeholder="新用户名" />
    </div>
    <div class="form-field profile-form-field">
      <label class="sl-label">当前密码 <span class="required">*</span></label>
      <input v-model="currentPassword" type="password" class="sl-input" placeholder="输入当前密码以确认身份" autocomplete="current-password" />
    </div>
    <div class="form-field profile-form-field">
      <label class="sl-label">新密码（不修改请留空）</label>
      <input v-model="newPassword" type="password" class="sl-input" placeholder="至少 6 位" autocomplete="new-password" />
    </div>
    <button class="sl-btn sl-btn--primary profile-save-btn" :disabled="saving" @click="handleSave">
      {{ saving ? '保存中...' : '保存修改' }}
    </button>
  </PopupLayer>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import PopupLayer from '@/components/PopupLayer.vue'

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
.profile-form-field { margin-top: 12px; }
.profile-save-btn { width: 100%; margin-top: 18px; }
.required { color: var(--sl-danger); }
</style>

