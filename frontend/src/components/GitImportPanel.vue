<template>
  <section class="git-panel">
    <div class="git-panel__header">
      <div>
        <h4 class="git-panel__title">通过 Git 导入</h4>
        <p class="git-panel__desc">支持 HTTP / HTTPS 仓库地址、分支选择、目录筛选、手动重导入与定时自动同步。</p>
      </div>
      <span class="sl-badge">Git</span>
    </div>

    <div class="transfer-alert transfer-alert--warning">
      <strong>重要提醒：</strong>
      仓库地址中的认证信息会保存到服务器，用于后续重导入和自动同步；请仅在可信服务器上使用。重新导入会覆盖你对这些导入笔记的修改，并硬删除旧导入数据（包含回收站中的相关笔记）。
    </div>

    <div v-if="statusLoaded" class="git-feature-summary">
      <span>功能状态：{{ featureStatus.enabled ? '管理员已开启' : '管理员未开启' }}</span>
      <span>并发限制：{{ featureStatus.maxConcurrentImports > 0 ? `${featureStatus.maxConcurrentImports} 个任务` : '不限制' }}</span>
    </div>

    <div class="git-form-grid">
      <div class="form-field settings-form-field settings-form-field--full">
        <label class="sl-label">仓库地址</label>
        <input
          v-model="importForm.repositoryUrl"
          class="sl-input"
          placeholder="https://token@github.com/owner/repo.git"
          :disabled="branchesLoading || previewLoading || importing"
        />
        <div class="field-hint">仅支持 HTTP / HTTPS。私有仓库可在 URL 中嵌入 token 或账号密码，服务端不会把认证信息写入日志。</div>
      </div>

      <div class="form-field settings-form-field settings-form-field--full git-actions-row">
        <button class="sl-btn" :disabled="!featureStatus.enabled || branchesLoading || previewLoading || importing" @click="handleResolveBranches">
          {{ branchesLoading ? '解析中…' : '解析分支' }}
        </button>
        <span v-if="importForm.branches.length" class="git-form-meta">已解析 {{ importForm.branches.length }} 个分支</span>
      </div>

      <div class="form-field settings-form-field">
        <label class="sl-label">分支</label>
        <select v-model="importForm.branchName" class="sl-input" :disabled="!importForm.branches.length || previewLoading || importing">
          <option value="">请选择分支</option>
          <option v-for="branch in importForm.branches" :key="branch" :value="branch">{{ branch }}</option>
        </select>
      </div>

      <div class="form-field settings-form-field git-actions-row">
        <label class="sl-label">仓库预览</label>
        <button class="sl-btn sl-btn--primary" :disabled="!featureStatus.enabled || !importForm.branchName || previewLoading || importing" @click="handleCreatePreview">
          {{ previewLoading ? '加载目录中…' : '加载仓库目录' }}
        </button>
      </div>
    </div>

    <div v-if="preview" class="git-preview-card">
      <div class="git-preview-card__header">
        <div>
          <h5>导入预览</h5>
          <p>{{ preview.repositoryName }} · {{ preview.branchName }} · 最新提交 {{ preview.headCommitId || '—' }}</p>
        </div>
        <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="handleDiscardPreview">关闭预览</button>
      </div>

      <div class="git-form-grid">
        <div class="form-field settings-form-field settings-form-field--full git-tree-field">
          <label class="sl-label">仓库目录</label>
          <DirectoryTree
            v-model="importForm.sourcePath"
            :items="directoryTreeItems"
            title="仓库目录树"
            description="目录默认折叠。点击左侧箭头可展开子目录，也可以使用右侧按钮全部展开或全部收起。"
            empty-text="当前仓库预览没有可导入的目录"
          />
          <div class="git-tree-summary">当前导入目录：{{ selectedDirectoryLabel }}</div>
          <div class="field-hint">只会导入该目录下的 Markdown 文件以及包含 Markdown 文件的目录结构。</div>
        </div>

        <div class="form-field settings-form-field settings-form-field--full git-tree-field">
          <label class="sl-label">目标分类</label>
          <div class="settings-inline-field">
            <div class="field-hint" style="margin-top: 0;">当前：{{ selectedTargetCategoryLabel }}</div>
            <button class="sl-btn sl-btn--ghost sl-btn--sm" type="button" :disabled="importing" @click="importForm.existingTargetCategoryId = ''">
              使用自动创建规则
            </button>
          </div>
          <DirectoryTree
            v-model="importForm.existingTargetCategoryId"
            :items="categoryTreeItems"
            title="已有分类目录"
            description="选择后会导入到该分类及其子目录范围内；留空则自动创建到“来自git”下。"
            empty-text="当前还没有可选的目标分类"
          />
          <div class="field-hint">留空时默认会在 <code>来自git</code> 分类下创建仓库同名分类；若重名会自动追加中文全角序号后缀。</div>
        </div>

        <div v-if="!importForm.existingTargetCategoryId" class="form-field settings-form-field settings-form-field--full">
          <label class="sl-label">自动创建的分类名称</label>
          <input v-model="importForm.targetCategoryName" class="sl-input" :disabled="importing" />
        </div>

        <div class="form-field settings-form-field settings-form-field--full">
          <div class="settings-inline-field">
            <label class="sl-label">自动同步</label>
            <label class="sl-switch-row">
              <input v-model="importForm.autoSyncEnabled" type="checkbox" :disabled="importing" />
              <span>{{ importForm.autoSyncEnabled ? '已开启' : '暂不启用' }}</span>
            </label>
          </div>
          <div class="field-hint">前端展示时间按你本地时区显示，后端会保存时区信息后再进行换算调度。</div>
        </div>

        <template v-if="importForm.autoSyncEnabled">
          <div class="form-field settings-form-field">
            <label class="sl-label">同步频率</label>
            <select v-model="importForm.scheduleType" class="sl-input" :disabled="importing">
              <option v-for="item in scheduleTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>

          <div class="form-field settings-form-field">
            <label class="sl-label">时区</label>
            <input v-model="importForm.scheduleTimezone" class="sl-input" :disabled="importing" placeholder="Asia/Shanghai" />
          </div>

          <div v-if="requiresHour(importForm.scheduleType)" class="form-field settings-form-field">
            <label class="sl-label">小时</label>
            <input v-model.number="importForm.scheduleHour" class="sl-input" type="number" min="0" max="23" :disabled="importing" />
          </div>

          <div v-if="requiresMinute(importForm.scheduleType)" class="form-field settings-form-field">
            <label class="sl-label">分钟</label>
            <input v-model.number="importForm.scheduleMinute" class="sl-input" type="number" min="0" max="59" :disabled="importing" />
          </div>

          <div v-if="importForm.scheduleType === 'WEEKLY'" class="form-field settings-form-field">
            <label class="sl-label">星期</label>
            <select v-model.number="importForm.scheduleDayOfWeek" class="sl-input" :disabled="importing">
              <option v-for="item in weekDayOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
        </template>
      </div>

      <div v-if="selectedDirectory && selectedDirectory.markdownFileCount === 0" class="transfer-alert">
        当前所选目录内没有 Markdown 文件，执行导入时会直接提示并结束。
      </div>

      <div class="git-preview-card__actions">
        <button class="sl-btn sl-btn--primary" :disabled="importing || !featureStatus.enabled" @click="handleImportFromPreview">
          {{ importing ? '导入中…' : '开始导入 Git 仓库' }}
        </button>
      </div>
    </div>

    <div class="git-sources-card">
      <div class="git-sources-card__header">
        <div>
          <h5>已保存的 Git 导入源</h5>
          <p>可手动重新导入最新内容，也可为单个仓库配置自动同步。</p>
        </div>
        <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="loadSources">刷新</button>
      </div>

      <div v-if="!sources.length" class="git-empty-state">暂无已保存的 Git 导入源。</div>

      <div v-for="source in sources" :key="source.id" class="git-source-item">
        <div class="git-source-item__main">
          <div class="git-source-item__top">
            <div>
              <div class="git-source-item__name">{{ source.repositoryName }}</div>
              <div class="git-source-item__meta">{{ source.repositoryUrlMasked }} · {{ source.branchName }} · {{ source.sourcePath || '/' }}</div>
            </div>
            <span :class="['git-status-badge', source.lastSyncSuccess === false ? 'is-error' : 'is-ok']">
              {{ source.lastSyncSuccess === false ? '最近同步失败' : (source.lastSyncSuccess === true ? '最近同步成功' : '尚未同步') }}
            </span>
          </div>

          <div class="git-source-item__meta-row">
            <span>目标分类：{{ source.targetCategoryName || '—' }}</span>
            <span v-if="source.targetCategoryMissing" class="git-text-warning">目标分类已缺失，将在下次同步时重建。</span>
            <span v-if="source.lastSyncAt">上次同步：{{ formatDateTime(source.lastSyncAt) }}</span>
            <span v-if="source.lastSyncedCommitId">提交：{{ source.lastSyncedCommitId }}</span>
          </div>

          <div v-if="source.lastSyncMessage" class="field-hint">{{ source.lastSyncMessage }}</div>

          <div class="git-source-actions">
            <button
              v-if="confirmSyncSourceId !== source.id"
              class="sl-btn"
              :disabled="!featureStatus.enabled || syncingSourceId === source.id"
              @click="confirmSyncSourceId = source.id"
            >
              重新导入
            </button>
            <template v-else>
              <div class="transfer-alert transfer-alert--warning git-inline-warning">
                重新导入会克隆最新仓库、覆盖已修改的导入笔记，并硬删除上一轮导入留下的旧数据（包含回收站中的相关笔记）。
              </div>
              <button class="sl-btn sl-btn--danger" :disabled="syncingSourceId === source.id" @click="handleSyncSource(source)">
                {{ syncingSourceId === source.id ? '重导入中…' : '确认重新导入' }}
              </button>
              <button class="sl-btn" :disabled="syncingSourceId === source.id" @click="confirmSyncSourceId = ''">取消</button>
            </template>
            <button
              class="sl-btn sl-btn--danger"
              :disabled="syncingSourceId === source.id || savingSourceId === source.id"
              @click="openDeleteSourceConfirm(source)"
            >
              删除导入源
            </button>
          </div>

          <div class="git-source-schedule">
            <div class="form-field settings-form-field">
              <label class="sl-label">自动同步</label>
              <label class="sl-switch-row">
                <input v-model="sourceDrafts[source.id].autoSyncEnabled" type="checkbox" :disabled="savingSourceId === source.id || !featureStatus.enabled" />
                <span>{{ sourceDrafts[source.id].autoSyncEnabled ? '已开启' : '已关闭' }}</span>
              </label>
            </div>

            <template v-if="sourceDrafts[source.id].autoSyncEnabled">
              <div class="form-field settings-form-field">
                <label class="sl-label">频率</label>
                <select v-model="sourceDrafts[source.id].scheduleType" class="sl-input" :disabled="savingSourceId === source.id || !featureStatus.enabled">
                  <option v-for="item in scheduleTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
              <div class="form-field settings-form-field">
                <label class="sl-label">时区</label>
                <input v-model="sourceDrafts[source.id].scheduleTimezone" class="sl-input" :disabled="savingSourceId === source.id || !featureStatus.enabled" />
              </div>
              <div v-if="requiresHour(sourceDrafts[source.id].scheduleType)" class="form-field settings-form-field">
                <label class="sl-label">小时</label>
                <input v-model.number="sourceDrafts[source.id].scheduleHour" class="sl-input" type="number" min="0" max="23" :disabled="savingSourceId === source.id || !featureStatus.enabled" />
              </div>
              <div v-if="requiresMinute(sourceDrafts[source.id].scheduleType)" class="form-field settings-form-field">
                <label class="sl-label">分钟</label>
                <input v-model.number="sourceDrafts[source.id].scheduleMinute" class="sl-input" type="number" min="0" max="59" :disabled="savingSourceId === source.id || !featureStatus.enabled" />
              </div>
              <div v-if="sourceDrafts[source.id].scheduleType === 'WEEKLY'" class="form-field settings-form-field">
                <label class="sl-label">星期</label>
                <select v-model.number="sourceDrafts[source.id].scheduleDayOfWeek" class="sl-input" :disabled="savingSourceId === source.id || !featureStatus.enabled">
                  <option v-for="item in weekDayOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
            </template>

            <div class="form-field settings-form-field git-schedule-save">
              <label class="sl-label">保存配置</label>
              <button class="sl-btn" :disabled="savingSourceId === source.id || !featureStatus.enabled" @click="handleSaveSourceSchedule(source)">
                {{ savingSourceId === source.id ? '保存中…' : '保存自动同步设置' }}
              </button>
            </div>
          </div>

          <div v-if="source.histories?.length" class="git-history-list">
            <div class="git-history-title">最近 5 次同步记录</div>
            <div v-for="history in source.histories" :key="history.id" class="git-history-item">
              <span :class="['git-history-dot', history.successFlag ? 'is-ok' : 'is-error']"></span>
              <span>{{ history.triggerType === 'AUTO' ? '自动同步' : (history.triggerType === 'INITIAL_IMPORT' ? '首次导入' : '手动重导入') }}</span>
              <span>{{ formatDateTime(history.startedAt) }}</span>
              <span v-if="history.commitId">{{ history.commitId }}</span>
              <span class="git-history-message">{{ history.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <PopupLayer
    v-if="deleteSourceState.visible"
    title="确认删除已保存的 Git 导入源？"
    eyebrow="Git 导入"
    tone="danger"
    width="min(460px, calc(100vw - 32px))"
    @close="closeDeleteSourceConfirm"
  >
    <div class="transfer-alert transfer-alert--warning">
      <strong>{{ deleteSourceState.repositoryName || '该导入源' }}</strong> 的仓库地址、同步历史和自动同步配置会被删除，但已经导入的笔记与分类数据会保留，不会被连带删除。
    </div>
    <div class="field-hint git-source-delete-hint">如果之后还需要重新同步这个仓库，需要再次填写仓库地址并重新保存导入源。</div>
    <template #footer>
      <button class="sl-btn" @click="closeDeleteSourceConfirm">取消</button>
      <button class="sl-btn sl-btn--danger" :disabled="deletingSource" @click="handleDeleteSourceConfirmed">
        {{ deletingSource ? '删除中…' : '确认删除导入源' }}
      </button>
    </template>
  </PopupLayer>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { gitApi } from '@/api'
import DirectoryTree from '@/components/DirectoryTree.vue'
import PopupLayer from '@/components/PopupLayer.vue'
import { useNoteStore } from '@/stores/note'
import { useToastStore } from '@/stores/toast'
import { buildCategorySelectionTree, buildGitDirectoryTree, findTreeNodeById } from '@/utils/directoryTree'

const props = defineProps({
  treeItems: {
    type: Array,
    default: () => []
  }
})

const toast = useToastStore()
const noteStore = useNoteStore()

const featureStatus = ref({ enabled: false, maxConcurrentImports: 0 })
const statusLoaded = ref(false)
const branchesLoading = ref(false)
const previewLoading = ref(false)
const importing = ref(false)
const syncingSourceId = ref('')
const savingSourceId = ref('')
const confirmSyncSourceId = ref('')
const preview = ref(null)
const sources = ref([])
const sourceDrafts = ref({})
const deletingSource = ref(false)
const deleteSourceState = ref({
  visible: false,
  sourceId: '',
  repositoryName: ''
})

const scheduleTypeOptions = [
  { value: 'EVERY_30_MINUTES', label: '每 30 分钟' },
  { value: 'HOURLY', label: '每小时一次' },
  { value: 'DAILY', label: '每天一次' },
  { value: 'WEEKLY', label: '每周一次' }
]

const weekDayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

const importForm = ref(createImportForm())

const categoryTreeItems = computed(() => buildCategorySelectionTree(props.treeItems))

const directoryTreeItems = computed(() => buildGitDirectoryTree(preview.value?.directories || []))

const selectedDirectory = computed(() => {
  return preview.value?.directories?.find(item => item.path === importForm.value.sourcePath)
    || preview.value?.directories?.[0]
    || null
})

const selectedDirectoryLabel = computed(() => {
  if (!selectedDirectory.value) return '未选择'
  return `${selectedDirectory.value.label || (selectedDirectory.value.path || '/') }（${selectedDirectory.value.markdownFileCount} 个 Markdown 文件）`
})

const selectedTargetCategoryLabel = computed(() => {
  if (!importForm.value.existingTargetCategoryId) {
    return '自动创建到「来自git」下'
  }
  const targetNode = findTreeNodeById(categoryTreeItems.value, importForm.value.existingTargetCategoryId)
  return targetNode?.label || '自动创建到「来自git」下'
})

onMounted(async () => {
  await loadStatus()
  await loadSources()
})

onBeforeUnmount(async () => {
  if (preview.value?.previewToken) {
    try {
      await gitApi.discardPreview(preview.value.previewToken)
    } catch {}
  }
})

function createImportForm() {
  return {
    repositoryUrl: '',
    repositoryName: '',
    branches: [],
    branchName: '',
    sourcePath: '',
    existingTargetCategoryId: '',
    targetCategoryName: '',
    autoSyncEnabled: false,
    scheduleType: 'DAILY',
    scheduleTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    scheduleHour: 3,
    scheduleMinute: 0,
    scheduleDayOfWeek: 1
  }
}

function createSourceDraft(source) {
  return {
    autoSyncEnabled: Boolean(source.autoSyncEnabled),
    scheduleType: source.scheduleType && source.scheduleType !== 'MANUAL_ONLY' ? source.scheduleType : 'DAILY',
    scheduleTimezone: source.scheduleTimezone || (Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'),
    scheduleHour: Number.isInteger(source.scheduleHour) ? source.scheduleHour : 3,
    scheduleMinute: Number.isInteger(source.scheduleMinute) ? source.scheduleMinute : 0,
    scheduleDayOfWeek: Number.isInteger(source.scheduleDayOfWeek) ? source.scheduleDayOfWeek : 1
  }
}

async function loadStatus() {
  statusLoaded.value = false
  try {
    featureStatus.value = await gitApi.getStatus()
  } catch (err) {
    toast.error(err.message)
  } finally {
    statusLoaded.value = true
  }
}

async function loadSources() {
  try {
    const list = await gitApi.listSources()
    sources.value = list
    const nextDrafts = {}
    for (const item of list) {
      nextDrafts[item.id] = createSourceDraft(item)
    }
    sourceDrafts.value = nextDrafts
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleResolveBranches() {
  if (!String(importForm.value.repositoryUrl || '').trim()) {
    toast.error('请输入仓库地址')
    return
  }
  branchesLoading.value = true
  try {
    const result = await gitApi.resolveBranches(importForm.value.repositoryUrl.trim())
    importForm.value.repositoryName = result.repositoryName || ''
    importForm.value.branches = result.branches || []
    importForm.value.branchName = result.defaultBranch || result.branches?.[0] || ''
    if (!importForm.value.targetCategoryName) {
      importForm.value.targetCategoryName = result.repositoryName || ''
    }
    toast.success('仓库分支解析完成')
  } catch (err) {
    toast.error(err.message)
  } finally {
    branchesLoading.value = false
  }
}

async function handleCreatePreview() {
  if (!importForm.value.repositoryUrl.trim()) {
    toast.error('请输入仓库地址')
    return
  }
  if (!importForm.value.branchName) {
    toast.error('请先选择分支')
    return
  }
  previewLoading.value = true
  try {
    if (preview.value?.previewToken) {
      try {
        await gitApi.discardPreview(preview.value.previewToken)
      } catch {}
    }
    preview.value = await gitApi.createPreview(importForm.value.repositoryUrl.trim(), importForm.value.branchName)
    importForm.value.repositoryName = preview.value.repositoryName || importForm.value.repositoryName
    importForm.value.sourcePath = preview.value?.directories?.[0]?.path ?? ''
    if (!importForm.value.targetCategoryName) {
      importForm.value.targetCategoryName = preview.value.defaultTargetCategoryName || preview.value.repositoryName || ''
    }
    toast.success('仓库目录已加载')
  } catch (err) {
    toast.error(err.message)
  } finally {
    previewLoading.value = false
  }
}

async function handleDiscardPreview() {
  if (!preview.value?.previewToken) {
    preview.value = null
    return
  }
  try {
    await gitApi.discardPreview(preview.value.previewToken)
  } catch (err) {
    toast.error(err.message)
  } finally {
    preview.value = null
  }
}

async function handleImportFromPreview() {
  if (!preview.value?.previewToken) {
    toast.error('请先加载仓库目录')
    return
  }
  if (selectedDirectory.value && selectedDirectory.value.markdownFileCount === 0) {
    toast.error('当前所选目录没有 Markdown 文件，无法导入')
    return
  }
  importing.value = true
  try {
    const result = await gitApi.importFromPreview({
      previewToken: preview.value.previewToken,
      sourcePath: importForm.value.sourcePath || '',
      existingTargetCategoryId: importForm.value.existingTargetCategoryId || null,
      targetCategoryName: importForm.value.targetCategoryName || importForm.value.repositoryName || preview.value.repositoryName,
      ...buildSchedulePayload(importForm.value)
    })
    await noteStore.refreshTree()
    await noteStore.refreshTrash()
    await loadSources()
    toast.success(`Git 导入完成：新增 ${result.categoryCount} 个分类、${result.noteCount} 篇笔记`)
    preview.value = null
    importForm.value = {
      ...createImportForm(),
      repositoryUrl: importForm.value.repositoryUrl,
      repositoryName: importForm.value.repositoryName
    }
  } catch (err) {
    toast.error(err.message)
  } finally {
    importing.value = false
  }
}

async function handleSyncSource(source) {
  syncingSourceId.value = source.id
  try {
    const result = await gitApi.syncNow(source.id)
    await noteStore.refreshTree()
    await noteStore.refreshTrash()
    await loadSources()
    toast.success(result.skipped ? '仓库没有新提交，本次已跳过重导入' : `重导入完成：新增 ${result.categoryCount} 个分类、${result.noteCount} 篇笔记`)
    confirmSyncSourceId.value = ''
  } catch (err) {
    toast.error(err.message)
  } finally {
    syncingSourceId.value = ''
  }
}

async function handleSaveSourceSchedule(source) {
  savingSourceId.value = source.id
  try {
    const draft = sourceDrafts.value[source.id]
    await gitApi.updateAutoSync(source.id, buildSchedulePayload(draft))
    await loadSources()
    toast.success('自动同步设置已保存')
  } catch (err) {
    toast.error(err.message)
  } finally {
    savingSourceId.value = ''
  }
}

function openDeleteSourceConfirm(source) {
  deleteSourceState.value = {
    visible: true,
    sourceId: source.id,
    repositoryName: source.repositoryName || ''
  }
}

function closeDeleteSourceConfirm() {
  deleteSourceState.value = {
    visible: false,
    sourceId: '',
    repositoryName: ''
  }
}

async function handleDeleteSourceConfirmed() {
  if (!deleteSourceState.value.sourceId) return
  deletingSource.value = true
  try {
    await gitApi.deleteSource(deleteSourceState.value.sourceId)
    confirmSyncSourceId.value = ''
    await loadSources()
    toast.success('已删除保存的 Git 导入源，已导入的笔记数据仍然保留')
    closeDeleteSourceConfirm()
  } catch (err) {
    toast.error(err.message)
  } finally {
    deletingSource.value = false
  }
}

function buildSchedulePayload(state) {
  if (!state.autoSyncEnabled) {
    return {
      autoSyncEnabled: false,
      scheduleType: 'MANUAL_ONLY',
      scheduleTimezone: state.scheduleTimezone || (Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'),
      scheduleHour: null,
      scheduleMinute: null,
      scheduleDayOfWeek: null
    }
  }
  return {
    autoSyncEnabled: true,
    scheduleType: state.scheduleType,
    scheduleTimezone: state.scheduleTimezone,
    scheduleHour: requiresHour(state.scheduleType) ? Number(state.scheduleHour) : null,
    scheduleMinute: requiresMinute(state.scheduleType) ? Number(state.scheduleMinute) : null,
    scheduleDayOfWeek: state.scheduleType === 'WEEKLY' ? Number(state.scheduleDayOfWeek) : null
  }
}

function requiresHour(scheduleType) {
  return scheduleType === 'DAILY' || scheduleType === 'WEEKLY'
}

function requiresMinute(scheduleType) {
  return scheduleType === 'HOURLY' || scheduleType === 'DAILY' || scheduleType === 'WEEKLY'
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
</script>

<style scoped>
.git-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-form-field--full {
  grid-column: 1 / -1;
}

.settings-inline-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sl-switch-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--sl-text);
}

.field-hint {
  font-size: 12px;
  line-height: 1.7;
  color: var(--sl-text-tertiary);
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

.git-panel__header,
.git-preview-card__header,
.git-sources-card__header,
.git-source-item__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.git-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sl-text);
}

.git-panel__desc,
.git-preview-card__header p,
.git-sources-card__header p {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--sl-text-secondary);
}

.git-feature-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--sl-text-secondary);
}

.git-form-grid,
.git-source-schedule {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.git-actions-row {
  display: flex;
  align-items: end;
  gap: 12px;
}

.git-form-meta {
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

.git-preview-card,
.git-sources-card,
.git-source-item {
  padding: 16px;
  border-radius: calc(var(--sl-radius-lg) + 2px);
  border: 1px solid var(--sl-border);
  background: var(--sl-panel, var(--sl-card));
  box-shadow: var(--sl-shadow-card);
}

.git-preview-card,
.git-source-item {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.git-tree-field {
  gap: 10px;
}

.git-tree-summary {
  font-size: 12px;
  color: var(--sl-text-secondary);
}

.git-preview-card__actions,
.git-source-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.git-empty-state {
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

.git-source-item__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text);
}

.git-source-item__meta,
.git-source-item__meta-row {
  font-size: 12px;
  line-height: 1.7;
  color: var(--sl-text-secondary);
}

.git-source-item__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
}

.git-status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.git-status-badge.is-ok {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
}

.git-status-badge.is-error {
  background: color-mix(in srgb, var(--sl-danger) 12%, var(--sl-card));
  color: var(--sl-danger);
}

.git-history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.git-history-title {
  font-size: 12px;
  color: var(--sl-text-secondary);
  font-weight: 600;
}

.git-history-item {
  display: grid;
  grid-template-columns: 10px auto auto auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--sl-text-secondary);
}

.git-history-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.git-history-dot.is-ok {
  background: var(--sl-primary);
}

.git-history-dot.is-error {
  background: var(--sl-danger);
}

.git-history-message {
  min-width: 0;
  word-break: break-word;
}

.git-text-warning {
  color: var(--sl-danger);
}

.git-inline-warning {
  width: 100%;
}

.git-source-delete-hint {
  margin-top: 10px;
}

.transfer-alert--warning {
  border-color: color-mix(in srgb, var(--sl-danger) 22%, var(--sl-border));
  background: color-mix(in srgb, var(--sl-danger) 6%, var(--sl-card));
}

@media (max-width: 900px) {
  .git-form-grid,
  .git-source-schedule,
  .git-history-item {
    grid-template-columns: 1fr;
  }
}
</style>

