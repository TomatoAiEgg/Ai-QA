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
              <div class="message-text markdown-body" v-html="renderMarkdown(msg.content, msg.id === 'streaming')"></div>
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
        <el-select
          v-if="useRag"
          v-model="selectedKbId"
          placeholder="请选择知识库"
          size="small"
          class="kb-select"
        >
          <el-option
            label="全部知识库"
            :value="ALL_KNOWLEDGE_BASES"
          />
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
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
import { ref, reactive, computed, onMounted, onUnmounted, inject, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api.js'
import { Promotion, DocumentCopy, Check } from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// 配置 marked 使用 highlight.js 进行代码高亮
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (e) {
        return hljs.highlightAuto(code).value
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true,
  silent: true
})

// 流式 Markdown 渲染器 - 在流式传输过程中使用
function streamingMarkdownRender(text) {
  if (!text) return ''

  // 转义 HTML 防止 XSS
  let escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 代码块 ```code``` (优先处理，避免内容被转义)
  let codeBlocks = []
  escaped = escaped.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
    const language = lang || 'plaintext'
    const index = codeBlocks.length
    // 添加 hljs 类名以应用高亮样式
    codeBlocks.push(`<pre><code class="language-${language} hljs">${code.trim()}</code></pre>`)
    return `%%CODEBLOCK${index}%%`
  })

  // 行内代码 `code`
  escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>')

  // 粗体 **text**
  escaped = escaped.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')

  // 斜体 *text*
  escaped = escaped.replace(/\*([^*]+)\*/g, '<em>$1</em>')

  // 标题
  escaped = escaped.replace(/^### (.*$)/gim, '<h3>$1</h3>')
  escaped = escaped.replace(/^## (.*$)/gim, '<h2>$1</h2>')
  escaped = escaped.replace(/^# (.*$)/gim, '<h1>$1</h1>')

  // 引用
  escaped = escaped.replace(/^> (.*$)/gim, '<blockquote>$1</blockquote>')

  // 有序列表
  escaped = escaped.replace(/^\s*\d+\.\s+(.*$)/gim, '<li>$1</li>')

  // 无序列表
  escaped = escaped.replace(/^\s*[-*]\s+(.*$)/gim, '<li>$1</li>')

  // 链接 [text](url)
  escaped = escaped.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')

  // 换行（段落之间用</p><p>，行内用<br>）
  escaped = escaped.replace(/\n\n/g, '</p><p>')
  escaped = escaped.replace(/\n/g, '<br>')

  // 包裹在 p 标签中
  escaped = '<p>' + escaped + '</p>'

  // 恢复代码块
  codeBlocks.forEach((block, index) => {
    escaped = escaped.replace(`%%CODEBLOCK${index}%%`, block)
  })

  return escaped
}

const messagesContainer = ref(null)
const question = ref('')
const messages = ref([])
const isBotResponding = ref(false)
const useRag = ref(false)
const model = ref('qwen')
const knowledgeBases = ref([])
const ALL_KNOWLEDGE_BASES = '__ALL_KNOWLEDGE_BASES__'
const selectedKbId = ref(ALL_KNOWLEDGE_BASES)

const currentConvId = inject('currentConvId', ref(null))
const refreshConversations = inject('refreshConversations', () => {})
const loadAndSelectFirstConversation = inject('loadAndSelectFirstConversation', () => {})

const handleConversationChange = (event) => {
  const { id } = event.detail
  currentConvId.value = id
  // 只有在没有正在响应的消息时才加载历史消息
  // 这样可以避免覆盖 useSuggestion 中刚添加的消息
  if (!isBotResponding.value) {
    if (id) {
      loadMessages(id)
    } else {
      messages.value = []
    }
  }
}

const handleNewConversation = () => {
  currentConvId.value = null
  messages.value = []
}

const handleKnowledgeBasesUpdated = () => {
  loadKnowledgeBases()
}

onMounted(() => {
  window.addEventListener('conversation-change', handleConversationChange)
  window.addEventListener('new-conversation', handleNewConversation)
  window.addEventListener('knowledge-bases-updated', handleKnowledgeBasesUpdated)
  loadKnowledgeBases()
})

onUnmounted(() => {
  window.removeEventListener('conversation-change', handleConversationChange)
  window.removeEventListener('new-conversation', handleNewConversation)
  window.removeEventListener('knowledge-bases-updated', handleKnowledgeBasesUpdated)
})

const canSend = computed(() => question.value.trim().length > 0)

const loadKnowledgeBases = async () => {
  try {
    knowledgeBases.value = await api.getKnowledgeBases()
    if (selectedKbId.value !== ALL_KNOWLEDGE_BASES && !knowledgeBases.value.some(kb => kb.id === selectedKbId.value)) {
      selectedKbId.value = ALL_KNOWLEDGE_BASES
    }
  } catch (error) {
    console.error('加载知识库失败:', error)
  }
}

const ensureRagSelection = () => {
  return true
}

const resolveSelectedKbId = () => selectedKbId.value === ALL_KNOWLEDGE_BASES ? null : selectedKbId.value

const useSuggestion = async (text) => {
  let streamingMsg = null
  try {
    if (!ensureRagSelection()) {
      return
    }
    // 1. 创建新对话
    const conv = await api.createConversation()
    currentConvId.value = conv.id

    // 2. 刷新侧边栏对话列表
    await refreshConversations()

    // 3. 先清空消息数组（确保从欢迎页面切换出来）
    messages.value = []

    // 4. 设置问题
    question.value = text
    await nextTick()

    // 5. 直接执行 handleSend 的逻辑（不依赖 canSend）
    const content = question.value.trim()
    question.value = ''

    // 添加用户消息
    messages.value.push({
      id: `user-${Date.now()}`,
      content,
      role: 'user',
      timestamp: new Date().toISOString(),
      copied: false
    })

    // 创建流式消息对象 - 使用 ref 确保响应式
    const botMsgId = 'streaming'
    streamingMsg = reactive({
      id: botMsgId,
      content: '',
      role: 'bot',
      timestamp: new Date().toISOString(),
      copied: false
    })
    messages.value.push(streamingMsg)

    isBotResponding.value = true
    await nextTick()
    scrollToBottom(true)

    // 发送请求
    const response = await api.chatByStream(content, currentConvId.value, {
      useRag: useRag.value,
      model: model.value,
      kbId: resolveSelectedKbId()
    })

    await api.readSSEStream(response, async (data) => {
      streamingMsg.content += data
      await nextTick()
      scrollToBottom()
    })

    streamingMsg.id = `bot-${Date.now()}`
    isBotResponding.value = false

    // 发送 conversation-change 事件，让侧边栏选中当前对话（在完成后）
    window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id: conv.id } }))

    await nextTick()
    await refreshConversations()
    scrollToBottom(true)
  } catch (error) {
    if (streamingMsg?.value) {
      streamingMsg.content = streamingMsg.content || ('❌ 请求失败：' + error.message)
      streamingMsg.id = `bot-${Date.now()}`
    }
    ElMessage.error('创建对话失败：' + error.message)
  } finally {
    isBotResponding.value = false
  }
}

// 选择历史对话
const selectConversation = (id) => {
  currentConvId.value = id
  window.dispatchEvent(new CustomEvent('conversation-change', { detail: { id } }))
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
  if (!ensureRagSelection()) return

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

  // 使用 shallowRef 存储消息数组，确保响应式更新
  messages.value.push({
    id: `user-${Date.now()}`,
    content,
    role: 'user',
    timestamp: new Date().toISOString(),
    copied: false
  })

  // 创建流式消息对象 - 使用 ref 包装整个对象以确保响应式
  const botMsgId = 'streaming'
  const streamingMsg = reactive({
    id: botMsgId,
    content: '',
    role: 'bot',
    timestamp: new Date().toISOString(),
    copied: false
  })
  messages.value.push(streamingMsg)

  isBotResponding.value = true
  await nextTick()
  scrollToBottom(true)

  try {
    const response = await api.chatByStream(content, currentConvId.value, {
      useRag: useRag.value,
      model: model.value,
      kbId: resolveSelectedKbId()
    })

    // 使用 SSE 流读取
    await api.readSSEStream(response, async (data) => {
      // 直接更新 content，Vue 3 会检测到变化
      streamingMsg.content += data
      // 使用 nextTick 确保 DOM 更新后再滚动
      await nextTick()
      scrollToBottom()
    })

    // 流式传输完成，将 streaming 消息改为正式 ID
    streamingMsg.id = `bot-${Date.now()}`

    await nextTick()
    await refreshConversations()
  } catch (error) {
    streamingMsg.content = '❌ 请求失败：' + error.message
    streamingMsg.id = `bot-${Date.now()}`
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

// 渲染 Markdown - 流式传输时使用简单渲染，完成后使用完整渲染
const renderMarkdown = (content, isStreaming = false) => {
  if (!content) return ''
  try {
    // 流式传输过程中使用简单渲染
    if (isStreaming) {
      return marked.parse(content)
    }
    // 流式完成后使用完整的 marked 渲染（带代码高亮）
    return marked.parse(content)
  } catch (error) {
    console.error('Markdown 解析失败:', error)
    return content
  }
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

<style>
/* 全局导入 chat.css，确保 markdown 样式能应用到 v-html 内容 */
@import '../styles/chat.css';
</style>

<style scoped>
/* 仅作用于当前组件的样式 */
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
}
</style>
