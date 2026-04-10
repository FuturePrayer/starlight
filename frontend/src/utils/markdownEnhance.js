import { Canvg } from 'canvg'
import mermaid from 'mermaid'

let mermaidRenderSeed = 0

function getCssVar(name, fallback = '') {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function escapeSelector(value) {
  if (window.CSS?.escape) {
    return window.CSS.escape(value)
  }
  return String(value || '').replace(/[^a-zA-Z0-9_-]/g, match => `\\${match}`)
}

function buildMermaidConfig() {
  return {
    startOnLoad: false,
    securityLevel: 'strict',
    theme: 'base',
    fontFamily: getCssVar('--sl-font', 'Segoe UI, sans-serif'),
    flowchart: {
      useMaxWidth: true,
      htmlLabels: false,
      curve: 'basis'
    },
    themeVariables: {
      primaryColor: getCssVar('--sl-card', '#ffffff'),
      primaryTextColor: getCssVar('--sl-text', '#111111'),
      primaryBorderColor: getCssVar('--sl-border', '#d0d7de'),
      lineColor: getCssVar('--sl-text-secondary', '#57606a'),
      textColor: getCssVar('--sl-text', '#111111'),
      mainBkg: getCssVar('--sl-card', '#ffffff'),
      secondBkg: getCssVar('--sl-bg-secondary', '#f6f8fa'),
      tertiaryColor: getCssVar('--sl-primary-light', '#eef6ff'),
      clusterBkg: getCssVar('--sl-bg-secondary', '#f6f8fa'),
      clusterBorder: getCssVar('--sl-border', '#d0d7de'),
      edgeLabelBackground: getCssVar('--sl-card', '#ffffff'),
      nodeBorder: getCssVar('--sl-border', '#d0d7de'),
      background: getCssVar('--sl-bg-secondary', '#ffffff'),
      fontFamily: getCssVar('--sl-font', 'Segoe UI, sans-serif')
    }
  }
}

function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

function getSvgDimensions(svgElement) {
  const viewBox = svgElement.viewBox?.baseVal
  const width = Math.ceil(
    viewBox?.width || svgElement.getBBox?.().width || Number.parseFloat(svgElement.getAttribute('width')) || 1200
  )
  const height = Math.ceil(
    viewBox?.height || svgElement.getBBox?.().height || Number.parseFloat(svgElement.getAttribute('height')) || 800
  )
  return { width: Math.max(width, 1), height: Math.max(height, 1) }
}

function serializeSvg(svgElement) {
  const clonedSvg = svgElement.cloneNode(true)
  clonedSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  if (!clonedSvg.getAttribute('xmlns:xlink')) {
    clonedSvg.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink')
  }
  const { width, height } = getSvgDimensions(svgElement)
  if (!clonedSvg.getAttribute('viewBox')) {
    clonedSvg.setAttribute('viewBox', `0 0 ${width} ${height}`)
  }
  clonedSvg.setAttribute('width', String(width))
  clonedSvg.setAttribute('height', String(height))
  return {
    markup: new XMLSerializer().serializeToString(clonedSvg),
    width,
    height
  }
}

async function exportSvg(svgElement, fileName) {
  const { markup } = serializeSvg(svgElement)
  downloadBlob(new Blob([markup], { type: 'image/svg+xml;charset=utf-8' }), fileName)
}

async function exportPng(svgElement, fileName) {
  const { markup, width, height } = serializeSvg(svgElement)
  const scale = Math.max(window.devicePixelRatio || 1, 2)

  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('当前浏览器不支持 PNG 导出')
  }

  context.setTransform(scale, 0, 0, scale, 0, 0)
  context.fillStyle = getCssVar('--sl-bg-secondary', '#ffffff')
  context.fillRect(0, 0, width, height)
  const canvg = await Canvg.fromString(context, markup, {
    ignoreAnimation: true,
    ignoreMouse: true,
    useCORS: true
  })
  await canvg.render()

  const pngBlob = await new Promise((resolve, reject) => {
    canvas.toBlob(blob => {
      if (blob) {
        resolve(blob)
        return
      }
      reject(new Error('PNG 导出失败'))
    }, 'image/png')
  })

  downloadBlob(pngBlob, fileName)
}

function buildDiagramFileName(index, kind) {
  return `starlight-mermaid-${String(index).padStart(2, '0')}.${kind}`
}

function bindExportActions(block, index) {
  const svgElement = block.querySelector('svg')
  if (!svgElement) {
    return
  }

  const svgButton = block.querySelector('[data-export="svg"]')
  const pngButton = block.querySelector('[data-export="png"]')

  if (svgButton) {
    svgButton.addEventListener('click', async () => {
      svgButton.disabled = true
      try {
        await exportSvg(svgElement, buildDiagramFileName(index, 'svg'))
      } finally {
        svgButton.disabled = false
      }
    })
  }

  if (pngButton) {
    pngButton.addEventListener('click', async () => {
      pngButton.disabled = true
      try {
        await exportPng(svgElement, buildDiagramFileName(index, 'png'))
      } finally {
        pngButton.disabled = false
      }
    })
  }
}

export async function enhanceMarkdown(container) {
  if (!container) {
    return { diagramCount: 0 }
  }

  const mermaidBlocks = Array.from(container.querySelectorAll('.sl-mermaid[data-mermaid-source]'))
  if (!mermaidBlocks.length) {
    return { diagramCount: 0 }
  }

  mermaid.initialize(buildMermaidConfig())

  for (const [index, block] of mermaidBlocks.entries()) {
    const source = decodeURIComponent(block.dataset.mermaidSource || '').trim()
    if (!source) {
      block.innerHTML = '<div class="sl-mermaid__error">Mermaid 图表内容为空</div>'
      continue
    }

    try {
      const renderId = `starlight-mermaid-${++mermaidRenderSeed}`
      const { svg, bindFunctions } = await mermaid.render(renderId, source)
      block.innerHTML = `
        <div class="sl-mermaid__toolbar">
          <span class="sl-mermaid__label">Mermaid</span>
          <div class="sl-mermaid__actions">
            <button type="button" class="sl-btn sl-btn--ghost sl-btn--sm" data-export="svg">导出 SVG</button>
            <button type="button" class="sl-btn sl-btn--ghost sl-btn--sm" data-export="png">导出 PNG</button>
          </div>
        </div>
        <div class="sl-mermaid__surface">${svg}</div>
      `
      bindFunctions?.(block)
      bindExportActions(block, index + 1)
    } catch (error) {
      const message = error instanceof Error ? error.message : '未知错误'
      block.innerHTML = `
        <div class="sl-mermaid__error">Mermaid 图表渲染失败：${message}</div>
        <pre class="sl-mermaid__source">${source.replace(/[&<>]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[char]))}</pre>
      `
    }
  }

  return { diagramCount: mermaidBlocks.length }
}

export function scrollMarkdownContainerToHash(container, hash, { behavior = 'smooth', offset = 12 } = {}) {
  const normalizedHash = decodeURIComponent(String(hash || '').replace(/^#/, '').trim())
  if (!container || !normalizedHash) {
    return false
  }

  const target = container.querySelector(`#${escapeSelector(normalizedHash)}`)
  if (!target) {
    return false
  }

  const targetTop = target.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop - offset
  container.scrollTo({
    top: Math.max(targetTop, 0),
    behavior
  })
  return true
}

function getHeadingNodes(container) {
  if (!container) {
    return []
  }
  return Array.from(container.querySelectorAll('h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]'))
}

function getHeadingTop(container, heading) {
  return heading.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop
}

export function detectActiveHeadingAnchor(container, { offset = 48 } = {}) {
  const headings = getHeadingNodes(container)
  if (!container || !headings.length) {
    return ''
  }

  const threshold = container.scrollTop + offset
  let activeAnchor = headings[0].id || ''

  for (const heading of headings) {
    if (getHeadingTop(container, heading) <= threshold) {
      activeAnchor = heading.id || activeAnchor
      continue
    }
    break
  }

  return activeAnchor
}

export function detectActiveOutlineAnchorByEditor(textarea, outlineItems, { offsetLines = 2 } = {}) {
  if (!textarea || !outlineItems?.length) {
    return ''
  }

  const lineHeight = Number.parseFloat(window.getComputedStyle(textarea).lineHeight) || 24
  const currentLine = Math.max(1, Math.floor(textarea.scrollTop / lineHeight) + 1 + offsetLines)
  let activeAnchor = outlineItems[0]?.anchor || ''

  for (const item of outlineItems) {
    if ((item.line || 1) <= currentLine) {
      activeAnchor = item.anchor
      continue
    }
    break
  }

  return activeAnchor
}

