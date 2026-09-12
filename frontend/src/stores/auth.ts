import { reactive } from 'vue'

export interface User {
  id: number
  username: string
  nickname: string
  memoryEnabled: boolean
}

interface AuthState {
  /** 是否启用登录/持久化；null = 尚未探测（/auth/status） */
  enabled: boolean | null
  token: string
  user: User | null
}

const TOKEN_KEY = 'ailove:token'

export const auth = reactive<AuthState>({
  enabled: null,
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: null,
})

export function setSession(token: string, user: User) {
  auth.token = token
  auth.user = user
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearSession() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
}

/** 收到 401 时由 api 层调用：清会话并回到登录页（体验模式不触发） */
export function onUnauthorized() {
  if (auth.enabled && auth.token) clearSession()
  if (auth.enabled) window.location.assign('/login')
}
