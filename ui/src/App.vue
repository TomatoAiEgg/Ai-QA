<template>
  <div class="app-container">
    <el-container>
      <!-- 侧边栏 -->
      <el-aside :width="sidebarVisible ? '280px' : '0'" class="app-aside">
        <div class="sidebar-header">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话历史</span>
        </div>
        <div class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :class="['conversation-item', { active: currentConvId === conv.id }]"
            @click="selectConversation(conv.id)"
          >
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-actions">
              <el-tooltip content="编辑标题" placement="top">
                <el-button class="edit-btn" :icon="Edit" circle size="small" @click.stop="editConversation(conv)" />
              </el-tooltip>
              <el-tooltip content="删除对话" placement="top">
                <el-button class="delete-btn" :icon="Delete" circle size="small" type="danger" @click.stop="deleteConversation(conv)" />
              </el-tooltip>
            </div>
          </div>
          <div v-if="conversations.length === 0" class="empty-tip">暂无历史对话</div>
        </div>
      </el-aside>

      <!-- 主内容区 -->
      <el-container>
        <el-header class="app-header">
          <div class="header-left">
            <el-tooltip :content="sidebarVisible ? '收起侧边栏' : '展开侧边栏'" placement="bottom">
              <el-button class="toggle-btn" :icon="sidebarVisible ? 'Fold' : 'Expand'" circle @click="toggleSidebar" />
            </el-tooltip>
            <div class="brand-logo">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C10.8954 2 10 2.89543 10 4V6H6C3.79086 6 2 7.79086 2 10V16C2 18.2091 3.79086 20 6 20H18C20.2091 20 22 18.2091 22 16V10C22 7.79086 20.2091 6 18 6H14V4C14 2.89543 13.1046 2 12 2Z" fill="url(#gradient1)"/>
                <path d="M7 11.5C7 10.6716 7.67157 10 8.5 10C9.32843 10 10 10.6716 10 11.5C10 12.3284 9.32843 13 8.5 13C7.67157 13 7 12.3284 7 11.5Z" fill="white"/>
                <path d="M14 11.5C14 10.6716 14.6716 10 15.5 10C16.3284 10 17 10.6716 17 11.5C17 12.3284 16.3284 13 15.5 13C14.6716 13 14 12.3284 14 11.5Z" fill="white"/>
                <path d="M8 16C8 15.4477 8.44772 15 9 15H15C15.5523 15 16 15.4477 16 16C16 16.5523 15.5523 17 15 17H9C8.44772 17 8 16.5523 8 16Z" fill="white"/>
                <defs>
                  <linearGradient id="gradient1" x1="2" y1="2" x2="22" y2="22">
                    <stop stop-color="#667EEA"/>
                    <stop offset="1" stop-color="#764BA2"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <h1>AI-QA</h1>
          </div>
          <div class="header-right">
            <el-tooltip content="新建对话" placement="bottom">
              <el-button class="new-chat-btn" :icon="Plus" @click="createNewConversation">
                <span class="btn-text">新对话</span>
              </el-button>
            </el-tooltip>
            <el-tooltip content="知识库管理" placement="bottom">
              <el-button class="rag-btn" :icon="Folder" @click="openRagModal" circle />
            </el-tooltip>
          </div>
        </el-header>

        <el-main class="app-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>

    <!-- RAG 知识库管理模态框 -->
    <RagView v-if="showRagModal" @close="closeRagModal" />
  </div>
</template>

<script setup>
import { ref, onMounted, provide, computed } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from './api.js'
import { ChatDotRound, Fold, Expand, Plus, Folder, Edit, Delete } from '@element-plus/icons-vue'
import RagView from './views/RagView.vue'

const sidebarVisible = ref(true)
const conversations = ref([])
const currentConvId = ref(null)
const showRagModal = ref(false)

const refreshConversations = async () => {
  try {
    conversations.value = await api.getConversations()
  } catch (error) {
    console.error('加载对话列表失败:', error)
  }
}

// 加载并选中第一个历史对话
const loadAndSelectFirstConversation = async () => {
  await refreshConversations()
  if (conversations.value.length > 0) {
    // 有历史对话，选中第一个
    currentConvId.value = conversations.value[0].id
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: conversations.value[0].id } }))
  } else {
    // 没有历史对话，清空当前对话
    currentConvId.value = null
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: null } }))
  }
}

provide('refreshConversations', refreshConversations)
provide('currentConvId', currentConvId)
provide('loadAndSelectFirstConversation', loadAndSelectFirstConversation)

onMounted(() => {
  loadAndSelectFirstConversation()
})

const toggleSidebar = () => {
  sidebarVisible.value = !sidebarVisible.value
}

// 选择历史对话
const selectConversation = (id) => {
  currentConvId.value = id
  window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id } }))
}

// 新建对话 - 直接跳首页，清空当前对话
const createNewConversation = () => {
  currentConvId.value = null
  window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: null } }))
}

const editConversation = async (conv) => {
  const newTitle = prompt('修改对话标题:', conv.title)
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

// 打开 RAG 模态框
const openRagModal = () => {
  showRagModal.value = true
}

// 关闭 RAG 模态框
const closeRagModal = () => {
  showRagModal.value = false
}
</script>

<style scoped>
.app-container {
  height: 100vh;
  overflow: hidden;
}

.app-aside {
  background: linear-gradient(to bottom, #1a1a2e 0%, #16213e 100%);
  transition: width 0.3s ease;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  color: white;
  font-size: 1.1rem;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-header .el-icon {
  font-size: 1.3rem;
  color: #667EEA;
}

.conversation-list {
  padding: 12px;
  overflow-y: auto;
  height: calc(100vh - 70px);
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: rgba(255, 255, 255, 0.8);
}

.conversation-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

.conversation-item.active {
  background: linear-gradient(135deg, #667EEA 0%, #764BA2 100%);
  color: white;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
}

.conv-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .conv-actions {
  opacity: 1;
}

.edit-btn,
.delete-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.edit-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.delete-btn:hover {
  background: rgba(239, 68, 68, 0.8);
}

.empty-tip {
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  padding: 40px 20px;
  font-size: 0.9rem;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 24px;
  height: 64px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toggle-btn {
  border: 1px solid #e8e8e8;
  background: #f9f9f9;
  color: #666;
}

.toggle-btn:hover {
  border-color: #667EEA;
  color: #667EEA;
}

.brand-logo {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-logo svg {
  width: 100%;
  height: 100%;
}

.header-left h1 {
  font-size: 1.3rem;
  font-weight: 700;
  margin: 0;
  background: linear-gradient(135deg, #667EEA 0%, #764BA2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  gap: 12px;
}

.new-chat-btn {
  background: linear-gradient(135deg, #667EEA 0%, #764BA2 100%);
  border: none;
  color: white;
  padding: 10px 20px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: all 0.3s;
}

.new-chat-btn:hover {
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
  transform: translateY(-1px);
}

.btn-text {
  font-weight: 500;
}

.rag-btn {
  border: 1px solid #e8e8e8;
  background: #f9f9f9;
  color: #666;
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 12px;
}

.rag-btn:hover {
  border-color: #667EEA;
  color: #667EEA;
  background: rgba(102, 126, 234, 0.1);
}

.app-main {
  padding: 0;
  overflow: hidden;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  height: calc(100vh - 64px);
}
</style>
