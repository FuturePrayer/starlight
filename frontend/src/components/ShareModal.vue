<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="$emit('close')">
      <div class="modal sl-card">
        <div class="modal-header">
          <h3>分享笔记</h3>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="$emit('close')">✕</button>
        </div>

        <div class="modal-body">
          <div class="form-grid">
            <div class="form-field">
              <label class="sl-label">分享类型</label>
              <select v-model="accessType" class="sl-select">
                <option value="PUBLIC">公开</option>
                <option value="PASSWORD">私密</option>
              </select>
            </div>
            <div class="form-field">
              <label class="sl-label">分享密码</label>
              <input v-model="sharePassword" class="sl-input" placeholder="私密分享时填写" :disabled="accessType !== 'PASSWORD'" />
            </div>
            <div class="form-field">
              <label class="sl-label">过期策略</label>
              <select v-model="expiryMode" class="sl-select">
                <option value="forever">永久</option>
                <option value="custom">自定义</option>
              </select>
            </div>
          </div>

          <div v-if="expiryMode === 'custom'" class="expiry-grid">
            <div class="form-field">
              <label class="sl-label">年</label>
              <input v-model.number="year" type="number" class="sl-input" />
            </div>
            <div class="form-field">
              <label class="sl-label">月</label>
              <input v-model.number="month" type="number" min="1" max="12" class="sl-input" />
            </div>
            <div class="form-field">
              <label class="sl-label">日</label>
              <input v-model.number="day" type="number" min="1" max="31" class="sl-input" />
            </div>
            <div class="form-field">
              <label class="sl-label">时</label>
              <input v-model.number="hour" type="number" min="0" max="23" class="sl-input" />
            </div>
          </div>

          <button class="sl-btn sl-btn--primary" style="width:100%;margin-top:16px" @click="handleCreate">
            创建分享
          </button>

          <!-- QR Code display -->
          <div v-if="qrData" class="qr-section sl-card">
            <h4>分享二维码</h4>
            <div class="qr-container">
              <img :src="qrData.qrDataUrl" alt="分享二维码" class="qr-img" />
            </div>
            <div class="qr-url" @click="copyUrl(qrData.url)">{{ qrData.url }}</div>
            <p class="qr-hint">点击链接可复制，扫描二维码可直接访问。</p>
          </div>

          <div v-if="shares.length" class="share-list-section">
            <h4>已有分享</h4>
            <div v-for="s in shares" :key="s.id" class="share-item sl-card">
              <div class="share-item-header">
                <div class="share-url" @click="copyUrl(s.url)">{{ s.url }}</div>
                <div class="share-item-actions">
                  <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="handleShowQr(s.token)" title="显示二维码">📱</button>
                  <button class="sl-btn sl-btn--ghost sl-btn--sm share-delete-btn" @click="handleDelete(s.id)" title="删除分享">🗑</button>
                </div>
              </div>
              <div class="share-info">
                <span class="sl-badge">{{ s.accessType === 'PASSWORD' ? '私密' : '公开' }}</span>
                <span class="sl-badge">{{ formatTime(s.expiresAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { shareApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import { formatTime } from '@/utils/markdown'

const props = defineProps({ noteId: String })
const emit = defineEmits(['close'])
const toast = useToastStore()

const accessType = ref('PUBLIC')
const sharePassword = ref('')
const expiryMode = ref('forever')
const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const day = ref(now.getDate())
const hour = ref(23)
const shares = ref([])
const qrData = ref(null)

onMounted(async () => {
  try { shares.value = await shareApi.list(props.noteId) } catch {}
})

async function handleCreate() {
  try {
    let expiresAt = null
    if (expiryMode.value === 'custom') {
      const y = year.value
      const m = String(month.value).padStart(2, '0')
      const d = String(day.value).padStart(2, '0')
      const h = String(hour.value).padStart(2, '0')
      expiresAt = `${y}-${m}-${d}T${h}:00:00`
    }
    await shareApi.create(props.noteId, {
      accessType: accessType.value,
      password: sharePassword.value,
      expiresAt,
      timezoneOffset: new Date().getTimezoneOffset()
    })
    shares.value = await shareApi.list(props.noteId)
    // Show QR for the latest share
    if (shares.value.length) {
      await handleShowQr(shares.value[0].token)
    }
    toast.success('分享链接已创建')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleShowQr(token) {
  try {
    qrData.value = await shareApi.qrCode(token)
  } catch (err) {
    toast.error(err.message)
  }
}

function copyUrl(url) {
  navigator.clipboard?.writeText(url)
  toast.info('链接已复制')
}

async function handleDelete(shareId) {
  if (!confirm('确定删除该分享链接？')) return
  try {
    await shareApi.delete(props.noteId, shareId)
    shares.value = await shareApi.list(props.noteId)
    qrData.value = null
    toast.success('分享链接已删除')
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: var(--sl-backdrop);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 20px;
  animation: sl-fade-in 0.15s ease;
}
.modal {
  width: min(540px, 100%);
  padding: 24px;
  max-height: 85vh;
  overflow-y: auto;
  animation: sl-scale-in 0.2s ease;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.modal-header h3 { font-size: 16px; font-weight: 600; margin: 0; }
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.form-grid .form-field:last-child:nth-child(odd) { grid-column: 1 / -1; }
.expiry-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 12px;
}
.qr-section {
  margin-top: 16px;
  padding: 16px;
  text-align: center;
}
.qr-section h4 { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.qr-container { padding: 8px 0; }
.qr-img { width: 200px; height: 200px; border-radius: var(--sl-radius); border: 1px solid var(--sl-border); }
.qr-url {
  font-size: 12px;
  color: var(--sl-primary);
  word-break: break-all;
  cursor: pointer;
  margin-top: 8px;
}
.qr-url:hover { text-decoration: underline; }
.qr-hint { font-size: 11px; color: var(--sl-text-tertiary); margin-top: 4px; }
.share-list-section { margin-top: 20px; }
.share-list-section h4 { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.share-item {
  padding: 10px 12px;
  margin-bottom: 8px;
}
.share-item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.share-item-actions { display: flex; gap: 2px; flex-shrink: 0; }
.share-url {
  font-size: 12px;
  color: var(--sl-primary);
  word-break: break-all;
  cursor: pointer;
  margin-bottom: 6px;
}
.share-url:hover { text-decoration: underline; }
.share-delete-btn {
  color: var(--sl-danger);
  font-size: 14px;
  opacity: 0.6;
  transition: opacity 0.15s;
}
.share-delete-btn:hover { opacity: 1; }
.share-info { display: flex; gap: 6px; }
</style>

