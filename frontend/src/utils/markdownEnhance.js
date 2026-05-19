import { Canvg } from 'canvg'
import mermaid from 'mermaid'

let mermaidRenderSeed = 0

const mermaidPalette = [
  '#4f9ef8',
  '#c27af0',
  '#62d680',
  '#ffb743',
  '#8582f3',
  '#ff6666',
  '#5fc4ea',
  '#f97316',
  '#22c55e',
  '#e879f9',
  '#14b8a6',
  '#facc15'
]

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
  const themeText = getCssVar('--sl-text', '#111111')
  const themeTextSecondary = getCssVar('--sl-text-secondary', '#57606a')
  const themeCard = getCssVar('--sl-card', '#ffffff')
  const themeSurface = getCssVar('--sl-bg-secondary', '#ffffff')
  const themeBorder = getCssVar('--sl-border', '#d0d7de')
  const seriesColors = Object.fromEntries(mermaidPalette.flatMap((color, index) => [
    [`pie${index + 1}`, color],
    [`cScale${index}`, color],
    [`git${index}`, color]
  ]))

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
      ...seriesColors,
      primaryColor: themeCard,
      primaryTextColor: themeText,
      primaryBorderColor: themeBorder,
      secondaryColor: themeSurface,
      secondaryTextColor: themeText,
      secondaryBorderColor: themeBorder,
      tertiaryColor: getCssVar('--sl-primary-light', '#eef6ff'),
      tertiaryTextColor: themeText,
      tertiaryBorderColor: themeBorder,
      lineColor: themeTextSecondary,
      textColor: themeText,
      mainBkg: themeCard,
      secondBkg: themeSurface,
      clusterBkg: themeSurface,
      clusterBorder: themeBorder,
      edgeLabelBackground: themeCard,
      nodeBorder: themeBorder,
      background: themeSurface,
      fontFamily: getCssVar('--sl-font', 'Segoe UI, sans-serif'),
      pieTitleTextColor: themeText,
      pieLegendTextColor: themeText,
      pieSectionTextColor: '#ffffff',
      pieStrokeColor: themeSurface,
      pieStrokeWidth: '2px',
      pieOpacity: '1',
      xyChart: {
        backgroundColor: themeSurface,
        titleColor: themeText,
        dataLabelColor: themeText,
        xAxisTitleColor: themeText,
        xAxisLabelColor: themeText,
        xAxisTickColor: themeTextSecondary,
        xAxisLineColor: themeTextSecondary,
        yAxisTitleColor: themeText,
        yAxisLabelColor: themeText,
        yAxisTickColor: themeTextSecondary,
        yAxisLineColor: themeTextSecondary,
        plotColorPalette: mermaidPalette.join(',')
      }
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

function getSvgMarkupDimensions(markup) {
  const doc = new DOMParser().parseFromString(markup, 'image/svg+xml')
  const svg = doc.documentElement
  const viewBox = svg.getAttribute('viewBox')?.split(/\s+/).map(Number) || []
  const widthAttr = svg.getAttribute('width') || ''
  const heightAttr = svg.getAttribute('height') || ''
  const width = viewBox[2] || (!widthAttr.includes('%') && Number.parseFloat(widthAttr)) || 1200
  const height = viewBox[3] || (!heightAttr.includes('%') && Number.parseFloat(heightAttr)) || 800
  return { width: Math.max(Math.ceil(width), 1), height: Math.max(Math.ceil(height), 1) }
}

function normalizeSvgMarkup(markup, width, height) {
  const doc = new DOMParser().parseFromString(markup, 'image/svg+xml')
  const svg = doc.documentElement
  svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  if (!svg.getAttribute('xmlns:xlink')) {
    svg.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink')
  }
  if (!svg.getAttribute('viewBox')) {
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`)
  }
  svg.setAttribute('width', String(width))
  svg.setAttribute('height', String(height))
  return new XMLSerializer().serializeToString(svg)
}

async function blobToArrayBuffer(blob) {
  if (blob.arrayBuffer) {
    return blob.arrayBuffer()
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(reader.error || new Error('读取图片数据失败'))
    reader.readAsArrayBuffer(blob)
  })
}

function canvasToPngBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(result => {
      if (result) {
        resolve(result)
        return
      }
      reject(new Error('Mermaid 图片生成失败'))
    }, 'image/png')
  })
}

function createScaledCanvas(width, height, scale) {
  const canvas = document.createElement('canvas')
  canvas.width = Math.ceil(width * scale)
  canvas.height = Math.ceil(height * scale)
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('当前浏览器不支持 Mermaid 图片导出')
  }
  context.setTransform(scale, 0, 0, scale, 0, 0)
  context.fillStyle = getCssVar('--sl-bg-secondary', '#ffffff')
  context.fillRect(0, 0, width, height)
  return { canvas, context }
}

function getRenderedSvgDimensions(svgElement, fallbackWidth, fallbackHeight) {
  return {
    width: Math.max(
      1,
      Math.ceil(svgElement.clientWidth || svgElement.getBoundingClientRect().width || fallbackWidth)
    ),
    height: Math.max(
      1,
      Math.ceil(svgElement.clientHeight || svgElement.getBoundingClientRect().height || fallbackHeight)
    )
  }
}

function mountHiddenSvg(markup) {
  const host = document.createElement('div')
  host.style.position = 'fixed'
  host.style.left = '-10000px'
  host.style.top = '0'
  host.style.visibility = 'hidden'
  host.style.pointerEvents = 'none'
  host.style.width = 'fit-content'
  host.style.height = 'fit-content'
  host.innerHTML = markup
  document.body.appendChild(host)
  const svgElement = host.querySelector('svg')
  if (!svgElement) {
    host.remove()
    throw new Error('无法解析 Mermaid SVG')
  }
  return { host, svgElement }
}

async function rasterizeSvgElementToPng(svgElement, fallbackWidth, fallbackHeight, scale) {
  const { width, height } = getRenderedSvgDimensions(svgElement, fallbackWidth, fallbackHeight)
  const markup = normalizeSvgMarkup(new XMLSerializer().serializeToString(svgElement), width, height)
  try {
    return await rasterizeSerializedSvgWithImage(markup, width, height, scale, 'blob')
  } catch (error) {
    try {
      return await rasterizeSerializedSvgWithImage(markup, width, height, scale, 'data')
    } catch (fallbackError) {
      return {
        blob: await rasterizeSvgWithCanvg(markup, width, height, scale),
        width,
        height
      }
    }
  }
}

async function rasterizeSerializedSvgWithImage(markup, width, height, scale, mode) {
  const { canvas, context } = createScaledCanvas(width, height, scale)
  const isBlobUrl = mode === 'blob'
  const url = isBlobUrl
    ? URL.createObjectURL(new Blob([markup], { type: 'image/svg+xml;charset=utf-8' }))
    : `data:image/svg+xml;charset=utf-8,${encodeURIComponent(markup)}`
  try {
    const image = new Image()
    if (isBlobUrl) {
      image.crossOrigin = 'anonymous'
    }
    await new Promise((resolve, reject) => {
      image.onload = resolve
      image.onerror = () => reject(new Error('浏览器无法加载 Mermaid SVG'))
      image.src = url
    })
    context.drawImage(image, 0, 0, width, height)
    return {
      blob: await canvasToPngBlob(canvas),
      width,
      height
    }
  } finally {
    if (isBlobUrl) {
      URL.revokeObjectURL(url)
    }
  }
}

async function rasterizeSvgWithImage(markup, fallbackWidth, fallbackHeight, scale) {
  const { host, svgElement } = mountHiddenSvg(markup)
  try {
    return await rasterizeSvgElementToPng(svgElement, fallbackWidth, fallbackHeight, scale)
  } finally {
    host.remove()
  }
}

async function rasterizeSvgWithCanvg(markup, width, height, scale) {
  const { canvas, context } = createScaledCanvas(width, height, scale)
  const canvg = await Canvg.fromString(context, markup, {
    ignoreAnimation: true,
    ignoreMouse: true,
    useCORS: true
  })
  await canvg.render()
  return canvasToPngBlob(canvas)
}

export async function renderMermaidSourceToSvg(source, { idPrefix = 'starlight-mermaid' } = {}) {
  mermaid.initialize(buildMermaidConfig())
  const renderId = `${idPrefix}-${++mermaidRenderSeed}`
  return mermaid.render(renderId, source)
}

export async function renderMermaidSourceToPng(source, { scale = 2, maxWidth = 600 } = {}) {
  const { svg } = await renderMermaidSourceToSvg(source, { idPrefix: 'starlight-docx-mermaid' })
  const { width, height } = getSvgMarkupDimensions(svg)
  const markup = normalizeSvgMarkup(svg, width, height)
  const pixelScale = Math.max(window.devicePixelRatio || 1, scale)
  const renderedImage = await rasterizeSvgWithImage(markup, width, height, pixelScale)

  const displayWidth = Math.min(renderedImage.width, maxWidth)
  return {
    data: await blobToArrayBuffer(renderedImage.blob),
    width: displayWidth,
    height: Math.max(1, Math.round(renderedImage.height * (displayWidth / renderedImage.width)))
  }
}

async function exportSvg(svgElement, fileName) {
  const { markup } = serializeSvg(svgElement)
  downloadBlob(new Blob([markup], { type: 'image/svg+xml;charset=utf-8' }), fileName)
}

async function exportPng(svgElement, fileName) {
  const { markup, width, height } = serializeSvg(svgElement)
  const scale = Math.max(window.devicePixelRatio || 1, 2)
  const pngBlob = (await rasterizeSvgElementToPng(svgElement, width, height, scale)).blob

  downloadBlob(pngBlob, fileName)
}

async function copyTextToClipboard(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  textarea.remove()

  if (!copied) {
    throw new Error('复制失败')
  }
}

function buildDiagramFileName(index, kind) {
  return `starlight-mermaid-${String(index).padStart(2, '0')}.${kind}`
}

function bindMermaidActions(block, index, source) {
  const svgElement = block.querySelector('svg')
  const svgButton = block.querySelector('[data-export="svg"]')
  const pngButton = block.querySelector('[data-export="png"]')
  const copyButton = block.querySelector('[data-copy-source]')

  if (svgElement && svgButton) {
    svgButton.addEventListener('click', async () => {
      svgButton.disabled = true
      try {
        await exportSvg(svgElement, buildDiagramFileName(index, 'svg'))
      } finally {
        svgButton.disabled = false
      }
    })
  }

  if (svgElement && pngButton) {
    pngButton.addEventListener('click', async () => {
      pngButton.disabled = true
      try {
        await exportPng(svgElement, buildDiagramFileName(index, 'png'))
      } finally {
        pngButton.disabled = false
      }
    })
  }

  if (copyButton) {
    copyButton.addEventListener('click', async () => {
      const label = copyButton.textContent
      copyButton.disabled = true
      try {
        await copyTextToClipboard(source)
        copyButton.textContent = '已复制'
        setTimeout(() => {
          copyButton.textContent = label
        }, 1600)
      } catch (error) {
        copyButton.textContent = '复制失败'
        setTimeout(() => {
          copyButton.textContent = label
        }, 1600)
      } finally {
        copyButton.disabled = false
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

  for (const [index, block] of mermaidBlocks.entries()) {
    const source = decodeURIComponent(block.dataset.mermaidSource || '').trim()
    if (!source) {
      block.innerHTML = '<div class="sl-mermaid__error">Mermaid 图表内容为空</div>'
      continue
    }

    try {
      const { svg, bindFunctions } = await renderMermaidSourceToSvg(source)
      block.innerHTML = `
        <div class="sl-mermaid__toolbar">
          <span class="sl-mermaid__label">Mermaid</span>
          <div class="sl-mermaid__actions">
            <button type="button" class="sl-btn sl-btn--ghost sl-btn--sm" data-export="svg">导出 SVG</button>
            <button type="button" class="sl-btn sl-btn--ghost sl-btn--sm" data-export="png">导出 PNG</button>
            <button type="button" class="sl-btn sl-btn--ghost sl-btn--sm" data-copy-source>复制源代码</button>
          </div>
        </div>
        <div class="sl-mermaid__surface">${svg}</div>
      `
      bindFunctions?.(block)
      bindMermaidActions(block, index + 1, source)
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
