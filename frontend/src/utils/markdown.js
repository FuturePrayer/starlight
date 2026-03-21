import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

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

