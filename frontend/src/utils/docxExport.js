import {
  AlignmentType,
  BorderStyle,
  Document,
  ExternalHyperlink,
  HeadingLevel,
  ImageRun,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableRow,
  TextRun,
  WidthType
} from 'docx'
import remarkGfm from 'remark-gfm'
import remarkParse from 'remark-parse'
import { unified } from 'unified'
import { renderMermaidSourceToPng } from '@/utils/markdownEnhance'

const BODY_FONT = { ascii: 'Aptos', hAnsi: 'Aptos', eastAsia: 'Microsoft YaHei' }
const CODE_FONT = { ascii: 'Consolas', hAnsi: 'Consolas', eastAsia: 'Microsoft YaHei' }
const BODY_SIZE = 22
const FOOTNOTE_SIZE = 18

function safeFileName(value) {
  return String(value || '未命名笔记')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ')
    .trim() || '未命名笔记'
}

function triggerDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

function textRun(text, options = {}) {
  return new TextRun({
    text: String(text || ''),
    font: options.font || BODY_FONT,
    size: options.size || BODY_SIZE,
    ...options
  })
}

function collectPlainText(node) {
  if (!node) return ''
  if (node.type === 'text' || node.type === 'inlineCode' || node.type === 'code') return node.value || ''
  if (node.type === 'break') return '\n'
  if (node.type === 'image') return node.alt || ''
  return (node.children || []).map(collectPlainText).join('')
}

function collectFootnotes(root) {
  const definitions = new Map()
  const content = []

  for (const node of root.children || []) {
    if (node.type === 'footnoteDefinition') {
      definitions.set(String(node.identifier || '').toLowerCase(), node)
      continue
    }
    content.push(node)
  }

  return { definitions, content }
}

function createInlineRenderer(footnoteDefinitions) {
  const footnoteOrder = []
  const footnoteNumbers = new Map()

  function reserveFootnote(identifier) {
    const key = String(identifier || '').toLowerCase()
    if (!footnoteNumbers.has(key)) {
      footnoteNumbers.set(key, footnoteNumbers.size + 1)
      footnoteOrder.push(key)
    }
    return footnoteNumbers.get(key)
  }

  function renderInline(nodes = [], style = {}) {
    const runs = []

    for (const node of nodes) {
      if (!node) continue
      if (node.type === 'text') {
        runs.push(textRun(node.value, style))
      } else if (node.type === 'strong') {
        runs.push(...renderInline(node.children, { ...style, bold: true }))
      } else if (node.type === 'emphasis') {
        runs.push(...renderInline(node.children, { ...style, italics: true }))
      } else if (node.type === 'delete') {
        runs.push(...renderInline(node.children, { ...style, strike: true }))
      } else if (node.type === 'inlineCode') {
        runs.push(textRun(node.value, {
          ...style,
          font: CODE_FONT,
          color: 'C2410C',
          shading: { fill: 'F6F8FA' }
        }))
      } else if (node.type === 'break') {
        runs.push(new TextRun({ break: 1 }))
      } else if (node.type === 'link') {
        const children = renderInline(node.children, { ...style, color: '0F6CBD', underline: {} })
        runs.push(new ExternalHyperlink({ link: node.url || '', children }))
      } else if (node.type === 'footnoteReference') {
        const number = reserveFootnote(node.identifier)
        runs.push(textRun(`[${number}]`, {
          ...style,
          size: FOOTNOTE_SIZE,
          color: '0F6CBD',
          superScript: true
        }))
      } else if (node.type === 'image') {
        runs.push(textRun(node.alt ? `[图片: ${node.alt}]` : '[图片]', style))
      } else {
        runs.push(textRun(collectPlainText(node), style))
      }
    }

    return runs
  }

  return { footnoteDefinitions, footnoteOrder, footnoteNumbers, renderInline }
}

function headingLevel(depth) {
  return {
    1: HeadingLevel.HEADING_1,
    2: HeadingLevel.HEADING_2,
    3: HeadingLevel.HEADING_3,
    4: HeadingLevel.HEADING_4,
    5: HeadingLevel.HEADING_5,
    6: HeadingLevel.HEADING_6
  }[depth] || HeadingLevel.HEADING_2
}

function headingSize(depth) {
  return { 1: 40, 2: 32, 3: 28, 4: 24, 5: 22, 6: 22 }[depth] || 24
}

function paragraphFromInline(nodes, renderer, options = {}) {
  const children = renderer.renderInline(nodes, options.run || {})
  return new Paragraph({
    children: children.length ? children : [textRun('', options.run || {})],
    spacing: options.spacing || { after: 160, line: 330 },
    indent: options.indent,
    bullet: options.bullet,
    heading: options.heading,
    alignment: options.alignment
  })
}

function codeParagraph(value) {
  const lines = String(value || '').split('\n')
  const children = []
  lines.forEach((line, index) => {
    if (index > 0) children.push(new TextRun({ break: 1 }))
    children.push(textRun(line || ' ', { font: CODE_FONT, size: 19, color: '24292F' }))
  })
  return new Paragraph({
    children,
    spacing: { before: 80, after: 180 },
    shading: { fill: 'F6F8FA' },
    border: {
      top: { style: BorderStyle.SINGLE, size: 1, color: 'D0D7DE' },
      bottom: { style: BorderStyle.SINGLE, size: 1, color: 'D0D7DE' },
      left: { style: BorderStyle.SINGLE, size: 1, color: 'D0D7DE' },
      right: { style: BorderStyle.SINGLE, size: 1, color: 'D0D7DE' }
    }
  })
}

async function renderMermaidBlock(node) {
  try {
    const image = await renderMermaidSourceToPng(node.value || '')
    return new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 160, after: 220 },
      children: [
        new ImageRun({
          type: 'png',
          data: image.data,
          transformation: { width: image.width, height: image.height }
        })
      ]
    })
  } catch (error) {
    return codeParagraph(node.value || '')
  }
}

function tableFromNode(node, renderer) {
  const rows = (node.children || []).map(row => new TableRow({
    children: (row.children || []).map(cell => new TableCell({
      children: [paragraphFromInline(cell.children || [], renderer, { spacing: { after: 80 } })]
    }))
  }))

  return new Table({
    rows,
    width: { size: 100, type: WidthType.PERCENTAGE }
  })
}

async function blockToDocx(node, renderer, context = {}) {
  if (!node) return []

  if (node.type === 'heading') {
    return [paragraphFromInline(node.children, renderer, {
      heading: headingLevel(node.depth),
      run: { bold: true, size: headingSize(node.depth), font: BODY_FONT },
      spacing: { before: 220, after: 140 }
    })]
  }

  if (node.type === 'paragraph') {
    return [paragraphFromInline(node.children, renderer, { indent: context.indent })]
  }

  if (node.type === 'code') {
    if (String(node.lang || '').toLowerCase() === 'mermaid') {
      return [await renderMermaidBlock(node)]
    }
    return [codeParagraph(node.value)]
  }

  if (node.type === 'blockquote') {
    const result = []
    for (const child of node.children || []) {
      result.push(...await blockToDocx(child, renderer, { indent: { left: 360 } }))
    }
    return result
  }

  if (node.type === 'list') {
    const result = []
    const ordered = Boolean(node.ordered)
    let index = node.start || 1
    for (const item of node.children || []) {
      const itemChildren = item.children || []
      const first = itemChildren[0]
      const prefix = ordered ? `${index}. ` : '• '
      if (first?.type === 'paragraph') {
        result.push(paragraphFromInline([
          { type: 'text', value: prefix },
          ...(first.children || [])
        ], renderer, { indent: { left: 360 } }))
        for (const rest of itemChildren.slice(1)) {
          result.push(...await blockToDocx(rest, renderer, { indent: { left: 520 } }))
        }
      } else {
        result.push(new Paragraph({ children: [textRun(prefix)] }))
      }
      index += 1
    }
    return result
  }

  if (node.type === 'table') {
    return [tableFromNode(node, renderer), new Paragraph({ text: '', spacing: { after: 120 } })]
  }

  if (node.type === 'thematicBreak') {
    return [new Paragraph({
      children: [textRun('')],
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: 'D0D7DE' } },
      spacing: { before: 160, after: 160 }
    })]
  }

  return collectPlainText(node).trim()
    ? [new Paragraph({ children: [textRun(collectPlainText(node))] })]
    : []
}

async function renderFootnotes(renderer) {
  if (!renderer.footnoteOrder.length) return []

  const result = [
    new Paragraph({
      children: [textRun('脚注', { bold: true, size: 28 })],
      spacing: { before: 320, after: 120 },
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: 'D0D7DE' } }
    })
  ]

  for (const key of renderer.footnoteOrder) {
    const definition = renderer.footnoteDefinitions.get(key)
    const number = renderer.footnoteNumbers.get(key)
    const children = definition?.children || []
    const first = children[0]
    if (first?.type === 'paragraph') {
      result.push(paragraphFromInline([
        { type: 'text', value: `${number}. ` },
        ...(first.children || [])
      ], renderer, {
        run: { size: FOOTNOTE_SIZE, color: '57606A' },
        spacing: { after: 80 },
        indent: { left: 260 }
      }))
    } else {
      result.push(new Paragraph({
        children: [textRun(`${number}. ${collectPlainText(definition)}`, { size: FOOTNOTE_SIZE, color: '57606A' })],
        spacing: { after: 80 },
        indent: { left: 260 }
      }))
    }
  }

  return result
}

export async function exportMarkdownAsDocx({ title, markdown }) {
  const tree = unified().use(remarkParse).use(remarkGfm).parse(markdown || '')
  const { definitions, content } = collectFootnotes(tree)
  const renderer = createInlineRenderer(definitions)
  const children = []

  if (title) {
    children.push(new Paragraph({
      children: [textRun(title, { bold: true, size: 42 })],
      heading: HeadingLevel.TITLE,
      spacing: { after: 260 }
    }))
  }

  for (const node of content) {
    children.push(...await blockToDocx(node, renderer))
  }
  children.push(...await renderFootnotes(renderer))

  const doc = new Document({
    title: title || 'Starlight Note',
    creator: 'Starlight',
    styles: {
      default: {
        document: {
          run: { font: BODY_FONT, size: BODY_SIZE },
          paragraph: { spacing: { line: 330, after: 160 } }
        }
      }
    },
    sections: [{
      properties: {
        page: {
          margin: { top: 1134, right: 1134, bottom: 1134, left: 1134 }
        }
      },
      children: children.length ? children : [new Paragraph('')]
    }]
  })

  const blob = await Packer.toBlob(doc)
  const fileName = `${safeFileName(title)}.docx`
  triggerDownload(blob, fileName)
  return { blob, fileName }
}
