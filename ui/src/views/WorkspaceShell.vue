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

            <div class="user-chip">{{ currentUserLabel }}</div>

            <el-button class="ghost-btn" @click="openRagModal">
              <el-icon><Folder /></el-icon>
              <span>知识库管理</span>
            </el-button>

            <el-button class="ghost-btn logout-btn" @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              <span>退出</span>
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
import { computed, onMounted, provide, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '../api.js'
import { authState, clearAuthState } from '../auth.js'
import { Fold, Expand, Plus, Folder, Edit, Delete, SwitchButton } from '@element-plus/icons-vue'
import RagView from './RagView.vue'

const router = useRouter()

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
const getErrorMessage = (error, fallback = '操作失败') => error?.message || fallback

const currentTheme = computed(() => themes.find(theme => theme.value === activeTheme.value) || themes[0])
const currentUserLabel = computed(() => {
  const user = authState.user
  if (!user) return '未登录'
  return user.nickname || user.email || user.phone || '用户'
})

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
  const newTitle = prompt('修改对话标题', conv.title)
  if (newTitle && newTitle !== conv.title) {
    try {
      await api.updateConversationTitle(conv.id, newTitle)
      await refreshConversations()
      ElMessage.success('标题已更新')
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '更新失败'))
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
    ElMessage.error(getErrorMessage(error, '删除失败'))
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

const handleLogout = async () => {
  try {
    await api.logout()
  } catch (error) {
    console.error('退出登录失败:', error)
  } finally {
    clearAuthState()
    currentConvId.value = null
    conversations.value = []
    closeRagModal()
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: null } }))
    await router.replace('/login')
  }
}
</script>

<style>
@import '../styles/workspace-shell.css';
</style>
