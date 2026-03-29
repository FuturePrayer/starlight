<template>
  <PopupLayer
    title="星迹书阁"
    eyebrow="公开站点设置"
    width="min(520px, calc(100vw - 32px))"
    @close="$emit('close')"
  >
    <div class="site-modal" v-if="!loading">

      <!-- 继承状态提示：该分类被父级分类的星迹书阁覆盖 -->
      <div v-if="inherited" class="site-modal__inherited-notice">
        <div class="site-modal__inherited-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg>
        </div>
        <div class="site-modal__inherited-text">
          <div class="site-modal__inherited-label">公开设置已继承</div>
          <div class="site-modal__inherited-hint">
            该分类的公开访问继承自父级分类「<strong>{{ inheritedFromName }}</strong>」的星迹书阁，无需单独设置。
          </div>
          <button class="sl-btn sl-btn--sm site-modal__inherited-link" @click="openParentSite">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
            查看父级星迹书阁
          </button>
        </div>
      </div>

      <!-- 正常状态：开启/关闭开关 -->
      <template v-if="!inherited">
        <div class="site-modal__switch-row">
          <div class="site-modal__switch-info">
            <div class="site-modal__switch-label">开启星迹书阁</div>
            <div class="site-modal__switch-hint">
              开启后，该分类及其所有子分类下的笔记将通过公开链接只读访问，<br>适合用作个人博客或知识小册。
            </div>
          </div>
          <button
            :class="['site-toggle', { on: siteEnabled }]"
            @click="toggleSite"
            :disabled="toggling"
          >
            <span class="site-toggle__thumb"></span>
          </button>
        </div>

        <!-- 合并确认对话框 -->
        <div v-if="showMergeConfirm" class="site-modal__merge-confirm">
          <div class="site-modal__merge-header">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            <span>{{ mergeMessage }}</span>
          </div>
          <div class="site-modal__merge-list">
            <div v-for="sub in conflictingSubs" :key="sub.id" class="site-modal__merge-item">
              <div class="site-modal__merge-item-name">{{ sub.name }}</div>
              <div class="site-modal__merge-item-token">
                <span class="site-modal__merge-item-label">原链接：</span>
                <code>/site/{{ sub.siteToken }}</code>
                <span class="site-modal__merge-item-badge">将作废</span>
              </div>
            </div>
          </div>
          <div class="site-modal__merge-actions">
            <button class="sl-btn" @click="cancelMerge">取消</button>
            <button class="sl-btn sl-btn--primary" @click="confirmMerge" :disabled="toggling">
              {{ toggling ? '处理中…' : '确认合并并开启' }}
            </button>
          </div>
        </div>

        <!-- Site settings (shown when enabled) -->
        <template v-if="siteEnabled && !showMergeConfirm">
          <div class="site-modal__field">
            <label class="sl-label">站点标题</label>
            <div class="site-modal__field-row">
              <input
                v-model="siteTitle"
                class="sl-input"
                placeholder="自定义标题（留空使用分类名）"
                @keyup.enter="handleUpdateTitle"
              />
              <button class="sl-btn sl-btn--primary sl-btn--sm" @click="handleUpdateTitle" :disabled="saving">
                {{ saving ? '保存中…' : '保存' }}
              </button>
            </div>
          </div>

          <div class="site-modal__field">
            <label class="sl-label">公开链接</label>
            <div class="site-modal__link-row">
              <input
                class="sl-input"
                :value="siteUrl"
                readonly
                @click="$event.target.select()"
              />
              <button class="sl-btn sl-btn--sm" @click="copyLink">复制</button>
            </div>
            <div class="site-modal__sub-hint" v-if="hasSubInfo">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
              子分类下的笔记也将包含在此公开页面中
            </div>
          </div>

          <div class="site-modal__field">
            <label class="sl-label">二维码</label>
            <div class="site-modal__qr-area" v-if="qrDataUrl">
              <img :src="qrDataUrl" alt="站点二维码" class="site-modal__qr-img" />
            </div>
            <div class="site-modal__qr-area" v-else>
              <span class="site-modal__qr-loading">加载中…</span>
            </div>
          </div>

          <div class="site-modal__preview">
            <button class="sl-btn" @click="openSite">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
              预览公开页面
            </button>
          </div>
        </template>
      </template>
    </div>

    <!-- Loading state -->
    <div v-else class="site-modal site-modal--loading">
      <div class="site-modal__loading-text">加载中…</div>
    </div>
  </PopupLayer>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { siteApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import PopupLayer from '@/components/PopupLayer.vue'

const props = defineProps({
  categoryId: { type: String, required: true },
  categoryName: { type: String, default: '' }
})
const emit = defineEmits(['close', 'updated'])

const toast = useToastStore()
const loading = ref(true)
const toggling = ref(false)
const saving = ref(false)
const siteEnabled = ref(false)
const siteToken = ref('')
const siteTitle = ref('')
const qrDataUrl = ref('')
const siteUrl = ref('')

// 继承状态
const inherited = ref(false)
const inheritedFromName = ref('')
const inheritedFromId = ref('')
const inheritedFromToken = ref('')

// 合并确认
const showMergeConfirm = ref(false)
const conflictingSubs = ref([])
const mergeMessage = ref('')
const hasSubInfo = ref(false)

async function loadSiteInfo() {
  loading.value = true
  try {
    const data = await siteApi.getInfo(props.categoryId)

    // 检查是否为继承状态
    if (data.inherited) {
      inherited.value = true
      inheritedFromName.value = data.inheritedFrom?.name || ''
      inheritedFromId.value = data.inheritedFrom?.id || ''
      inheritedFromToken.value = data.inheritedFrom?.siteToken || ''
    } else {
      inherited.value = false
      siteEnabled.value = data.enabled
      siteToken.value = data.siteToken || ''
      siteTitle.value = data.siteTitle || ''
      if (data.enabled && data.siteToken) {
        siteUrl.value = `${window.location.origin}/site/${data.siteToken}`
        loadQrCode(data.siteToken)
      }
    }
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

async function loadQrCode(token) {
  try {
    const data = await siteApi.qrCode(token)
    qrDataUrl.value = data.qrDataUrl
    if (data.url) siteUrl.value = data.url
  } catch {
    // 二维码加载失败不影响功能
  }
}

async function toggleSite() {
  toggling.value = true
  try {
    if (siteEnabled.value) {
      // 关闭
      await siteApi.disable(props.categoryId)
      siteEnabled.value = false
      siteToken.value = ''
      siteTitle.value = ''
      qrDataUrl.value = ''
      siteUrl.value = ''
      showMergeConfirm.value = false
      hasSubInfo.value = false
      toast.success('星迹书阁已关闭')
    } else {
      // 开启（第一次尝试，不带 mergeSubSites）
      const data = await siteApi.enable(props.categoryId, siteTitle.value || null, false)

      // 检查是否需要确认合并
      if (data.needConfirmMerge) {
        conflictingSubs.value = data.conflictingSubs || []
        mergeMessage.value = data.message || '以下子分类已独立开启星迹书阁，原公开链接将作废：'
        showMergeConfirm.value = true
        toggling.value = false
        return
      }

      // 正常开启成功
      siteEnabled.value = true
      siteToken.value = data.siteToken || ''
      siteTitle.value = data.siteTitle || ''
      hasSubInfo.value = true
      if (data.siteToken) {
        siteUrl.value = `${window.location.origin}/site/${data.siteToken}`
        loadQrCode(data.siteToken)
      }
      toast.success('星迹书阁已开启')
    }
    emit('updated')
  } catch (err) {
    toast.error(err.message)
  } finally {
    toggling.value = false
  }
}

async function confirmMerge() {
  toggling.value = true
  try {
    const data = await siteApi.enable(props.categoryId, siteTitle.value || null, true)
    siteEnabled.value = true
    siteToken.value = data.siteToken || ''
    siteTitle.value = data.siteTitle || ''
    hasSubInfo.value = true
    if (data.siteToken) {
      siteUrl.value = `${window.location.origin}/site/${data.siteToken}`
      loadQrCode(data.siteToken)
    }
    showMergeConfirm.value = false
    conflictingSubs.value = []
    toast.success('星迹书阁已开启，子分类已合并')
    emit('updated')
  } catch (err) {
    toast.error(err.message)
  } finally {
    toggling.value = false
  }
}

function cancelMerge() {
  showMergeConfirm.value = false
  conflictingSubs.value = []
  mergeMessage.value = ''
}

async function handleUpdateTitle() {
  saving.value = true
  try {
    await siteApi.updateTitle(props.categoryId, siteTitle.value)
    toast.success('站点标题已更新')
    emit('updated')
  } catch (err) {
    toast.error(err.message)
  } finally {
    saving.value = false
  }
}

function copyLink() {
  navigator.clipboard.writeText(siteUrl.value).then(() => {
    toast.success('链接已复制')
  }).catch(() => {
    toast.error('复制失败，请手动选择复制')
  })
}

function openSite() {
  window.open(siteUrl.value, '_blank')
}

function openParentSite() {
  if (inheritedFromToken.value) {
    window.open(`${window.location.origin}/site/${inheritedFromToken.value}`, '_blank')
  }
}

onMounted(loadSiteInfo)
</script>

<style scoped>
.site-modal {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.site-modal--loading {
  align-items: center;
  justify-content: center;
  min-height: 120px;
}
.site-modal__loading-text {
  font-size: 13px;
  color: var(--sl-text-tertiary);
}

/* ──── Inherited notice ──── */
.site-modal__inherited-notice {
  display: flex;
  gap: 14px;
  padding: 16px;
  background: var(--sl-primary-light);
  border: 1px solid var(--sl-primary);
  border-radius: var(--sl-radius-lg);
}
.site-modal__inherited-icon {
  flex-shrink: 0;
  color: var(--sl-primary);
  margin-top: 2px;
}
.site-modal__inherited-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text);
  margin-bottom: 4px;
}
.site-modal__inherited-hint {
  font-size: 12px;
  color: var(--sl-text-secondary);
  line-height: 1.5;
}
.site-modal__inherited-hint strong {
  color: var(--sl-primary);
}
.site-modal__inherited-link {
  margin-top: 10px;
}

/* ──── Switch row ──── */
.site-modal__switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  background: var(--sl-hover-bg);
  border-radius: var(--sl-radius-lg);
}
.site-modal__switch-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text);
}
.site-modal__switch-hint {
  font-size: 12px;
  color: var(--sl-text-tertiary);
  line-height: 1.4;
  margin-top: 4px;
}

/* ──── Toggle switch ──── */
.site-toggle {
  position: relative;
  width: 44px;
  height: 24px;
  border-radius: 12px;
  border: 1px solid var(--sl-border-strong);
  background: var(--sl-hover-bg);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  flex-shrink: 0;
  padding: 0;
}
.site-toggle.on {
  background: var(--sl-primary);
  border-color: var(--sl-primary);
}
.site-toggle__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--sl-card);
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
  transition: transform 0.2s;
}
.site-toggle.on .site-toggle__thumb {
  transform: translateX(20px);
}

/* ──── Merge confirmation ──── */
.site-modal__merge-confirm {
  border: 1px solid var(--sl-warning, #9d5d00);
  border-radius: var(--sl-radius-lg);
  padding: 16px;
  background: var(--sl-hover-bg);
}
.site-modal__merge-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-warning, #9d5d00);
  margin-bottom: 12px;
}
.site-modal__merge-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.site-modal__merge-item {
  padding: 10px 12px;
  background: var(--sl-card);
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
}
.site-modal__merge-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text);
  margin-bottom: 4px;
}
.site-modal__merge-item-token {
  font-size: 11px;
  color: var(--sl-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.site-modal__merge-item-token code {
  font-family: var(--sl-font-mono);
  font-size: 11px;
  background: var(--sl-code-bg);
  padding: 1px 4px;
  border-radius: 3px;
  color: var(--sl-code-text);
}
.site-modal__merge-item-label {
  color: var(--sl-text-secondary);
}
.site-modal__merge-item-badge {
  font-size: 10px;
  font-weight: 600;
  color: var(--sl-danger);
  background: rgba(196, 43, 28, 0.08);
  padding: 1px 6px;
  border-radius: 3px;
}
.site-modal__merge-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ──── Fields ──── */
.site-modal__field {
  display: flex;
  flex-direction: column;
}
.site-modal__field-row,
.site-modal__link-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.site-modal__link-row .sl-input {
  font-family: var(--sl-font-mono);
  font-size: 12px;
}
.site-modal__sub-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--sl-text-tertiary);
  margin-top: 6px;
  padding: 0 2px;
}

/* ──── QR code ──── */
.site-modal__qr-area {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--sl-hover-bg);
  border-radius: var(--sl-radius);
}
.site-modal__qr-img {
  width: 140px;
  height: 140px;
  border-radius: var(--sl-radius);
}
.site-modal__qr-loading {
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

/* ──── Preview button area ──── */
.site-modal__preview {
  display: flex;
  justify-content: flex-end;
}
</style>
