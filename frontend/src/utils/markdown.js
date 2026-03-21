import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

function renderCodeBlock(content, { lang = '', showLineNumbers = false } = {}) {
  const normalizedCode = String(content || '').replace(/\n$/, '')
  const lines = normalizedCode.split('\n')
  const lineCount = Math.max(lines.length, 1)
  const gutterWidth = String(lineCount).length
  const langLabel = lang ? `<span class="sl-code-lang">${md.utils.escapeHtml(lang)}</span>` : ''

  const lineHtml = lines.map((line, index) => {
    const contentHtml = `<span class="sl-code-content">${md.utils.escapeHtml(line)}</span>`

    if (!showLineNumbers) {
      return `<span class="sl-code-line sl-code-line--plain">${contentHtml}</span>`
    }

    const lineNumber = String(index + 1).padStart(gutterWidth, ' ')
    return `<span class="sl-code-line"><span class="sl-code-ln">${lineNumber}</span>${contentHtml}</span>`
  }).join('')

  return `<div class="sl-code-block">${langLabel}<pre><code>${lineHtml}</code></pre></div>`
}

// ── Code blocks: fenced blocks with line numbers, indented blocks with same spacing ──
md.renderer.rules.fence = function (tokens, idx) {
  const token = tokens[idx]
  const lang = token.info ? token.info.trim().split(/\s+/)[0] : ''
  return renderCodeBlock(token.content, { lang, showLineNumbers: true })
}

md.renderer.rules.code_block = function (tokens, idx) {
  return renderCodeBlock(tokens[idx].content)
}

// Add id to headings for outline anchors
const defaultRender = md.renderer.rules.heading_open || function (tokens, idx, options, env, self) {
  return self.renderToken(tokens, idx, options)
}
md.renderer.rules.heading_open = function (tokens, idx, options, env, self) {
  const token = tokens[idx]
  const contentToken = tokens[idx + 1]
  if (contentToken && contentToken.children) {
    const text = contentToken.children.map(t => t.content).join('')
    const slug = slugify(text)
    token.attrSet('id', slug)
  }
  return defaultRender(tokens, idx, options, env, self)
}

export function renderMarkdown(text) {
  return md.render(text || '')
}

export function parseOutline(markdown) {
  return String(markdown || '')
    .split(/\r?\n/)
    .map(line => {
      const match = line.match(/^(#{2,6})\s+(.+)$/)
      if (!match) return null
      return {
        level: match[1].length,
        title: match[2].trim(),
        anchor: slugify(match[2].trim())
      }
    })
    .filter(Boolean)
}

function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '') || 'section'
}

export function formatTime(value) {
  if (!value) return '永久'
  return new Date(value).toLocaleString('zh-CN')
}

