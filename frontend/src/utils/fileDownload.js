const WINDOWS_RESERVED_NAMES = new Set([
  'CON', 'PRN', 'AUX', 'NUL',
  'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9',
  'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9'
])

export function sanitizeFileName(value, fallback = '未命名文件') {
  let sanitized = String(value || fallback)
    .replace(/[\\/:*?"<>|\x00-\x1F]/g, '')
    .trim()

  while (sanitized.endsWith('.') || sanitized.endsWith(' ')) {
    sanitized = sanitized.slice(0, -1).trimEnd()
  }

  if (!sanitized) {
    sanitized = fallback
  }
  if (WINDOWS_RESERVED_NAMES.has(sanitized.toUpperCase())) {
    sanitized = `${sanitized}_`
  }
  if (sanitized.length > 120) {
    sanitized = sanitized.slice(0, 120).trim()
  }
  return sanitized || fallback
}

export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
