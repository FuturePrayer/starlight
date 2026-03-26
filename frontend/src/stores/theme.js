import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { themeApi } from '@/api'

const LIGHT_CODE_VARS = {
  '--sl-code-border': 'rgba(31, 35, 40, 0.12)',
  '--sl-code-text': '#1f2328',
  '--sl-code-line-hover': 'rgba(15, 23, 42, 0.04)',
  '--sl-code-ln': '#8c959f',
  '--sl-code-comment': '#6e7781',
  '--sl-code-keyword': '#8250df',
  '--sl-code-title': '#0550ae',
  '--sl-code-string': '#0a7f42',
  '--sl-code-number': '#b35900',
  '--sl-code-attr': '#953800',
  '--sl-code-variable': '#953800',
  '--sl-code-meta': '#5a32a3',
}

const DARK_CODE_VARS = {
  '--sl-code-border': 'rgba(240, 246, 252, 0.10)',
  '--sl-code-text': '#e6edf3',
  '--sl-code-line-hover': 'rgba(255, 255, 255, 0.05)',
  '--sl-code-ln': '#7d8590',
  '--sl-code-comment': '#8b949e',
  '--sl-code-keyword': '#ff7b72',
  '--sl-code-title': '#79c0ff',
  '--sl-code-string': '#a5d6ff',
  '--sl-code-number': '#79c0ff',
  '--sl-code-attr': '#d2a8ff',
  '--sl-code-variable': '#ffa657',
  '--sl-code-meta': '#d2a8ff',
}

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
      ...LIGHT_CODE_VARS,
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
      ...DARK_CODE_VARS,
    }
  },
  {
    id: 'golden-light',
    name: '琥珀金 浅色',
    previewColor: '#b8860b',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#b8860b',
      '--sl-primary-hover': '#9e7309',
      '--sl-primary-active': '#846008',
      '--sl-primary-light': 'rgba(184, 134, 11, 0.08)',
      '--sl-bg': '#faf6ed',
      '--sl-bg-secondary': '#fffdf7',
      '--sl-panel': '#fffdf7',
      '--sl-card': '#fffdf7',
      '--sl-card-hover': '#fdf8ec',
      '--sl-border': '#e8dfc8',
      '--sl-border-strong': '#d4c9a8',
      '--sl-text': '#2c2410',
      '--sl-text-secondary': '#6b5d42',
      '--sl-text-tertiary': '#a09272',
      '--sl-danger': '#c44117',
      '--sl-success': '#578c2a',
      '--sl-warning': '#c28b1a',
      '--sl-shadow-card': '0 2px 8px rgba(120, 90, 20, 0.06), 0 0 1px rgba(120, 90, 20, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(120, 90, 20, 0.10), 0 0 1px rgba(120, 90, 20, 0.14)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#fffdf7',
      '--sl-input-border': '#e8dfc8',
      '--sl-input-focus': '#b8860b',
      '--sl-sidebar-bg': '#f7f1e1',
      '--sl-hover-bg': 'rgba(184, 134, 11, 0.05)',
      '--sl-active-bg': 'rgba(184, 134, 11, 0.09)',
      '--sl-selection': 'rgba(184, 134, 11, 0.12)',
      '--sl-scrollbar': 'rgba(120, 90, 20, 0.18)',
      '--sl-scrollbar-hover': 'rgba(120, 90, 20, 0.32)',
      '--sl-code-bg': '#f5f0e0',
      '--sl-backdrop': 'rgba(60, 45, 10, 0.3)',
      ...LIGHT_CODE_VARS,
    }
  },
  {
    id: 'green-light',
    name: '薄荷绿 浅色',
    previewColor: '#2e7d32',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#2e7d32',
      '--sl-primary-hover': '#256b29',
      '--sl-primary-active': '#1b5e20',
      '--sl-primary-light': 'rgba(46, 125, 50, 0.08)',
      '--sl-bg': '#f1f8f2',
      '--sl-bg-secondary': '#f9fdf9',
      '--sl-panel': '#f9fdf9',
      '--sl-card': '#f9fdf9',
      '--sl-card-hover': '#f0f7f0',
      '--sl-border': '#c8e6c9',
      '--sl-border-strong': '#a5d6a7',
      '--sl-text': '#1b2e1c',
      '--sl-text-secondary': '#4a6b4c',
      '--sl-text-tertiary': '#82a084',
      '--sl-danger': '#c62828',
      '--sl-success': '#2e7d32',
      '--sl-warning': '#f9a825',
      '--sl-shadow-card': '0 2px 8px rgba(30, 80, 35, 0.06), 0 0 1px rgba(30, 80, 35, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(30, 80, 35, 0.10), 0 0 1px rgba(30, 80, 35, 0.14)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#f9fdf9',
      '--sl-input-border': '#c8e6c9',
      '--sl-input-focus': '#2e7d32',
      '--sl-sidebar-bg': '#e8f5e9',
      '--sl-hover-bg': 'rgba(46, 125, 50, 0.05)',
      '--sl-active-bg': 'rgba(46, 125, 50, 0.09)',
      '--sl-selection': 'rgba(46, 125, 50, 0.12)',
      '--sl-scrollbar': 'rgba(30, 80, 35, 0.18)',
      '--sl-scrollbar-hover': 'rgba(30, 80, 35, 0.32)',
      '--sl-code-bg': '#e8f5e9',
      '--sl-backdrop': 'rgba(10, 40, 12, 0.3)',
      ...LIGHT_CODE_VARS,
    }
  },
  {
    id: 'red-light',
    name: '樱花红 浅色',
    previewColor: '#c62828',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#c62828',
      '--sl-primary-hover': '#b71c1c',
      '--sl-primary-active': '#a01616',
      '--sl-primary-light': 'rgba(198, 40, 40, 0.08)',
      '--sl-bg': '#fdf2f2',
      '--sl-bg-secondary': '#fffafa',
      '--sl-panel': '#fffafa',
      '--sl-card': '#fffafa',
      '--sl-card-hover': '#fdf0f0',
      '--sl-border': '#f5c6c6',
      '--sl-border-strong': '#ef9a9a',
      '--sl-text': '#2c1111',
      '--sl-text-secondary': '#6b3a3a',
      '--sl-text-tertiary': '#a07070',
      '--sl-danger': '#c62828',
      '--sl-success': '#2e7d32',
      '--sl-warning': '#f57f17',
      '--sl-shadow-card': '0 2px 8px rgba(120, 20, 20, 0.06), 0 0 1px rgba(120, 20, 20, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(120, 20, 20, 0.10), 0 0 1px rgba(120, 20, 20, 0.14)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#fffafa',
      '--sl-input-border': '#f5c6c6',
      '--sl-input-focus': '#c62828',
      '--sl-sidebar-bg': '#fce4ec',
      '--sl-hover-bg': 'rgba(198, 40, 40, 0.05)',
      '--sl-active-bg': 'rgba(198, 40, 40, 0.09)',
      '--sl-selection': 'rgba(198, 40, 40, 0.12)',
      '--sl-scrollbar': 'rgba(120, 20, 20, 0.18)',
      '--sl-scrollbar-hover': 'rgba(120, 20, 20, 0.32)',
      '--sl-code-bg': '#fce4ec',
      '--sl-backdrop': 'rgba(50, 10, 10, 0.3)',
      ...LIGHT_CODE_VARS,
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

