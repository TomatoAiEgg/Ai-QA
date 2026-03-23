const AUTH_STORAGE_KEY = 'aiqa-auth-session'

export const readStoredSession = () => {
  if (typeof window === 'undefined') {
    return null
  }
  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
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
    tokenValue: payload?.tokenValue || ''
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
