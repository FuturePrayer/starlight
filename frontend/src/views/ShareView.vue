<template>
  <div class="share-page">
    <!-- Mobile sidebar toggle -->
    <button class="share-sidebar-toggle" @click="sidebarOpen = !sidebarOpen" v-if="isMobile">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
    </button>

    <!-- Sidebar -->
    <aside :class="['share-sidebar', { open: sidebarOpen }]" @click.self="sidebarOpen = false">
      <div class="share-sidebar-inner">
        <div class="share-sidebar-header">
          <div class="share-badge">分享阅读</div>
          <h2 class="share-author">{{ ownerName }}</h2>
          <p class="share-hint">分享页使用作者选择的主题显示。</p>
        </div>

        <div class="share-outline-section">
          <h3>文档大纲</h3>
          <OutlineList :markdown="noteMarkdown" />
        </div>

        <div class="share-password-section sl-card" v-if="needsPassword || !loaded">
          <h3>私密访问</h3>
          <p class="share-hint">若为私密分享，请输入密码查看。</p>
          <label class="sl-label">分享密码</label>
          <input v-model="password" type="password" class="sl-input" placeholder="输入分享密码" @keyup.enter="loadShare" />
          <button class="sl-btn sl-btn--primary" style="width:100%;margin-top:10px" @click="loadShare">查看内容</button>
        </div>
      </div>
    </aside>

    <!-- Main -->
    <main class="share-main">
      <div class="share-topbar">
        <div>
          <h1 class="share-title">{{ noteTitle }}</h1>
          <div class="share-meta" v-if="loaded">
            <span class="sl-badge">{{ accessLabel }}</span>
            <span class="sl-badge" v-if="noteUpdatedAt">{{ formatTime(noteUpdatedAt) }}</span>
            <span class="sl-badge">过期：{{ expiresLabel }}</span>
          </div>
        </div>
      </div>
      <div class="share-content">
        <div v-if="loaded && noteMarkdown" class="markdown-body" v-html="renderedHtml"></div>
        <div v-else-if="errorMsg" class="empty-state">
          <div class="empty-icon">🔒</div>
          <h2>无法查看</h2>
          <p>{{ errorMsg }}</p>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon">📄</div>
          <h2>正在加载分享内容...</h2>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import { shareApi } from '@/api'
import { renderMarkdown, formatTime } from '@/utils/markdown'
import OutlineList from '@/components/OutlineList.vue'

const route = useRoute()
const themeStore = useThemeStore()
const toast = useToastStore()

const password = ref('')
const loaded = ref(false)
const needsPassword = ref(false)
const errorMsg = ref('')
const ownerName = ref('加载中...')
const noteTitle = ref('分享笔记')
const noteMarkdown = ref('')
const noteUpdatedAt = ref(null)
const accessType = ref('')
const expiresAt = ref(null)
const sidebarOpen = ref(false)
const isMobile = ref(window.innerWidth <= 768)

const renderedHtml = computed(() => renderMarkdown(noteMarkdown.value))
const accessLabel = computed(() => accessType.value === 'PASSWORD' ? '私密' : '公开')
const expiresLabel = computed(() => formatTime(expiresAt.value))

async function loadShare() {
  const token = route.params.token
  try {
    const data = await shareApi.open(token, password.value || undefined)
    // Apply owner's theme
    if (data.owner?.theme) {
      const resolved = themeStore.resolveTheme(data.owner.theme)
      themeStore.apply(resolved)
    }
    ownerName.value = data.owner?.username || '未知'
    noteTitle.value = data.note.title
    noteMarkdown.value = data.note.markdownContent
    noteUpdatedAt.value = data.note.updatedAt
    accessType.value = data.share.accessType
    expiresAt.value = data.share.expiresAt
    loaded.value = true
    needsPassword.value = false
    errorMsg.value = ''
  } catch (err) {
    errorMsg.value = err.message
    if (err.message?.includes('密码')) needsPassword.value = true
    toast.error(err.message)
  }
}

onMounted(async () => {
  themeStore.loadCached()
  window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 768 })
  await loadShare()
})
</script>

<style scoped>
.share-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--sl-bg);
}
.share-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--sl-sidebar-bg);
  border-right: 1px solid var(--sl-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.share-sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 16px;
  gap: 20px;
  overflow-y: auto;
}
.share-sidebar-header {}
.share-badge {
  display: inline-flex;
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-primary);
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--sl-primary-light);
  margin-bottom: 8px;
}
.share-author { font-size: 16px; font-weight: 600; margin: 0 0 4px; }
.share-hint { font-size: 12px; color: var(--sl-text-tertiary); margin: 0; line-height: 1.4; }
.share-outline-section h3, .share-password-section h3 { font-size: 13px; font-weight: 600; margin: 0 0 10px; }
.share-password-section { padding: 16px; }

.share-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--sl-bg-secondary);
}
.share-topbar {
  padding: 16px 24px;
  border-bottom: 1px solid var(--sl-border);
  background: var(--sl-card);
}
.share-title { font-size: 18px; font-weight: 600; margin: 0; }
.share-meta { display: flex; gap: 8px; margin-top: 6px; }
.share-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60%;
  text-align: center;
  color: var(--sl-text-secondary);
}
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h2 { font-size: 18px; font-weight: 600; margin-bottom: 8px; }
.empty-state p { font-size: 13px; color: var(--sl-text-tertiary); }

.share-sidebar-toggle {
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 60;
  width: 36px;
  height: 36px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
  background: var(--sl-card);
  box-shadow: var(--sl-shadow-card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--sl-text);
}

@media (max-width: 768px) {
  .share-sidebar {
    position: fixed;
    inset: 0;
    z-index: 55;
    width: 100%;
    min-width: unset;
    background: var(--sl-backdrop);
    border-right: none;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .share-sidebar.open { transform: translateX(0); }
  .share-sidebar.open .share-sidebar-inner {
    width: 300px;
    max-width: 85vw;
    background: var(--sl-sidebar-bg);
    height: 100%;
    box-shadow: var(--sl-shadow-flyout);
  }
  .share-topbar { padding: 16px 16px 16px 56px; }
  .share-content { padding: 16px; }
}
</style>

