import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { themeApi } from '@/api'

const BUILTIN_THEMES = [
  {
    id: 'win11-light',
    name: 'Windows 11 浅色',
    previewColor: '#005fb8',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#005fb8',
      '--sl-primary-hover': '#004e99',
      '--sl-primary-active': '#003e7e',
      '--sl-primary-light': 'rgba(0, 95, 184, 0.08)',
      '--sl-bg': '#f3f3f3',
      '--sl-bg-secondary': '#ffffff',
      '--sl-panel': '#ffffff',
      '--sl-card': '#ffffff',
      '--sl-card-hover': '#f9f9f9',
      '--sl-border': '#e5e5e5',
      '--sl-border-strong': '#c4c4c4',
      '--sl-text': '#1a1a1a',
      '--sl-text-secondary': '#616161',
      '--sl-text-tertiary': '#9e9e9e',
      '--sl-danger': '#c42b1c',
      '--sl-success': '#0f7b0f',
      '--sl-warning': '#9d5d00',
      '--sl-shadow-card': '0 2px 8px rgba(0, 0, 0, 0.04), 0 0 1px rgba(0, 0, 0, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(0, 0, 0, 0.08), 0 0 1px rgba(0, 0, 0, 0.12)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#ffffff',
      '--sl-input-border': '#e5e5e5',
      '--sl-input-focus': '#005fb8',
      '--sl-sidebar-bg': '#f3f3f3',
      '--sl-hover-bg': 'rgba(0, 0, 0, 0.03)',
      '--sl-active-bg': 'rgba(0, 0, 0, 0.05)',
      '--sl-selection': 'rgba(0, 95, 184, 0.12)',
      '--sl-scrollbar': 'rgba(0, 0, 0, 0.2)',
      '--sl-scrollbar-hover': 'rgba(0, 0, 0, 0.35)',
      '--sl-code-bg': '#f6f6f6',
      '--sl-backdrop': 'rgba(0, 0, 0, 0.3)',
    }
  },
  {
    id: 'win11-dark',
    name: 'Windows 11 深色',
    previewColor: '#60cdff',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#60cdff',
      '--sl-primary-hover': '#4cc2ff',
      '--sl-primary-active': '#38b7ff',
      '--sl-primary-light': 'rgba(96, 205, 255, 0.06)',
      '--sl-bg': '#202020',
      '--sl-bg-secondary': '#282828',
      '--sl-panel': '#2d2d2d',
      '--sl-card': '#2d2d2d',
      '--sl-card-hover': '#333333',
      '--sl-border': '#3d3d3d',
      '--sl-border-strong': '#555555',
      '--sl-text': '#ffffff',
      '--sl-text-secondary': '#c5c5c5',
      '--sl-text-tertiary': '#8b8b8b',
      '--sl-danger': '#ff99a4',
      '--sl-success': '#6ccb5f',
      '--sl-warning': '#fce100',
      '--sl-shadow-card': '0 2px 8px rgba(0, 0, 0, 0.16), 0 0 1px rgba(0, 0, 0, 0.24)',
      '--sl-shadow-flyout': '0 8px 32px rgba(0, 0, 0, 0.24), 0 0 1px rgba(0, 0, 0, 0.24)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#1e1e1e',
      '--sl-input-border': '#3d3d3d',
      '--sl-input-focus': '#60cdff',
      '--sl-sidebar-bg': '#202020',
      '--sl-hover-bg': 'rgba(255, 255, 255, 0.04)',
      '--sl-active-bg': 'rgba(255, 255, 255, 0.07)',
      '--sl-selection': 'rgba(96, 205, 255, 0.12)',
      '--sl-scrollbar': 'rgba(255, 255, 255, 0.15)',
      '--sl-scrollbar-hover': 'rgba(255, 255, 255, 0.3)',
      '--sl-code-bg': '#1e1e1e',
      '--sl-backdrop': 'rgba(0, 0, 0, 0.5)',
    }
  }
]

const CACHE_KEY = 'starlight-theme'

export const useThemeStore = defineStore('theme', () => {
  const current = ref(null)
  const themes = ref([])

  const currentId = computed(() => current.value?.id || 'win11-light')

  function applyVars(theme) {
    const root = document.documentElement
    if (theme.vars) {
      Object.entries(theme.vars).forEach(([k, v]) => root.style.setProperty(k, v))
    }
    if (theme.cssUrl) {
      let link = document.getElementById('sl-theme-css')
      if (!link) {
        link = document.createElement('link')
        link.id = 'sl-theme-css'
        link.rel = 'stylesheet'
        document.head.appendChild(link)
      }
      link.href = theme.cssUrl
    } else {
      const existing = document.getElementById('sl-theme-css')
      if (existing) existing.remove()
    }
    document.body.style.setProperty('--sl-background-image', theme.backgroundImage ? `url(${theme.backgroundImage})` : 'none')
    document.body.style.setProperty('--sl-background-opacity', theme.backgroundOpacity ?? 0)
  }

  function apply(theme) {
    if (!theme) return
    current.value = theme
    applyVars(theme)
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(theme))
    } catch {}
  }

  function loadCached() {
    try {
      const raw = localStorage.getItem(CACHE_KEY)
      if (raw) {
        const parsed = JSON.parse(raw)
        apply(parsed)
        return parsed
      }
    } catch {}
    apply(BUILTIN_THEMES[0])
    return BUILTIN_THEMES[0]
  }

  function resolveTheme(themeData) {
    if (!themeData) return BUILTIN_THEMES[0]
    const id = themeData.id || themeData
    const found = [...BUILTIN_THEMES, ...themes.value].find(t => t.id === id)
    if (found) return found
    if (typeof themeData === 'object') return themeData
    return BUILTIN_THEMES[0]
  }

  async function loadThemes() {
    try {
      const serverThemes = await themeApi.list()
      // Merge server themes (external) with built-in, built-in always present
      const external = serverThemes.filter(t => !BUILTIN_THEMES.some(b => b.id === t.id))
      themes.value = external
    } catch {}
    return [...BUILTIN_THEMES, ...themes.value]
  }

  async function selectTheme(themeId) {
    const result = await themeApi.select(themeId)
    const theme = resolveTheme(result)
    apply(theme)
    return theme
  }

  function getAll() {
    return [...BUILTIN_THEMES, ...themes.value]
  }

  return { current, themes, currentId, apply, loadCached, loadThemes, selectTheme, getAll, resolveTheme }
})

