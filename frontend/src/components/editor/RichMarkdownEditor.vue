<template>
  <div class="rich-md-editor" :class="{ 'rich-md-editor--source': sourceModeInternal }">
    <div class="rich-md-editor__toolbar" role="toolbar" aria-label="Markdown 编辑工具栏">
      <div class="rich-md-editor__toolbar-group">
        <button class="rich-md-editor__tool" type="button" title="撤销" :disabled="!canUndo" @click="runCommand('undo')">
          <Undo2 :size="16" />
        </button>
        <button class="rich-md-editor__tool" type="button" title="重做" :disabled="!canRedo" @click="runCommand('redo')">
          <Redo2 :size="16" />
        </button>
      </div>

      <div class="rich-md-editor__toolbar-group rich-md-editor__toolbar-group--select">
        <select class="rich-md-editor__select" :value="headingValue" title="段落样式" @change="handleHeadingChange">
          <option value="paragraph">正文</option>
          <option value="1">标题 1</option>
          <option value="2">标题 2</option>
          <option value="3">标题 3</option>
          <option value="4">标题 4</option>
          <option value="5">标题 5</option>
          <option value="6">标题 6</option>
        </select>
      </div>

      <div class="rich-md-editor__toolbar-group">
        <button :class="toolClass('bold')" type="button" title="粗体" @click="toggleMark('bold')">
          <Bold :size="16" />
        </button>
        <button :class="toolClass('italic')" type="button" title="斜体" @click="toggleMark('italic')">
          <Italic :size="16" />
        </button>
        <button :class="toolClass('strike')" type="button" title="删除线" @click="toggleMark('strike')">
          <Strikethrough :size="16" />
        </button>
        <button :class="toolClass('code')" type="button" title="行内代码" @click="toggleMark('code')">
          <Code2 :size="16" />
        </button>
        <button :class="toolClass('highlight')" type="button" title="高亮" @click="toggleMark('highlight')">
          <Highlighter :size="16" />
        </button>
      </div>

      <div class="rich-md-editor__toolbar-group">
        <button :class="toolClass('bulletList')" type="button" title="无序列表" @click="toggleNode('bulletList')">
          <List :size="16" />
        </button>
        <button :class="toolClass('orderedList')" type="button" title="有序列表" @click="toggleNode('orderedList')">
          <ListOrdered :size="16" />
        </button>
        <button :class="toolClass('taskList')" type="button" title="任务列表" @click="toggleNode('taskList')">
          <ListTodo :size="16" />
        </button>
        <button :class="toolClass('blockquote')" type="button" title="引用" @click="toggleNode('blockquote')">
          <Quote :size="16" />
        </button>
      </div>

      <div class="rich-md-editor__toolbar-group">
        <button class="rich-md-editor__tool" type="button" title="链接" @click="setLink">
          <LinkIcon :size="16" />
        </button>
        <button class="rich-md-editor__tool" type="button" title="移除链接" :disabled="!isActive('link')" @click="unsetLink">
          <Unlink :size="16" />
        </button>
        <div class="rich-md-editor__split-tool">
          <button class="rich-md-editor__tool" type="button" title="上传图片" :disabled="uploadingImage" @click="openImageUpload">
            <ImageUp :size="16" />
          </button>
          <button class="rich-md-editor__tool" type="button" title="插入图片链接" @click="insertImageUrl">
            <LinkIcon :size="16" />
          </button>
        </div>
        <button class="rich-md-editor__tool" type="button" title="分割线" @click="insertHorizontalRule">
          <Minus :size="16" />
        </button>
      </div>

      <div class="rich-md-editor__toolbar-group">
        <button :class="toolClass('codeBlock')" type="button" title="代码块" @click="insertCodeBlock('javascript')">
          <FileCode2 :size="16" />
        </button>
        <button class="rich-md-editor__tool" type="button" title="Mermaid 图表" @click="insertMermaidBlock">
          <Braces :size="16" />
        </button>
        <button class="rich-md-editor__tool" type="button" title="表格" @click="insertTable">
          <Table2 :size="16" />
        </button>
      </div>

      <div class="rich-md-editor__toolbar-group">
        <button :class="toolClass({ textAlign: 'left' })" type="button" title="左对齐" @click="setTextAlign('left')">
          <AlignLeft :size="16" />
        </button>
        <button :class="toolClass({ textAlign: 'center' })" type="button" title="居中" @click="setTextAlign('center')">
          <AlignCenter :size="16" />
        </button>
        <button :class="toolClass({ textAlign: 'right' })" type="button" title="右对齐" @click="setTextAlign('right')">
          <AlignRight :size="16" />
        </button>
      </div>

      <div class="rich-md-editor__toolbar-group rich-md-editor__toolbar-group--mode">
        <button
          class="rich-md-editor__mode"
          type="button"
          :class="{ active: !sourceModeInternal }"
          :disabled="Boolean(unsupportedFeatures.length)"
          :title="unsupportedFeatures.length ? `包含暂不支持的语法：${unsupportedFeatures.join('、')}` : '富文本模式'"
          @click="setSourceMode(false)"
        >
          <Pencil :size="15" />
          富文本
        </button>
        <button
          class="rich-md-editor__mode"
          type="button"
          :class="{ active: sourceModeInternal }"
          title="源码模式"
          @click="setSourceMode(true)"
        >
          <FileCode2 :size="15" />
          源码
        </button>
      </div>
    </div>

    <div v-if="unsupportedFeatures.length" class="rich-md-editor__notice">
      当前笔记包含 {{ unsupportedFeatures.join('、') }}，已切换到源码模式以避免 Markdown 往返时丢失内容。
    </div>

    <div v-if="!sourceModeInternal && isActive('codeBlock')" class="rich-md-editor__contextbar">
      <span class="rich-md-editor__contextbar-label">代码块</span>
      <select class="rich-md-editor__select rich-md-editor__select--compact" :value="currentCodeLanguage" @change="handleCodeLanguageChange">
        <option v-for="lang in codeLanguages" :key="lang || 'plain'" :value="lang">{{ lang || 'plain text' }}</option>
      </select>
      <button class="rich-md-editor__mini" type="button" @click="clearCodeBlock">转为正文</button>
    </div>

    <div v-if="!sourceModeInternal && isActive('table')" class="rich-md-editor__contextbar">
      <span class="rich-md-editor__contextbar-label">表格</span>
      <button class="rich-md-editor__mini" type="button" @click="tableCommand('addColumnBefore')">左侧列</button>
      <button class="rich-md-editor__mini" type="button" @click="tableCommand('addColumnAfter')">右侧列</button>
      <button class="rich-md-editor__mini" type="button" @click="tableCommand('deleteColumn')">删列</button>
      <button class="rich-md-editor__mini" type="button" @click="tableCommand('addRowBefore')">上方行</button>
      <button class="rich-md-editor__mini" type="button" @click="tableCommand('addRowAfter')">下方行</button>
      <button class="rich-md-editor__mini" type="button" @click="tableCommand('deleteRow')">删行</button>
      <button class="rich-md-editor__mini rich-md-editor__mini--danger" type="button" @click="tableCommand('deleteTable')">删表</button>
    </div>

    <input
      ref="imageInput"
      class="rich-md-editor__file-input"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif,image/avif"
      @change="handleImageInputChange"
    />

    <textarea
      v-if="sourceModeInternal"
      ref="sourceTextarea"
      v-model="sourceMarkdown"
      class="rich-md-editor__source"
      spellcheck="false"
      placeholder="# 从这里开始记录你的星光..."
      @paste="handlePaste"
      @drop="handleDrop"
      @dragover.prevent
      @input="handleSourceInput"
      @scroll="handleSourceScroll"
    ></textarea>

    <div
      v-else
      ref="editorScrollHost"
      class="rich-md-editor__surface"
      @scroll="handleRichScroll"
      @paste="handlePaste"
      @drop="handleDrop"
      @dragover.prevent
    >
      <EditorContent v-if="editor" :editor="editor" class="rich-md-editor__content" />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { assetApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import { Editor, EditorContent, VueNodeViewRenderer } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Markdown } from '@tiptap/markdown'
import { CodeBlockLowlight } from '@tiptap/extension-code-block-lowlight'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import CharacterCount from '@tiptap/extension-character-count'
import Typography from '@tiptap/extension-typography'
import Underline from '@tiptap/extension-underline'
import TextAlign from '@tiptap/extension-text-align'
import Highlight from '@tiptap/extension-highlight'
import { common, createLowlight } from 'lowlight'
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Bold,
  Braces,
  Code2,
  FileCode2,
  Highlighter,
  Image as ImageIcon,
  ImageUp,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  ListTodo,
  Minus,
  Pencil,
  Quote,
  Redo2,
  Strikethrough,
  Table2,
  Undo2,
  Unlink
} from '@lucide/vue'
import { createSlugTracker, getUniqueSlug } from '@/utils/markdown'
import {
  COMMON_CODE_LANGUAGES,
  getRichEditorUnsupportedFeatures,
  normalizeEditorMarkdown,
  shouldPreferSourceMode
} from '@/utils/editorMarkdown'
import CodeBlockNodeView from './CodeBlockNodeView.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  noteId: { type: String, default: '' }
})

const emit = defineEmits([
  'update:modelValue',
  'active-anchor-change',
  'source-mode-change'
])

const lowlight = createLowlight(common)
const editor = shallowRef(null)
const editorRevision = ref(0)
const sourceModeInternal = ref(shouldPreferSourceMode(props.modelValue))
const sourceMarkdown = ref(normalizeEditorMarkdown(props.modelValue))
const sourceTextarea = ref(null)
const editorScrollHost = ref(null)
const imageInput = ref(null)
const toast = useToastStore()
const assetSettings = ref(null)
const uploadingImage = ref(false)
let applyingExternalContent = false

const codeLanguages = COMMON_CODE_LANGUAGES
const unsupportedFeatures = computed(() => getRichEditorUnsupportedFeatures(sourceMarkdown.value || props.modelValue))
const uploadEnabled = computed(() => Boolean(assetSettings.value?.uploadEnabled))

const extensions = [
  StarterKit.configure({
    codeBlock: false,
    link: false,
    heading: {
      levels: [1, 2, 3, 4, 5, 6]
    }
  }),
  Markdown.configure({
    indentation: { style: 'space', size: 2 },
    markedOptions: {
      gfm: true,
      breaks: true
    }
  }),
  CodeBlockLowlight.extend({
    addNodeView() {
      return VueNodeViewRenderer(CodeBlockNodeView)
    }
  }).configure({
    lowlight,
    enableTabIndentation: true,
    tabSize: 2
  }),
  Link.configure({
    openOnClick: false,
    autolink: true,
    linkOnPaste: true
  }),
  Image.configure({
    allowBase64: true,
    inline: false
  }),
  Table.configure({
    resizable: true,
    lastColumnResizable: false
  }),
  TableRow,
  TableHeader,
  TableCell,
  TaskList,
  TaskItem.configure({
    nested: true
  }),
  Placeholder.configure({
    placeholder: '# 从这里开始记录你的星光...'
  }),
  CharacterCount,
  Typography,
  Underline,
  TextAlign.configure({
    types: ['heading', 'paragraph']
  }),
  Highlight.configure({
    multicolor: false
  })
]

editor.value = new Editor({
  extensions,
  content: normalizeEditorMarkdown(props.modelValue),
  contentType: 'markdown',
  editorProps: {
    attributes: {
      class: 'rich-md-editor__prosemirror markdown-body',
      spellcheck: 'false'
    },
    handlePaste(view, event) {
      return handleEditorPasteOrDrop(event)
    },
    handleDrop(view, event) {
      return handleEditorPasteOrDrop(event)
    }
  },
  onUpdate: ({ editor: currentEditor }) => {
    bumpEditorRevision()
    if (applyingExternalContent || sourceModeInternal.value) return
    emitMarkdown(currentEditor.getMarkdown())
  },
  onSelectionUpdate: () => {
    bumpEditorRevision()
    emitActiveHeading()
  },
  onTransaction: () => {
    bumpEditorRevision()
  },
  onFocus: () => emitActiveHeading()
})

function bumpEditorRevision() {
  editorRevision.value += 1
}

function emitMarkdown(value) {
  const normalized = normalizeEditorMarkdown(value)
  sourceMarkdown.value = normalized
  emit('update:modelValue', normalized)
}

function getCurrentMarkdown() {
  if (sourceModeInternal.value) {
    return normalizeEditorMarkdown(sourceMarkdown.value)
  }
  return normalizeEditorMarkdown(editor.value?.getMarkdown?.() || '')
}

function setEditorMarkdown(markdown) {
  if (!editor.value) return
  applyingExternalContent = true
  editor.value.commands.setContent(normalizeEditorMarkdown(markdown), {
    contentType: 'markdown',
    emitUpdate: false
  })
  applyingExternalContent = false
  bumpEditorRevision()
}

watch(() => props.modelValue, value => {
  const normalized = normalizeEditorMarkdown(value)
  if (normalized === getCurrentMarkdown()) return
  sourceMarkdown.value = normalized
  if (shouldPreferSourceMode(normalized)) {
    sourceModeInternal.value = true
    emit('source-mode-change', true)
    return
  }
  if (!sourceModeInternal.value) {
    setEditorMarkdown(normalized)
  }
})

watch(sourceModeInternal, value => {
  emit('source-mode-change', value)
  nextTick(() => {
    if (value) {
      sourceTextarea.value?.focus()
    } else {
      editor.value?.commands.focus('end')
    }
  })
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})

loadAssetSettings()

const canUndo = computed(() => {
  editorRevision.value
  return Boolean(editor.value?.can().undo())
})

const canRedo = computed(() => {
  editorRevision.value
  return Boolean(editor.value?.can().redo())
})

const headingValue = computed(() => {
  editorRevision.value
  if (!editor.value) return 'paragraph'
  for (const level of [1, 2, 3, 4, 5, 6]) {
    if (editor.value.isActive('heading', { level })) {
      return String(level)
    }
  }
  return 'paragraph'
})

const currentCodeLanguage = computed(() => {
  editorRevision.value
  return editor.value?.getAttributes('codeBlock')?.language || ''
})

function isActive(nameOrAttrs, attrs = undefined) {
  editorRevision.value
  if (!editor.value || sourceModeInternal.value) return false
  if (typeof nameOrAttrs === 'string') {
    return editor.value.isActive(nameOrAttrs, attrs)
  }
  return editor.value.isActive(nameOrAttrs)
}

function toolClass(nameOrAttrs, attrs = undefined) {
  return ['rich-md-editor__tool', { active: isActive(nameOrAttrs, attrs) }]
}

function runCommand(command) {
  const currentEditor = editor.value
  if (!currentEditor || sourceModeInternal.value) return
  currentEditor.chain().focus()[command]().run()
}

function toggleMark(mark) {
  const currentEditor = editor.value
  if (!currentEditor || sourceModeInternal.value) return
  currentEditor.chain().focus()[`toggle${capitalize(mark)}`]().run()
}

function toggleNode(node) {
  const currentEditor = editor.value
  if (!currentEditor || sourceModeInternal.value) return
  currentEditor.chain().focus()[`toggle${capitalize(node)}`]().run()
}

function capitalize(value) {
  return String(value || '').charAt(0).toUpperCase() + String(value || '').slice(1)
}

function handleHeadingChange(event) {
  const value = event.target.value
  const chain = editor.value?.chain().focus()
  if (!chain) return
  if (value === 'paragraph') {
    chain.setParagraph().run()
    return
  }
  chain.toggleHeading({ level: Number(value) }).run()
}

function setTextAlign(align) {
  editor.value?.chain().focus().setTextAlign(align).run()
}

function setLink() {
  const currentEditor = editor.value
  if (!currentEditor || sourceModeInternal.value) return
  const previousUrl = currentEditor.getAttributes('link').href || ''
  const url = window.prompt('链接地址', previousUrl)
  if (url === null) return
  if (!url.trim()) {
    currentEditor.chain().focus().unsetLink().run()
    return
  }
  currentEditor.chain().focus().extendMarkRange('link').setLink({ href: url.trim() }).run()
}

function unsetLink() {
  editor.value?.chain().focus().unsetLink().run()
}

function insertImageUrl() {
  const src = window.prompt('图片地址')
  if (!src?.trim()) return
  const alt = window.prompt('图片说明（可选）') || ''
  insertImageMarkdownOrNode({ src: src.trim(), alt })
}

async function loadAssetSettings() {
  try {
    assetSettings.value = await assetApi.settings()
  } catch {
    assetSettings.value = { uploadEnabled: false }
  }
}

function openImageUpload() {
  if (!uploadEnabled.value) {
    insertImageUrl()
    return
  }
  imageInput.value?.click()
}

async function handleImageInputChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) {
    await uploadImageFile(file)
  }
}

function handlePaste(event) {
  handleEditorPasteOrDrop(event)
}

function handleDrop(event) {
  handleEditorPasteOrDrop(event)
}

function handleEditorPasteOrDrop(event) {
  if (event?.defaultPrevented) return false
  const file = firstImageFile(event?.clipboardData || event?.dataTransfer)
  if (!file) return false
  if (!uploadEnabled.value) {
    event.preventDefault()
    toast.info('图片上传未开启，可通过图片链接插入')
    return true
  }
  event.preventDefault()
  uploadImageFile(file)
  return true
}

function firstImageFile(dataTransfer) {
  const files = Array.from(dataTransfer?.files || [])
  return files.find(file => file?.type?.startsWith('image/')) || null
}

async function uploadImageFile(file) {
  if (!uploadEnabled.value) {
    toast.info('图片上传未开启，可通过图片链接插入')
    return
  }
  const maxFileSize = Number(assetSettings.value?.maxFileSize || 0)
  if (maxFileSize > 0 && file.size > maxFileSize) {
    toast.error(`图片超过单文件上限：${formatBytes(maxFileSize)}`)
    return
  }
  uploadingImage.value = true
  try {
    const result = await assetApi.uploadImage(file, { noteId: props.noteId })
    insertImageMarkdownOrNode({
      src: result.url,
      alt: result.originalFilename || file.name || 'image',
      markdown: result.markdown
    })
    toast.success('图片已上传')
  } catch (err) {
    toast.error(err.message || '图片上传失败')
  } finally {
    uploadingImage.value = false
  }
}

function insertImageMarkdownOrNode({ src, alt = '', markdown = '' }) {
  if (!src) return
  if (sourceModeInternal.value) {
    insertSourceText(markdown || `![${escapeMarkdownLabel(alt)}](${src})`)
    return
  }
  editor.value?.chain().focus().setImage({ src, alt }).run()
}

function insertSourceText(text) {
  const textarea = sourceTextarea.value
  const current = sourceMarkdown.value || ''
  if (!textarea) {
    sourceMarkdown.value = `${current}${current && !current.endsWith('\n') ? '\n' : ''}${text}`
    emitMarkdown(sourceMarkdown.value)
    return
  }
  const start = textarea.selectionStart ?? current.length
  const end = textarea.selectionEnd ?? start
  const next = current.slice(0, start) + text + current.slice(end)
  sourceMarkdown.value = next
  emitMarkdown(next)
  nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(start + text.length, start + text.length)
  })
}

function escapeMarkdownLabel(value) {
  return String(value || 'image').replace(/[[\]\\]/g, '\\$&')
}

function formatBytes(bytes) {
  const value = Number(bytes || 0)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MiB`
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GiB`
}

function insertHorizontalRule() {
  editor.value?.chain().focus().setHorizontalRule().run()
}

function insertCodeBlock(language = '') {
  const currentEditor = editor.value
  if (!currentEditor || sourceModeInternal.value) return
  const chain = currentEditor.chain().focus()
  if (currentEditor.isActive('codeBlock')) {
    chain.updateAttributes('codeBlock', { language }).run()
    return
  }
  chain.setCodeBlock({ language }).run()
}

function insertMermaidBlock() {
  const currentEditor = editor.value
  if (!currentEditor || sourceModeInternal.value) return
  currentEditor.chain().focus().insertContent({
    type: 'codeBlock',
    attrs: { language: 'mermaid' },
    content: [{ type: 'text', text: 'graph TD\n  A --> B' }]
  }).run()
}

function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

function clearCodeBlock() {
  editor.value?.chain().focus().clearNodes().run()
}

function handleCodeLanguageChange(event) {
  editor.value?.chain().focus().updateAttributes('codeBlock', { language: event.target.value || null }).run()
}

function tableCommand(command) {
  editor.value?.chain().focus()[command]().run()
}

function handleSourceInput() {
  emitMarkdown(sourceMarkdown.value)
  emitActiveHeadingByMarkdown(sourceMarkdown.value, sourceTextarea.value?.scrollTop || 0)
}

function handleSourceScroll() {
  emitActiveHeadingByMarkdown(sourceMarkdown.value, sourceTextarea.value?.scrollTop || 0)
}

function setSourceMode(value) {
  if (!value && unsupportedFeatures.value.length) return
  if (value) {
    sourceMarkdown.value = getCurrentMarkdown()
    sourceModeInternal.value = true
    return
  }
  setEditorMarkdown(sourceMarkdown.value)
  sourceModeInternal.value = false
  emitMarkdown(editor.value?.getMarkdown?.() || sourceMarkdown.value)
}

function handleRichScroll() {
  emitActiveHeading()
}

function emitActiveHeading() {
  if (sourceModeInternal.value) return
  const host = editorScrollHost.value
  if (!host) return
  const headings = Array.from(host.querySelectorAll('.ProseMirror h1, .ProseMirror h2, .ProseMirror h3, .ProseMirror h4, .ProseMirror h5, .ProseMirror h6'))
  if (!headings.length) {
    emit('active-anchor-change', '')
    return
  }

  const tracker = createSlugTracker()
  let activeAnchor = ''
  const threshold = host.getBoundingClientRect().top + 72
  for (const heading of headings) {
    const anchor = getUniqueSlug(heading.textContent || 'section', tracker)
    heading.dataset.outlineAnchor = anchor
    if (heading.getBoundingClientRect().top <= threshold) {
      activeAnchor = anchor
      continue
    }
    break
  }
  emit('active-anchor-change', activeAnchor || headings[0]?.dataset.outlineAnchor || '')
}

function emitActiveHeadingByMarkdown(markdown, scrollTop) {
  const lineHeight = sourceTextarea.value
    ? Number.parseFloat(window.getComputedStyle(sourceTextarea.value).lineHeight) || 22
    : 22
  const currentLine = Math.max(1, Math.floor(scrollTop / lineHeight) + 1)
  const tracker = createSlugTracker()
  let activeAnchor = ''
  String(markdown || '').split(/\r?\n/).forEach((line, index) => {
    const match = line.match(/^(#{1,6})\s+(.+)$/)
    if (!match) return
    const anchor = getUniqueSlug(match[2].trim(), tracker)
    if (index + 1 <= currentLine) {
      activeAnchor = anchor
    }
  })
  emit('active-anchor-change', activeAnchor)
}

async function scrollToHeading(anchor, { behavior = 'smooth' } = {}) {
  if (!anchor) return false
  if (sourceModeInternal.value) {
    return scrollSourceToHeading(anchor, { behavior })
  }
  await nextTick()
  const host = editorScrollHost.value
  if (!host) return false
  emitActiveHeading()
  const target = host.querySelector(`[data-outline-anchor="${cssEscape(anchor)}"]`)
  if (!target) return false
  const targetTop = target.getBoundingClientRect().top - host.getBoundingClientRect().top + host.scrollTop - 16
  host.scrollTo({ top: Math.max(targetTop, 0), behavior })
  editor.value?.commands.focus()
  return true
}

function scrollSourceToHeading(anchor, { behavior = 'smooth' } = {}) {
  const textarea = sourceTextarea.value
  if (!textarea) return false
  const tracker = createSlugTracker()
  const lines = sourceMarkdown.value.split(/\r?\n/)
  const index = lines.findIndex(line => {
    const match = line.match(/^(#{1,6})\s+(.+)$/)
    return match ? getUniqueSlug(match[2].trim(), tracker) === anchor : false
  })
  if (index < 0) return false
  const lineHeight = Number.parseFloat(window.getComputedStyle(textarea).lineHeight) || 22
  textarea.scrollTo({ top: Math.max(0, index * lineHeight - 16), behavior })
  const offset = lines.slice(0, index).join('\n').length + (index ? 1 : 0)
  textarea.setSelectionRange(offset, offset)
  textarea.focus()
  return true
}

function cssEscape(value) {
  if (window.CSS?.escape) return window.CSS.escape(value)
  return String(value || '').replace(/[^a-zA-Z0-9_-]/g, match => `\\${match}`)
}

function focus() {
  if (sourceModeInternal.value) {
    sourceTextarea.value?.focus()
    return
  }
  editor.value?.commands.focus()
}

defineExpose({
  getMarkdown: getCurrentMarkdown,
  focus,
  scrollToHeading,
  getEditor: () => editor.value
})
</script>

<style scoped>
.rich-md-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--sl-card);
}

.rich-md-editor__toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--sl-border);
  background: color-mix(in srgb, var(--sl-card) 92%, var(--sl-bg-secondary));
}

.rich-md-editor__toolbar-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-right: 8px;
  border-right: 1px solid var(--sl-border);
}

.rich-md-editor__toolbar-group:last-child {
  border-right: none;
  padding-right: 0;
}

.rich-md-editor__toolbar-group--mode {
  margin-left: auto;
}

.rich-md-editor__tool,
.rich-md-editor__mode,
.rich-md-editor__mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 32px;
  height: 32px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--sl-text-secondary);
  background: transparent;
  cursor: pointer;
}

.rich-md-editor__split-tool {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.rich-md-editor__file-input {
  display: none;
}

.rich-md-editor__tool:hover,
.rich-md-editor__mode:hover,
.rich-md-editor__mini:hover {
  color: var(--sl-text);
  background: var(--sl-hover-bg);
  border-color: var(--sl-border);
}

.rich-md-editor__tool.active,
.rich-md-editor__mode.active {
  color: var(--sl-primary);
  background: var(--sl-primary-light);
  border-color: color-mix(in srgb, var(--sl-primary) 35%, var(--sl-border));
}

.rich-md-editor__tool:disabled,
.rich-md-editor__mode:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rich-md-editor__mode {
  min-width: auto;
  padding: 0 10px;
  font-size: 13px;
}

.rich-md-editor__select {
  height: 32px;
  min-width: 112px;
  padding: 0 28px 0 10px;
  border: 1px solid var(--sl-border);
  border-radius: 6px;
  color: var(--sl-text);
  background: var(--sl-card);
  font: inherit;
  font-size: 13px;
}

.rich-md-editor__select--compact {
  min-width: 132px;
}

.rich-md-editor__notice {
  margin: 10px 20px 0;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--sl-warning) 36%, var(--sl-border));
  border-radius: 8px;
  color: var(--sl-text);
  background: color-mix(in srgb, var(--sl-warning) 10%, var(--sl-card));
  font-size: 13px;
}

.rich-md-editor__contextbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 20px;
  border-bottom: 1px solid var(--sl-border);
  background: var(--sl-bg-secondary);
}

.rich-md-editor__contextbar-label {
  color: var(--sl-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.rich-md-editor__mini {
  min-width: auto;
  height: 28px;
  padding: 0 8px;
  border-color: var(--sl-border);
  background: var(--sl-card);
  font-size: 12px;
}

.rich-md-editor__mini--danger {
  color: var(--sl-danger);
}

.rich-md-editor__surface {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: var(--sl-bg);
}

.rich-md-editor__content {
  min-height: 100%;
}

.rich-md-editor__source {
  flex: 1;
  width: 100%;
  min-height: 0;
  resize: none;
  border: none;
  outline: none;
  padding: 16px 20px 28px;
  color: var(--sl-text);
  background: var(--sl-bg);
  font-family: var(--sl-font-mono);
  font-size: 15px;
  line-height: 1.65;
}

:deep(.rich-md-editor__prosemirror) {
  min-height: 100%;
  padding: 20px 24px 56px;
  outline: none;
  color: var(--sl-text);
}

:deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  color: var(--sl-text-tertiary);
  pointer-events: none;
}

:deep(.ProseMirror-selectednode) {
  outline: 2px solid color-mix(in srgb, var(--sl-primary) 55%, transparent);
  outline-offset: 2px;
}

:deep(.ProseMirror pre) {
  position: relative;
  margin: 14px 0;
  padding: 14px 16px;
  border: 1px solid var(--sl-code-border, var(--sl-border));
  border-radius: var(--sl-radius);
  color: var(--sl-code-text);
  background: var(--sl-code-bg);
  overflow-x: auto;
}

:deep(.ProseMirror pre code) {
  font-family: var(--sl-font-mono), monospace;
  font-size: 0.92em;
  white-space: pre;
}

:deep(.ProseMirror .rich-code-node) {
  position: relative;
  margin: 14px 0;
  border: 1px solid var(--sl-code-border, var(--sl-border));
  border-radius: var(--sl-radius);
  color: var(--sl-code-text);
  background: var(--sl-code-bg);
}

:deep(.ProseMirror .rich-code-node.ProseMirror-selectednode) {
  outline: 2px solid color-mix(in srgb, var(--sl-primary) 55%, transparent);
  outline-offset: 2px;
}

:deep(.ProseMirror .rich-code-node__language) {
  position: absolute;
  top: 8px;
  right: 10px;
  z-index: 2;
  max-width: 140px;
  padding: 2px 7px;
  border: 1px solid color-mix(in srgb, var(--sl-code-border, var(--sl-border)) 70%, transparent);
  border-radius: 999px;
  color: var(--sl-text-secondary);
  background: color-mix(in srgb, var(--sl-code-bg) 88%, var(--sl-card));
  font-family: var(--sl-font);
  font-size: 11px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
}

:deep(.ProseMirror .rich-code-node__floating) {
  position: absolute;
  top: 8px;
  left: 10px;
  z-index: 3;
  display: inline-flex;
}

:deep(.ProseMirror .rich-code-node__toggle) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--sl-primary) 38%, var(--sl-border));
  border-radius: 6px;
  color: var(--sl-primary);
  background: color-mix(in srgb, var(--sl-card) 92%, var(--sl-primary-light));
  box-shadow: var(--sl-shadow-card);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

:deep(.ProseMirror .rich-code-node__toggle:hover) {
  background: var(--sl-primary-light);
}

:deep(.ProseMirror pre.rich-code-node__source) {
  margin: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  padding: 38px 16px 14px;
}

:deep(.ProseMirror .rich-code-node:not(.rich-code-node--mermaid) pre.rich-code-node__source) {
  padding-top: 14px;
}

:deep(.ProseMirror .rich-code-node__preview) {
  min-height: 180px;
  padding: 48px 18px 18px;
  overflow: auto;
}

:deep(.ProseMirror .rich-code-node__diagram) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: max-content;
}

:deep(.ProseMirror .rich-code-node__diagram svg) {
  max-width: 100%;
  height: auto;
}

:deep(.ProseMirror .rich-code-node__error),
:deep(.ProseMirror .rich-code-node__placeholder) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 110px;
  border: 1px dashed var(--sl-border);
  border-radius: var(--sl-radius);
  color: var(--sl-text-secondary);
  background: color-mix(in srgb, var(--sl-card) 70%, transparent);
  font-family: var(--sl-font);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

:deep(.ProseMirror .rich-code-node__error) {
  color: var(--sl-danger);
  white-space: pre-wrap;
}

:deep(.ProseMirror table) {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  margin: 14px 0;
}

:deep(.ProseMirror th),
:deep(.ProseMirror td) {
  min-width: 1em;
  padding: 8px 10px;
  border: 1px solid var(--sl-border);
  vertical-align: top;
}

:deep(.ProseMirror th) {
  background: var(--sl-hover-bg);
  font-weight: 700;
}

:deep(.ProseMirror .selectedCell::after) {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 2;
  background: color-mix(in srgb, var(--sl-primary) 16%, transparent);
  pointer-events: none;
}

:deep(.ProseMirror ul[data-type="taskList"]) {
  list-style: none;
  padding-left: 0;
}

:deep(.ProseMirror ul[data-type="taskList"] li) {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

:deep(.ProseMirror ul[data-type="taskList"] li > label) {
  flex: 0 0 auto;
  margin-top: 2px;
}

:deep(.ProseMirror ul[data-type="taskList"] li > div) {
  flex: 1;
}

:deep(.ProseMirror mark) {
  padding: 0 2px;
  border-radius: 3px;
  color: inherit;
  background: var(--sl-mark-bg);
}

:deep(.ProseMirror img) {
  max-width: 100%;
  border-radius: var(--sl-radius);
}

:deep(.hljs-comment),
:deep(.hljs-quote) {
  color: var(--sl-code-comment);
  font-style: italic;
}

:deep(.hljs-keyword),
:deep(.hljs-selector-tag),
:deep(.hljs-doctag),
:deep(.hljs-meta-keyword),
:deep(.hljs-tag) {
  color: var(--sl-code-keyword);
}

:deep(.hljs-title),
:deep(.hljs-title.class_),
:deep(.hljs-title.function_),
:deep(.hljs-section),
:deep(.hljs-name),
:deep(.hljs-built_in),
:deep(.hljs-type),
:deep(.hljs-literal) {
  color: var(--sl-code-title);
}

:deep(.hljs-string),
:deep(.hljs-regexp),
:deep(.hljs-addition),
:deep(.hljs-template-tag),
:deep(.hljs-template-variable) {
  color: var(--sl-code-string);
}

:deep(.hljs-number),
:deep(.hljs-symbol),
:deep(.hljs-bullet),
:deep(.hljs-deletion) {
  color: var(--sl-code-number);
}

@media (max-width: 768px) {
  .rich-md-editor__toolbar {
    padding: 8px 12px;
  }

  .rich-md-editor__toolbar-group--mode {
    margin-left: 0;
  }

  :deep(.rich-md-editor__prosemirror),
  .rich-md-editor__source {
    padding: 16px;
  }

  :deep(.ProseMirror .rich-code-node__floating) {
    position: sticky;
    top: 8px;
    margin: 8px 0 -36px 10px;
  }

  :deep(.ProseMirror pre.rich-code-node__source) {
    padding: 42px 12px 14px;
  }

  :deep(.ProseMirror .rich-code-node__preview) {
    padding: 46px 12px 14px;
  }
}
</style>
