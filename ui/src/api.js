import axios from 'axios'
import { getStoredTokenHeaders } from './auth-storage.js'

const api = axios.create({
  baseURL: '',
  timeout: 60000,
  withCredentials: true
})

const redirectToLogin = () => {
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

const extractErrorMessage = (error) => {
  return error?.response?.data?.message || error?.message || '请求失败'
}

const unwrapResult = (response) => {
  const body = response?.data
  if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
    if (body.code !== 200) {
      throw new Error(body.message || '请求失败')
    }
    return body.data
  }
  return body
}

api.interceptors.response.use(
  response => response,
  error => {
    const status = error?.response?.status
    const url = error?.config?.url || ''
    const isAuthRequest = url.startsWith('/api/auth/')
    if (status === 401 && !isAuthRequest) {
      redirectToLogin()
    }
    return Promise.reject(new Error(extractErrorMessage(error)))
  }
)

api.interceptors.request.use((config) => {
  const tokenHeaders = getStoredTokenHeaders()
  config.headers = {
    ...(config.headers || {}),
    ...tokenHeaders
  }
  return config
})

export const register = async ({ email = '', phone = '', password, nickname = '' }) => {
  return unwrapResult(await api.post('/api/auth/register', { email, phone, password, nickname }))
}

export const login = async (account, password) => {
  return unwrapResult(await api.post('/api/auth/login', { account, password }))
}

export const logout = async () => {
  return unwrapResult(await api.post('/api/auth/logout'))
}

export const getAuthSession = async () => {
  return unwrapResult(await api.get('/api/auth/session'))
}

export const getKnowledgeBases = async () => {
  return unwrapResult(await api.get('/api/knowledge-bases'))
}

export const createKnowledgeBase = async (name, description) => {
  return unwrapResult(await api.post('/api/knowledge-bases', { name, description }))
}

export const updateKnowledgeBase = async (id, name, description) => {
  return unwrapResult(await api.put(`/api/knowledge-bases/${id}`, { name, description }))
}

export const deleteKnowledgeBase = async (id) => {
  return unwrapResult(await api.delete(`/api/knowledge-bases/${id}`))
}

export const getKnowledgeBase = async (id) => {
  return unwrapResult(await api.get(`/api/knowledge-bases/${id}`))
}

export const getDocumentsByKbId = async (kbId) => {
  return unwrapResult(await api.get(`/api/knowledge-bases/${kbId}/documents`))
}

export const uploadDocument = async (file, kbId) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('kbId', kbId)
  return unwrapResult(await api.post('/ai/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }))
}

export const deleteDocument = async (id) => {
  return unwrapResult(await api.delete(`/ai/documents/${id}`))
}

export const retryDocument = async (id) => {
  return unwrapResult(await api.post(`/ai/documents/${id}/retry`))
}

export const getConversations = async () => {
  return unwrapResult(await api.get('/ai/conversations'))
}

export const createConversation = async () => {
  return unwrapResult(await api.post('/ai/conversations'))
}

export const deleteConversation = async (id) => {
  return unwrapResult(await api.delete(`/ai/conversations/${id}`))
}

export const updateConversationTitle = async (id, title) => {
  return unwrapResult(await api.put(`/ai/conversations/${id}/title`, { title }))
}

export const getConversationMessages = async (id) => {
  return unwrapResult(await api.get(`/ai/conversations/${id}/messages`))
}

export const chatByStream = async (question, conversationId, options = {}) => {
  const { useRag = false, model = 'qwen', kbId = null } = options
  const url = useRag ? '/ai/chatByRag' : '/ai/chat'
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...getStoredTokenHeaders()
    },
    body: JSON.stringify({
      question,
      conversationId: conversationId || null,
      useRag,
      model,
      kbId
    })
  })

  if (!response.ok) {
    if (response.status === 401) {
      redirectToLogin()
    }
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const payload = await response.json()
      throw new Error(payload?.message || `请求失败（${response.status}）`)
    }
    const errorText = await response.text()
    throw new Error(errorText || `请求失败（${response.status}）`)
  }

  return response
}

export const readSSEStream = async (response, onChunk) => {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventDataLines = []

  const flushEvent = () => {
    if (!eventDataLines.length) {
      return
    }
    const data = eventDataLines.join('\n')
    eventDataLines = []
    if (data && data !== '[DONE]') {
      onChunk(data)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }

    const text = decoder.decode(value, { stream: true })
    buffer += text
    const lines = buffer.split(/\r\n|\r|\n/)
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line === '') {
        flushEvent()
        continue
      }
      if (line.startsWith(':')) {
        continue
      }
      if (line.startsWith('data:')) {
        let data = line.substring(5)
        if (data.startsWith(' ')) {
          data = data.substring(1)
        }
        eventDataLines.push(data)
      }
    }
  }

  if (buffer.startsWith('data:')) {
    let data = buffer.substring(5)
    if (data.startsWith(' ')) {
      data = data.substring(1)
    }
    eventDataLines.push(data)
  }

  flushEvent()
  return buffer
}

export const getDocumentPreviewUrl = (id) => `/ai/documents/${id}/preview`

export default api
