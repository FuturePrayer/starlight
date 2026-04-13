<template>
  <div class="app-shell">
    <!-- Mobile sidebar toggle -->
    <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen" v-if="isMobile">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
    </button>

    <!-- Sidebar -->
    <aside :class="['sidebar', { open: sidebarOpen, resizing: isSidebarResizing }]" :style="sidebarStyle" @click.self="sidebarOpen = false">
      <div class="sidebar-inner">
        <div v-if="isMobile" class="sidebar-mobile-chrome sl-card">
          <div>
            <div class="sidebar-mobile-chrome__title">{{ mobileSidebarTitle }}</div>
            <div class="sidebar-mobile-chrome__meta">目录、回收站与当前大纲</div>
          </div>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" type="button" @click="sidebarOpen = false" title="关闭侧边栏">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <!-- Profile section -->
        <div class="sidebar-profile">
          <div class="profile-info" style="cursor:pointer" @click="openSettings('profile')" title="打开设置中心">
            <div class="profile-avatar">{{ authStore.username?.charAt(0)?.toUpperCase() || '?' }}</div>
            <div>
              <div class="profile-name">{{ authStore.username }}</div>
              <div class="profile-role">{{ authStore.isAdmin ? '管理员' : '用户' }}</div>
            </div>
          </div>
          <div class="profile-actions">
            <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="openSettings('security')" title="安全与集成设置">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            </button>
            <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="handleLogout" title="退出登录">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            </button>
          </div>
        </div>

        <!-- Theme selector -->
        <div class="sidebar-section">
          <label class="sl-label">主题</label>
          <select class="sl-select" :value="themeStore.currentId" @change="handleThemeChange($event.target.value)">
            <option v-for="t in allThemes" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </div>

        <!-- Action buttons -->
        <div class="sidebar-actions">
          <button class="sl-btn sl-btn--primary" @click="handleNewNote" style="flex:1">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            新建笔记
          </button>
          <button class="sl-btn" @click="showCategoryModal = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
          </button>
          <button v-if="authStore.isAdmin" class="sl-btn" @click="openSettings('admin')" title="管理员设置">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
          </button>
        </div>
        <div class="sidebar-tools">
          <button class="sl-btn sidebar-tools__btn" @click="showSearchModal = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            搜索
          </button>
          <button class="sl-btn sidebar-tools__btn" @click="showImportExportModal = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            导入 / 导出
          </button>
        </div>

        <!-- Sidebar tabs -->
        <div class="sidebar-tabs">
          <button :class="['tab-btn', { active: sidebarTab === 'tree' }]" @click="sidebarTab = 'tree'">目录</button>
          <button :class="['tab-btn', { active: sidebarTab === 'trash' }]" @click="handleTrashTabClick">
            回收站
            <span v-if="noteStore.tree.trashCount" class="tab-count">{{ noteStore.tree.trashCount }}</span>
          </button>
          <button :class="['tab-btn', { active: sidebarTab === 'outline' }]" @click="sidebarTab = 'outline'">大纲</button>
        </div>

        <!-- Tree / Trash / Outline panels -->
        <div class="sidebar-scroll">
          <div v-show="sidebarTab === 'tree'" class="tree-panel">
            <MobileTreeBrowser
              v-if="isMobile"
              :items="noteStore.tree.items"
              :pinned-items="noteStore.tree.pinnedItems"
              :path="mobileTreePath"
              :selected-id="noteStore.currentNote?.id"
              :selected-category-id="selectedCategoryId"
              root-title="全部笔记"
              empty-text="还没有笔记，点击上方按钮创建"
              @navigate="handleMobileTreeNavigate"
              @select-note="handleOpenNote"
              @select-category="handleMobileTreeSelectCategory"
            >
              <template #actions="{ item, isCategory }">
                <template v-if="isCategory">
                  <button class="sl-btn sl-btn--ghost sl-btn--sm" type="button" @click.stop="handleOpenSiteModal(item.id)" title="星迹书阁设置">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/></svg>
                  </button>
                  <button class="sl-btn sl-btn--ghost sl-btn--sm mobile-tree-action--danger" type="button" @click.stop="handleDeleteCategoryRequest(item.id)" title="删除分类">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                  </button>
                </template>
              </template>
            </MobileTreeBrowser>
            <template v-else>
              <section v-if="noteStore.tree.pinnedItems?.length" class="quick-section">
                <div class="quick-section__header">
                  <span class="quick-section__title">置顶</span>
                  <span class="quick-section__hint">目录最上方</span>
                </div>
                <div class="quick-section__list">
                  <TreeNode
                    v-for="item in noteStore.tree.pinnedItems"
                    :key="`pinned-${item.id}`"
                    :item="item"
                    :selected-id="noteStore.currentNote?.id"
                    :expanded-ids="noteStore.expandedCategoryIds"
                    @select-note="handleOpenNote"
                  />
                </div>
              </section>
              <TreeNode
                v-for="item in noteStore.tree.items"
                :key="item.id"
                :item="item"
                :selected-id="noteStore.currentNote?.id"
                :expanded-ids="noteStore.expandedCategoryIds"
                @select-note="handleOpenNote"
                @select-category="selectedCategoryId = $event"
                @toggle-category="handleToggleCategory"
                @open-site="handleOpenSiteModal"
                @delete-category="handleDeleteCategoryRequest"
              />
              <div
                v-if="!noteStore.tree.items?.length && !noteStore.tree.pinnedItems?.length"
                class="empty-hint"
              >
                还没有笔记，点击上方按钮创建
              </div>
            </template>
          </div>

          <div v-show="sidebarTab === 'trash'" class="trash-panel">
            <div class="trash-panel__header">
              <div>
                <div class="quick-section__title">回收站</div>
                <div class="quick-section__hint">删除后保留 30 天，可恢复或彻底删除</div>
              </div>
              <span class="sl-badge">{{ noteStore.trashTree.totalCount || 0 }} 项</span>
            </div>
            <MobileTreeBrowser
              v-if="isMobile && noteStore.trashTree.totalCount"
              :items="noteStore.trashTree.items"
              :path="mobileTrashPath"
              :selected-id="isDeletedNote ? noteStore.currentNote?.id : null"
              :selected-category-id="selectedTrashCategoryId"
              root-title="回收站"
              empty-text="回收站还是空的，误删的笔记和分类会先来到这里"
              @navigate="handleMobileTrashNavigate"
              @select-note="handleOpenTrashNote"
              @select-category="handleMobileTrashSelectCategory"
            >
              <template #actions="{ item, isCategory }">
                <button
                  v-if="!isCategory"
                  class="sl-btn sl-btn--ghost sl-btn--sm"
                  type="button"
                  :disabled="item.restorable === false"
                  @click.stop="handleRestore(item.id)"
                  title="恢复笔记"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 109-9 9.75 9.75 0 00-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                </button>
                <button
                  v-if="isCategory"
                  class="sl-btn sl-btn--ghost sl-btn--sm"
                  type="button"
                  :disabled="item.restorable === false"
                  @click.stop="handleRestoreTrashCategory(item.id)"
                  title="恢复分类"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 109-9 9.75 9.75 0 00-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                </button>
                <button
                  v-if="!isCategory"
                  class="sl-btn sl-btn--ghost sl-btn--sm mobile-tree-action--danger"
                  type="button"
                  @click.stop="handlePurge(item.id)"
                  title="彻底删除笔记"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                </button>
                <button
                  v-if="isCategory"
                  class="sl-btn sl-btn--ghost sl-btn--sm mobile-tree-action--danger"
                  type="button"
                  @click.stop="handlePurgeTrashCategoryRequest(item.id)"
                  title="彻底删除分类"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                </button>
              </template>
            </MobileTreeBrowser>
            <div v-else-if="!noteStore.trashTree.totalCount" class="empty-hint">回收站还是空的，误删的笔记和分类会先来到这里</div>
            <div v-else class="trash-tree-wrap">
              <TreeNode
                v-for="item in noteStore.trashTree.items"
                :key="`trash-${item.id}`"
                :item="item"
                :mode="'trash'"
                :selected-id="isDeletedNote ? noteStore.currentNote?.id : null"
                :selected-category-id="selectedTrashCategoryId"
                :expanded-ids="noteStore.trashExpandedCategoryIds"
                @select-note="handleOpenTrashNote"
                @select-category="selectedTrashCategoryId = $event"
                @toggle-category="handleToggleTrashCategory"
                @restore-note="handleRestore"
                @purge-note="handlePurge"
                @restore-category="handleRestoreTrashCategory"
                @purge-category="handlePurgeTrashCategoryRequest"
              />
            </div>
          </div>
          <div v-show="sidebarTab === 'outline'" class="outline-panel">
            <OutlineList
              :markdown="outlineSource"
              :active-anchor="activeOutlineAnchor"
              @select="handleOutlineSelect"
            />
          </div>
        </div>
      </div>
    </aside>
    <div v-if="!isMobile" class="sidebar-resize-handle" @pointerdown="startSidebarResize" @dblclick="resetSidebarWidth" />

    <!-- Main content -->
    <main class="main-content">
      <!-- Top bar -->
      <div class="topbar">
        <div class="topbar-left">
          <h1 class="topbar-title">{{ topbarTitle }}</h1>
          <div class="topbar-meta" v-if="noteStore.currentNote">
            <span class="sl-badge">{{ noteStore.editMode ? '编辑中' : '查看' }}</span>
            <span class="sl-badge" v-if="isDeletedNote">回收站</span>
            <span class="sl-badge" v-if="noteStore.currentNote.pinnedFlag">已置顶</span>
            <span class="sl-badge" v-if="noteStore.editMode">{{ noteStore.autosaveEnabled ? '自动保存开启' : '自动保存暂停' }}</span>
            <span class="sl-badge" v-if="isDeletedNote && noteStore.currentNote.purgeAt">{{ formatTime(noteStore.currentNote.purgeAt) }} 自动清理</span>
            <span class="sl-badge" v-if="noteStore.currentNote.updatedAt && !noteStore.editMode">{{ formatTime(noteStore.currentNote.updatedAt) }}</span>
          </div>
        </div>
        <div class="topbar-actions" v-if="!isMobile">
          <template v-if="noteStore.editMode">
            <button class="sl-btn" @click="togglePreview">{{ previewVisible ? '关闭预览' : '打开预览' }}</button>
            <button class="sl-btn" @click="toggleAutosave">{{ noteStore.autosaveEnabled ? '暂停自动保存' : '恢复自动保存' }}</button>
            <button class="sl-btn sl-btn--primary" @click="handleSave">保存</button>
            <button class="sl-btn" @click="handleDiscardExit">不保存退出</button>
            <button class="sl-btn" @click="handleFinish">完成</button>
          </template>
          <template v-else-if="noteStore.currentNote && !isDeletedNote">
            <button class="sl-btn" @click="handleTogglePinned">{{ noteStore.currentNote.pinnedFlag ? '取消置顶' : '置顶' }}</button>
            <button class="sl-btn" @click="handleShare" v-if="noteStore.currentNote.id">分享</button>
            <button class="sl-btn sl-btn--primary" @click="handleEnterEditMode">编辑</button>
            <button class="sl-btn sl-btn--danger" @click="handleDelete" v-if="noteStore.currentNote.id" title="删除笔记">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            </button>
          </template>
          <template v-else-if="noteStore.currentNote && isDeletedNote">
            <button class="sl-btn sl-btn--primary" @click="handleRestore(noteStore.currentNote.id)">恢复</button>
            <button class="sl-btn sl-btn--danger" @click="handlePurge(noteStore.currentNote.id)">彻底删除</button>
          </template>
          <button class="sl-btn" @click="showImportExportModal = true">导入 / 导出</button>
        </div>
      </div>

      <!-- Mobile floating action bar -->
      <div v-if="isMobile && noteStore.editMode" class="mobile-fab">
        <div :class="['fab-menu', { expanded: mobileActionsOpen }]">
          <button class="fab-toggle sl-btn sl-btn--primary" @click="mobileActionsOpen = !mobileActionsOpen">
            {{ mobileActionsOpen ? '收起' : '操作' }}
          </button>
          <template v-if="mobileActionsOpen">
            <button class="sl-btn" @click="togglePreview">{{ previewVisible ? '恢复输入' : '预览' }}</button>
            <button class="sl-btn" @click="toggleAutosave">{{ noteStore.autosaveEnabled ? '暂停自动保存' : '恢复自动保存' }}</button>
            <button class="sl-btn sl-btn--primary" @click="handleSave">保存</button>
            <button class="sl-btn" @click="handleDiscardExit">不保存退出</button>
            <button class="sl-btn" @click="handleFinish">完成</button>
          </template>
        </div>
      </div>
      <div v-if="isMobile && !noteStore.editMode && noteStore.currentNote" class="mobile-fab">
        <div :class="['fab-menu', { expanded: mobileActionsOpen }]">
          <button class="fab-toggle sl-btn sl-btn--primary" @click="mobileActionsOpen = !mobileActionsOpen">
            {{ mobileActionsOpen ? '收起' : '操作' }}
          </button>
          <template v-if="mobileActionsOpen && !isDeletedNote">
            <button class="sl-btn" @click="handleTogglePinned">{{ noteStore.currentNote.pinnedFlag ? '取消置顶' : '置顶' }}</button>
            <button class="sl-btn" @click="handleShare" v-if="noteStore.currentNote.id">分享</button>
            <button class="sl-btn sl-btn--primary" @click="handleEnterEditMode">编辑</button>
            <button class="sl-btn sl-btn--danger" @click="handleDelete">移入回收站</button>
          </template>
          <template v-else-if="mobileActionsOpen && isDeletedNote">
            <button class="sl-btn sl-btn--primary" @click="handleRestore(noteStore.currentNote.id)">恢复</button>
            <button class="sl-btn sl-btn--danger" @click="handlePurge(noteStore.currentNote.id)">彻底删除</button>
          </template>
        </div>
      </div>

      <!-- View mode -->
      <div v-if="!noteStore.editMode" ref="viewerAreaRef" class="viewer-area" @scroll="handleViewerScroll">
        <template v-if="noteStore.currentNote">
          <div v-if="isDeletedNote" class="trash-banner">
            <div class="trash-banner__title">这篇笔记当前位于回收站</div>
            <div class="trash-banner__text">
              <span v-if="noteStore.currentNote.purgeAt">将在 {{ formatTime(noteStore.currentNote.purgeAt) }} 自动清理。</span>
              恢复后即可继续编辑、分享与置顶。
            </div>
          </div>
          <div ref="viewerMarkdownRef" class="markdown-body" v-html="renderedHtml"></div>
        </template>
        <div v-else class="empty-state">
          <div class="empty-icon">✨</div>
          <h2>欢迎使用 Starlight</h2>
          <p>从左侧选择笔记，或新建一篇星光笔记。</p>
        </div>
      </div>

      <!-- Edit mode -->
      <div v-else class="editor-area" :class="{ 'with-preview': previewVisible && !isMobile }">
        <div class="editor-pane" :class="{ hidden: isMobile && previewVisible }">
          <div class="editor-statusbar">
            <div class="editor-status-group">
              <span class="editor-status-text">{{ autosaveStatusText }}</span>
              <span class="editor-status-text">{{ lastSavedText }}</span>
            </div>
            <div class="editor-status-group editor-status-group--right">
              <span :class="['editor-status-pill', { dirty: noteStore.dirty }]">{{ saveStateText }}</span>
            </div>
          </div>
          <div class="editor-fields">
            <div class="field-row">
              <div class="field-col" style="flex:2">
                <label class="sl-label">标题</label>
                <input v-model="editorTitle" class="sl-input" placeholder="笔记标题" @input="markDirty" />
              </div>
              <div class="field-col" style="flex:1">
                <label class="sl-label">分类</label>
                <select v-model="editorCategory" class="sl-select" @change="markDirty">
                  <option value="">无分类</option>
                  <option v-for="opt in categoryOptions" :key="opt.id" :value="opt.id">{{ opt.label }}</option>
                </select>
              </div>
            </div>
          </div>
          <textarea
            ref="editorTextarea"
            v-model="editorContent"
            class="editor-textarea"
            placeholder="# 从这里开始记录你的星光..."
            @input="handleEditorInput"
            @scroll="handleEditorScroll"
          ></textarea>
        </div>
        <div
          v-if="previewVisible"
          ref="previewPaneRef"
          class="preview-pane"
          :class="{ 'mobile-full': isMobile }"
          @scroll="handlePreviewScroll"
        >
          <div ref="previewMarkdownRef" class="markdown-body" v-html="livePreviewHtml"></div>
        </div>
      </div>
    </main>

    <!-- Modals -->
    <ShareModal
      v-if="showShareModal"
      :note-id="noteStore.currentNote?.id"
      @close="showShareModal = false"
    />
    <CategoryModal
      v-if="showCategoryModal"
      :tree-items="noteStore.tree.items"
      @close="showCategoryModal = false"
      @created="handleCategoryCreated"
    />
    <SettingsModal
      v-if="showSettingsModal"
      :tree-items="noteStore.tree.items"
      :initial-tab="settingsInitialTab"
      @close="showSettingsModal = false"
    />
    <ImportExportModal
      v-if="showImportExportModal"
      :tree-items="noteStore.tree.items"
      @close="showImportExportModal = false"
    />
    <SiteModal
      v-if="showSiteModal"
      :category-id="selectedCategoryId"
      :category-name="selectedCategoryName"
      @close="showSiteModal = false"
      @updated="handleSiteUpdated"
    />
    <SearchModal
      v-if="showSearchModal"
      :current-note-id="noteStore.currentNote?.id"
      :tree-items="noteStore.tree.items"
      @close="showSearchModal = false"
      @open-note="handleOpenNote"
    />
    <PopupLayer
      v-if="showDiscardConfirm"
      title="确认不保存退出？"
      eyebrow="退出编辑"
      tone="warning"
      width="min(460px, calc(100vw - 32px))"
      @close="showDiscardConfirm = false"
    >
      <div class="discard-confirm">
        <div class="discard-confirm__icon">!</div>
        <div class="discard-confirm__content">
          <p class="discard-confirm__text">{{ discardConfirmDescription }}</p>
          <div class="discard-confirm__meta">
            <span class="sl-badge">{{ noteStore.dirty ? '有未保存更改' : '未检测到新修改' }}</span>
            <span class="sl-badge">{{ discardConfirmNoteLabel }}</span>
          </div>
          <p class="discard-confirm__hint">你也可以先保存，再点击“完成”退出编辑。</p>
        </div>
      </div>

      <template #footer>
        <button class="sl-btn" @click="showDiscardConfirm = false">继续编辑</button>
        <button class="sl-btn sl-btn--danger" @click="confirmDiscardExit">确认退出</button>
      </template>
    </PopupLayer>
    <PopupLayer
      v-if="showDeleteConfirm"
      :title="deleteConfirmTitle"
      eyebrow="删除确认"
      tone="danger"
      width="min(460px, calc(100vw - 32px))"
      @close="closeDeleteConfirm"
    >
      <div class="discard-confirm">
        <div class="discard-confirm__icon discard-confirm__icon--danger">!</div>
        <div class="discard-confirm__content">
          <p class="discard-confirm__text">{{ deleteConfirmDescription }}</p>
          <div class="discard-confirm__meta">
            <span class="sl-badge">{{ deleteConfirmNoteLabel }}</span>
            <span class="sl-badge">{{ deleteConfirmMode === 'purge' ? '不可恢复' : '30 天内可恢复' }}</span>
          </div>
          <p class="discard-confirm__hint">{{ deleteConfirmHint }}</p>
        </div>
      </div>

      <template #footer>
        <button class="sl-btn" @click="closeDeleteConfirm">取消</button>
        <button class="sl-btn sl-btn--danger" @click="confirmDeleteAction">确认</button>
      </template>
    </PopupLayer>
    <PopupLayer
      v-if="categoryActionState.visible"
      :title="categoryConfirmTitle"
      eyebrow="分类操作"
      tone="danger"
      width="min(480px, calc(100vw - 32px))"
      @close="closeCategoryActionConfirm"
    >
      <div class="discard-confirm">
        <div class="discard-confirm__icon discard-confirm__icon--danger">!</div>
        <div class="discard-confirm__content">
          <p class="discard-confirm__text">{{ categoryConfirmDescription }}</p>
          <div class="discard-confirm__meta">
            <span class="sl-badge">{{ categoryActionState.categoryName || '未命名分类' }}</span>
            <span class="sl-badge">{{ categoryActionState.categoryCount }} 个分类</span>
            <span class="sl-badge">{{ categoryActionState.noteCount }} 篇笔记</span>
          </div>
          <p class="discard-confirm__hint">{{ categoryConfirmHint }}</p>
        </div>
      </div>

      <template #footer>
        <button class="sl-btn" @click="closeCategoryActionConfirm">取消</button>
        <button class="sl-btn sl-btn--danger" @click="confirmCategoryAction">确认</button>
      </template>
    </PopupLayer>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useNoteStore } from '@/stores/note'
import { useToastStore } from '@/stores/toast'
import { renderMarkdown, formatTime, parseOutline } from '@/utils/markdown'
import {
  enhanceMarkdown,
  scrollMarkdownContainerToHash,
  detectActiveHeadingAnchor,
  detectActiveOutlineAnchorByEditor
} from '@/utils/markdownEnhance'
import TreeNode from '@/components/TreeNode.vue'
import OutlineList from '@/components/OutlineList.vue'
import ImportExportModal from '@/components/ImportExportModal.vue'
import PopupLayer from '@/components/PopupLayer.vue'
import ShareModal from '@/components/ShareModal.vue'
import CategoryModal from '@/components/CategoryModal.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import SiteModal from '@/components/SiteModal.vue'
import SearchModal from '@/components/SearchModal.vue'
import MobileTreeBrowser from '@/components/MobileTreeBrowser.vue'
import { findTreeNodeById, findTreePathById, summarizeTreeSubtree } from '@/utils/directoryTree'
import { useSidebarWidth } from '@/utils/sidebarLayout'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const noteStore = useNoteStore()
const toast = useToastStore()

const sidebarOpen = ref(false)
const sidebarTab = ref('tree')
const previewVisible = ref(true)
const mobileActionsOpen = ref(false)
const mobileTreePath = ref([])
const mobileTrashPath = ref([])
const selectedCategoryId = ref(null)
const selectedTrashCategoryId = ref(null)
const allThemes = ref([])
const showShareModal = ref(false)
const showCategoryModal = ref(false)
const showSettingsModal = ref(false)
const settingsInitialTab = ref('profile')
const showImportExportModal = ref(false)
const showSearchModal = ref(false)
const showSiteModal = ref(false)
const selectedCategoryName = ref('')
const showDiscardConfirm = ref(false)
const showDeleteConfirm = ref(false)
const deleteConfirmMode = ref('trash')
const deleteTargetId = ref(null)
const deleteTargetTitle = ref('')
const categoryActionState = ref({
  visible: false,
  mode: 'trash',
  categoryId: '',
  categoryName: '',
  categoryCount: 0,
  noteCount: 0,
  categoryIds: [],
  noteIds: []
})

const editorTitle = ref('')
const editorContent = ref('')
const editorCategory = ref('')
const editorTextarea = ref(null)
const viewerAreaRef = ref(null)
const viewerMarkdownRef = ref(null)
const previewPaneRef = ref(null)
const previewMarkdownRef = ref(null)
const nowTick = ref(Date.now())
const saveInProgress = ref(false)
const activeOutlineAnchor = ref('')
const appReady = ref(false)

const isMobile = ref(window.innerWidth <= 768)
const {
  sidebarStyle,
  isResizing: isSidebarResizing,
  startResize: startSidebarResize,
  resetWidth: resetSidebarWidth,
  syncSidebarWidth
} = useSidebarWidth({
  storageKey: 'starlight:app-sidebar-width',
  defaultWidth: 360,
  minWidth: 280,
  maxWidth: 480
})
let autosaveTimer = null
let clockTimer = null
let editorScrollSyncTimer = null
let previewScrollSyncTimer = null
let syncFromEditor = false
let syncFromPreview = false


const topbarTitle = computed(() => {
  if (noteStore.editMode) {
    return editorTitle.value.trim() || (noteStore.currentNote?.id ? '未命名笔记' : '新建笔记')
  }
  if (noteStore.currentNote?.deletedAt) {
    return `回收站 · ${noteStore.currentNote.title || '未命名笔记'}`
  }
  return noteStore.currentNote?.title || 'Starlight'
})

const isDeletedNote = computed(() => Boolean(noteStore.currentNote?.deletedAt))

const autosaveStatusText = computed(() => noteStore.autosaveEnabled
  ? '自动保存：已开启（每 30 秒检查一次）'
  : '自动保存：已暂停')

const lastSavedText = computed(() => {
  if (!noteStore.lastSavedAt) return '最近保存：尚未保存'
  return `最近保存：${formatAbsoluteTime(noteStore.lastSavedAt)}（${formatRelativeTime(noteStore.lastSavedAt, nowTick.value)}）`
})

const saveStateText = computed(() => {
  if (saveInProgress.value) return '保存中…'
  if (noteStore.dirty) return '有未保存更改'
  if (noteStore.lastSavedAt) return '内容已保存'
  return '等待首次保存'
})

const mobileSidebarTitle = computed(() => {
  if (sidebarTab.value === 'trash') return '回收站'
  if (sidebarTab.value === 'outline') return '当前大纲'
  return '笔记目录'
})

const renderedHtml = computed(() => {
  if (!noteStore.currentNote) return ''
  return renderMarkdown(noteStore.currentNote.markdownContent)
})

const livePreviewHtml = computed(() => renderMarkdown(editorContent.value))

const outlineSource = computed(() => {
  if (noteStore.editMode) return editorContent.value
  return noteStore.currentNote?.markdownContent || ''
})

const outlineItems = computed(() => parseOutline(outlineSource.value))

const categoryOptions = computed(() => {
  const result = []
  function walk(items, prefix = '') {
    for (const item of items || []) {
      if (item.type === 'category') {
        result.push({ id: item.id, label: prefix + item.name })
        if (item.children?.length) walk(item.children, prefix + '— ')
      }
    }
  }
  walk(noteStore.tree.items)
  return result
})

const discardConfirmDescription = computed(() => (noteStore.dirty
  ? '当前内容还有未保存更改，退出编辑后这些修改将无法恢复。'
  : '当前没有新的未保存更改，确认后会直接退出编辑模式。'))

const discardConfirmNoteLabel = computed(() => {
  const value = editorTitle.value.trim() || noteStore.currentNote?.title || ''
  return value || '未命名笔记'
})

const deleteConfirmTitle = computed(() => (deleteConfirmMode.value === 'purge' ? '确认彻底删除？' : '确认移入回收站？'))

const deleteConfirmDescription = computed(() => {
  if (deleteConfirmMode.value === 'purge') {
    return '这篇笔记会被永久移除，相关分享记录也会一起失效，之后无法再恢复。'
  }
  return '这篇笔记会先移入回收站，并保留 30 天。你可以随时恢复，避免误删带来的损失。'
})

const deleteConfirmHint = computed(() => {
  if (deleteConfirmMode.value === 'purge') {
    return '请确认当前内容已经不再需要，再执行彻底删除。'
  }
  return '移入回收站后，目录中将暂时隐藏这篇笔记。'
})

const deleteConfirmNoteLabel = computed(() => {
  const value = deleteTargetTitle.value || noteStore.currentNote?.title || ''
  return value || '未命名笔记'
})

const categoryConfirmTitle = computed(() => (categoryActionState.value.mode === 'purge'
  ? '确认彻底删除分类子树？'
  : '确认删除分类？'))

const categoryConfirmDescription = computed(() => {
  const { mode, noteCount, categoryCount, categoryName } = categoryActionState.value
  const safeName = categoryName || '该分类'
  if (mode === 'purge') {
    if (noteCount > 0) {
      return `“${safeName}”及其子树下共有 ${categoryCount} 个分类、${noteCount} 篇笔记，确认后会从回收站中彻底删除，且无法恢复。`
    }
    return `“${safeName}”及其 ${categoryCount} 个分类节点将从回收站中彻底删除，之后无法恢复。`
  }
  if (noteCount > 0) {
    return `“${safeName}”及其子树下共有 ${categoryCount} 个分类、${noteCount} 篇笔记，删除后会整体移入回收站，并保留原有目录结构。`
  }
  return `“${safeName}”会连同其 ${categoryCount} 个分类节点一起移入回收站，并保留原有目录结构。`
})

const categoryConfirmHint = computed(() => (categoryActionState.value.mode === 'purge'
  ? '彻底删除会同步清理该分类子树下的回收站内容，请确认这些数据已经不再需要。'
  : '移入回收站后，仍可以在回收站目录树中按原结构查看并彻底删除。'))

// Sync editor fields when note changes
watch(() => noteStore.currentNote, (note) => {
  if (note) {
    editorTitle.value = note.title || ''
    editorContent.value = note.markdownContent || ''
    editorCategory.value = note.categoryId || ''
    return
  }
  editorTitle.value = ''
  editorContent.value = ''
  editorCategory.value = ''
}, { immediate: true })

watch(() => noteStore.editMode, (mode) => {
  if (mode && noteStore.currentNote) {
    editorTitle.value = noteStore.currentNote.title || ''
    editorContent.value = noteStore.currentNote.markdownContent || ''
    editorCategory.value = noteStore.currentNote.categoryId || ''
  }
})

function markDirty() { noteStore.dirty = true }
function handleEditorInput() { noteStore.dirty = true }

function togglePreview() { previewVisible.value = !previewVisible.value }

function normalizeHash(value) {
  return decodeURIComponent(String(value || '').replace(/^#/, '').trim())
}

function getOutlineItemByAnchor(anchor) {
  return outlineItems.value.find(item => item.anchor === anchor) || null
}

function getScrollProgress(element) {
  if (!element) return 0
  const maxScrollTop = element.scrollHeight - element.clientHeight
  if (maxScrollTop <= 0) return 0
  return element.scrollTop / maxScrollTop
}

function setScrollProgress(element, progress) {
  if (!element) return
  const maxScrollTop = element.scrollHeight - element.clientHeight
  element.scrollTop = maxScrollTop > 0 ? maxScrollTop * progress : 0
}

function releaseEditorSyncLock() {
  clearTimeout(editorScrollSyncTimer)
  editorScrollSyncTimer = setTimeout(() => {
    syncFromPreview = false
  }, 80)
}

function releasePreviewSyncLock() {
  clearTimeout(previewScrollSyncTimer)
  previewScrollSyncTimer = setTimeout(() => {
    syncFromEditor = false
  }, 80)
}

function syncPreviewToEditor() {
  if (!noteStore.editMode || !previewVisible.value) return
  const editor = editorTextarea.value
  const preview = previewPaneRef.value
  if (!editor || !preview) return
  syncFromEditor = true
  setScrollProgress(preview, getScrollProgress(editor))
  releasePreviewSyncLock()
}

function syncEditorToPreview() {
  if (!noteStore.editMode || !previewVisible.value) return
  const editor = editorTextarea.value
  const preview = previewPaneRef.value
  if (!editor || !preview) return
  syncFromPreview = true
  setScrollProgress(editor, getScrollProgress(preview))
  releaseEditorSyncLock()
}

function getLineOffset(text, lineNumber) {
  const safeLineNumber = Math.max(1, Number(lineNumber) || 1)
  let currentLine = 1
  let offset = 0
  const content = String(text || '')
  while (currentLine < safeLineNumber && offset < content.length) {
    const nextBreak = content.indexOf('\n', offset)
    if (nextBreak < 0) {
      return content.length
    }
    offset = nextBreak + 1
    currentLine += 1
  }
  return offset
}

function scrollEditorToLine(lineNumber, { focus = false } = {}) {
  const textarea = editorTextarea.value
  if (!textarea || !lineNumber) return
  const lineHeight = Number.parseFloat(window.getComputedStyle(textarea).lineHeight) || 24
  const targetTop = Math.max((Number(lineNumber) - 1) * lineHeight - lineHeight * 2, 0)
  syncFromPreview = true
  textarea.scrollTo({ top: targetTop, behavior: 'smooth' })
  const selectionStart = getLineOffset(editorContent.value, lineNumber)
  textarea.setSelectionRange(selectionStart, selectionStart)
  if (focus && !isMobile.value) {
    textarea.focus()
  }
  releaseEditorSyncLock()
}

function handleEditorScroll() {
  if (syncFromPreview) return
  activeOutlineAnchor.value = detectActiveOutlineAnchorByEditor(editorTextarea.value, outlineItems.value)
  syncPreviewToEditor()
  if (previewVisible.value) {
    activeOutlineAnchor.value = detectActiveHeadingAnchor(previewPaneRef.value)
  }
}

function handlePreviewScroll() {
  if (syncFromEditor) return
  activeOutlineAnchor.value = detectActiveHeadingAnchor(previewPaneRef.value)
  syncEditorToPreview()
}

function handleViewerScroll() {
  activeOutlineAnchor.value = detectActiveHeadingAnchor(viewerAreaRef.value)
}

async function applyCurrentHashScroll(behavior = 'auto') {
  const anchor = normalizeHash(route.hash)
  activeOutlineAnchor.value = anchor
  if (!anchor) {
    return false
  }

  if (noteStore.editMode) {
    const outlineItem = getOutlineItemByAnchor(anchor)
    if (outlineItem) {
      scrollEditorToLine(outlineItem.line)
    }
  }

  const container = noteStore.editMode && previewVisible.value
    ? previewPaneRef.value
    : viewerAreaRef.value

  if (!container) {
    return false
  }

  return scrollMarkdownContainerToHash(container, anchor, { behavior })
}

async function enhanceViewerContent() {
  if (noteStore.editMode) return
  await nextTick()
  await enhanceMarkdown(viewerMarkdownRef.value)
  const scrolled = await applyCurrentHashScroll('auto')
  if (!scrolled) {
    activeOutlineAnchor.value = detectActiveHeadingAnchor(viewerAreaRef.value)
  }
}

async function enhancePreviewContent() {
  if (!noteStore.editMode || !previewVisible.value) return
  await nextTick()
  await enhanceMarkdown(previewMarkdownRef.value)
  syncPreviewToEditor()
  const scrolled = await applyCurrentHashScroll('auto')
  if (!scrolled) {
    activeOutlineAnchor.value = previewVisible.value
      ? detectActiveHeadingAnchor(previewPaneRef.value)
      : detectActiveOutlineAnchorByEditor(editorTextarea.value, outlineItems.value)
  }
}

async function updateRouteHash(anchor) {
  const nextHash = anchor ? `#${anchor}` : ''
  if (route.hash === nextHash) {
    activeOutlineAnchor.value = anchor || ''
    return
  }
  await router.replace({ path: route.path, query: route.query, hash: nextHash })
  activeOutlineAnchor.value = anchor || ''
}

async function handleOutlineSelect(item) {
  await updateRouteHash(item.anchor)
  activeOutlineAnchor.value = item.anchor

  if (noteStore.editMode) {
    scrollEditorToLine(item.line, { focus: true })
    if (previewVisible.value) {
      scrollMarkdownContainerToHash(previewPaneRef.value, item.anchor)
    }
    return
  }

  scrollMarkdownContainerToHash(viewerAreaRef.value, item.anchor)
}

async function syncRouteToCurrentNote({ replace = true, preserveHash = false } = {}) {
  const noteId = noteStore.currentNote?.id || undefined
  const nextHash = preserveHash ? route.hash : ''
  const nextQuery = noteStore.currentNote?.deletedAt ? { trash: '1' } : {}
  const currentNoteId = String(route.params.noteId || '')
  const currentTrash = route.query.trash === '1'
  const nextTrash = nextQuery.trash === '1'

  if (currentNoteId === String(noteId || '') && currentTrash === nextTrash && route.hash === nextHash) {
    return
  }

  const location = noteId
    ? { name: 'App', params: { noteId }, query: nextQuery, hash: nextHash }
    : { name: 'App', query: {}, hash: '' }

  if (replace) {
    await router.replace(location)
    return
  }
  await router.push(location)
}

async function syncNoteFromRoute() {
  if (!appReady.value) return
  const noteId = String(route.params.noteId || '').trim()
  const routeTrash = route.query.trash === '1'
  activeOutlineAnchor.value = normalizeHash(route.hash)

  if (!noteId) {
    if (!noteStore.editMode && noteStore.currentNote) {
      noteStore.clearCurrentNote()
    }
    return
  }

  const currentId = String(noteStore.currentNote?.id || '')
  const currentTrash = Boolean(noteStore.currentNote?.deletedAt)
  if (currentId === noteId && currentTrash === routeTrash) {
    await nextTick()
    await applyCurrentHashScroll('auto')
    return
  }

  try {
    if (routeTrash) {
      await noteStore.openTrashNote(noteId)
    } else {
      await noteStore.openNote(noteId)
    }
    sidebarOpen.value = false
    mobileActionsOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}

function formatAbsoluteTime(value) {
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false
  })
}

function formatRelativeTime(value, currentTime) {
  const diff = Math.max(0, Math.floor((currentTime - value) / 1000))
  if (diff < 5) return '刚刚'
  if (diff < 60) return `${diff} 秒前`

  const minutes = Math.floor(diff / 60)
  if (minutes < 60) return `${minutes} 分钟前`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`

  const days = Math.floor(hours / 24)
  return `${days} 天前`
}

function buildNotePayload() {
  return {
    title: editorTitle.value,
    markdownContent: editorContent.value,
    categoryId: editorCategory.value || null
  }
}

async function persistNote({ successMessage = '已保存', exitAfterSave = false, source = 'manual' } = {}) {
  if (saveInProgress.value) return false

  saveInProgress.value = true
  try {
    await noteStore.saveNote(buildNotePayload())
    await syncRouteToCurrentNote({ replace: true })
    if (exitAfterSave) {
      noteStore.finishEditing()
    }
    if (source === 'auto') {
      toast.info(successMessage)
    } else {
      toast.success(successMessage)
    }
    return true
  } catch (err) {
    toast.error(err.message)
    return false
  } finally {
    saveInProgress.value = false
  }
}

async function handleSave() {
  await persistNote()
}

async function handleFinish() {
  await persistNote({ exitAfterSave: true })
}

async function handleOpenNote(id) {
  try {
    await noteStore.openNote(id)
    await syncRouteToCurrentNote({ replace: false })
    sidebarOpen.value = false
    mobileActionsOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}


function handleEnterEditMode() {
  if (isDeletedNote.value) {
    toast.info('请先恢复笔记，再继续编辑')
    return
  }
  noteStore.enterEditMode()
}

function handleToggleCategory(id) {
  noteStore.toggleCategoryExpanded(id)
}

function handleToggleTrashCategory(id) {
  noteStore.toggleTrashCategoryExpanded(id)
}

function expandCategoryPath(path, mode = 'tree') {
  for (const id of path || []) {
    if (mode === 'trash') {
      noteStore.setTrashCategoryExpanded(id, true)
    } else {
      noteStore.setCategoryExpanded(id, true)
    }
  }
}

function syncMobileTreePathFromSelection() {
  if (!isMobile.value) return
  if (selectedCategoryId.value) {
    mobileTreePath.value = findTreePathById(noteStore.tree.items, selectedCategoryId.value, { includeTargetCategory: true })
    expandCategoryPath(mobileTreePath.value, 'tree')
    return
  }
  if (noteStore.currentNote?.id && !isDeletedNote.value) {
    mobileTreePath.value = findTreePathById(noteStore.tree.items, noteStore.currentNote.id)
    expandCategoryPath(mobileTreePath.value, 'tree')
    return
  }
  mobileTreePath.value = []
}

function syncMobileTrashPathFromSelection() {
  if (!isMobile.value) return
  if (selectedTrashCategoryId.value) {
    mobileTrashPath.value = findTreePathById(noteStore.trashTree.items, selectedTrashCategoryId.value, { includeTargetCategory: true })
    expandCategoryPath(mobileTrashPath.value, 'trash')
    return
  }
  if (isDeletedNote.value && noteStore.currentNote?.id) {
    mobileTrashPath.value = findTreePathById(noteStore.trashTree.items, noteStore.currentNote.id)
    expandCategoryPath(mobileTrashPath.value, 'trash')
    return
  }
  mobileTrashPath.value = []
}

function handleMobileTreeNavigate(path) {
  mobileTreePath.value = [...path]
  selectedCategoryId.value = path[path.length - 1] || null
  expandCategoryPath(path, 'tree')
}

function handleMobileTrashNavigate(path) {
  mobileTrashPath.value = [...path]
  selectedTrashCategoryId.value = path[path.length - 1] || null
  expandCategoryPath(path, 'trash')
}

function handleMobileTreeSelectCategory(categoryId) {
  selectedCategoryId.value = categoryId || null
}

function handleMobileTrashSelectCategory(categoryId) {
  selectedTrashCategoryId.value = categoryId || null
}

function toggleAutosave() {
  noteStore.setAutosaveEnabled(!noteStore.autosaveEnabled)
  toast.info(noteStore.autosaveEnabled ? '自动保存已开启' : '自动保存已暂停')
}

function handleDiscardExit() {
  showDiscardConfirm.value = true
}

function confirmDiscardExit() {
  showDiscardConfirm.value = false
  noteStore.discardEdit()
  applyCurrentHashScroll('auto')
  toast.info('已退出编辑，未保存内容已放弃')
}

function handleNewNote() {
  noteStore.startNewNote(selectedCategoryId.value)
  editorTitle.value = ''
  editorContent.value = ''
  editorCategory.value = selectedCategoryId.value || ''
  sidebarOpen.value = false
  mobileActionsOpen.value = false
  activeOutlineAnchor.value = ''
  router.replace({ name: 'App', query: {}, hash: '' })
}

function handleShare() {
  if (noteStore.currentNote?.id) showShareModal.value = true
}

async function handleDelete() {
  if (!noteStore.currentNote?.id) return
  openDeleteConfirm('trash', noteStore.currentNote.id, noteStore.currentNote.title)
}

async function handleRestore(id = noteStore.currentNote?.id) {
  if (!id) return
  try {
    await noteStore.restoreNote(id)
    await syncRouteToCurrentNote({ replace: true })
    toast.success('笔记已恢复')
    sidebarTab.value = 'tree'
    mobileActionsOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}

async function handlePurge(id = noteStore.currentNote?.id) {
  if (!id) return
  const title = id === noteStore.currentNote?.id
    ? noteStore.currentNote?.title
    : noteStore.trashNotes.find(item => item.id === id)?.title
  openDeleteConfirm('purge', id, title)
}

async function handleRestoreTrashCategory(categoryId) {
  if (!categoryId) return
  const summary = summarizeTreeSubtree(noteStore.trashTree.items, categoryId)
  try {
    await noteStore.restoreTrashCategory(categoryId)
    toast.success('分类子树已恢复')
    sidebarTab.value = 'tree'
    if (selectedTrashCategoryId.value && summary.categoryIds.includes(selectedTrashCategoryId.value)) {
      selectedTrashCategoryId.value = null
    }
    await syncStateAfterCategoryRestore(summary)
  } catch (err) {
    toast.error(err.message)
  }
}

function handleDeleteCategoryRequest(categoryId) {
  const node = findTreeNodeById(noteStore.tree.items, categoryId)
  const summary = summarizeTreeSubtree(noteStore.tree.items, categoryId)
  openCategoryActionConfirm('trash', categoryId, node?.name || node?.label || '', summary)
}

function handlePurgeTrashCategoryRequest(categoryId) {
  const node = findTreeNodeById(noteStore.trashTree.items, categoryId)
  const summary = summarizeTreeSubtree(noteStore.trashTree.items, categoryId)
  openCategoryActionConfirm('purge', categoryId, node?.name || node?.label || '', summary)
}

function openCategoryActionConfirm(mode, categoryId, categoryName, summary) {
  categoryActionState.value = {
    visible: true,
    mode,
    categoryId,
    categoryName,
    categoryCount: summary.categoryCount,
    noteCount: summary.noteCount,
    categoryIds: summary.categoryIds,
    noteIds: summary.noteIds
  }
}

function closeCategoryActionConfirm() {
  categoryActionState.value = {
    visible: false,
    mode: 'trash',
    categoryId: '',
    categoryName: '',
    categoryCount: 0,
    noteCount: 0,
    categoryIds: [],
    noteIds: []
  }
}

async function confirmCategoryAction() {
  if (!categoryActionState.value.categoryId) return
  const action = { ...categoryActionState.value }
  closeCategoryActionConfirm()
  try {
    if (action.mode === 'purge') {
      await noteStore.purgeTrashCategory(action.categoryId)
      toast.success('分类子树已彻底删除')
    } else {
      await noteStore.deleteCategory(action.categoryId)
      toast.success('分类已移入回收站')
      sidebarTab.value = 'trash'
    }
    await syncStateAfterCategoryMutation(action)
  } catch (err) {
    toast.error(err.message)
  }
}

function openDeleteConfirm(mode, id, title = '') {
  deleteConfirmMode.value = mode
  deleteTargetId.value = id
  deleteTargetTitle.value = title || ''
  showDeleteConfirm.value = true
}

function closeDeleteConfirm() {
  showDeleteConfirm.value = false
  deleteTargetId.value = null
  deleteTargetTitle.value = ''
  deleteConfirmMode.value = 'trash'
}

async function confirmDeleteAction() {
  if (!deleteTargetId.value) return
  const mode = deleteConfirmMode.value
  const targetId = deleteTargetId.value
  closeDeleteConfirm()
  try {
    if (mode === 'purge') {
      await noteStore.purgeNote(targetId)
      toast.success('已彻底删除')
    } else {
      await noteStore.deleteNote(targetId)
      toast.success('已移入回收站')
      sidebarTab.value = 'trash'
    }
    await syncRouteToCurrentNote({ replace: true })
    mobileActionsOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleTogglePinned() {
  if (!noteStore.currentNote?.id || isDeletedNote.value) return
  try {
    const nextValue = !noteStore.currentNote.pinnedFlag
    await noteStore.setPinned(noteStore.currentNote.id, nextValue)
    toast.success(nextValue ? '已置顶到目录上方' : '已取消置顶')
    mobileActionsOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}

function handleCategoryCreated() {
  showCategoryModal.value = false
  toast.success('分类已创建')
}

function openSettings(tab = 'profile') {
  settingsInitialTab.value = tab
  showSettingsModal.value = true
}

function handleOpenSiteModal(categoryId) {
  selectedCategoryId.value = categoryId
  // 从树结构中找到分类名称
  function findName(items) {
    for (const item of items || []) {
      if (item.id === categoryId) return item.name
      if (item.children?.length) {
        const found = findName(item.children)
        if (found) return found
      }
    }
    return ''
  }
  selectedCategoryName.value = findName(noteStore.tree.items)
  showSiteModal.value = true
}

async function handleSiteUpdated() {
  // 刷新树结构以更新站点状态标识
  try {
    await noteStore.refreshTree()
  } catch (err) {
    // 忽略刷新失败
  }
}

async function handleThemeChange(id) {
  try {
    await themeStore.selectTheme(id)
    toast.success('主题已切换')
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

function handleResize() {
  const wasMobile = isMobile.value
  isMobile.value = window.innerWidth <= 768
  syncSidebarWidth()
  if (wasMobile !== isMobile.value) {
    sidebarOpen.value = false
  }
  if (!isMobile.value) previewVisible.value = true
  if (!isMobile.value) mobileActionsOpen.value = false
  if (isMobile.value) {
    syncMobileTreePathFromSelection()
    syncMobileTrashPathFromSelection()
  }
}

function handleGlobalKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    showSearchModal.value = true
  }
}

// ── Trash ──

async function handleTrashTabClick() {
  sidebarTab.value = 'trash'
  selectedTrashCategoryId.value = null
  try {
    await noteStore.refreshTrash()
  } catch (err) {
    toast.error(err.message)
  }
}

async function handleOpenTrashNote(id) {
  try {
    selectedTrashCategoryId.value = null
    await noteStore.openTrashNote(id)
    await syncRouteToCurrentNote({ replace: false })
    sidebarOpen.value = false
    mobileActionsOpen.value = false
  } catch (err) {
    toast.error(err.message)
  }
}

async function syncStateAfterCategoryMutation(action) {
  const affectedCurrentNote = Boolean(noteStore.currentNote?.id && action.noteIds.includes(noteStore.currentNote.id))
  const affectedCurrentCategory = Boolean(noteStore.currentNote?.categoryId && action.categoryIds.includes(noteStore.currentNote.categoryId))

  if (selectedCategoryId.value && action.categoryIds.includes(selectedCategoryId.value)) {
    selectedCategoryId.value = null
  }

  if (action.mode === 'trash' && affectedCurrentNote) {
    try {
      await noteStore.openTrashNote(noteStore.currentNote.id)
    } catch {
      noteStore.clearCurrentNote()
    }
  } else if (affectedCurrentNote || affectedCurrentCategory) {
    noteStore.clearCurrentNote()
  }

  await syncRouteToCurrentNote({ replace: true })
  mobileActionsOpen.value = false
}

async function syncStateAfterCategoryRestore(summary) {
  const restoredCurrentNote = Boolean(noteStore.currentNote?.id && summary.noteIds.includes(noteStore.currentNote.id))

  if (selectedCategoryId.value && summary.categoryIds.includes(selectedCategoryId.value)) {
    selectedCategoryId.value = null
  }

  if (restoredCurrentNote && noteStore.currentNote?.id) {
    try {
      await noteStore.openNote(noteStore.currentNote.id)
    } catch {
      noteStore.clearCurrentNote()
    }
  }

  await syncRouteToCurrentNote({ replace: true })
  mobileActionsOpen.value = false
}

watch(() => [route.params.noteId, route.query.trash], async () => {
  await syncNoteFromRoute()
})

watch(
  [() => noteStore.tree.items, () => noteStore.currentNote?.id, selectedCategoryId, isDeletedNote, isMobile],
  () => {
    syncMobileTreePathFromSelection()
  },
  { deep: true }
)

watch(
  [() => noteStore.trashTree.items, () => noteStore.currentNote?.id, selectedTrashCategoryId, isDeletedNote, isMobile],
  () => {
    syncMobileTrashPathFromSelection()
  },
  { deep: true }
)

watch(() => route.hash, async hash => {
  activeOutlineAnchor.value = normalizeHash(hash)
  await nextTick()
  await applyCurrentHashScroll('smooth')
})

watch([renderedHtml, () => themeStore.currentId], async () => {
  await enhanceViewerContent()
}, { flush: 'post' })

watch([livePreviewHtml, previewVisible, () => themeStore.currentId], async () => {
  await enhancePreviewContent()
}, { flush: 'post' })

watch([() => noteStore.editMode, previewVisible], async () => {
  await nextTick()
  if (noteStore.editMode) {
    await enhancePreviewContent()
    return
  }
  await enhanceViewerContent()
}, { flush: 'post' })

onMounted(async () => {
  themeStore.loadCached()
  handleResize()
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', handleGlobalKeydown)

  try {
    await authStore.fetchMe()
    allThemes.value = await themeStore.loadThemes()
    await noteStore.refreshTree()
    await noteStore.refreshTrash()
    // Autosave every 30s
    autosaveTimer = setInterval(async () => {
      if (noteStore.editMode && noteStore.autosaveEnabled && noteStore.dirty && !saveInProgress.value) {
        await persistNote({ successMessage: '自动保存完成', source: 'auto' })
      }
    }, 30000)
    clockTimer = setInterval(() => {
      nowTick.value = Date.now()
    }, 1000)
    appReady.value = true
    activeOutlineAnchor.value = normalizeHash(route.hash)
    await syncNoteFromRoute()
    await enhanceViewerContent()
    await enhancePreviewContent()
  } catch {
    router.replace('/login')
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleGlobalKeydown)
  clearInterval(autosaveTimer)
  clearInterval(clockTimer)
  clearTimeout(editorScrollSyncTimer)
  clearTimeout(previewScrollSyncTimer)
})
</script>

<style scoped>
.app-shell {
  --sl-sidebar-width: 340px;
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--sl-bg);
}

/* --- Sidebar --- */
.sidebar {
  width: var(--sl-sidebar-width);
  min-width: var(--sl-sidebar-width);
  max-width: var(--sl-sidebar-width);
  background: var(--sl-sidebar-bg);
  border-right: 1px solid var(--sl-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}
.sidebar.resizing { transition: none; }
.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  gap: 14px;
  overflow: hidden;
}
.sidebar-profile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--sl-border);
}
.profile-info { display: flex; align-items: center; gap: 10px; }
.profile-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--sl-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}
.profile-name { font-size: 13px; font-weight: 600; }
.profile-role { font-size: 11px; color: var(--sl-text-tertiary); }
.profile-actions { display: flex; gap: 2px; }
.sidebar-mobile-chrome {
  display: none;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--sl-radius-lg);
  border: 1px solid var(--sl-border);
  background: linear-gradient(180deg, var(--sl-card) 0%, color-mix(in srgb, var(--sl-card) 70%, var(--sl-hover-bg)) 100%);
  box-shadow: var(--sl-shadow-card);
}
.sidebar-mobile-chrome__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sl-text);
}
.sidebar-mobile-chrome__meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

.sidebar-section { display: flex; flex-direction: column; }
.sidebar-actions { display: flex; gap: 6px; }
.sidebar-tools {
  display: flex;
  gap: 6px;
}
.sidebar-tools__btn {
  width: 100%;
  justify-content: center;
  background: linear-gradient(180deg, var(--sl-card) 0%, var(--sl-card-hover) 100%);
}
.sidebar-tabs {
  display: flex;
  background: var(--sl-active-bg);
  border-radius: var(--sl-radius);
  padding: 3px;
}
.tab-btn {
  flex: 1;
  background: transparent;
  border: none;
  padding: 5px 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--sl-text-secondary);
  cursor: pointer;
  border-radius: 5px;
  transition: all 0.15s;
}
.tab-btn.active {
  background: var(--sl-card);
  color: var(--sl-text);
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 6px;
  border-radius: 999px;
  background: var(--sl-selection);
  color: var(--sl-primary);
  font-size: 11px;
}
.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.sidebar-resize-handle {
  position: relative;
  width: 10px;
  margin-left: -1px;
  flex-shrink: 0;
  cursor: col-resize;
  touch-action: none;
  background: transparent;
}
.sidebar-resize-handle::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: var(--sl-border);
  transition: background 0.15s, box-shadow 0.15s;
}
.sidebar-resize-handle:hover::before {
  background: var(--sl-primary);
  box-shadow: 0 0 0 3px var(--sl-primary-light);
}
.sidebar-resize-handle:active::before {
  background: var(--sl-primary);
  box-shadow: 0 0 0 4px var(--sl-primary-light);
}
.empty-hint {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--sl-text-tertiary);
}
.quick-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--sl-border);
}
.quick-section__header,
.trash-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.quick-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text);
}
.quick-section__hint {
  font-size: 11px;
  color: var(--sl-text-tertiary);
}
.quick-section__list,
.trash-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trash-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trash-panel__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: var(--sl-text-secondary);
}

.trash-tree-wrap {
  display: flex;
  flex-direction: column;
}
.mobile-tree-action--danger {
  color: var(--sl-danger);
}
.mobile-tree-action--danger:hover {
  color: var(--sl-danger);
}
.trash-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
  background: var(--sl-card);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}
.trash-item:hover {
  border-color: var(--sl-border-strong);
  background: var(--sl-card-hover);
}
.trash-item.active {
  border-color: var(--sl-primary);
  background: var(--sl-selection);
}
.trash-item__main {
  min-width: 0;
  flex: 1;
}
.trash-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trash-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--sl-text-tertiary);
}
.trash-item__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}


/* --- Main --- */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--sl-bg-secondary);
  position: relative;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--sl-border);
  gap: 16px;
  background: var(--sl-card);
  min-height: 60px;
}
.topbar-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 500px;
}
.topbar-left {
  min-width: 0;
  flex: 1;
}
.topbar-meta { display: flex; gap: 8px; margin-top: 4px; }
.topbar-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* --- Viewer --- */
.viewer-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}
.trash-banner {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  background: linear-gradient(180deg, var(--sl-card) 0%, var(--sl-hover-bg) 100%);
  box-shadow: var(--sl-shadow-card);
}
.trash-banner__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text);
}
.trash-banner__text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--sl-text-secondary);
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: var(--sl-text-secondary);
}
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h2 { font-size: 20px; font-weight: 600; margin-bottom: 8px; }
.empty-state p { font-size: 14px; color: var(--sl-text-tertiary); }

/* --- Editor --- */
.editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.editor-area.with-preview {
  flex-direction: row;
}
.editor-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-pane.hidden { display: none; }
.editor-statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 20px 0;
  color: var(--sl-text-secondary);
  font-size: 12px;
}
.editor-status-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.editor-status-group--right {
  justify-content: flex-end;
}
.editor-status-text {
  color: var(--sl-text-secondary);
}
.editor-status-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--sl-hover-bg);
  color: var(--sl-success);
}
.editor-status-pill.dirty {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
}
.editor-fields { padding: 16px 20px 8px; }
.field-row { display: flex; gap: 12px; }
.field-col { display: flex; flex-direction: column; }
.editor-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  padding: 12px 20px;
  font-family: var(--sl-font-mono);
  font-size: 14px;
  line-height: 1.65;
  background: transparent;
  color: var(--sl-text);
}
.editor-textarea::placeholder { color: var(--sl-text-tertiary); }
.preview-pane {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  border-left: 1px solid var(--sl-border);
  background: var(--sl-bg);
}
.preview-pane.mobile-full {
  border-left: none;
  position: absolute;
  inset: 0;
  z-index: 10;
  background: var(--sl-bg-secondary);
}

/* --- Mobile --- */
.sidebar-toggle {
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 60;
  width: 36px;
  height: 36px;
  border-radius: var(--sl-radius);
  border: 1px solid var(--sl-border);
  background: color-mix(in srgb, var(--sl-card) 88%, transparent);
  box-shadow: var(--sl-shadow-card);
  backdrop-filter: saturate(1.08) blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--sl-text);
}
.mobile-fab {
  position: fixed;
  bottom: 16px;
  right: 16px;
  z-index: 50;
}
.fab-menu {
  display: flex;
  gap: 6px;
  background: var(--sl-card);
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius-lg);
  padding: 6px;
  box-shadow: var(--sl-shadow-flyout);
}
.fab-toggle { border-radius: var(--sl-radius); }

.discard-confirm {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
.discard-confirm__icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  border: 1px solid var(--sl-border);
  background: var(--sl-hover-bg);
  color: var(--sl-warning);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px var(--sl-primary-light);
}
.discard-confirm__icon--danger {
  color: var(--sl-danger);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--sl-danger) 22%, transparent);
}
.discard-confirm__content {
  min-width: 0;
  flex: 1;
}
.discard-confirm__text {
  font-size: 14px;
  line-height: 1.75;
  color: var(--sl-text-secondary);
}
.discard-confirm__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.discard-confirm__hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--sl-text-tertiary);
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    inset: 0;
    z-index: 55;
    width: 100vw;
    min-width: unset;
    max-width: none;
    background: color-mix(in srgb, var(--sl-sidebar-bg) 94%, var(--sl-bg-secondary));
    backdrop-filter: saturate(1.08) blur(18px);
    border-right: none;
    transform: translateX(-100%);
    transition: transform 0.28s ease;
  }
  .sidebar.open { transform: translateX(0); }
  .sidebar-inner {
    width: 100%;
    height: 100%;
    padding: calc(env(safe-area-inset-top, 0px) + 14px) 14px calc(env(safe-area-inset-bottom, 0px) + 12px);
    gap: 12px;
  }
  .sidebar.open .sidebar-inner {
    max-width: 100vw;
    background: transparent;
    box-shadow: none;
  }
  .sidebar-mobile-chrome {
    display: flex;
  }
  .sidebar-profile,
  .sidebar-section,
  .sidebar-actions,
  .sidebar-tools,
  .sidebar-tabs,
  .trash-panel__header,
  .quick-section,
  .outline-panel,
  .tree-panel,
  .trash-panel {
    border-radius: var(--sl-radius-lg);
  }
  .sidebar-profile,
  .sidebar-section,
  .sidebar-actions,
  .sidebar-tools,
  .sidebar-tabs,
  .trash-panel__header {
    padding: 12px;
    background: color-mix(in srgb, var(--sl-card) 92%, transparent);
    border: 1px solid var(--sl-border);
    box-shadow: var(--sl-shadow-card);
  }
  .sidebar-tabs {
    padding: 4px;
  }
  .sidebar-scroll {
    padding-right: 2px;
  }
  .sidebar-resize-handle {
    display: none;
  }
  .topbar {
    padding: 16px 16px 16px 56px;
    min-height: auto;
    align-items: flex-start;
  }
  .topbar-left {
    width: 100%;
  }
  .topbar-title {
    max-width: none;
  }
  .topbar-meta {
    flex-wrap: wrap;
    row-gap: 6px;
  }
  .viewer-area { padding: 16px; }
  .trash-item {
    flex-direction: column;
    align-items: stretch;
  }
  .trash-item__actions {
    width: 100%;
  }
  .trash-item__actions .sl-btn {
    flex: 1;
  }
  .mobile-fab {
    left: 16px;
    right: 16px;
  }
  .fab-menu {
    flex-direction: column;
    align-items: stretch;
    width: min(240px, calc(100vw - 32px));
    max-width: 100%;
    margin-left: auto;
  }
  .fab-menu .sl-btn {
    width: 100%;
    justify-content: center;
  }
  .discard-confirm {
    flex-direction: column;
    gap: 12px;
  }
  .editor-statusbar {
    flex-direction: column;
    align-items: flex-start;
    padding: 12px 16px 0;
  }
  .editor-status-group--right {
    justify-content: flex-start;
  }
  .editor-fields { padding: 12px 16px 8px; }
  .field-row { flex-direction: column; gap: 8px; }
  .editor-textarea { padding: 12px 16px; }
}
</style>

