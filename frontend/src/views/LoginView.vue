<template>
  <div class="login-page">
    <div class="login-card sl-card">
      <div class="login-header">
        <div class="login-logo">✨</div>
        <h1>{{ loginTitle }}</h1>
        <p class="login-subtitle">{{ loginSubtitle }}</p>
      </div>

      <div v-if="showBootstrapGuide" class="bootstrap-guide sl-card">
        <div class="bootstrap-guide__badge">首次启动</div>
        <h2 class="bootstrap-guide__title">先创建管理员账户，完成系统初始化</h2>
        <p class="bootstrap-guide__text">
          当前系统还没有管理员。首次注册的账户会自动获得管理员权限，并可在进入系统后决定是否开放普通用户注册。
        </p>
        <ul class="bootstrap-guide__list">
          <li>首个账户会自动成为管理员</li>
          <li>注册完成后将直接进入系统</li>
          <li>邮箱注册后不可修改，请确认后再提交</li>
        </ul>
        <router-link to="/register" class="sl-btn sl-btn--primary sl-btn--lg bootstrap-guide__cta">
          创建管理员账户
        </router-link>
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
        <template v-if="registrationAvailable">
          <span v-if="isRegister && bootstrapAdminRequired">创建完管理员账户后，可直接返回 <router-link to="/login">登录页</router-link></span>
          <span v-else-if="isRegister">已有账户？<router-link to="/login">返回登录</router-link></span>
          <span v-else-if="bootstrapAdminRequired" class="muted">首次初始化完成后，管理员可在系统设置中决定是否开放注册。</span>
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
const registrationAvailable = ref(false)
const bootstrapAdminRequired = ref(false)
const totpStep = ref(false)
const totpCode = ref('')
const passkeyAvailable = ref(false)
const passkeyHint = ref('')

const isRegister = computed(() => route.path === '/register')
const loginTitle = computed(() => {
  if (totpStep.value) return '两步验证'
  return isRegister.value ? '注册 Starlight' : '登录 Starlight'
})

const loginSubtitle = computed(() => {
  if (bootstrapAdminRequired.value) {
    return '系统首次启动，请先创建管理员账户。第一个注册账户会自动成为管理员。'
  }
  return '支持用户名/邮箱登录，第一个注册账户自动成为管理员。'
})

const showBootstrapGuide = computed(() => (
  !totpStep.value
  && !isRegister.value
  && bootstrapAdminRequired.value
  && registrationAvailable.value
))

onMounted(async () => {
  themeStore.loadCached()
  try {
    const status = await authApi.registrationStatus()
    registrationAvailable.value = status.available
    bootstrapAdminRequired.value = status.bootstrapAdminRequired
    if (isRegister.value && !status.available) {
      router.replace('/login')
      toast.info('当前仅管理员可开启注册。')
    }
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
    const { handle, optionsJson } = await authApi.passkeyLoginStart();
    const options = JSON.parse(optionsJson).publicKey;

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
.bootstrap-guide {
  margin-bottom: 24px;
  padding: 20px;
  background:
    linear-gradient(180deg, var(--sl-primary-light) 0, transparent 120px),
    var(--sl-card);
  border-color: color-mix(in srgb, var(--sl-primary) 18%, var(--sl-border));
}
.bootstrap-guide__badge {
  display: inline-flex;
  align-items: center;
  margin-bottom: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-primary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.bootstrap-guide__title {
  font-size: 17px;
  font-weight: 600;
  line-height: 1.45;
  margin-bottom: 10px;
}
.bootstrap-guide__text {
  font-size: 13px;
  line-height: 1.65;
  color: var(--sl-text-secondary);
}
.bootstrap-guide__list {
  margin: 14px 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--sl-text-secondary);
  font-size: 12px;
}
.bootstrap-guide__list li::marker {
  color: var(--sl-primary);
}
.bootstrap-guide__cta {
  width: 100%;
  margin-top: 16px;
  text-decoration: none;
}
.bootstrap-guide__cta:hover {
  text-decoration: none;
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

@media (max-width: 480px) {
  .login-card {
    padding: 28px 20px;
  }
  .bootstrap-guide {
    padding: 16px;
  }
}
</style>
