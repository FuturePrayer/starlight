<template>
  <PopupLayer title="安全设置" width="min(480px, calc(100vw - 32px))" @close="$emit('close')">

    <!-- ──── TOTP Section ──── -->
    <div class="section">
      <h4>🔐 两步验证（TOTP）</h4>
      <p class="hint" v-if="!totpGlobalEnabled">管理员尚未开启两步验证功能。</p>
      <template v-else>
        <!-- User has TOTP bound -->
        <template v-if="authStore.totpBound && !totpSetupData">
          <p class="status-ok">✓ 已开启两步验证</p>
          <button class="sl-btn sl-btn--danger sl-btn--sm" @click="handleTotpRevoke">解除绑定</button>
        </template>
        <!-- Setup flow -->
        <template v-else-if="totpSetupData">
          <p class="hint">使用认证器应用扫描二维码，然后输入 6 位验证码完成绑定。</p>
          <div class="qr-container">
            <img v-if="totpQrDataUrl" :src="totpQrDataUrl" alt="TOTP QR Code" class="qr-img" />
          </div>
          <div class="form-field security-form-field security-form-field--compact">
            <label class="sl-label">密钥（手动输入）</label>
            <input :value="totpSetupData.secret" class="sl-input security-secret-input" readonly @click="copyText(totpSetupData.secret)" />
          </div>
          <div class="form-field security-form-field">
            <label class="sl-label">验证码</label>
            <input v-model="totpCode" class="sl-input" placeholder="输入 6 位验证码" maxlength="6" @keyup.enter="handleTotpConfirm" />
          </div>
          <div class="btn-row security-btn-row">
            <button class="sl-btn sl-btn--primary" @click="handleTotpConfirm" :disabled="!totpCode">确认绑定</button>
            <button class="sl-btn" @click="totpSetupData = null; totpQrDataUrl = ''">取消</button>
          </div>
        </template>
        <!-- Not yet bound -->
        <template v-else>
          <p class="hint">绑定两步验证后，登录时需要额外输入验证码。</p>
          <button class="sl-btn sl-btn--primary sl-btn--sm" @click="handleTotpSetup">开始设置</button>
        </template>
      </template>
    </div>

    <hr class="divider" />

    <!-- ──── Passkey Section ──── -->
    <div class="section">
      <h4>🔑 通行密钥</h4>
      <p class="hint" v-if="!passkeyGlobalEnabled">
        管理员尚未开启通行密钥功能。
        <template v-if="authStore.isAdmin && !siteUrlHttps">
          <br /><strong class="warn">⚠ 通行密钥要求站点 URL 为 HTTPS 协议。请先在管理员设置中配置 HTTPS 站点 URL。</strong>
        </template>
      </p>
      <template v-else>
        <p class="hint">通行密钥可代替密码登录，并自动跳过两步验证。</p>
        <!-- Existing passkeys -->
        <div v-if="passkeys.length" class="passkey-list">
          <div v-for="pk in passkeys" :key="pk.id" class="passkey-item">
            <div>
              <span class="passkey-name">{{ pk.nickname }}</span>
              <span class="passkey-date">{{ formatTime(pk.createdAt) }}</span>
            </div>
            <button class="sl-btn sl-btn--ghost sl-btn--sm passkey-delete-btn" @click="handlePasskeyDelete(pk.id)">删除</button>
          </div>
        </div>
        <button class="sl-btn sl-btn--primary sl-btn--sm security-passkey-btn" @click="handlePasskeyRegister" :disabled="registeringPasskey">
          {{ registeringPasskey ? '注册中...' : '注册新通行密钥' }}
        </button>
      </template>
    </div>
  </PopupLayer>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi, adminApi, base64urlToBuffer, bufferToBase64url } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { formatTime } from '@/utils/markdown'
import { generateQrDataUrl } from '@/utils/qrcode'
import PopupLayer from '@/components/PopupLayer.vue'

const emit = defineEmits(['close'])
const authStore = useAuthStore()
const toast = useToastStore()

const totpGlobalEnabled = ref(false)
const passkeyGlobalEnabled = ref(false)
const siteUrlHttps = ref(false)
const totpSetupData = ref(null)
const totpQrDataUrl = ref('')
const totpCode = ref('')
const passkeys = ref([])
const registeringPasskey = ref(false)

onMounted(async () => {
  // Load global settings status
  try {
    const status = await authApi.registrationStatus()
    passkeyGlobalEnabled.value = status.passkeyEnabled
  } catch {}
  totpGlobalEnabled.value = true // assume enabled; will be caught by API error if not
  try {
    if (authStore.isAdmin) {
      const settings = await adminApi.getSettings()
      totpGlobalEnabled.value = settings.totpEnabled
      passkeyGlobalEnabled.value = settings.passkeyEnabled
      siteUrlHttps.value = settings.siteUrlHttps
    }
  } catch {}
  // Load passkeys
  try {
    passkeys.value = await authApi.passkeyList()
  } catch {}
})

async function handleTotpSetup() {
  try {
    const data = await authApi.totpSetup()
    totpSetupData.value = data
    totpCode.value = ''
    // 使用前端库生成二维码
    totpQrDataUrl.value = await generateQrDataUrl(data.otpAuthUri, 256)
  } catch (err) {
    if (err.message?.includes('尚未开启')) totpGlobalEnabled.value = false
    toast.error(err.message)
  }
}

async function handleTotpConfirm() {
  try {
    await authApi.totpConfirm(totpSetupData.value.secret, totpCode.value)
    totpSetupData.value = null
    totpQrDataUrl.value = ''
    totpCode.value = ''
    await authStore.fetchMe()
    toast.success('两步验证已绑定')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleTotpRevoke() {
  if (!confirm('确定解除两步验证绑定？')) return
  try {
    await authApi.totpRevoke()
    await authStore.fetchMe()
    toast.success('两步验证已解除')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handlePasskeyRegister() {
  registeringPasskey.value = true
  try {
    // 1. Get options from server
    const { handle, optionsJson } = await authApi.passkeyRegisterStart();
    const options = JSON.parse(optionsJson);

    // 2. Convert base64url fields to ArrayBuffer for browser API
    const publicKey = {
      ...options,
      challenge: base64urlToBuffer(options.challenge),
      user: {
        ...options.user,
        id: base64urlToBuffer(options.user.id)
      },
      excludeCredentials: (options.excludeCredentials || []).map(c => ({
        ...c,
        id: base64urlToBuffer(c.id)
      }))
    }

    // 3. Create credential via browser
    const credential = await navigator.credentials.create({ publicKey })

    // 4. Encode response
    const credentialResponse = {
      id: credential.id,
      rawId: bufferToBase64url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
        attestationObject: bufferToBase64url(credential.response.attestationObject)
      },
      clientExtensionResults: credential.getClientExtensionResults()
    }
    if (credential.response.getTransports) {
      credentialResponse.response.transports = credential.response.getTransports()
    }

    // 5. Send to server
    const nickname = prompt('为这个通行密钥取个名字：', '我的通行密钥') || '通行密钥'
    await authApi.passkeyRegisterFinish({ handle, credential: credentialResponse, nickname })

    passkeys.value = await authApi.passkeyList()
    await authStore.fetchMe()
    toast.success('通行密钥已注册')
  } catch (err) {
    if (err.name !== 'AbortError' && err.name !== 'NotAllowedError') {
      toast.error(err.message || '通行密钥注册失败')
    }
  } finally {
    registeringPasskey.value = false
  }
}

async function handlePasskeyDelete(id) {
  if (!confirm('确定删除该通行密钥？')) return
  try {
    await authApi.passkeyDelete(id)
    passkeys.value = await authApi.passkeyList()
    await authStore.fetchMe()
    toast.success('通行密钥已删除')
  } catch (err) {
    toast.error(err.message)
  }
}

function copyText(text) {
  navigator.clipboard?.writeText(text)
  toast.info('已复制到剪贴板')
}
</script>

<style scoped>
.section h4 { font-size: 14px; font-weight: 600; margin: 0 0 8px; }
.hint { font-size: 12px; color: var(--sl-text-tertiary); margin: 0 0 10px; line-height: 1.5; }
.warn { color: var(--sl-warning); }
.status-ok { font-size: 13px; color: var(--sl-success); font-weight: 500; margin: 0 0 8px; }
.divider { border: none; border-top: 1px solid var(--sl-border); margin: 18px 0; }
.btn-row { display: flex; gap: 8px; }
.security-btn-row { margin-top: 10px; }
.security-form-field { margin-top: 10px; }
.security-form-field--compact { margin-top: 8px; }
.qr-container { text-align: center; padding: 8px 0; }
.qr-img { width: 200px; height: 200px; border-radius: var(--sl-radius); border: 1px solid var(--sl-border); }
.security-secret-input { cursor: pointer; font-family: var(--sl-font-mono); font-size: 12px; }
.passkey-list { display: flex; flex-direction: column; gap: 6px; }
.passkey-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 10px; border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border); font-size: 13px;
}
.passkey-delete-btn { color: var(--sl-danger); }
.security-passkey-btn { margin-top: 10px; }
.passkey-name { font-weight: 500; }
.passkey-date { font-size: 11px; color: var(--sl-text-tertiary); margin-left: 8px; }
</style>

