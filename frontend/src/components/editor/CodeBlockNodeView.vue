<template>
  <NodeViewWrapper
    as="div"
    :class="['rich-code-node', { 'rich-code-node--mermaid': isMermaid, 'rich-code-node--preview': isMermaidPreview, 'rich-code-node--selected': selected }]"
    @click="handleNodeClick"
    @focusin="actionsVisible = true"
    @mouseenter="actionsVisible = true"
    @mouseleave="handleMouseLeave"
  >
    <div v-if="languageLabel" class="rich-code-node__language" contenteditable="false">{{ languageLabel }}</div>
    <div v-if="isMermaid && actionsVisible" class="rich-code-node__floating" contenteditable="false">
      <button class="rich-code-node__toggle" type="button" @mousedown.prevent.stop @click.stop="toggleMermaidMode">
        {{ isMermaidPreview ? '编辑源码' : '预览图表' }}
      </button>
    </div>

    <div v-if="isMermaidPreview" class="rich-code-node__preview" contenteditable="false">
      <div v-if="mermaidError" class="rich-code-node__error">{{ mermaidError }}</div>
      <div v-else-if="!renderedSvg" class="rich-code-node__placeholder">Mermaid 图表渲染中...</div>
      <div v-else ref="diagramRef" class="rich-code-node__diagram" v-html="renderedSvg"></div>
    </div>

    <pre v-show="!isMermaidPreview" class="rich-code-node__source"><NodeViewContent as="code" /></pre>
  </NodeViewWrapper>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { NodeViewContent, NodeViewWrapper } from '@tiptap/vue-3'
import { useThemeStore } from '@/stores/theme'
import { renderMermaidSourceToSvg } from '@/utils/markdownEnhance'

const props = defineProps({
  node: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const actionsVisible = ref(false)
const mermaidMode = ref('source')
const renderedSvg = ref('')
const mermaidError = ref('')
const diagramRef = ref(null)
const themeStore = useThemeStore()
let activeRenderToken = 0
let pendingBindFunctions = null

const language = computed(() => String(props.node.attrs?.language || '').trim().toLowerCase())
const isMermaid = computed(() => language.value === 'mermaid')
const isMermaidPreview = computed(() => isMermaid.value && mermaidMode.value === 'preview')
const languageLabel = computed(() => language.value || '')
const source = computed(() => props.node.textContent || '')

watch([isMermaidPreview, source, () => themeStore.currentId], async () => {
  if (!isMermaidPreview.value) return
  await renderMermaid()
}, { immediate: true })

watch(isMermaid, value => {
  if (!value) {
    mermaidMode.value = 'source'
    renderedSvg.value = ''
    mermaidError.value = ''
    pendingBindFunctions = null
  }
})

async function renderMermaid() {
  const nextSource = source.value.trim()
  renderedSvg.value = ''
  mermaidError.value = ''
  if (!nextSource) {
    mermaidError.value = 'Mermaid 图表内容为空'
    return
  }

  try {
    const renderToken = ++activeRenderToken
    const { svg, bindFunctions } = await renderMermaidSourceToSvg(nextSource, {
      idPrefix: 'starlight-editor-mermaid'
    })
    if (renderToken !== activeRenderToken) return
    renderedSvg.value = svg
    pendingBindFunctions = bindFunctions
    await nextTick()
    if (diagramRef.value) {
      pendingBindFunctions?.(diagramRef.value)
    }
  } catch (error) {
    mermaidError.value = error instanceof Error ? error.message : 'Mermaid 图表渲染失败'
    pendingBindFunctions = null
  }
}

function toggleMermaidMode() {
  if (!isMermaid.value) return
  mermaidMode.value = isMermaidPreview.value ? 'source' : 'preview'
}

function handleNodeClick() {
  if (isMermaid.value) {
    actionsVisible.value = true
  }
}

function handleMouseLeave() {
  if (!props.selected && !isMermaidPreview.value) {
    actionsVisible.value = false
  }
}
</script>
