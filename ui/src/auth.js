import { reactive } from 'vue'
import { getAuthSession } from './api.js'
import { clearStoredSession, persistSession, readStoredSession } from './auth-storage.js'

const storedSession = readStoredSession()

export const authState = reactive({
  ready: false,
  isAuthenticated: Boolean(storedSession?.user),
  user: storedSession?.user || null,
  tokenName: storedSession?.tokenName || '',
  tokenValue: storedSession?.tokenValue || ''
})

export const applySession = (payload) => {
  authState.user = payload?.user || null
  authState.isAuthenticated = Boolean(payload?.user)
  authState.tokenName = payload?.tokenName || ''
  authState.tokenValue = payload?.tokenValue || ''
  persistSession(payload)
  authState.ready = true
}

export const clearAuthState = () => {
  authState.user = null
  authState.isAuthenticated = false
  authState.tokenName = ''
  authState.tokenValue = ''
  clearStoredSession()
  authState.ready = true
}

export const ensureSession = async (force = false) => {
  if (authState.ready && !force) {
    return authState.isAuthenticated
  }
  try {
    const payload = await getAuthSession()
    applySession(payload)
  } catch (error) {
    clearAuthState()
  }
  return authState.isAuthenticated
}
