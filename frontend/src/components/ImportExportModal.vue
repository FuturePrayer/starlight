<template>
  <PopupLayer
    title="导入 / 导出笔记"
    description="将全部笔记导出为纯 Markdown + 文件夹结构 ZIP，或导入同类 ZIP 包重建分类与笔记。"
    eyebrow="Markdown ZIP"
    width="min(620px, calc(100vw - 32px))"
    @close="emit('close')"
  >
    <div class="transfer-layout">
      <section class="transfer-card">
        <div class="transfer-card__header">
          <div>
            <h4 class="transfer-card__title">批量导出</h4>
            <p class="transfer-card__desc">导出当前账号下的全部分类与笔记，分类会保留为文件夹，笔记会保存为 `.md` 文件。</p>
          </div>
          <span class="sl-badge">ZIP</span>
        </div>

        <div v-if="hasUnsavedChanges" class="transfer-alert">
          <strong>提示：</strong> 当前存在未保存内容，导出只会包含已经保存到服务器的版本。
        </div>

        <button class="sl-btn sl-btn--primary transfer-action-btn" :disabled="exporting || importing" @click="handleExport">
          {{ exporting ? '正在打包…' : '导出 ZIP' }}
        </button>
      </section>

      <section class="transfer-card transfer-card--import">
        <div class="transfer-card__header">
          <div>
            <h4 class="transfer-card__title">批量导入</h4>
            <p class="transfer-card__desc">选择 ZIP 文件后，将按文件夹结构创建分类，并把其中的 Markdown 文件导入为笔记。</p>
          </div>
          <span class="sl-badge">导入</span>
        </div>

        <input
          ref="fileInputRef"
          class="transfer-file-input"
          type="file"
          accept=".zip,application/zip"
          @change="handleFileChange"
        />

        <button class="transfer-dropzone" type="button" :disabled="importing || exporting" @click="openFilePicker">
          <span class="transfer-dropzone__icon">⇪</span>
          <span class="transfer-dropzone__content">
            <span class="transfer-dropzone__title">{{ selectedFile ? selectedFile.name : '选择 ZIP 文件' }}</span>
            <span class="transfer-dropzone__desc">
              {{ selectedFile ? formatFileSize(selectedFile.size) : '支持 UTF-8 文件名；会忽略非 Markdown 文件。' }}
            </span>
          </span>
          <span class="transfer-dropzone__action">浏览</span>
        </button>

        <div class="transfer-hints">
          <p>· 目录会导入为分类，空目录也会被保留。</p>
          <p>· `.md` / `.markdown` 文件会导入为笔记，文件名会作为笔记标题。</p>
        </div>

        <button class="sl-btn sl-btn--primary transfer-action-btn" :disabled="!selectedFile || importing || exporting" @click="handleImport">
          {{ importing ? '正在导入…' : '开始导入' }}
        </button>
      </section>
    </div>
  </PopupLayer>
</template>

<script setup>
import { computed, ref } from 'vue'
import PopupLayer from '@/components/PopupLayer.vue'
import { useNoteStore } from '@/stores/note'
import { useToastStore } from '@/stores/toast'

const emit = defineEmits(['close'])
const noteStore = useNoteStore()
const toast = useToastStore()

const fileInputRef = ref(null)
const selectedFile = ref(null)
const exporting = ref(false)
const importing = ref(false)

const hasUnsavedChanges = computed(() => noteStore.editMode && noteStore.dirty)

function openFilePicker() {
  fileInputRef.value?.click()
}

function handleFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!/\.zip$/i.test(file.name)) {
    toast.error('请选择 ZIP 文件')
    event.target.value = ''
    selectedFile.value = null
    return
  }
  selectedFile.value = file
}

function formatFileSize(size = 0) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function triggerBrowserDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || `starlight-notes-${Date.now()}.zip`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

async function handleExport() {
  exporting.value = true
  try {
    const { blob, fileName } = await noteStore.exportArchive()
    triggerBrowserDownload(blob, fileName)
    toast.success('导出完成，ZIP 已开始下载')
  } catch (err) {
    toast.error(err.message)
  } finally {
    exporting.value = false
  }
}

async function handleImport() {
  if (!selectedFile.value) {
    toast.error('请先选择 ZIP 文件')
    return
  }

  importing.value = true
  try {
    const result = await noteStore.importArchive(selectedFile.value)
    const ignoredText = result.ignoredCount ? `，忽略 ${result.ignoredCount} 个非 Markdown 条目` : ''
    toast.success(`导入完成：新增 ${result.categoryCount} 个分类、${result.noteCount} 篇笔记${ignoredText}`)
    selectedFile.value = null
    if (fileInputRef.value) fileInputRef.value.value = ''
    emit('close')
  } catch (err) {
    toast.error(err.message)
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.transfer-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.transfer-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  border-radius: calc(var(--sl-radius-lg) + 2px);
  border: 1px solid var(--sl-border);
  background:
    linear-gradient(180deg, var(--sl-primary-light) 0, transparent 120px),
    var(--sl-panel, var(--sl-card));
  box-shadow: var(--sl-shadow-card);
}

.transfer-card--import {
  background:
    linear-gradient(180deg, var(--sl-hover-bg) 0, transparent 140px),
    var(--sl-panel, var(--sl-card));
}

.transfer-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.transfer-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sl-text);
}

.transfer-card__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--sl-text-secondary);
}

.transfer-alert {
  padding: 12px 14px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
  background: var(--sl-hover-bg);
  color: var(--sl-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.transfer-file-input {
  display: none;
}

.transfer-dropzone {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px;
  border-radius: calc(var(--sl-radius-lg) + 2px);
  border: 1px dashed var(--sl-border-strong);
  background: var(--sl-card);
  color: var(--sl-text);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}

.transfer-dropzone:hover:not(:disabled) {
  border-color: var(--sl-primary);
  background: var(--sl-card-hover);
}

.transfer-dropzone:active:not(:disabled) {
  transform: scale(0.99);
}

.transfer-dropzone:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.transfer-dropzone__icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  font-size: 20px;
  font-weight: 700;
  flex-shrink: 0;
}

.transfer-dropzone__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.transfer-dropzone__title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text);
  word-break: break-word;
}

.transfer-dropzone__desc {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--sl-text-tertiary);
  line-height: 1.6;
}

.transfer-dropzone__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.transfer-hints {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--sl-text-secondary);
  line-height: 1.6;
}

.transfer-action-btn {
  width: 100%;
}

@media (max-width: 768px) {
  .transfer-layout {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .transfer-card {
    padding: 16px;
  }

  .transfer-dropzone {
    align-items: flex-start;
    padding: 14px;
  }

  .transfer-dropzone__action {
    margin-left: auto;
  }
}
</style>



