import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import { useThemeStore } from './theme'

export const useAuthStore = defineStore('auth', () => {
  const profile = ref(null)

  const isLoggedIn = computed(() => !!profile.value)
  const isAdmin = computed(() => profile.value?.admin === true)
  const username = computed(() => profile.value?.username || '')

  async function fetchMe() {
    const themeStore = useThemeStore()
    try {
      const data = await authApi.me()
      profile.value = data
      if (data.theme) {
        const resolved = themeStore.resolveTheme(data.theme)
        themeStore.apply(resolved)
      }
      return data
    } catch (err) {
      profile.value = null
      throw err
    }
  }

  async function login(usernameOrEmail, password) {
    const themeStore = useThemeStore()
    const data = await authApi.login(usernameOrEmail, password)
    profile.value = data
    if (data.theme) {
      const resolved = themeStore.resolveTheme(data.theme)
      themeStore.apply(resolved)
    }
    return data
  }

  async function register(email, password) {
    const themeStore = useThemeStore()
    const data = await authApi.register(email, password)
    profile.value = data
    if (data.theme) {
      const resolved = themeStore.resolveTheme(data.theme)
      themeStore.apply(resolved)
    }
    return data
  }

  async function logout() {
    await authApi.logout()
    profile.value = null
  }

  return { profile, isLoggedIn, isAdmin, username, fetchMe, login, register, logout }
})

