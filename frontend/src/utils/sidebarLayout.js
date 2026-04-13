import { computed, onUnmounted, ref } from 'vue'

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function getSafeStorage() {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function useSidebarWidth({
  storageKey,
  defaultWidth = 340,
  minWidth = 280,
  maxWidth = 480,
  desktopBreakpoint = 768,
  maxViewportRatio = 0.45
} = {}) {
  function getViewportMax() {
    if (typeof window === 'undefined') return maxWidth
    const ratioMax = Math.floor(window.innerWidth * maxViewportRatio)
    return Math.max(minWidth, Math.min(maxWidth, ratioMax))
  }

  function clampWidth(value) {
    return clamp(Number(value) || defaultWidth, minWidth, getViewportMax())
  }

  const storage = typeof window !== 'undefined' ? getSafeStorage() : null
  const initialWidth = storage ? Number(storage.getItem(storageKey)) : NaN
  const sidebarWidth = ref(clampWidth(Number.isFinite(initialWidth) ? initialWidth : defaultWidth))
  const isResizing = ref(false)

  let startX = 0
  let startWidth = sidebarWidth.value

  function persistWidth() {
    if (!storage || !storageKey) return
    storage.setItem(storageKey, String(Math.round(sidebarWidth.value)))
  }

  function syncSidebarWidth() {
    sidebarWidth.value = clampWidth(sidebarWidth.value)
    persistWidth()
  }

  function resetWidth() {
    sidebarWidth.value = clampWidth(defaultWidth)
    persistWidth()
  }

  function cleanupPointerState() {
    if (typeof document === 'undefined') return
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  function stopResize() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', stopResize)
      window.removeEventListener('pointercancel', stopResize)
    }
    cleanupPointerState()
    isResizing.value = false
    persistWidth()
  }

  function handlePointerMove(event) {
    if (!isResizing.value) return
    const nextWidth = startWidth + (event.clientX - startX)
    sidebarWidth.value = clampWidth(nextWidth)
  }

  function startResize(event) {
    if (typeof window === 'undefined') return
    if (window.innerWidth <= desktopBreakpoint) return
    startX = event.clientX
    startWidth = sidebarWidth.value
    isResizing.value = true
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', stopResize)
    window.addEventListener('pointercancel', stopResize)
  }

  onUnmounted(() => {
    stopResize()
  })

  const sidebarStyle = computed(() => ({
    '--sl-sidebar-width': `${Math.round(sidebarWidth.value)}px`
  }))

  return {
    sidebarStyle,
    isResizing,
    startResize,
    resetWidth,
    syncSidebarWidth
  }
}

