import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import { useThemeStore } from './theme'

export const useAuthStore = defineStore('auth', () => {
  const profile = ref(null)
  const pendingTotp = ref(null) // { pendingToken }

  const isLoggedIn = computed(() => !!profile.value)
  const isAdmin = computed(() => profile.value?.admin === true)
  const username = computed(() => profile.value?.username || '')
  const totpBound = computed(() => profile.value?.totpBound === true)
  const passkeyCount = computed(() => profile.value?.passkeyCount || 0)

  function applyTheme(data) {
    const themeStore = useThemeStore()
    if (data.theme) {
      const resolved = themeStore.resolveTheme(data.theme)
      themeStore.apply(resolved)
    }
  }

  async function fetchMe() {
    try {
      const data = await authApi.me()
      profile.value = data
      applyTheme(data)
      return data
    } catch (err) {
      profile.value = null
      throw err
    }
  }

  async function login(usernameOrEmail, password) {
    const data = await authApi.login(usernameOrEmail, password)
    if (data.requireTotp) {
      pendingTotp.value = { pendingToken: data.pendingToken }
      return data
    }
    profile.value = data
    pendingTotp.value = null
    applyTheme(data)
    return data
  }

  async function verifyTotp(code) {
    if (!pendingTotp.value) throw new Error('无待验证的登录')
    const data = await authApi.totpVerifyLogin(pendingTotp.value.pendingToken, code)
    profile.value = data
    pendingTotp.value = null
    applyTheme(data)
    return data
  }

  async function passkeyLogin(handle, credential) {
    const data = await authApi.passkeyLoginFinish({ handle, credential })
    profile.value = data
    pendingTotp.value = null
    applyTheme(data)
    return data
  }

  async function register(email, password) {
    const data = await authApi.register(email, password)
    profile.value = data
    applyTheme(data)
    return data
  }

  async function updateProfile(payload) {
    const data = await authApi.updateProfile(payload)
    profile.value = data
    return data
  }

  async function logout() {
    await authApi.logout()
    profile.value = null
    pendingTotp.value = null
  }

  return {
    profile, pendingTotp,
    isLoggedIn, isAdmin, username, totpBound, passkeyCount,
    fetchMe, login, verifyTotp, passkeyLogin, register, updateProfile, logout
  }
})
