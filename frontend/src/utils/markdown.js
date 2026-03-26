import hljs from 'highlight.js/lib/common'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

function normalizeLanguage(lang) {
  return String(lang || '').trim().split(/\s+/)[0].toLowerCase()
}

function isOpeningTag(token) {
  return /^<([a-zA-Z][^\s/>]*)\b[^>]*>$/.test(token) && !token.startsWith('</') && !token.endsWith('/>')
}

function getTagName(token) {
  const match = token.match(/^<\/?([a-zA-Z][^\s/>]*)/)
  return match ? match[1].toLowerCase() : ''
}

function splitHighlightedLines(html) {
  const tokens = String(html || '').split(/(<[^>]+>|\n)/g).filter(Boolean)
  const lines = ['']
  const openTags = []

  tokens.forEach(token => {
    const currentIndex = lines.length - 1

    if (token === '\n') {
      if (openTags.length) {
        lines[currentIndex] += openTags.slice().reverse().map(tag => `</${tag.name}>`).join('')
      }
      lines.push(openTags.map(tag => tag.token).join(''))
      return
    }

    lines[currentIndex] += token

    if (token.startsWith('</')) {
      const closingTagName = getTagName(token)
      const openIndex = [...openTags].reverse().findIndex(tag => tag.name === closingTagName)
      if (openIndex >= 0) {
        openTags.splice(openTags.length - 1 - openIndex, 1)
      }
      return
    }

    if (isOpeningTag(token)) {
      openTags.push({ name: getTagName(token), token })
    }
  })

  return lines
}

function highlightCode(content, lang) {
  const normalizedLang = normalizeLanguage(lang)

  if (normalizedLang && hljs.getLanguage(normalizedLang)) {
    const result = hljs.highlight(content, {
      language: normalizedLang,
      ignoreIllegals: true
    })
    return {
      html: result.value,
      language: result.language || normalizedLang,
      highlighted: true
    }
  }

  return {
    html: md.utils.escapeHtml(content),
    language: normalizedLang,
    highlighted: false
  }
}

function renderCodeBlock(content, { lang = '', showLineNumbers = false } = {}) {
  const normalizedCode = String(content || '').replace(/\n$/, '')
  const { html: highlightedHtml, highlighted } = highlightCode(normalizedCode, lang)
  const lines = splitHighlightedLines(highlightedHtml)
  const lineCount = Math.max(lines.length, 1)
  const gutterWidth = String(lineCount).length
  const normalizedLang = normalizeLanguage(lang)
  const langLabel = normalizedLang ? `<span class="sl-code-lang">${md.utils.escapeHtml(normalizedLang)}</span>` : ''

  const lineHtml = lines.map((line, index) => {
    const contentHtml = `<span class="sl-code-content">${line}</span>`

    if (!showLineNumbers) {
      return `<span class="sl-code-line sl-code-line--plain">${contentHtml}</span>`
    }

    const lineNumber = String(index + 1).padStart(gutterWidth, ' ')
    return `<span class="sl-code-line"><span class="sl-code-ln">${lineNumber}</span>${contentHtml}</span>`
  }).join('')

  const languageClass = normalizedLang ? ` language-${md.utils.escapeHtml(normalizedLang)}` : ''
  const highlightedClass = highlighted ? ' hljs' : ''

  return `<div class="sl-code-block">${langLabel}<pre><code class="sl-code${highlightedClass}${languageClass}">${lineHtml}</code></pre></div>`
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

