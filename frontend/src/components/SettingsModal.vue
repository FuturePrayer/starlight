<template>
  <PopupLayer
    title="设置中心"
    width="min(960px, calc(100vw - 24px))"
    @close="$emit('close')"
  >
    <div class="settings-layout">
      <aside class="settings-nav">
        <button
          v-for="item in navItems"
          :key="item.id"
          :class="['settings-nav__item', { active: currentTab === item.id }]"
          @click="currentTab = item.id"
        >
          <span class="settings-nav__title">{{ item.title }}</span>
          <span class="settings-nav__desc">{{ item.desc }}</span>
        </button>
      </aside>

      <section class="settings-content">
        <div v-if="currentTab === 'profile'" class="settings-panel">
          <div class="settings-panel__header">
            <h3>个人资料</h3>
            <p>修改昵称与登录密码。为保证安全，提交时需要输入当前密码。</p>
          </div>
          <div class="settings-form-grid">
            <div class="form-field settings-form-field settings-form-field--full">
              <label class="sl-label">邮箱（不可修改）</label>
              <input :value="authStore.profile?.email" class="sl-input" disabled />
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">用户名</label>
              <input v-model="newUsername" class="sl-input" placeholder="输入新用户名" />
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">当前密码 <span class="required">*</span></label>
              <input v-model="currentPassword" type="password" class="sl-input" placeholder="确认身份" autocomplete="current-password" />
            </div>
            <div class="form-field settings-form-field settings-form-field--full">
              <label class="sl-label">新密码</label>
              <input v-model="newPassword" type="password" class="sl-input" placeholder="不修改请留空" autocomplete="new-password" />
            </div>
          </div>
          <div class="settings-actions">
            <button class="sl-btn sl-btn--primary" :disabled="profileSaving" @click="handleProfileSave">
              {{ profileSaving ? '保存中...' : '保存资料' }}
            </button>
          </div>
        </div>

        <div v-else-if="currentTab === 'security'" class="settings-panel">
          <div class="settings-panel__header">
            <h3>安全设置</h3>
            <p>管理两步验证与通行密钥，提升账户登录安全性。</p>
          </div>

          <section class="settings-section-card">
            <div class="settings-section-card__header">
              <div>
                <h4>两步验证（TOTP）</h4>
                <p>登录时额外验证 6 位动态验证码。</p>
              </div>
              <span v-if="authStore.totpBound" class="sl-badge">已绑定</span>
            </div>
            <p v-if="!totpGlobalEnabled" class="field-hint">管理员尚未开启两步验证功能。</p>
            <template v-else>
              <template v-if="authStore.totpBound && !totpSetupData">
                <div class="status-ok">✓ 已开启两步验证</div>
                <button class="sl-btn sl-btn--danger sl-btn--sm" @click="handleTotpRevoke">解除绑定</button>
              </template>
              <template v-else-if="totpSetupData">
                <p class="field-hint">使用认证器应用扫描二维码，然后输入 6 位验证码完成绑定。</p>
                <div class="qr-container">
                  <img v-if="totpQrDataUrl" :src="totpQrDataUrl" alt="TOTP QR Code" class="qr-img" />
                </div>
                <div class="form-field settings-form-field">
                  <label class="sl-label">密钥（点击复制）</label>
                  <input :value="totpSetupData.secret" class="sl-input security-secret-input" readonly @click="copyText(totpSetupData.secret)" />
                </div>
                <div class="form-field settings-form-field">
                  <label class="sl-label">验证码</label>
                  <input v-model="totpCode" class="sl-input" placeholder="输入 6 位验证码" maxlength="6" @keyup.enter="handleTotpConfirm" />
                </div>
                <div class="settings-actions settings-actions--inline">
                  <button class="sl-btn sl-btn--primary" @click="handleTotpConfirm" :disabled="!totpCode">确认绑定</button>
                  <button class="sl-btn" @click="cancelTotpSetup">取消</button>
                </div>
              </template>
              <template v-else>
                <button class="sl-btn sl-btn--primary sl-btn--sm" @click="handleTotpSetup">开始设置</button>
              </template>
            </template>
          </section>

          <section class="settings-section-card">
            <div class="settings-section-card__header">
              <div>
                <h4>通行密钥</h4>
                <p>可替代密码登录，并在兼容设备上获得更顺滑的验证体验。</p>
              </div>
              <span class="sl-badge">{{ passkeys.length }} 个</span>
            </div>
            <p v-if="!passkeyGlobalEnabled" class="field-hint">
              管理员尚未开启通行密钥功能。
              <template v-if="authStore.isAdmin && !siteUrlHttps">
                通行密钥要求站点 URL 为 HTTPS。
              </template>
            </p>
            <template v-else>
              <div v-if="passkeys.length" class="passkey-list">
                <div v-for="pk in passkeys" :key="pk.id" class="passkey-item">
                  <div>
                    <div class="passkey-name">{{ pk.nickname }}</div>
                    <div class="passkey-date">创建于 {{ formatTime(pk.createdAt) }}</div>
                  </div>
                  <button class="sl-btn sl-btn--ghost sl-btn--sm passkey-delete-btn" @click="handlePasskeyDelete(pk.id)">删除</button>
                </div>
              </div>
              <div class="settings-actions">
                <button class="sl-btn sl-btn--primary" :disabled="registeringPasskey" @click="handlePasskeyRegister">
                  {{ registeringPasskey ? '注册中...' : '注册新通行密钥' }}
                </button>
              </div>
            </template>
          </section>
        </div>

        <div v-else-if="currentTab === 'apiKeys'" class="settings-panel">
          <div class="settings-panel__header">
            <h3>API Key / 集成权限</h3>
            <p>创建多个 API Key，并分别配置 MCP Server 的访问模式与目录范围。MCP Server 必须使用 API Key 访问。</p>
          </div>

          <div v-if="createdApiKey" class="created-key-box">
            <div class="created-key-box__title">新 API Key 已生成</div>
            <div class="created-key-box__desc">该明文只会展示一次，请立即复制保存。</div>
            <div class="created-key-box__value" @click="copyText(createdApiKey)">{{ createdApiKey }}</div>
          </div>

          <div class="api-key-workspace">
            <div class="api-key-list-card settings-section-card">
              <div class="settings-section-card__header">
                <div>
                  <h4>我的 API Key</h4>
                  <p>支持多个独立密钥，便于按工具或设备分开管理。</p>
                </div>
                <button class="sl-btn sl-btn--primary sl-btn--sm" @click="startCreateApiKey">新建 Key</button>
              </div>
              <div v-if="!apiKeys.length" class="empty-hint empty-hint--compact">还没有 API Key，先创建一个吧。</div>
              <div v-else class="api-key-list">
                <button
                  v-for="item in apiKeys"
                  :key="item.id"
                  :class="['api-key-item', { active: editingKeyId === item.id }]"
                  @click="selectApiKey(item)"
                >
                  <span class="api-key-item__title">{{ item.name }}</span>
                  <span class="api-key-item__meta">
                    <span>{{ item.keyPrefix }}...</span>
                    <span>{{ item.enabledFlag ? '启用中' : '已停用' }}</span>
                    <span>{{ item.readOnlyFlag ? '只读' : '可写' }}</span>
                  </span>
                  <span class="api-key-item__meta api-key-item__meta--secondary">
                    <span>{{ item.allowAllCategoriesFlag ? '全部目录' : `指定目录 ${item.scopeCategoryIds?.length || 0} 个` }}</span>
                    <span>{{ item.copyableFlag ? '支持受保护复制' : '旧版 Key 不可复制' }}</span>
                    <span>{{ item.lastUsedAt ? `最近使用 ${formatTime(item.lastUsedAt)}` : '尚未使用' }}</span>
                  </span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="currentTab === 'admin'" class="settings-panel">
          <div class="settings-panel__header">
            <h3>管理员设置</h3>
            <p>管理注册开关、登录安全能力、MCP Server 以及 Git 导入全局状态。</p>
          </div>
          <div class="settings-form-grid">
            <div class="form-field settings-form-field settings-form-field--full">
              <label class="sl-label">站点 URL</label>
              <input v-model="adminForm.shareBaseUrl" class="sl-input" placeholder="例如：https://notes.example.com" />
              <div class="field-hint">用于分享链接与通行密钥域名校验。启用通行密钥时必须为 HTTPS。</div>
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">注册开关</label>
              <label class="sl-switch-row">
                <input v-model="adminForm.registrationEnabled" type="checkbox" />
                <span>{{ adminForm.registrationEnabled ? '允许新用户注册' : '关闭公开注册' }}</span>
              </label>
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">TOTP</label>
              <label class="sl-switch-row">
                <input v-model="adminForm.totpEnabled" type="checkbox" />
                <span>{{ adminForm.totpEnabled ? '已开启' : '已关闭' }}</span>
              </label>
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">通行密钥</label>
              <label class="sl-switch-row">
                <input v-model="adminForm.passkeyEnabled" type="checkbox" />
                <span>{{ adminForm.passkeyEnabled ? '已开启' : '已关闭' }}</span>
              </label>
              <div v-if="adminForm.passkeyEnabled && !siteUrlHttps" class="field-hint field-hint--warning">当前站点 URL 不是 HTTPS，保存时将自动关闭通行密钥。</div>
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">MCP Server</label>
              <label class="sl-switch-row">
                <input v-model="adminForm.mcpEnabled" type="checkbox" />
                <span>{{ adminForm.mcpEnabled ? '已启用' : '已关闭' }}</span>
              </label>
              <div class="field-hint">默认端点为 <code>/api/mcp</code>。仅支持 API Key 访问，并同时受目录范围与只读/可写权限约束。</div>
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">Git 导入</label>
              <label class="sl-switch-row">
                <input v-model="adminForm.gitImportEnabled" type="checkbox" />
                <span>{{ adminForm.gitImportEnabled ? '已开启' : '已关闭' }}</span>
              </label>
              <div class="field-hint">新项目默认关闭。开启后用户才可以通过 Git 仓库导入笔记、保存仓库地址并设置自动同步。</div>
            </div>
            <div class="form-field settings-form-field">
              <label class="sl-label">Git 导入并发数</label>
              <input v-model.number="adminForm.gitImportMaxConcurrent" class="sl-input" type="number" />
              <div class="field-hint">0 或负数表示不限制。建议根据服务器性能设置一个较小值，防止恶意导入造成压力。</div>
            </div>
          </div>
          <div class="settings-actions">
            <button class="sl-btn sl-btn--primary" :disabled="adminSaving" @click="handleAdminSave">
              {{ adminSaving ? '保存中...' : '保存管理员设置' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </PopupLayer>

  <PopupLayer
    v-if="showApiKeyEditor"
    :title="editingKeyId ? '编辑 API Key' : '新建 API Key'"
    eyebrow="API Key / MCP"
    description="建议按设备、客户端或自动化任务拆分 Key，便于分别授权、停用与审计。"
    width="min(820px, calc(100vw - 24px))"
    @close="closeApiKeyEditor"
  >
    <div class="api-key-editor-layout">
      <div class="settings-form-grid">
        <div class="form-field settings-form-field settings-form-field--full">
          <label class="sl-label">名称 / 备注</label>
          <input v-model="apiKeyForm.name" class="sl-input" placeholder="例如：Claude Desktop / 手机自动化" />
        </div>
        <div class="form-field settings-form-field">
          <label class="sl-label">启用状态</label>
          <label class="sl-switch-row">
            <input v-model="apiKeyForm.enabledFlag" type="checkbox" :disabled="!editingKeyId" />
            <span>{{ apiKeyForm.enabledFlag ? '已启用' : '已停用' }}</span>
          </label>
          <div v-if="!editingKeyId" class="field-hint">新建后默认启用，如需停用可在保存后再次打开修改。</div>
        </div>
      </div>

      <div class="api-key-group-divider"></div>

      <div v-if="editingKeyId" class="api-key-copy-hint" :class="{ 'api-key-copy-hint--warning': !selectedApiKey?.copyableFlag || !hasProtectedCopyMethod }">
        <template v-if="!selectedApiKey?.copyableFlag">
          这个 API Key 创建于旧版本，数据库中没有保存明文，因此无法复制。
        </template>
        <template v-else-if="!hasProtectedCopyMethod">
          复制前必须先开启两步验证或至少注册一个通行密钥。
        </template>
        <template v-else>
          复制前需要先选择一种安全验证方式，并完成一次身份校验。
        </template>
      </div>

      <div class="settings-section-card api-key-subsection">
        <div class="settings-section-card__header api-key-subsection__header">
          <div>
            <h4>MCP Server</h4>
            <p>这里统一管理与 MCP 相关的访问模式、目录范围以及使用说明入口。</p>
          </div>
          <div class="api-key-subsection__actions">
            <button class="sl-btn sl-btn--ghost sl-btn--sm" type="button" @click="showMcpInfoModal = true">查看说明与工具</button>
            <button
              class="sl-btn sl-btn--ghost sl-btn--sm api-key-section-toggle"
              type="button"
              :title="mcpSectionExpanded ? '收起 MCP Server 区域' : '展开 MCP Server 区域'"
              :aria-label="mcpSectionExpanded ? '收起 MCP Server 区域' : '展开 MCP Server 区域'"
              :aria-expanded="mcpSectionExpanded"
              @click="mcpSectionExpanded = !mcpSectionExpanded"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline :points="mcpSectionExpanded ? '18 15 12 9 6 15' : '9 18 15 12 9 6'"></polyline>
              </svg>
            </button>
          </div>
        </div>

        <div v-if="mcpSectionExpanded" class="api-key-subsection__body">
          <div class="settings-form-grid">
            <div class="form-field settings-form-field">
              <label class="sl-label">访问模式</label>
              <label class="sl-switch-row">
                <input v-model="apiKeyForm.readOnlyFlag" type="checkbox" />
                <span>{{ apiKeyForm.readOnlyFlag ? '只读' : '可读写' }}</span>
              </label>
              <div class="field-hint">只读 Key 只能读取目录和笔记内容，不能创建、修改或删除数据。</div>
            </div>
            <div class="form-field settings-form-field settings-form-field--full">
              <label class="sl-label">目录范围</label>
              <label class="sl-switch-row">
                <input v-model="apiKeyForm.allowAllCategoriesFlag" type="checkbox" />
                <span>{{ apiKeyForm.allowAllCategoriesFlag ? '允许访问全部目录' : '仅允许访问选定目录及其子目录' }}</span>
              </label>
              <div class="field-hint">若关闭“全部目录”，则必须至少选择一个分类。MCP 会自动限制到所选分类及其子分类。</div>
            </div>
          </div>

          <div v-if="!apiKeyForm.allowAllCategoriesFlag" class="scope-selector">
            <DirectoryTree
              v-model="apiKeyForm.scopeCategoryIds"
              :items="categoryTreeItems"
              :multiple="true"
              title="可授权的分类目录"
              description="目录默认折叠。勾选某个分类后，会自动授权它及其子分类给当前 API Key。"
              empty-text="当前还没有可授权的分类目录"
            />
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="api-key-editor-footer">
        <button v-if="editingKeyId" class="sl-btn sl-btn--danger api-key-editor-footer__danger" :disabled="apiKeySaving" @click="handleDeleteApiKey(editingKeyId)">删除</button>
        <div class="api-key-editor-footer__actions">
          <button
            v-if="editingKeyId"
            class="sl-btn"
            :disabled="apiKeySaving || !selectedApiKey?.copyableFlag || !hasProtectedCopyMethod"
            @click="openApiKeyCopyDialog"
          >
            复制
          </button>
          <button class="sl-btn" :disabled="apiKeySaving" @click="closeApiKeyEditor">关闭</button>
          <button class="sl-btn sl-btn--primary" :disabled="apiKeySaving" @click="handleSaveApiKey">
            {{ apiKeySaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </template>
  </PopupLayer>

  <PopupLayer
    v-if="confirmState.visible"
    title="确认操作"
    tone="danger"
    :description="confirmState.message"
    width="min(360px, calc(100vw - 32px))"
    :close-on-backdrop="false"
    @close="confirmState.visible = false"
  >
    <template #footer>
      <button class="sl-btn" @click="confirmState.visible = false">取消</button>
      <button class="sl-btn sl-btn--danger" @click="handleConfirmOk">确定</button>
    </template>
  </PopupLayer>

  <PopupLayer
    v-if="promptState.visible"
    title="输入"
    :description="promptState.message"
    width="min(380px, calc(100vw - 32px))"
    :close-on-backdrop="false"
    @close="promptState.visible = false"
  >
    <div class="form-field" style="margin-top:4px">
      <input v-model="promptState.value" class="sl-input" @keyup.enter="handlePromptOk" />
    </div>
    <template #footer>
      <button class="sl-btn" @click="promptState.visible = false">取消</button>
      <button class="sl-btn sl-btn--primary" @click="handlePromptOk">确定</button>
    </template>
  </PopupLayer>

  <PopupLayer
    v-if="apiKeyCopyState.visible"
    title="验证后复制 API Key"
    eyebrow="安全验证"
    width="min(420px, calc(100vw - 32px))"
    :close-on-backdrop="!apiKeyCopyState.busy"
    @close="closeApiKeyCopyDialog"
  >
    <div class="api-key-copy-dialog">
      <div class="field-hint">目标 Key：{{ apiKeyCopyState.keyName || '未命名 API Key' }}</div>
      <div v-if="apiKeyCopyState.step === 'pick'" class="api-key-copy-methods">
        <button v-if="authStore.totpBound" class="sl-btn" :disabled="apiKeyCopyState.busy" @click="apiKeyCopyState.step = 'totp'">使用两步验证</button>
        <button v-if="passkeys.length" class="sl-btn sl-btn--primary" :disabled="apiKeyCopyState.busy" @click="handleCopyApiKeyWithPasskey">使用通行密钥</button>
      </div>
      <div v-else class="form-field">
        <label class="sl-label">两步验证码</label>
        <input v-model="apiKeyCopyState.totpCode" class="sl-input" maxlength="6" placeholder="输入 6 位验证码" @keyup.enter="handleCopyApiKeyWithTotp" />
      </div>
    </div>
    <template #footer>
      <button class="sl-btn" :disabled="apiKeyCopyState.busy" @click="closeApiKeyCopyDialog">取消</button>
      <button v-if="apiKeyCopyState.step === 'totp'" class="sl-btn sl-btn--primary" :disabled="apiKeyCopyState.busy || !apiKeyCopyState.totpCode" @click="handleCopyApiKeyWithTotp">
        {{ apiKeyCopyState.busy ? '验证中...' : '验证并复制' }}
      </button>
    </template>
  </PopupLayer>

  <PopupLayer
    v-if="showMcpInfoModal"
    title="MCP Server 说明"
    eyebrow="API Key / MCP"
    description="为桌面 AI 客户端和自动化流程提供无状态 MCP 接口。以下工具都会受到 API Key 启用状态、目录范围与只读权限控制。"
    width="min(860px, calc(100vw - 24px))"
    @close="showMcpInfoModal = false"
  >
    <div class="mcp-info-layout">
      <section class="settings-section-card">
        <div class="settings-section-card__header">
          <div>
            <h4>接入方式</h4>
            <p>适合 MCP 客户端、脚本或自动化平台，通过 Streamable HTTP 调用。</p>
          </div>
        </div>
        <div class="mcp-summary-grid">
          <article class="mcp-summary-card">
            <div class="mcp-summary-card__title">访问端点</div>
            <code>/api/mcp</code>
          </article>
          <article class="mcp-summary-card">
            <div class="mcp-summary-card__title">鉴权方式</div>
            <div>仅支持 API Key（Bearer 或 <code>X-API-Key</code>）</div>
          </article>
          <article class="mcp-summary-card">
            <div class="mcp-summary-card__title">权限边界</div>
            <div>目录白名单自动包含子目录，并严格防止越权访问</div>
          </article>
        </div>
        <ul class="mcp-notes-list">
          <li>管理员关闭 MCP Server 后，所有 API Key 都无法访问 MCP 端点。</li>
          <li>只读 API Key 仅可调用标记为“只读可用”的工具。</li>
          <li>当前 Spring AI MCP 服务端暂不支持按请求动态裁剪 <code>tools/list</code>，因此只读 Key 仍可能看到写工具，但服务端会严格拒绝实际写调用。</li>
          <li><code>starlight_list_tree</code> 会额外返回 <code>rootCategoryId</code>、<code>rootCategoryName</code>、<code>virtualRoot</code> 与 <code>scopeHints</code>，用于告诉 AI 当前是否处于权限根、虚拟根或边界容器。</li>
          <li>即使查询深度不足，目录节点仍会返回子目录基础元信息；若未继续展开，会标记 <code>childrenTruncated = true</code>，提示客户端继续下钻。</li>
          <li>可写 API Key 仍然只能访问其授权目录及其子目录中的分类和笔记。</li>
        </ul>
      </section>

      <section class="settings-section-card">
        <div class="settings-section-card__header">
          <div>
            <h4>已提供的工具</h4>
            <p>建议先调用目录树工具获取分类与笔记 ID，再执行内容读取、搜索或写入操作。</p>
          </div>
        </div>
        <div class="mcp-tool-list">
          <article v-for="tool in mcpTools" :key="tool.name" class="mcp-tool-card">
            <div class="mcp-tool-card__header">
              <code>{{ tool.name }}</code>
              <span :class="['mcp-tool-badge', tool.readOnly ? 'mcp-tool-badge--readonly' : 'mcp-tool-badge--write']">
                {{ tool.readOnly ? '只读可用' : '只读不可用' }}
              </span>
            </div>
            <div class="mcp-tool-card__desc">{{ tool.description }}</div>
            <div class="mcp-tool-card__meta">{{ tool.meta }}</div>
          </article>
        </div>
      </section>
    </div>
  </PopupLayer>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi, apiKeyApi, authApi, base64urlToBuffer, bufferToBase64url } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import DirectoryTree from '@/components/DirectoryTree.vue'
import { formatTime } from '@/utils/markdown'
import { buildCategorySelectionTree } from '@/utils/directoryTree'
import { generateQrDataUrl } from '@/utils/qrcode'
import PopupLayer from '@/components/PopupLayer.vue'

const props = defineProps({
  treeItems: {
    type: Array,
    default: () => []
  },
  initialTab: {
    type: String,
    default: 'profile'
  }
})

const authStore = useAuthStore()
const toast = useToastStore()

const currentTab = ref(props.initialTab)
watch(() => props.initialTab, value => {
  currentTab.value = value || 'profile'
})

const navItems = computed(() => {
  const items = [
    { id: 'profile', title: '个人资料', desc: '昵称、密码' },
    { id: 'security', title: '安全设置', desc: 'TOTP、通行密钥' },
    { id: 'apiKeys', title: 'API Key', desc: '集成权限与目录范围' }
  ]
  if (authStore.isAdmin) {
    items.push({ id: 'admin', title: '管理员', desc: '注册、MCP、Git 导入' })
  }
  return items
})

const categoryTreeItems = computed(() => buildCategorySelectionTree(props.treeItems))
const availableCategoryIds = computed(() => {
  const result = []
  function walk(items) {
    for (const item of items || []) {
      result.push(item.id)
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(categoryTreeItems.value)
  return result
})

const newUsername = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const profileSaving = ref(false)

const totpGlobalEnabled = ref(false)
const passkeyGlobalEnabled = ref(false)
const siteUrlHttps = ref(false)
const totpSetupData = ref(null)
const totpQrDataUrl = ref('')
const totpCode = ref('')
const passkeys = ref([])
const registeringPasskey = ref(false)

const apiKeys = ref([])
const editingKeyId = ref(null)
const apiKeySaving = ref(false)
const createdApiKey = ref('')
const apiKeyForm = ref(createApiKeyForm())
const showApiKeyEditor = ref(false)
const mcpSectionExpanded = ref(false)
const apiKeyCopyState = ref({
  visible: false,
  apiKeyId: '',
  keyName: '',
  step: 'pick',
  totpCode: '',
  busy: false
})

const adminSaving = ref(false)
const adminForm = ref({
  registrationEnabled: false,
  shareBaseUrl: '',
  totpEnabled: false,
  passkeyEnabled: false,
  mcpEnabled: false,
  gitImportEnabled: false,
  gitImportMaxConcurrent: 2
})
const showMcpInfoModal = ref(false)

const mcpTools = [
  {
    name: 'starlight_list_tree',
    description: '查询权限范围内的分类目录和笔记树，可按起始分类与深度裁剪，并返回权限根与继续查询提示。',
    meta: '参数：categoryId（可选）、depth（默认 2）；返回：rootCategoryId、rootCategoryName、virtualRoot、scopeHints',
    readOnly: true
  },
  {
    name: 'starlight_get_note_content',
    description: '根据笔记 ID 读取 Markdown 原文、大纲和基础元数据。',
    meta: '参数：noteId',
    readOnly: true
  },
  {
    name: 'starlight_search_note_content',
    description: '在授权目录内全文搜索标题与正文，返回命中摘要。',
    meta: '参数：keyword、offset（可选）、limit（可选，最大 50）',
    readOnly: true
  },
  {
    name: 'starlight_create_category',
    description: '创建新的分类目录。',
    meta: '参数：name、parentId（可选）',
    readOnly: false
  },
  {
    name: 'starlight_update_category',
    description: '修改分类名称，或将分类移动到新的父目录。',
    meta: '参数：categoryId、name、parentId（可选）',
    readOnly: false
  },
  {
    name: 'starlight_delete_category',
    description: '删除一个空分类目录。',
    meta: '参数：categoryId',
    readOnly: false
  },
  {
    name: 'starlight_create_note',
    description: '在根目录或指定分类下创建 Markdown 笔记。',
    meta: '参数：title（可选）、markdownContent、categoryId（可选）',
    readOnly: false
  },
  {
    name: 'starlight_update_note',
    description: '修改笔记标题、Markdown 内容或所属分类。',
    meta: '参数：noteId、title（可选）、markdownContent、categoryId（可选）',
    readOnly: false
  },
  {
    name: 'starlight_delete_note',
    description: '将笔记移动到回收站。',
    meta: '参数：noteId',
    readOnly: false
  }
]

const confirmState = ref({ visible: false, message: '', callback: null })
const promptState = ref({ visible: false, message: '', value: '', callback: null })
const selectedApiKey = computed(() => apiKeys.value.find(item => item.id === editingKeyId.value) || null)
const hasProtectedCopyMethod = computed(() => authStore.totpBound || passkeys.value.length > 0)

watch(() => authStore.profile, profile => {
  newUsername.value = profile?.username || ''
}, { immediate: true })

onMounted(async () => {
  await Promise.allSettled([
    loadSecurityState(),
    loadApiKeys(),
    loadAdminSettings()
  ])
})

function createApiKeyForm() {
  return {
    name: '',
    enabledFlag: true,
    readOnlyFlag: true,
    allowAllCategoriesFlag: true,
    scopeCategoryIds: []
  }
}

async function loadSecurityState() {
  try {
    const status = await authApi.registrationStatus()
    passkeyGlobalEnabled.value = Boolean(status.passkeyEnabled)
  } catch {}
  totpGlobalEnabled.value = true
  try {
    passkeys.value = await authApi.passkeyList()
  } catch {}
  if (authStore.isAdmin) {
    try {
      const settings = await adminApi.getSettings()
      totpGlobalEnabled.value = Boolean(settings.totpEnabled)
      passkeyGlobalEnabled.value = Boolean(settings.passkeyEnabled)
      siteUrlHttps.value = Boolean(settings.siteUrlHttps)
    } catch {}
  }
}

async function loadApiKeys() {
  try {
    apiKeys.value = await apiKeyApi.list()
    if (editingKeyId.value) {
      const current = apiKeys.value.find(item => item.id === editingKeyId.value)
      if (current) {
        fillApiKeyForm(current)
      }
    }
  } catch (err) {
    toast.error(err.message)
  }
}

async function loadAdminSettings() {
  if (!authStore.isAdmin) return
  try {
    const settings = await adminApi.getSettings()
    adminForm.value = {
      registrationEnabled: Boolean(settings.registrationEnabled),
      shareBaseUrl: settings.shareBaseUrl || '',
      totpEnabled: Boolean(settings.totpEnabled),
      passkeyEnabled: Boolean(settings.passkeyEnabled),
      mcpEnabled: Boolean(settings.mcpEnabled),
      gitImportEnabled: Boolean(settings.gitImportEnabled),
      gitImportMaxConcurrent: Number(settings.gitImportMaxConcurrent ?? 2)
    }
    siteUrlHttps.value = Boolean(settings.siteUrlHttps)
    totpGlobalEnabled.value = Boolean(settings.totpEnabled)
    passkeyGlobalEnabled.value = Boolean(settings.passkeyEnabled)
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleProfileSave() {
  profileSaving.value = true
  try {
    await authStore.updateProfile({
      username: newUsername.value,
      currentPassword: currentPassword.value,
      newPassword: newPassword.value || null
    })
    currentPassword.value = ''
    newPassword.value = ''
    toast.success('个人资料已更新')
  } catch (err) {
    toast.error(err.message)
  } finally {
    profileSaving.value = false
  }
}

async function handleTotpSetup() {
  try {
    const data = await authApi.totpSetup()
    totpSetupData.value = data
    totpCode.value = ''
    totpQrDataUrl.value = await generateQrDataUrl(data.otpAuthUri, 256)
  } catch (err) {
    if (err.message?.includes('尚未开启')) totpGlobalEnabled.value = false
    toast.error(err.message)
  }
}

async function handleTotpConfirm() {
  try {
    await authApi.totpConfirm(totpSetupData.value.secret, totpCode.value)
    cancelTotpSetup()
    await authStore.fetchMe()
    toast.success('两步验证已绑定')
  } catch (err) {
    toast.error(err.message)
  }
}

function cancelTotpSetup() {
  totpSetupData.value = null
  totpQrDataUrl.value = ''
  totpCode.value = ''
}

function handleTotpRevoke() {
  confirmState.value = {
    visible: true,
    message: '确定解除两步验证绑定？',
    callback: async () => {
      try {
        await authApi.totpRevoke()
        await authStore.fetchMe()
        toast.success('两步验证已解除')
      } catch (err) {
        toast.error(err.message)
      }
    }
  }
}

async function handlePasskeyRegister() {
  registeringPasskey.value = true
  try {
    const { handle, optionsJson } = await authApi.passkeyRegisterStart()
    const options = JSON.parse(optionsJson)
    const publicKey = {
      ...options,
      challenge: base64urlToBuffer(options.challenge),
      user: {
        ...options.user,
        id: base64urlToBuffer(options.user.id)
      },
      excludeCredentials: (options.excludeCredentials || []).map(c => ({
        ...c,
        id: base64urlToBuffer(c.id)
      }))
    }
    const credential = await navigator.credentials.create({ publicKey })
    const credentialResponse = {
      id: credential.id,
      rawId: bufferToBase64url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
        attestationObject: bufferToBase64url(credential.response.attestationObject)
      },
      clientExtensionResults: credential.getClientExtensionResults()
    }
    if (credential.response.getTransports) {
      credentialResponse.response.transports = credential.response.getTransports()
    }
    promptState.value = {
      visible: true,
      message: '为这个通行密钥取个名字：',
      value: '我的通行密钥',
      callback: async (nickname) => {
        try {
          await authApi.passkeyRegisterFinish({ handle, credential: credentialResponse, nickname: nickname || '通行密钥' })
          passkeys.value = await authApi.passkeyList()
          await authStore.fetchMe()
          toast.success('通行密钥已注册')
        } catch (err) {
          toast.error(err.message || '通行密钥注册失败')
        }
      }
    }
  } catch (err) {
    if (err.name !== 'AbortError' && err.name !== 'NotAllowedError') {
      toast.error(err.message || '通行密钥注册失败')
    }
  } finally {
    registeringPasskey.value = false
  }
}

function handlePasskeyDelete(id) {
  confirmState.value = {
    visible: true,
    message: '确定删除该通行密钥？',
    callback: async () => {
      try {
        await authApi.passkeyDelete(id)
        passkeys.value = await authApi.passkeyList()
        await authStore.fetchMe()
        toast.success('通行密钥已删除')
      } catch (err) {
        toast.error(err.message)
      }
    }
  }
}

function startCreateApiKey() {
  editingKeyId.value = null
  createdApiKey.value = ''
  apiKeyForm.value = createApiKeyForm()
  mcpSectionExpanded.value = false
  showApiKeyEditor.value = true
}

function fillApiKeyForm(item) {
  apiKeyForm.value = {
    name: item.name || '',
    enabledFlag: Boolean(item.enabledFlag),
    readOnlyFlag: Boolean(item.readOnlyFlag),
    allowAllCategoriesFlag: Boolean(item.allowAllCategoriesFlag),
    scopeCategoryIds: [...(item.scopeCategoryIds || [])].filter(id => availableCategoryIds.value.includes(id))
  }
}

function selectApiKey(item) {
  editingKeyId.value = item.id
  createdApiKey.value = ''
  fillApiKeyForm(item)
  mcpSectionExpanded.value = false
  showApiKeyEditor.value = true
}

function closeApiKeyEditor() {
  showApiKeyEditor.value = false
}

function openApiKeyCopyDialog() {
  if (!selectedApiKey.value?.copyableFlag) {
    toast.info('旧版 API Key 未保存明文，无法复制')
    return
  }
  if (!hasProtectedCopyMethod.value) {
    toast.info('请先开启两步验证或至少注册一个通行密钥')
    return
  }
  apiKeyCopyState.value = {
    visible: true,
    apiKeyId: selectedApiKey.value.id,
    keyName: selectedApiKey.value.name || '',
    step: authStore.totpBound && !passkeys.value.length ? 'totp' : 'pick',
    totpCode: '',
    busy: false
  }
}

function closeApiKeyCopyDialog(force = false) {
  if (apiKeyCopyState.value.busy && !force) return
  apiKeyCopyState.value = {
    visible: false,
    apiKeyId: '',
    keyName: '',
    step: 'pick',
    totpCode: '',
    busy: false
  }
}

async function handleCopyApiKeyWithTotp() {
  if (!apiKeyCopyState.value.apiKeyId) return
  apiKeyCopyState.value.busy = true
  try {
    const data = await apiKeyApi.copyWithTotp(apiKeyCopyState.value.apiKeyId, apiKeyCopyState.value.totpCode)
    copyText(data.apiKey, { silent: true })
    closeApiKeyCopyDialog(true)
    toast.success('API Key 已复制')
  } catch (err) {
    toast.error(err.message)
  } finally {
    apiKeyCopyState.value.busy = false
  }
}

async function handleCopyApiKeyWithPasskey() {
  if (!apiKeyCopyState.value.apiKeyId) return
  apiKeyCopyState.value.busy = true
  try {
    const { handle, optionsJson } = await apiKeyApi.copyPasskeyStart(apiKeyCopyState.value.apiKeyId)
    const options = JSON.parse(optionsJson)
    const publicKey = {
      ...options,
      challenge: base64urlToBuffer(options.challenge),
      allowCredentials: (options.allowCredentials || []).map(item => ({
        ...item,
        id: base64urlToBuffer(item.id)
      }))
    }
    const credential = await navigator.credentials.get({ publicKey })
    const credentialResponse = {
      id: credential.id,
      rawId: bufferToBase64url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
        authenticatorData: bufferToBase64url(credential.response.authenticatorData),
        signature: bufferToBase64url(credential.response.signature),
        userHandle: credential.response.userHandle ? bufferToBase64url(credential.response.userHandle) : null
      },
      clientExtensionResults: credential.getClientExtensionResults()
    }
    const data = await apiKeyApi.copyPasskeyFinish(apiKeyCopyState.value.apiKeyId, {
      handle,
      credential: credentialResponse
    })
    copyText(data.apiKey, { silent: true })
    closeApiKeyCopyDialog(true)
    toast.success('API Key 已复制')
  } catch (err) {
    if (err.name !== 'AbortError' && err.name !== 'NotAllowedError') {
      toast.error(err.message || '通行密钥验证失败')
    }
  } finally {
    apiKeyCopyState.value.busy = false
  }
}

async function handleSaveApiKey() {
  if (!apiKeyForm.value.name.trim()) {
    toast.error('请输入 API Key 名称')
    return
  }
  if (!apiKeyForm.value.allowAllCategoriesFlag && !apiKeyForm.value.scopeCategoryIds.length) {
    toast.error('请至少选择一个授权分类')
    return
  }
  apiKeySaving.value = true
  try {
    if (editingKeyId.value) {
      await apiKeyApi.update(editingKeyId.value, {
        name: apiKeyForm.value.name,
        enabledFlag: apiKeyForm.value.enabledFlag,
        readOnlyFlag: apiKeyForm.value.readOnlyFlag,
        allowAllCategoriesFlag: apiKeyForm.value.allowAllCategoriesFlag,
        scopeCategoryIds: apiKeyForm.value.scopeCategoryIds
      })
      toast.success('API Key 已更新')
      await loadApiKeys()
      closeApiKeyEditor()
    } else {
      const created = await apiKeyApi.create({
        name: apiKeyForm.value.name,
        readOnlyFlag: apiKeyForm.value.readOnlyFlag,
        allowAllCategoriesFlag: apiKeyForm.value.allowAllCategoriesFlag,
        scopeCategoryIds: apiKeyForm.value.scopeCategoryIds
      })
      createdApiKey.value = created.apiKey || ''
      editingKeyId.value = created.id
      await loadApiKeys()
      closeApiKeyEditor()
      toast.success('API Key 已创建')
    }
  } catch (err) {
    toast.error(err.message)
  } finally {
    apiKeySaving.value = false
  }
}

function handleDeleteApiKey(id) {
  confirmState.value = {
    visible: true,
    message: '确定删除这个 API Key？删除后依赖它的 MCP 客户端将无法继续访问。',
    callback: async () => {
      try {
        await apiKeyApi.delete(id)
        if (editingKeyId.value === id) {
          editingKeyId.value = null
          apiKeyForm.value = createApiKeyForm()
          closeApiKeyEditor()
        }
        await loadApiKeys()
        toast.success('API Key 已删除')
      } catch (err) {
        toast.error(err.message)
      }
    }
  }
}

async function handleAdminSave() {
  adminSaving.value = true
  try {
    const isHttps = String(adminForm.value.shareBaseUrl || '').toLowerCase().startsWith('https://')
    if (adminForm.value.passkeyEnabled && !isHttps) {
      toast.error('通行密钥要求站点 URL 为 HTTPS 协议')
      adminForm.value.passkeyEnabled = false
      return
    }
    const saved = await adminApi.saveSettings({ ...adminForm.value })
    adminForm.value = {
      registrationEnabled: Boolean(saved.registrationEnabled),
      shareBaseUrl: saved.shareBaseUrl || '',
      totpEnabled: Boolean(saved.totpEnabled),
      passkeyEnabled: Boolean(saved.passkeyEnabled),
      mcpEnabled: Boolean(saved.mcpEnabled),
      gitImportEnabled: Boolean(saved.gitImportEnabled),
      gitImportMaxConcurrent: Number(saved.gitImportMaxConcurrent ?? 2)
    }
    siteUrlHttps.value = Boolean(saved.siteUrlHttps)
    passkeyGlobalEnabled.value = Boolean(saved.passkeyEnabled)
    totpGlobalEnabled.value = Boolean(saved.totpEnabled)
    toast.success('管理员设置已保存')
  } catch (err) {
    toast.error(err.message)
  } finally {
    adminSaving.value = false
  }
}

function copyText(text, { silent = false } = {}) {
  if (!text) return
  navigator.clipboard?.writeText(text)
  if (!silent) {
    toast.info('已复制到剪贴板')
  }
}

function handleConfirmOk() {
  const cb = confirmState.value.callback
  confirmState.value.visible = false
  cb?.()
}

function handlePromptOk() {
  const cb = promptState.value.callback
  const val = promptState.value.value
  promptState.value.visible = false
  cb?.(val)
}
</script>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  min-height: 60vh;
}
.settings-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.settings-nav__item {
  border: 1px solid var(--sl-border);
  background: var(--sl-card);
  border-radius: var(--sl-radius-lg);
  padding: 12px;
  text-align: left;
  color: var(--sl-text);
  cursor: pointer;
  transition: border-color .15s, background .15s, transform .15s;
}
.settings-nav__item:hover {
  background: var(--sl-card-hover);
  border-color: var(--sl-border-strong);
}
.settings-nav__item.active {
  border-color: var(--sl-primary);
  background: var(--sl-selection);
}
.settings-nav__title {
  display: block;
  font-size: 14px;
  font-weight: 600;
}
.settings-nav__desc {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--sl-text-tertiary);
}
.settings-content {
  min-width: 0;
}
.settings-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.settings-panel__header h3 {
  margin: 0;
  font-size: 18px;
}
.settings-panel__header p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sl-text-secondary);
}
.settings-section-card {
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  background: linear-gradient(180deg, var(--sl-card) 0%, var(--sl-bg-secondary) 100%);
  padding: 16px;
  box-shadow: var(--sl-shadow-card);
}
.settings-section-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.settings-section-card__header h4 {
  margin: 0;
  font-size: 15px;
}
.settings-section-card__header p {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--sl-text-tertiary);
  line-height: 1.5;
}
.settings-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.settings-form-field {
  margin: 0;
}
.settings-form-field--full {
  grid-column: 1 / -1;
}
.settings-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}
.settings-actions--inline {
  margin-top: 0;
}
.settings-inline-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.sl-switch-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--sl-text);
}
.field-hint {
  font-size: 12px;
  color: var(--sl-text-tertiary);
  margin-top: 6px;
  line-height: 1.6;
}
.field-hint--warning {
  color: var(--sl-warning);
}
.required {
  color: var(--sl-danger);
}
.status-ok {
  font-size: 13px;
  color: var(--sl-success);
  font-weight: 600;
  margin-bottom: 10px;
}
.qr-container {
  text-align: center;
  padding: 8px 0;
}
.qr-img {
  width: 200px;
  height: 200px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
}
.security-secret-input {
  cursor: pointer;
  font-family: var(--sl-font-mono);
  font-size: 12px;
}
.passkey-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.passkey-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
  background: var(--sl-card);
}
.passkey-name {
  font-size: 13px;
  font-weight: 600;
}
.passkey-date {
  margin-top: 4px;
  font-size: 11px;
  color: var(--sl-text-tertiary);
}
.passkey-delete-btn {
  color: var(--sl-danger);
}
.api-key-workspace {
  display: block;
}
.api-key-list-card {
  width: 100%;
}
.api-key-list,
.scope-selector {
  gap: 8px;
}
.api-key-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.scope-selector {
  display: flex;
  flex-direction: column;
}
.api-key-item {
  width: 100%;
  border: 1px solid var(--sl-border);
  background: var(--sl-card);
  border-radius: var(--sl-radius);
  padding: 12px;
  text-align: left;
  cursor: pointer;
  transition: border-color .15s, background .15s;
}
.api-key-item:hover {
  background: var(--sl-card-hover);
}
.api-key-item.active {
  border-color: var(--sl-primary);
  background: var(--sl-selection);
}
.api-key-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text);
}
.api-key-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--sl-text-secondary);
}
.api-key-item__meta--secondary {
  color: var(--sl-text-tertiary);
}
.created-key-box {
  padding: 14px;
  border-radius: var(--sl-radius-lg);
  border: 1px solid var(--sl-primary);
  background: color-mix(in srgb, var(--sl-primary) 10%, var(--sl-card));
}
.created-key-box__title {
  font-size: 14px;
  font-weight: 600;
}
.created-key-box__desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--sl-text-secondary);
}
.created-key-box__value {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--sl-radius);
  background: var(--sl-bg);
  border: 1px dashed var(--sl-border-strong);
  font-family: var(--sl-font-mono);
  font-size: 12px;
  word-break: break-all;
  cursor: pointer;
}
.api-key-group-divider {
  height: 1px;
  margin: 18px 0;
  background: linear-gradient(90deg, transparent, var(--sl-border-strong), transparent);
}
.api-key-editor-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.api-key-copy-hint {
  padding: 12px 14px;
  border-radius: var(--sl-radius-lg);
  border: 1px solid color-mix(in srgb, var(--sl-primary) 24%, var(--sl-border));
  background: color-mix(in srgb, var(--sl-primary) 8%, var(--sl-card));
  font-size: 12px;
  line-height: 1.7;
  color: var(--sl-text-secondary);
}
.api-key-copy-hint--warning {
  border-color: color-mix(in srgb, var(--sl-warning) 28%, var(--sl-border));
  background: color-mix(in srgb, var(--sl-warning) 10%, var(--sl-card));
}
.api-key-subsection {
  margin-top: 0;
  padding: 14px;
}
.api-key-subsection__header {
  margin-bottom: 0;
}
.api-key-subsection__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.api-key-section-toggle {
  width: 32px;
  min-width: 32px;
  height: 32px;
  padding: 0;
  justify-content: center;
}
.api-key-subsection__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
}
.api-key-editor-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.api-key-editor-footer__danger {
  margin-right: auto;
}
.api-key-editor-footer__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.api-key-copy-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.api-key-copy-methods {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.empty-hint--compact {
  padding: 18px 12px;
}
.mcp-info-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mcp-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.mcp-summary-card,
.mcp-tool-card {
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
  background: var(--sl-card);
  padding: 12px;
}
.mcp-summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--sl-text-secondary);
}
.mcp-summary-card__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text);
}
.mcp-summary-card code,
.mcp-tool-card code,
.field-hint code {
  font-family: var(--sl-font-mono);
}
.mcp-notes-list {
  margin: 12px 0 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--sl-text-secondary);
  line-height: 1.7;
}
.mcp-tool-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.mcp-tool-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.mcp-tool-card__desc {
  margin-top: 10px;
  font-size: 13px;
  color: var(--sl-text);
  line-height: 1.6;
}
.mcp-tool-card__meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--sl-text-tertiary);
  line-height: 1.6;
}
.mcp-tool-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.mcp-tool-badge--readonly {
  background: color-mix(in srgb, var(--sl-success) 14%, var(--sl-card));
  color: var(--sl-success);
}
.mcp-tool-badge--write {
  background: color-mix(in srgb, var(--sl-warning) 18%, var(--sl-card));
  color: var(--sl-warning);
}

@media (max-width: 900px) {
  .settings-layout,
  .api-key-workspace {
    grid-template-columns: 1fr;
  }
  .mcp-summary-grid,
  .mcp-tool-list {
    grid-template-columns: 1fr;
  }
  .settings-nav {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 4px;
  }
  .settings-nav__item {
    min-width: 160px;
  }
}

@media (max-width: 640px) {
  .api-key-list,
  .settings-form-grid {
    grid-template-columns: 1fr;
  }
  .settings-inline-field,
  .passkey-item,
  .settings-section-card__header,
  .api-key-editor-footer,
  .api-key-editor-footer__actions,
  .api-key-subsection__actions {
    flex-direction: column;
    align-items: stretch;
  }
  .settings-actions {
    flex-direction: column;
  }
  .settings-actions .sl-btn,
  .api-key-editor-footer .sl-btn,
  .api-key-editor-footer__actions .sl-btn,
  .api-key-subsection__actions .sl-btn {
    width: 100%;
    justify-content: center;
  }
  .api-key-editor-footer__danger,
  .api-key-editor-footer__actions {
    margin-left: 0;
    margin-right: 0;
  }
}
</style>


