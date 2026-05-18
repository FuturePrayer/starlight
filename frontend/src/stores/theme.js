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
    previewColor: '#0f6cbd',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#0f6cbd',
      '--sl-primary-hover': '#115ea3',
      '--sl-primary-active': '#0c4a7b',
      '--sl-primary-light': 'rgba(15, 108, 189, 0.10)',
      '--sl-bg': '#f5f7fb',
      '--sl-bg-secondary': '#ffffff',
      '--sl-panel': '#ffffff',
      '--sl-card': '#fbfdff',
      '--sl-card-hover': '#f2f6fb',
      '--sl-border': '#d7dee8',
      '--sl-border-strong': '#bcc7d8',
      '--sl-text': '#1b1f24',
      '--sl-text-secondary': '#5d6674',
      '--sl-text-tertiary': '#818da0',
      '--sl-danger': '#d13438',
      '--sl-danger-hover': '#b22d31',
      '--sl-danger-active': '#8f2529',
      '--sl-success': '#0f7b0f',
      '--sl-warning': '#9a6c08',
      '--sl-shadow-card': '0 12px 30px rgba(15, 23, 42, 0.08), 0 1px 2px rgba(15, 23, 42, 0.08)',
      '--sl-shadow-flyout': '0 24px 56px rgba(15, 23, 42, 0.14), 0 1px 3px rgba(15, 23, 42, 0.10)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#ffffff',
      '--sl-input-border': '#cfd8e6',
      '--sl-input-focus': '#0f6cbd',
      '--sl-sidebar-bg': '#eef3fb',
      '--sl-hover-bg': 'rgba(15, 108, 189, 0.06)',
      '--sl-active-bg': 'rgba(15, 108, 189, 0.12)',
      '--sl-selection': 'rgba(15, 108, 189, 0.14)',
      '--sl-scrollbar': 'rgba(84, 104, 141, 0.24)',
      '--sl-scrollbar-hover': 'rgba(84, 104, 141, 0.42)',
      '--sl-code-bg': '#f4f7fb',
      '--sl-backdrop': 'rgba(13, 19, 31, 0.32)',
      '--sl-mark-bg': 'rgba(255, 183, 0, 0.25)',
      '--sl-mark-text': 'inherit',
      '--sl-mark-border': 'rgba(255, 183, 0, 0.5)',
      ...DARK_CODE_VARS,
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
      '--sl-primary-hover': '#79d7ff',
      '--sl-primary-active': '#38b7ff',
      '--sl-primary-light': 'rgba(96, 205, 255, 0.14)',
      '--sl-bg': '#0f1115',
      '--sl-bg-secondary': '#151922',
      '--sl-panel': '#1b2028',
      '--sl-card': '#1b2028',
      '--sl-card-hover': '#232935',
      '--sl-border': '#2e3542',
      '--sl-border-strong': '#435066',
      '--sl-text': '#f4f7fb',
      '--sl-text-secondary': '#c3ccd9',
      '--sl-text-tertiary': '#8894a7',
      '--sl-danger': '#ff6b78',
      '--sl-danger-hover': '#ff5665',
      '--sl-danger-active': '#eb4857',
      '--sl-success': '#6ccb5f',
      '--sl-warning': '#ffd34d',
      '--sl-shadow-card': '0 18px 44px rgba(0, 0, 0, 0.38), 0 0 0 1px rgba(255, 255, 255, 0.03)',
      '--sl-shadow-flyout': '0 28px 72px rgba(0, 0, 0, 0.52), 0 0 0 1px rgba(255, 255, 255, 0.05)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': '#151922',
      '--sl-input-border': '#333b4a',
      '--sl-input-focus': '#60cdff',
      '--sl-sidebar-bg': '#12161d',
      '--sl-hover-bg': 'rgba(255, 255, 255, 0.045)',
      '--sl-active-bg': 'rgba(96, 205, 255, 0.15)',
      '--sl-selection': 'rgba(96, 205, 255, 0.18)',
      '--sl-scrollbar': 'rgba(195, 204, 217, 0.20)',
      '--sl-scrollbar-hover': 'rgba(195, 204, 217, 0.36)',
      '--sl-code-bg': '#12161d',
      '--sl-backdrop': 'rgba(1, 4, 9, 0.68)',
      '--sl-mark-bg': 'rgba(255, 211, 77, 0.18)',
      '--sl-mark-text': '#ffe082',
      '--sl-mark-border': 'rgba(255, 211, 77, 0.42)',
      ...DARK_CODE_VARS,
    }
  },
  {
    id: 'violet-light',
    name: '暮光紫 浅色',
    previewColor: '#7563ff',
    backgroundImage: '',
    backgroundOpacity: 0,
    vars: {
      '--sl-primary': '#7563ff',
      '--sl-primary-hover': '#6754f5',
      '--sl-primary-active': '#5746df',
      '--sl-primary-light': 'rgba(117, 99, 255, 0.10)',
      '--sl-bg': '#f6f3ff',
      '--sl-bg-secondary': '#fcfbff',
      '--sl-panel': '#ffffff',
      '--sl-card': '#ffffff',
      '--sl-card-hover': '#f4f1ff',
      '--sl-border': '#e4defa',
      '--sl-border-strong': '#cdc1ff',
      '--sl-text': '#241d3e',
      '--sl-text-secondary': '#5f5979',
      '--sl-text-tertiary': '#8b83aa',
      '--sl-danger': '#dd4d68',
      '--sl-danger-hover': '#cf3d59',
      '--sl-danger-active': '#ba314d',
      '--sl-success': '#2f9f70',
      '--sl-warning': '#b97a17',
      '--sl-shadow-card': '0 12px 32px rgba(62, 45, 142, 0.08), 0 0 0 1px rgba(117, 99, 255, 0.04)',
      '--sl-shadow-flyout': '0 24px 56px rgba(44, 33, 107, 0.12), 0 0 0 1px rgba(117, 99, 255, 0.06)',
      '--sl-radius': '8px',
      '--sl-radius-lg': '12px',
      '--sl-input-bg': 'rgba(255, 255, 255, 0.94)',
      '--sl-input-border': '#ddd2ff',
      '--sl-input-focus': '#7563ff',
      '--sl-sidebar-bg': '#f7f4ff',
      '--sl-hover-bg': 'rgba(117, 99, 255, 0.06)',
      '--sl-active-bg': 'rgba(117, 99, 255, 0.11)',
      '--sl-selection': 'rgba(117, 99, 255, 0.14)',
      '--sl-scrollbar': 'rgba(88, 70, 196, 0.24)',
      '--sl-scrollbar-hover': 'rgba(88, 70, 196, 0.40)',
      '--sl-code-bg': '#f4f0ff',
      '--sl-backdrop': 'rgba(18, 14, 42, 0.42)',
      '--sl-mark-bg': 'rgba(255, 183, 0, 0.25)',
      '--sl-mark-text': 'inherit',
      '--sl-mark-border': 'rgba(255, 183, 0, 0.5)',
      ...LIGHT_CODE_VARS,
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
      '--sl-danger-hover': '#ab3814',
      '--sl-danger-active': '#923012',
      '--sl-success': '#578c2a',
      '--sl-warning': '#c28b1a',
      '--sl-shadow-card': '0 2px 8px rgba(120, 90, 20, 0.06), 0 0 1px rgba(120, 90, 20, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(120, 90, 20, 0.10), 0 0 1px rgba(120, 90, 20, 0.14)',
      '--sl-radius': '5px',
      '--sl-radius-lg': '8px',
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
      '--sl-mark-bg': 'rgba(184, 134, 11, 0.18)',
      '--sl-mark-text': 'inherit',
      '--sl-mark-border': 'rgba(184, 134, 11, 0.45)',
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
      '--sl-danger-hover': '#b71c1c',
      '--sl-danger-active': '#a61a1a',
      '--sl-success': '#2e7d32',
      '--sl-warning': '#f9a825',
      '--sl-shadow-card': '0 2px 8px rgba(30, 80, 35, 0.06), 0 0 1px rgba(30, 80, 35, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(30, 80, 35, 0.10), 0 0 1px rgba(30, 80, 35, 0.14)',
      '--sl-radius': '5px',
      '--sl-radius-lg': '8px',
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
      '--sl-mark-bg': 'rgba(46, 125, 50, 0.15)',
      '--sl-mark-text': 'inherit',
      '--sl-mark-border': 'rgba(46, 125, 50, 0.4)',
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
      '--sl-danger-hover': '#b71c1c',
      '--sl-danger-active': '#a61a1a',
      '--sl-success': '#2e7d32',
      '--sl-warning': '#f57f17',
      '--sl-shadow-card': '0 2px 8px rgba(120, 20, 20, 0.06), 0 0 1px rgba(120, 20, 20, 0.12)',
      '--sl-shadow-flyout': '0 8px 32px rgba(120, 20, 20, 0.10), 0 0 1px rgba(120, 20, 20, 0.14)',
      '--sl-radius': '5px',
      '--sl-radius-lg': '8px',
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
      '--sl-mark-bg': 'rgba(198, 40, 40, 0.12)',
      '--sl-mark-text': 'inherit',
      '--sl-mark-border': 'rgba(198, 40, 40, 0.35)',
      ...LIGHT_CODE_VARS,
    }
  }
]

const CACHE_KEY = 'starlight-theme'

export const useThemeStore = defineStore('theme', () => {
  const current = ref(null)
  const themes = ref([...BUILTIN_THEMES])

  const currentId = computed(() => current.value?.id || 'win11-light')

  function applyVars(theme) {
    const root = document.documentElement
    if (theme.vars) {
      Object.entries(theme.vars).forEach(([k, v]) => root.style.setProperty(k, v))
    }
    const existing = document.getElementById('sl-theme-css')
    if (existing) existing.remove()
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
        const resolved = resolveTheme(parsed)
        apply(resolved)
        return resolved
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
      themes.value = serverThemes
        .map(item => BUILTIN_THEMES.find(theme => theme.id === item.id) || item)
        .filter((item, index, list) => list.findIndex(theme => theme.id === item.id) === index)
    } catch {
      themes.value = [...BUILTIN_THEMES]
    }
    return [...themes.value]
  }

  async function selectTheme(themeId) {
    const result = await themeApi.select(themeId)
    const theme = resolveTheme(result)
    apply(theme)
    return theme
  }

  function getAll() {
    return [...themes.value]
  }

  return { current, themes, currentId, apply, loadCached, loadThemes, selectTheme, getAll, resolveTheme }
})
