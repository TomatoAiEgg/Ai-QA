<template>
  <div class="chat-container">
    <div class="chat-messages" ref="messagesContainer">
      <!-- 欢迎状态 -->
      <div v-if="messages.length === 0" class="welcome-container">
        <div class="welcome-logo">
          <div class="logo-icon">
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
          <h1 class="welcome-title">AI-QA 智能助手</h1>
          <p class="welcome-subtitle">有什么可以帮你的吗？</p>
        </div>
        <div class="suggestions-grid">
          <div class="suggestion-card" @click="useSuggestion('介绍一下你自己')">
            <div class="suggestion-icon">👋</div>
            <div class="suggestion-text">介绍一下你自己</div>
          </div>
          <div class="suggestion-card" @click="useSuggestion('帮我写一封邮件')">
            <div class="suggestion-icon">✉️</div>
            <div class="suggestion-text">帮我写一封邮件</div>
          </div>
          <div class="suggestion-card" @click="useSuggestion('解释一下什么是人工智能')">
            <div class="suggestion-icon">🤖</div>
            <div class="suggestion-text">解释一下人工智能</div>
          </div>
          <div class="suggestion-card" @click="useSuggestion('帮我写一段 Python 代码')">
            <div class="suggestion-icon">💻</div>
            <div class="suggestion-text">帮我写代码</div>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages-list">
        <div v-for="msg in messages" :key="msg.id" :class="['message-wrapper', msg.role]">
          <div class="message-avatar">
            <div v-if="msg.role === 'user'" class="avatar user-avatar"><span>👤</span></div>
            <div v-else class="avatar bot-avatar"><span>🤖</span></div>
          </div>
          <div class="message-content">
            <div :class="['message-bubble', msg.role]">
              <div class="message-text">{{ msg.content }}</div>
            </div>
            <div class="message-meta">
              <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
              <button v-if="msg.role === 'bot' && msg.content" class="copy-btn-small" @click="copyToClipboard(msg)" :title="msg.copied ? '已复制' : '复制'">
                <el-icon v-if="!msg.copied"><DocumentCopy /></el-icon>
                <el-icon v-else><Check /></el-icon>
              </button>
            </div>
          </div>
        </div>
        <div v-if="isBotResponding" class="typing-wrapper">
          <div class="typing-indicator">
            <div class="typing-dot"></div>
            <div class="typing-dot"></div>
            <div class="typing-dot"></div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="input-options">
        <label class="rag-switch" :class="{ active: useRag }">
          <input type="checkbox" v-model="useRag">
          <span class="switch-toggle"></span>
          <span class="switch-label">RAG 知识库</span>
        </label>
        <el-select v-model="model" placeholder="选择模型" size="small" class="model-select">
          <el-option label="千问" value="qwen" />
          <el-option label="DeepSeek" value="deepseek" disabled />
          <el-option label="智谱" value="zhipu" disabled />
        </el-select>
      </div>
      <div class="input-box-wrapper">
        <el-input
          v-model="question"
          placeholder="输入消息，按 Enter 发送..."
          @keydown.enter.exact="handleSend"
          :disabled="isBotResponding"
          class="question-input"
          :rows="1"
          type="textarea"
          resize="none"
        />
        <el-button
          :icon="Promotion"
          @click="handleSend"
          :disabled="!canSend || isBotResponding"
          class="send-btn"
          :class="{ sending: isBotResponding }"
        >
          发送
        </el-button>
      </div>
      <div class="input-hint">AI 生成的内容可能不准确，请仔细甄别</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, inject, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api.js'
import { Promotion, DocumentCopy, Check } from '@element-plus/icons-vue'

const messagesContainer = ref(null)
const question = ref('')
const messages = ref([])
const isBotResponding = ref(false)
const useRag = ref(false)
const model = ref('qwen')

const currentConvId = inject('currentConvId', ref(null))
const refreshConversations = inject('refreshConversations', () => {})
const loadAndSelectFirstConversation = inject('loadAndSelectFirstConversation', () => {})

const handleConversationChange = (event) => {
  const { id } = event.detail
  currentConvId.value = id
  if (id) {
    loadMessages(id)
  } else {
    messages.value = []
  }
}

const handleNewConversation = () => {
  currentConvId.value = null
  messages.value = []
}

onMounted(() => {
  window.addEventListener('conversation-change', handleConversationChange)
  window.addEventListener('new-conversation', handleNewConversation)
})

onUnmounted(() => {
  window.removeEventListener('conversation-change', handleConversationChange)
  window.removeEventListener('new-conversation', handleNewConversation)
})

const canSend = computed(() => question.value.trim().length > 0)

const useSuggestion = async (text) => {
  try {
    const conv = await api.createConversation()
    currentConvId.value = conv.id
    await refreshConversations()
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: conv.id } }))
    question.value = text
    await nextTick()
    await handleSend()
  } catch (error) {
    ElMessage.error('创建对话失败：' + error.message)
  }
}

const loadMessages = async (convId) => {
  if (!convId) {
    messages.value = []
    return
  }
  try {
    const msgs = await api.getConversationMessages(convId)
    messages.value = msgs.map(m => ({
      id: m.id,
      content: m.content,
      role: m.role === 'USER' ? 'user' : 'bot',
      timestamp: m.createdAt,
      copied: false
    }))
    await nextTick()
    scrollToBottom()
  } catch (error) {
    ElMessage.error('加载消息失败：' + error.message)
  }
}

const handleSend = async () => {
  if (!canSend.value || isBotResponding.value) return

  const content = question.value.trim()
  question.value = ''

  if (!currentConvId.value) {
    try {
      const conv = await api.createConversation()
      currentConvId.value = conv.id
      await refreshConversations()
    } catch (error) {
      ElMessage.error('创建对话失败：' + error.message)
      return
    }
  }

  messages.value.push({
    id: `user-${Date.now()}`,
    content,
    role: 'user',
    timestamp: new Date().toISOString(),
    copied: false
  })

  const botMsgId = `bot-${Date.now()}`
  let botContent = ''
  messages.value.push({
    id: botMsgId,
    content: '',
    role: 'bot',
    timestamp: new Date().toISOString(),
    copied: false
  })

  isBotResponding.value = true
  await nextTick()
  scrollToBottom(true)

  try {
    const response = await api.chatByStream(content, currentConvId.value, useRag.value)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmedLine = line.trim()
        if (trimmedLine.startsWith('data:') && trimmedLine !== 'data:[DONE]') {
          const data = trimmedLine.substring(5).trim()
          if (data) {
            botContent += data
            const msg = messages.value.find(m => m.id === botMsgId)
            if (msg) {
              msg.content = botContent
            }
          }
        }
      }
      scrollToBottom()
    }

    await nextTick()
    await refreshConversations()
  } catch (error) {
    const msg = messages.value.find(m => m.id === botMsgId)
    if (msg) {
      msg.content = '❌ 请求失败：' + error.message
    }
    ElMessage.error('AI 响应失败：' + error.message)
  } finally {
    isBotResponding.value = false
    await nextTick()
    scrollToBottom(true)
  }
}

const copyToClipboard = async (botMsg) => {
  try {
    await navigator.clipboard.writeText(botMsg.content)
    botMsg.copied = true
    setTimeout(() => { botMsg.copied = false }, 2000)
    ElMessage.success('已复制')
  } catch (error) {
    ElMessage.error('复制失败：' + error.message)
  }
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const scrollToBottom = (force = false) => {
  setTimeout(() => {
    if (messagesContainer.value) {
      if (force) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      } else {
        messagesContainer.value.scrollTo({
          top: messagesContainer.value.scrollHeight,
          behavior: 'smooth'
        })
      }
    }
  }, 50)
}
</script>

<style scoped>
@import '../styles/chat.css';
</style>
