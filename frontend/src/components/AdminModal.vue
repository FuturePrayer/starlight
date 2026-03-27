<template>
  <PopupLayer title="管理员设置" width="min(480px, calc(100vw - 32px))" @close="$emit('close')">
    <div v-if="loaded">
      <div class="form-field">
        <label class="sl-label">允许注册</label>
        <select v-model="registrationEnabled" class="sl-select">
          <option :value="false">关闭</option>
          <option :value="true">开启</option>
        </select>
      </div>

      <div class="form-field admin-form-field">
        <label class="sl-label">站点 URL</label>
        <input v-model="shareBaseUrl" class="sl-input" placeholder="https://example.com（为空则跟随浏览器地址）" />
        <p class="field-hint">
          用于生成分享链接和通行密钥绑定。通行密钥要求 HTTPS 协议。
        </p>
      </div>

      <hr class="divider" />

      <div class="form-field">
        <label class="sl-label">两步验证（TOTP）</label>
        <select v-model="totpEnabled" class="sl-select">
          <option :value="false">关闭</option>
          <option :value="true">开启</option>
        </select>
        <p class="field-hint">
          开启后，已绑定 TOTP 的用户登录时需额外输入验证码。关闭不会清除已绑定的密钥，重新开启后立即生效。
        </p>
      </div>

      <div class="form-field admin-form-field">
        <label class="sl-label">通行密钥</label>
        <select v-model="passkeyEnabled" class="sl-select" :disabled="!siteUrlHttps">
          <option :value="false">关闭</option>
          <option :value="true">开启</option>
        </select>
        <p class="field-hint field-hint--warning" v-if="!siteUrlHttps">
          ⚠ 通行密钥要求站点 URL 为 HTTPS 协议。请先配置 HTTPS 站点 URL。
        </p>
        <p class="field-hint" v-else>
          开启后，用户可注册通行密钥代替密码登录（同时跳过两步验证）。
        </p>
        <p class="field-hint field-hint--danger" v-if="siteUrlHttps">
          ⚠ 修改站点 URL 的域名会导致所有已注册的通行密钥失效并被清除。修改为非 HTTPS 地址会自动关闭通行密钥功能。
        </p>
      </div>

      <button class="sl-btn sl-btn--primary admin-save-btn" @click="handleSave">保存设置</button>
    </div>
    <div v-else class="admin-loading">加载中...</div>
  </PopupLayer>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import PopupLayer from '@/components/PopupLayer.vue'

const emit = defineEmits(['close'])
const toast = useToastStore()

const loaded = ref(false)
const registrationEnabled = ref(false)
const shareBaseUrl = ref('')
const totpEnabled = ref(false)
const passkeyEnabled = ref(false)
const siteUrlHttps = ref(false)

onMounted(async () => {
  try {
    const s = await adminApi.getSettings()
    registrationEnabled.value = s.registrationEnabled
    shareBaseUrl.value = s.shareBaseUrl || ''
    totpEnabled.value = s.totpEnabled
    passkeyEnabled.value = s.passkeyEnabled
    siteUrlHttps.value = s.siteUrlHttps
    loaded.value = true
  } catch (err) {
    toast.error(err.message)
  }
})

async function handleSave() {
  // Check if HTTPS before enabling passkey
  const isHttps = shareBaseUrl.value.toLowerCase().startsWith('https://')
  if (passkeyEnabled.value && !isHttps) {
    toast.error('通行密钥要求站点 URL 为 HTTPS 协议')
    passkeyEnabled.value = false
    return
  }
  try {
    const result = await adminApi.saveSettings({
      registrationEnabled: registrationEnabled.value,
      shareBaseUrl: shareBaseUrl.value,
      totpEnabled: totpEnabled.value,
      passkeyEnabled: passkeyEnabled.value
    })
    registrationEnabled.value = result.registrationEnabled
    shareBaseUrl.value = result.shareBaseUrl || ''
    totpEnabled.value = result.totpEnabled
    passkeyEnabled.value = result.passkeyEnabled
    siteUrlHttps.value = result.siteUrlHttps
    toast.success('设置已保存')
    emit('close')
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<style scoped>
.admin-form-field { margin-top: 12px; }
.field-hint {
  font-size: 11px;
  color: var(--sl-text-tertiary);
  margin-top: 4px;
  line-height: 1.5;
}
.field-hint--warning {
  color: var(--sl-warning);
  font-weight: 500;
}
.field-hint--danger {
  color: var(--sl-danger);
}
.divider { border: none; border-top: 1px solid var(--sl-border); margin: 16px 0; }
.admin-save-btn { width: 100%; margin-top: 18px; }
.admin-loading { color: var(--sl-text-secondary); }
</style>
