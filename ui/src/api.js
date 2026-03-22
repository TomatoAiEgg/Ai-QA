import axios from 'axios'

const api = axios.create({
  baseURL: '',
  timeout: 60000
})

// ========== 知识库管理 ==========
export const getKnowledgeBases = async () => {
  const res = await api.get('/api/knowledge-bases')
  return res.data
}

export const createKnowledgeBase = async (name, description) => {
  const res = await api.post('/api/knowledge-bases', { name, description })
  return res.data
}

export const deleteKnowledgeBase = async (id) => {
  await api.delete(`/api/knowledge-bases/${id}`)
}

export const getKnowledgeBase = async (id) => {
  const res = await api.get(`/api/knowledge-bases/${id}`)
  return res.data
}

// ========== 文档管理 ==========
export const getDocumentsByKbId = async (kbId) => {
  const res = await api.get(`/api/knowledge-bases/${kbId}/documents`)
  return res.data
}

export const uploadDocument = async (file, kbId) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('kbId', kbId)
  const res = await api.post('/ai/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export const previewChunks = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await api.post('/ai/preview-chunks', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export const deleteDocument = async (id) => {
  await api.delete(`/ai/documents/${id}`)
}

// ========== 对话管理 ==========
export const getConversations = async () => {
  const res = await api.get('/ai/conversations')
  return res.data
}

export const createConversation = async () => {
  const res = await api.post('/ai/conversations')
  return res.data
}

export const deleteConversation = async (id) => {
  await api.delete(`/ai/conversations/${id}`)
}

export const updateConversationTitle = async (id, title) => {
  await api.put(`/ai/conversations/${id}/title`, { title })
}

export const getConversationMessages = async (id) => {
  const res = await api.get(`/ai/conversations/${id}/messages`)
  return res.data
}

// ========== AI 对话 ==========
// 使用 fetch API 读取流式响应 (SSE 格式)
export const chatByStream = async (question, conversationId, useRag) => {
  const url = useRag ? '/ai/chatByRag' : '/ai/chat'
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      question,
      conversationId: conversationId || null,
      useRag: useRag || false
    })
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`请求失败：${response.status} - ${errorText}`)
  }

  return response
}

// 读取 SSE 流中的文本内容
export const readSSEStream = async (response, onChunk) => {
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
          onChunk(data)
        }
      }
    }
  }
  
  return buffer
}

export default api
