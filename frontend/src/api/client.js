const BASE = ''

export async function request(url, options = {}) {
  const headers = new Headers(options.headers || {})
  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(BASE + url, {
    credentials: 'same-origin',
    headers,
    ...options
  })
  const payload = await res.json().catch(() => ({ success: false, message: '响应解析失败' }))
  if (!res.ok || !payload.success) {
    const err = new Error(payload.message || '请求失败')
    err.status = res.status
    err.data = payload.data
    throw err
  }
  return payload.data
}

function parseContentDispositionFileName(contentDisposition) {
  if (!contentDisposition) return ''
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] || ''
}

export async function download(url, options = {}) {
  const res = await fetch(BASE + url, {
    credentials: 'same-origin',
    headers: new Headers(options.headers || {}),
    ...options
  })

  if (!res.ok) {
    const payload = await res.json().catch(() => ({ message: '请求失败' }))
    const err = new Error(payload.message || '请求失败')
    err.status = res.status
    err.data = payload.data
    throw err
  }

  return {
    blob: await res.blob(),
    fileName: parseContentDispositionFileName(res.headers.get('content-disposition'))
  }
}
