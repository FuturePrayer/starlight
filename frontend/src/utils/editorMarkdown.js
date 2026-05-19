export const RICH_EDITOR_UNSUPPORTED_PATTERNS = [
  {
    key: 'footnote',
    label: '脚注',
    pattern: /(^|\n)\[\^[^\]]+\]:|(\[\^[^\]]+\])/m
  }
]

export function getRichEditorUnsupportedFeatures(markdown = '') {
  const source = String(markdown || '')
  return RICH_EDITOR_UNSUPPORTED_PATTERNS
    .filter(item => item.pattern.test(source))
    .map(item => item.label)
}

export function shouldPreferSourceMode(markdown = '') {
  return getRichEditorUnsupportedFeatures(markdown).length > 0
}

export const COMMON_CODE_LANGUAGES = [
  '',
  'javascript',
  'typescript',
  'java',
  'python',
  'bash',
  'json',
  'css',
  'html',
  'sql',
  'markdown',
  'mermaid',
  'yaml',
  'xml',
  'dockerfile'
]

export function normalizeEditorMarkdown(markdown = '') {
  return String(markdown || '').replace(/\r\n?/g, '\n')
}
