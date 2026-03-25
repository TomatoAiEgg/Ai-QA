const AUTH_STORAGE_KEY = 'aiqa-auth-session'
const AUTH_SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000

export const readStoredSession = () => {
  if (typeof window === 'undefined') {
    return null
  }
  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const session = JSON.parse(raw)
    if (!session?.expiresAt || Number(session.expiresAt) <= Date.now()) {
      window.localStorage.removeItem(AUTH_STORAGE_KEY)
      return null
    }
    return session
  } catch (error) {
    window.localStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export const persistSession = (payload) => {
  if (typeof window === 'undefined') {
    return
  }
  const session = {
    user: payload?.user || null,
    tokenName: payload?.tokenName || '',
    tokenValue: payload?.tokenValue || '',
    expiresAt: Date.now() + AUTH_SESSION_TTL_MS
  }
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export const clearStoredSession = () => {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
}

export const getStoredTokenHeaders = () => {
  const session = readStoredSession()
  if (!session?.tokenName || !session?.tokenValue) {
    return {}
  }
  return {
    [session.tokenName]: session.tokenValue
  }
}
