<template>
  <div class="login-page">
    <div class="login-card sl-card">
      <div class="login-header">
        <div class="login-logo">✨</div>
        <h1>{{ loginTitle }}</h1>
        <p class="login-subtitle">支持用户名/邮箱登录，第一个注册账户自动成为管理员。</p>
      </div>

      <!-- ──── TOTP verification step ──── -->
      <div v-if="totpStep" class="login-form">
        <div class="form-field">
          <label class="sl-label">两步验证码</label>
          <input v-model="totpCode" class="sl-input" placeholder="输入 6 位验证码" maxlength="6" autofocus @keyup.enter="handleTotpVerify" />
          <p class="field-hint">请打开认证器应用获取验证码。</p>
        </div>
        <button class="sl-btn sl-btn--primary sl-btn--lg login-submit" :disabled="loading" @click="handleTotpVerify">
          {{ loading ? '验证中...' : '验证' }}
        </button>
        <button class="sl-btn sl-btn--ghost" style="width:100%;margin-top:6px" @click="totpStep = false; authStore.pendingTotp = null">返回登录</button>
      </div>

      <!-- ──── Normal login / register form ──── -->
      <form v-else class="login-form" @submit.prevent="handleSubmit">
        <div v-if="!isRegister" class="form-field">
          <label class="sl-label">用户名或邮箱</label>
          <input v-model="principal" class="sl-input" placeholder="输入用户名或邮箱" autocomplete="username" />
        </div>
        <div v-if="isRegister" class="form-field">
          <label class="sl-label">注册邮箱</label>
          <input v-model="email" type="email" class="sl-input" placeholder="your@email.com" autocomplete="email" />
          <p class="field-hint warn">⚠ 注册后邮箱不可修改，请仔细填写。</p>
        </div>
        <div class="form-field">
          <label class="sl-label">密码</label>
          <input v-model="password" type="password" class="sl-input" placeholder="至少 6 位" autocomplete="current-password" />
        </div>
        <button type="submit" class="sl-btn sl-btn--primary sl-btn--lg login-submit" :disabled="loading">
          {{ loading ? '请稍候...' : (isRegister ? '注册并进入' : '登录') }}
        </button>

        <!-- Passkey login -->
        <div v-if="!isRegister && passkeyAvailable" class="passkey-section">
          <div class="passkey-divider"><span>或</span></div>
          <button type="button" class="sl-btn sl-btn--lg passkey-btn" @click="handlePasskeyLogin" :disabled="loading">
            🔑 使用通行密钥登录
          </button>
          <p class="field-hint" v-if="passkeyHint" style="text-align:center;margin-top:6px">{{ passkeyHint }}</p>
        </div>
      </form>

      <div class="login-footer">
        <template v-if="registrationEnabled">
          <span v-if="isRegister">已有账户？<router-link to="/login">返回登录</router-link></span>
          <span v-else>还没有账户？<router-link to="/register">立即注册</router-link></span>
        </template>
        <template v-else>
          <span class="muted">当前仅管理员可开启注册。</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import { authApi, base64urlToBuffer, bufferToBase64url } from '@/api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const toast = useToastStore()

const principal = ref('')
const email = ref('')
const password = ref('')
const loading = ref(false)
const registrationEnabled = ref(false)
const totpStep = ref(false)
const totpCode = ref('')
const passkeyAvailable = ref(false)
const passkeyHint = ref('')

const isRegister = computed(() => route.path === '/register')
const loginTitle = computed(() => {
  if (totpStep.value) return '两步验证'
  return isRegister.value ? '注册 Starlight' : '登录 Starlight'
})

onMounted(async () => {
  themeStore.loadCached()
  try {
    const status = await authApi.registrationStatus()
    registrationEnabled.value = status.enabled
    if (status.passkeyEnabled) {
      // Check if browser supports WebAuthn
      if (window.PublicKeyCredential) {
        passkeyAvailable.value = true
        // Check conditional mediation support
        try {
          const available = await PublicKeyCredential.isConditionalMediationAvailable?.()
          if (available) passkeyHint.value = '检测到您的设备支持通行密钥'
        } catch {}
      }
    }
  } catch {}
  try {
    await authStore.fetchMe()
    router.replace('/app')
  } catch {}
})

async function handleSubmit() {
  loading.value = true
  try {
    if (isRegister.value) {
      await authStore.register(email.value, password.value)
      router.push('/app')
    } else {
      const result = await authStore.login(principal.value, password.value)
      if (result.requireTotp) {
        totpStep.value = true
        totpCode.value = ''
      } else {
        router.push('/app')
      }
    }
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

async function handleTotpVerify() {
  loading.value = true
  try {
    await authStore.verifyTotp(totpCode.value)
    router.push('/app')
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

async function handlePasskeyLogin() {
  loading.value = true
  try {
    // 1. Get assertion options
    const { handle, optionsJson } = await authApi.passkeyLoginStart()
    const options = JSON.parse(optionsJson)

    // 2. Convert base64url to ArrayBuffer
    const publicKey = {
      ...options,
      challenge: base64urlToBuffer(options.challenge),
      allowCredentials: (options.allowCredentials || []).map(c => ({
        ...c,
        id: base64urlToBuffer(c.id)
      }))
    }

    // 3. Get credential from browser
    const credential = await navigator.credentials.get({ publicKey })

    // 4. Encode response
    const credentialResponse = {
      id: credential.id,
      rawId: bufferToBase64url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
        authenticatorData: bufferToBase64url(credential.response.authenticatorData),
        signature: bufferToBase64url(credential.response.signature),
        userHandle: credential.response.userHandle ? bufferToBase64url(credential.response.userHandle) : null
      },
      clientExtensionResults: credential.getClientExtensionResults()
    }

    // 5. Verify with server
    await authStore.passkeyLogin(handle, credentialResponse)
    router.push('/app')
  } catch (err) {
    if (err.name !== 'AbortError' && err.name !== 'NotAllowedError') {
      toast.error(err.message || '通行密钥登录失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--sl-bg);
}
.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  animation: sl-slide-up 0.5s ease;
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-logo {
  font-size: 40px;
  margin-bottom: 12px;
}
.login-header h1 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: -0.3px;
}
.login-subtitle {
  font-size: 13px;
  color: var(--sl-text-secondary);
  line-height: 1.5;
}
.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.form-field {
  display: flex;
  flex-direction: column;
}
.field-hint {
  font-size: 11px;
  color: var(--sl-text-tertiary);
  margin-top: 4px;
  line-height: 1.4;
}
.field-hint.warn {
  color: var(--sl-warning);
  font-weight: 500;
}
.login-submit {
  width: 100%;
  margin-top: 4px;
}
.login-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--sl-text-secondary);
}
.login-footer a {
  color: var(--sl-primary);
  font-weight: 500;
}
.muted { color: var(--sl-text-tertiary); }
.passkey-section { margin-top: 4px; }
.passkey-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0;
  color: var(--sl-text-tertiary);
  font-size: 12px;
}
.passkey-divider::before, .passkey-divider::after {
  content: '';
  flex: 1;
  border-top: 1px solid var(--sl-border);
}
.passkey-btn {
  width: 100%;
  background: var(--sl-card);
  border: 1px solid var(--sl-border-strong);
}
.passkey-btn:hover { background: var(--sl-card-hover); }
</style>
