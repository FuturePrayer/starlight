<template>
  <div class="login-page">
    <div class="login-card sl-card">
      <div class="login-header">
        <div class="login-logo">✨</div>
        <h1>{{ isRegister ? '注册 Starlight' : '登录 Starlight' }}</h1>
        <p class="login-subtitle">支持用户名/邮箱登录，第一个注册账户自动成为管理员。</p>
      </div>

      <form class="login-form" @submit.prevent="handleSubmit">
        <div v-if="!isRegister" class="form-field">
          <label class="sl-label">用户名或邮箱</label>
          <input v-model="principal" class="sl-input" placeholder="输入用户名或邮箱" autocomplete="username" />
        </div>
        <div v-if="isRegister" class="form-field">
          <label class="sl-label">注册邮箱</label>
          <input v-model="email" type="email" class="sl-input" placeholder="your@email.com" autocomplete="email" />
        </div>
        <div class="form-field">
          <label class="sl-label">密码</label>
          <input v-model="password" type="password" class="sl-input" placeholder="至少 6 位" autocomplete="current-password" />
        </div>
        <button type="submit" class="sl-btn sl-btn--primary sl-btn--lg login-submit" :disabled="loading">
          {{ loading ? '请稍候...' : (isRegister ? '注册并进入' : '登录') }}
        </button>
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
import { authApi } from '@/api'

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

const isRegister = computed(() => route.path === '/register')

onMounted(async () => {
  themeStore.loadCached()
  try {
    const status = await authApi.registrationStatus()
    registrationEnabled.value = status.enabled
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
    } else {
      await authStore.login(principal.value, password.value)
    }
    router.push('/app')
  } catch (err) {
    toast.error(err.message)
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
</style>

