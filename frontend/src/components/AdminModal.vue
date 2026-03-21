<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="$emit('close')">
      <div class="modal sl-card">
        <div class="modal-header">
          <h3>管理员设置</h3>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="$emit('close')">✕</button>
        </div>
        <div class="modal-body" v-if="loaded">
          <div class="form-field">
            <label class="sl-label">允许注册</label>
            <select v-model="registrationEnabled" class="sl-select">
              <option :value="false">关闭</option>
              <option :value="true">开启</option>
            </select>
          </div>
          <div class="form-field" style="margin-top:12px">
            <label class="sl-label">分享 Base URL</label>
            <input v-model="shareBaseUrl" class="sl-input" placeholder="为空则跟随浏览器地址" />
          </div>
          <button class="sl-btn sl-btn--primary" style="width:100%;margin-top:18px" @click="handleSave">保存设置</button>
        </div>
        <div v-else class="modal-body">加载中...</div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api'
import { useToastStore } from '@/stores/toast'

const emit = defineEmits(['close'])
const toast = useToastStore()

const loaded = ref(false)
const registrationEnabled = ref(false)
const shareBaseUrl = ref('')

onMounted(async () => {
  try {
    const s = await adminApi.getSettings()
    registrationEnabled.value = s.registrationEnabled
    shareBaseUrl.value = s.shareBaseUrl || ''
    loaded.value = true
  } catch (err) {
    toast.error(err.message)
  }
})

async function handleSave() {
  try {
    await adminApi.saveSettings({
      registrationEnabled: registrationEnabled.value,
      shareBaseUrl: shareBaseUrl.value
    })
    toast.success('设置已保存')
    emit('close')
  } catch (err) {
    toast.error(err.message)
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
</style>

