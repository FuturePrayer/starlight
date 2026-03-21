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

          <div v-if="shares.length" class="share-list-section">
            <h4>已有分享</h4>
            <div v-for="s in shares" :key="s.id" class="share-item sl-card">
              <div class="share-url" @click="copyUrl(s.url)">{{ s.url }}</div>
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

onMounted(async () => {
  try { shares.value = await shareApi.list(props.noteId) } catch {}
})

async function handleCreate() {
  try {
    let expiresAt = null
    if (expiryMode.value === 'custom') {
      expiresAt = new Date(year.value, month.value - 1, day.value, hour.value, 0, 0).toISOString().slice(0, 19)
    }
    await shareApi.create(props.noteId, {
      accessType: accessType.value,
      password: sharePassword.value,
      expiresAt
    })
    shares.value = await shareApi.list(props.noteId)
    toast.success('分享链接已创建')
  } catch (err) {
    toast.error(err.message)
  }
}

function copyUrl(url) {
  navigator.clipboard?.writeText(url)
  toast.info('链接已复制')
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
.share-list-section { margin-top: 20px; }
.share-list-section h4 { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.share-item {
  padding: 10px 12px;
  margin-bottom: 8px;
}
.share-url {
  font-size: 12px;
  color: var(--sl-primary);
  word-break: break-all;
  cursor: pointer;
  margin-bottom: 6px;
}
.share-url:hover { text-decoration: underline; }
.share-info { display: flex; gap: 6px; }
</style>

