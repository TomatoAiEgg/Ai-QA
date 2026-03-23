<template>
  <div :class="['app-shell', `theme-${activeTheme}`]">
    <div class="app-backdrop"></div>
    <el-container class="app-frame">
      <el-aside :width="sidebarVisible ? '288px' : '0'" class="app-aside">
        <div class="aside-inner">
          <div class="sidebar-header">
            <div class="brand-mark">
              <span class="brand-mark-core"></span>
            </div>
            <div class="sidebar-title-group">
              <span class="sidebar-eyebrow">Workspace</span>
              <span class="sidebar-title">AI-QA</span>
            </div>
          </div>

          <div class="sidebar-panel">
            <button class="primary-action" type="button" @click="createNewConversation">
              <el-icon><Plus /></el-icon>
              <span>新建对话</span>
            </button>

            <div class="conversation-section">
              <div class="section-label">最近会话</div>
              <div class="conversation-list">
                <button
                  v-for="conv in conversations"
                  :key="conv.id"
                  :class="['conversation-item', { active: currentConvId === conv.id }]"
                  type="button"
                  @click="selectConversation(conv.id)"
                >
                  <div class="conversation-main">
                    <div class="conversation-title">{{ conv.title }}</div>
                    <div class="conversation-subtitle">继续这段对话</div>
                  </div>
                  <div class="conversation-actions">
                    <el-tooltip content="编辑标题" placement="top">
                      <el-button class="action-btn" :icon="Edit" circle size="small" @click.stop="editConversation(conv)" />
                    </el-tooltip>
                    <el-tooltip content="删除对话" placement="top">
                      <el-button class="action-btn danger" :icon="Delete" circle size="small" @click.stop="deleteConversation(conv)" />
                    </el-tooltip>
                  </div>
                </button>
                <div v-if="conversations.length === 0" class="empty-tip">
                  还没有历史会话，先开始一段新的对话。
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-aside>

      <el-container class="content-shell">
        <el-header class="app-header">
          <div class="header-left">
            <el-button class="icon-btn" circle @click="toggleSidebar">
              <el-icon>
                <component :is="sidebarVisible ? Fold : Expand" />
              </el-icon>
            </el-button>
            <div class="header-copy">
              <span class="header-title">AI 对话工作台</span>
              <span class="header-subtitle">更干净的聊天界面，支持知识库检索与主题切换</span>
            </div>
          </div>

          <div class="header-right">
            <div class="theme-switcher">
              <button class="theme-button" type="button" @click="cycleTheme">
                <span class="theme-dot"></span>
                <span>{{ currentTheme.label }}</span>
              </button>
              <div class="theme-pills">
                <button
                  v-for="theme in themes"
                  :key="theme.value"
                  :class="['theme-pill', { active: activeTheme === theme.value }]"
                  type="button"
                  @click="setTheme(theme.value)"
                >
                  {{ theme.shortLabel }}
                </button>
              </div>
            </div>

            <el-button class="ghost-btn" @click="openRagModal">
              <el-icon><Folder /></el-icon>
              <span>知识库管理</span>
            </el-button>
          </div>
        </el-header>

        <el-main class="app-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>

    <RagView v-if="showRagModal" @close="closeRagModal" />
  </div>
</template>

<script setup>
import { ref, provide, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from './api.js'
import { Fold, Expand, Plus, Folder, Edit, Delete } from '@element-plus/icons-vue'
import RagView from './views/RagView.vue'

const THEME_STORAGE_KEY = 'aiqa-theme'

const themes = [
  { value: 'mist', label: '雾灰主题', shortLabel: '雾灰' },
  { value: 'forest', label: '青苔主题', shortLabel: '青苔' },
  { value: 'sunset', label: '日落主题', shortLabel: '日落' }
]

const sidebarVisible = ref(true)
const conversations = ref([])
const currentConvId = ref(null)
const showRagModal = ref(false)
const activeTheme = ref(localStorage.getItem(THEME_STORAGE_KEY) || themes[0].value)

const currentTheme = computed(() => themes.find(theme => theme.value === activeTheme.value) || themes[0])

const refreshConversations = async () => {
  try {
    conversations.value = await api.getConversations()
  } catch (error) {
    console.error('加载对话列表失败:', error)
  }
}

const loadAndSelectFirstConversation = async () => {
  await refreshConversations()
  if (conversations.value.length > 0) {
    currentConvId.value = conversations.value[0].id
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: conversations.value[0].id } }))
  } else {
    currentConvId.value = null
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: null } }))
  }
}

provide('refreshConversations', refreshConversations)
provide('currentConvId', currentConvId)
provide('loadAndSelectFirstConversation', loadAndSelectFirstConversation)

onMounted(() => {
  if (!themes.some(theme => theme.value === activeTheme.value)) {
    activeTheme.value = themes[0].value
  }
  loadAndSelectFirstConversation()
})

const toggleSidebar = () => {
  sidebarVisible.value = !sidebarVisible.value
}

const selectConversation = (id) => {
  currentConvId.value = id
  window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id } }))
}

const createNewConversation = () => {
  currentConvId.value = null
  window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: null } }))
}

const editConversation = async (conv) => {
  const newTitle = prompt('修改对话标题：', conv.title)
  if (newTitle && newTitle !== conv.title) {
    try {
      await api.updateConversationTitle(conv.id, newTitle)
      await refreshConversations()
      ElMessage.success('标题已更新')
    } catch (error) {
      ElMessage.error('更新失败：' + error.message)
    }
  }
}

const deleteConversation = async (conv) => {
  try {
    await api.deleteConversation(conv.id)
    if (currentConvId.value === conv.id) {
      currentConvId.value = null
      window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: null } }))
    }
    await refreshConversations()
    ElMessage.success('对话已删除')
  } catch (error) {
    ElMessage.error('删除失败：' + error.message)
  }
}

const setTheme = (theme) => {
  activeTheme.value = theme
  localStorage.setItem(THEME_STORAGE_KEY, theme)
}

const cycleTheme = () => {
  const currentIndex = themes.findIndex(theme => theme.value === activeTheme.value)
  const nextIndex = (currentIndex + 1) % themes.length
  setTheme(themes[nextIndex].value)
}

const openRagModal = () => {
  showRagModal.value = true
}

const closeRagModal = () => {
  showRagModal.value = false
}
</script>

<style>
:root {
  color-scheme: light;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}

html,
body,
#app {
  height: 100%;
  margin: 0;
}

body {
  background: #edf2f7;
  color: #11212d;
}

.app-shell {
  --bg-main: #edf2f7;
  --bg-panel: rgba(255, 255, 255, 0.78);
  --bg-panel-strong: rgba(255, 255, 255, 0.92);
  --bg-muted: rgba(241, 245, 249, 0.92);
  --bg-sidebar: rgba(245, 247, 251, 0.86);
  --text-primary: #14202b;
  --text-secondary: #5a6776;
  --text-muted: #8a95a3;
  --line-soft: rgba(148, 163, 184, 0.18);
  --line-strong: rgba(148, 163, 184, 0.26);
  --accent: #2563eb;
  --accent-strong: #1d4ed8;
  --accent-soft: rgba(37, 99, 235, 0.12);
  --accent-shadow: rgba(37, 99, 235, 0.18);
  --danger-soft: rgba(239, 68, 68, 0.14);
  --surface-shadow: 0 20px 50px rgba(15, 23, 42, 0.10);
  --chip-shadow: 0 16px 32px rgba(15, 23, 42, 0.08);
  --sidebar-accent: linear-gradient(145deg, rgba(37, 99, 235, 0.14), rgba(255, 255, 255, 0.65));
  --app-gradient: radial-gradient(circle at top left, rgba(37, 99, 235, 0.10), transparent 34%),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.12), transparent 24%),
    linear-gradient(180deg, #edf2f7 0%, #e5edf5 100%);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: var(--app-gradient);
  color: var(--text-primary);
}

.app-shell.theme-forest {
  --bg-main: #eef4ef;
  --bg-panel: rgba(253, 255, 253, 0.76);
  --bg-panel-strong: rgba(255, 255, 255, 0.9);
  --bg-muted: rgba(241, 247, 241, 0.92);
  --bg-sidebar: rgba(245, 249, 244, 0.88);
  --text-primary: #15241b;
  --text-secondary: #506256;
  --text-muted: #7c8e83;
  --line-soft: rgba(96, 128, 103, 0.16);
  --line-strong: rgba(96, 128, 103, 0.26);
  --accent: #2f855a;
  --accent-strong: #276749;
  --accent-soft: rgba(47, 133, 90, 0.12);
  --accent-shadow: rgba(47, 133, 90, 0.16);
  --sidebar-accent: linear-gradient(145deg, rgba(47, 133, 90, 0.14), rgba(255, 255, 255, 0.62));
  --app-gradient: radial-gradient(circle at top left, rgba(47, 133, 90, 0.12), transparent 32%),
    radial-gradient(circle at top right, rgba(113, 179, 143, 0.14), transparent 26%),
    linear-gradient(180deg, #eef4ef 0%, #e7f0e7 100%);
}

.app-shell.theme-sunset {
  --bg-main: #f7efe9;
  --bg-panel: rgba(255, 252, 249, 0.78);
  --bg-panel-strong: rgba(255, 255, 255, 0.9);
  --bg-muted: rgba(250, 244, 239, 0.92);
  --bg-sidebar: rgba(251, 244, 239, 0.88);
  --text-primary: #271a16;
  --text-secondary: #765a4d;
  --text-muted: #9b7c6d;
  --line-soft: rgba(180, 116, 86, 0.15);
  --line-strong: rgba(180, 116, 86, 0.25);
  --accent: #dd6b20;
  --accent-strong: #c05621;
  --accent-soft: rgba(221, 107, 32, 0.13);
  --accent-shadow: rgba(221, 107, 32, 0.18);
  --sidebar-accent: linear-gradient(145deg, rgba(221, 107, 32, 0.14), rgba(255, 255, 255, 0.62));
  --app-gradient: radial-gradient(circle at top left, rgba(221, 107, 32, 0.12), transparent 33%),
    radial-gradient(circle at top right, rgba(251, 146, 60, 0.14), transparent 28%),
    linear-gradient(180deg, #f7efe9 0%, #f4e6dc 100%);
}

.app-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 15%, rgba(255, 255, 255, 0.75), transparent 20%),
    radial-gradient(circle at 85% 5%, rgba(255, 255, 255, 0.55), transparent 18%);
  pointer-events: none;
}

.app-frame {
  position: relative;
  z-index: 1;
  height: 100vh;
  padding: 18px;
  gap: 16px;
}

.app-aside {
  overflow: hidden;
  transition: width 0.28s ease;
}

.aside-inner {
  height: 100%;
  border: 1px solid var(--line-soft);
  border-radius: 28px;
  background: var(--bg-sidebar);
  backdrop-filter: blur(18px);
  box-shadow: var(--surface-shadow);
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px 22px 18px;
}

.brand-mark {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--sidebar-accent);
  border: 1px solid var(--line-soft);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.brand-mark-core {
  position: absolute;
  inset: 10px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 55%, white));
}

.sidebar-title-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar-eyebrow {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.sidebar-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.sidebar-panel {
  display: flex;
  flex-direction: column;
  height: calc(100% - 84px);
  padding: 0 14px 16px;
}

.primary-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 68%, white));
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 18px 30px -18px var(--accent-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.primary-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 20px 36px -18px var(--accent-shadow);
}

.conversation-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin-top: 18px;
}

.section-label {
  padding: 0 8px 10px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 2px 2px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  margin-bottom: 10px;
  padding: 14px;
  border: 1px solid transparent;
  border-radius: 18px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.conversation-item:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.54);
  border-color: var(--line-soft);
}

.conversation-item.active {
  background: var(--bg-panel-strong);
  border-color: color-mix(in srgb, var(--accent) 24%, transparent);
  box-shadow: 0 18px 36px -22px var(--accent-shadow);
}

.conversation-main {
  flex: 1;
  min-width: 0;
}

.conversation-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.conversation-subtitle {
  margin-top: 5px;
  font-size: 12px;
  color: var(--text-muted);
}

.conversation-actions {
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.conversation-item:hover .conversation-actions,
.conversation-item.active .conversation-actions {
  opacity: 1;
}

.action-btn {
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.75);
  color: var(--text-secondary);
}

.action-btn:hover {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 28%, transparent);
  background: white;
}

.action-btn.danger:hover {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.22);
  background: rgba(254, 242, 242, 0.95);
}

.empty-tip {
  padding: 18px;
  border: 1px dashed var(--line-strong);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.4);
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.content-shell {
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 82px;
  padding: 0 8px 14px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.icon-btn,
.ghost-btn {
  height: 44px;
  border-radius: 16px;
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  color: var(--text-secondary);
  box-shadow: var(--chip-shadow);
}

.icon-btn:hover,
.ghost-btn:hover {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 24%, transparent);
  background: var(--bg-panel-strong);
}

.ghost-btn {
  padding: 0 16px;
}

.header-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.header-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

.theme-switcher {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  background: var(--bg-panel);
  backdrop-filter: blur(18px);
  box-shadow: var(--chip-shadow);
}

.theme-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 16px;
  border: none;
  border-radius: 14px;
  background: var(--bg-panel-strong);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.theme-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 50%, white));
  box-shadow: 0 0 0 6px var(--accent-soft);
}

.theme-pills {
  display: flex;
  gap: 8px;
}

.theme-pill {
  height: 36px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.theme-pill:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.55);
}

.theme-pill.active {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent) 20%, transparent);
}

.app-main {
  height: calc(100vh - 100px);
  padding: 0;
  border: 1px solid var(--line-soft);
  border-radius: 32px;
  background: var(--bg-panel);
  backdrop-filter: blur(20px);
  box-shadow: var(--surface-shadow);
  overflow: hidden;
}

.conversation-list::-webkit-scrollbar {
  width: 6px;
}

.conversation-list::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--accent) 16%, rgba(148, 163, 184, 0.45));
  border-radius: 999px;
}

@media (max-width: 1080px) {
  .app-frame {
    padding: 12px;
    gap: 12px;
  }

  .app-header {
    height: auto;
    flex-direction: column;
    align-items: stretch;
    padding: 0 0 12px;
    gap: 12px;
  }

  .header-right {
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .theme-switcher {
    flex-wrap: wrap;
  }

  .ghost-btn {
    flex: 1;
  }

  .app-main {
    height: calc(100vh - 150px);
  }
}

@media (max-width: 768px) {
  .app-frame {
    padding: 10px;
  }

  .app-aside {
    position: absolute;
    z-index: 20;
    top: 10px;
    bottom: 10px;
    left: 10px;
  }

  .content-shell {
    width: 100%;
  }

  .header-left {
    align-items: flex-start;
  }

  .header-copy {
    gap: 6px;
  }

  .header-subtitle {
    max-width: 220px;
    line-height: 1.4;
  }

  .theme-pills {
    width: 100%;
    justify-content: space-between;
  }

  .theme-pill {
    flex: 1;
  }
}
</style>
