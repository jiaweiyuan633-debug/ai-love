import { auth, clearSession } from './stores/auth'

export interface User {
  id: number
  username: string
  nickname: string
  memoryEnabled: boolean
}

export interface TokenResponse {
  token: string
  user: User
}

// ================= 认证 API（独立于 authHeaders，登录前无 token） =================

export async function fetchAuthStatus(): Promise<boolean> {
  const resp = await fetch('/auth/status')
  if (!resp.ok) return false
  const data = await resp.json()
  return !!data.enabled
}

/** 统一错误消息提取 */
async function errorOf(resp: Response, fallback: string): Promise<Error> {
  try {
    const data = await resp.json()
    return new Error(data.error || fallback)
  } catch {
    return new Error(fallback)
  }
}

export async function login(username: string, password: string): Promise<TokenResponse> {
  const resp = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!resp.ok) throw await errorOf(resp, '登录失败')
  return resp.json()
}

export async function register(username: string, password: string, nickname?: string): Promise<TokenResponse> {
  const resp = await fetch('/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, nickname: nickname || undefined }),
  })
  if (!resp.ok) throw await errorOf(resp, '注册失败')
  return resp.json()
}

export async function fetchMe(): Promise<User> {
  const resp = await authRequest('/auth/me')
  if (!resp.ok) throw new Error('登录已过期')
  return resp.json()
}

export async function patchMe(patch: { nickname?: string; memoryEnabled?: boolean }): Promise<User> {
  const resp = await authRequest('/auth/me', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
  if (!resp.ok) throw await errorOf(resp, '保存失败')
  return resp.json()
}

// ================= 通用请求（自动附带 Authorization；401 自动登出） =================

function authHeaders(): Record<string, string> {
  const h: Record<string, string> = {}
  if (auth.token) h.Authorization = `Bearer ${auth.token}`
  return h
}

function authRequest(path: string, options: RequestInit = {}): Promise<Response> {
  return fetch(path, { ...options, headers: { ...authHeaders(), ...(options.headers as Record<string, string>) } })
}

// ================= SSE 流式对话（fetch 版：可携带 token、可真正中断） =================

/**
 * 基于 fetch + ReadableStream 的 SSE 封装：
 * - 自动附带 Authorization 头（EventSource 做不到）；
 * - 正确合并多行 data（token 含换行时服务端会拆成多行）；
 * - 以 [DONE] 为正常结束标记；
 * - 返回取消函数，取消视为正常结束（用于“停止生成”）。
 */
export function openSseStream(
  url: string,
  onData: (text: string) => void,
  onDone?: () => void,
  onError?: (message: string) => void,
): () => void {
  const controller = new AbortController()
  let finished = false

  const finish = (fn?: () => void) => {
    if (finished) return
    finished = true
    fn?.()
  }

  void (async () => {
    try {
      const resp = await fetch(url, { headers: authHeaders(), signal: controller.signal })
      if (!resp.ok || !resp.body) {
        if (resp.status === 401 && auth.enabled && !url.startsWith('/auth/')) {
          clearSession()
          window.location.assign('/login')
          return
        }
        finish(() => onError?.(`连接失败 (${resp.status})`))
        return
      }
      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let sep: number
        // SSE 事件以空行分隔
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const raw = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          const data = raw
            .split('\n')
            .filter((l) => l.startsWith('data:'))
            .map((l) => (l.startsWith('data: ') ? l.slice(6) : l.slice(5)))
            .join('\n')
          if (!data) continue
          if (data === '[DONE]') {
            finish(onDone)
            return
          }
          onData(data)
        }
      }
      finish(onDone)
    } catch (err) {
      if (controller.signal.aborted) {
        finish(onDone) // 用户主动“停止生成”视为正常结束
      } else {
        finish(() => onError?.(err instanceof Error ? err.message : '连接中断'))
      }
    }
  })()

  return () => controller.abort()
}

// ================= 会话管理（持久化模式） =================

export interface Conversation {
  id: string
  title: string
  ragEnabled: boolean
  updatedAt: string
  messageCount: number
}

export interface StoredMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export async function listConversations(): Promise<Conversation[]> {
  const resp = await authRequest('/api/conversations')
  if (!resp.ok) throw await errorOf(resp, '获取会话列表失败')
  return resp.json()
}

export async function createConversation(title?: string): Promise<Conversation> {
  const resp = await authRequest('/api/conversations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
  if (!resp.ok) throw await errorOf(resp, '创建会话失败')
  return resp.json()
}

export async function renameConversation(id: string, title: string): Promise<void> {
  const resp = await authRequest(`/api/conversations/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
  if (!resp.ok) throw await errorOf(resp, '重命名失败')
}

export async function deleteConversation(id: string): Promise<void> {
  const resp = await authRequest(`/api/conversations/${id}`, { method: 'DELETE' })
  if (!resp.ok) throw await errorOf(resp, '删除失败')
}

export async function listMessages(id: string): Promise<StoredMessage[]> {
  const resp = await authRequest(`/api/conversations/${id}/messages`)
  if (!resp.ok) throw await errorOf(resp, '获取历史消息失败')
  return resp.json()
}

export interface ConversationSearchHit {
  conversationId: string
  conversationTitle: string
  role: string
  snippet: string
  createdAt: string
}

export async function searchConversations(q: string): Promise<ConversationSearchHit[]> {
  const resp = await authRequest(`/api/conversations/search?q=${encodeURIComponent(q)}`)
  if (!resp.ok) throw await errorOf(resp, '搜索失败')
  return resp.json()
}

/** 导出会话为 Markdown 并触发浏览器下载 */
export async function exportConversation(id: string, title: string): Promise<void> {
  const resp = await authRequest(`/api/conversations/${id}/export`)
  if (!resp.ok) throw await errorOf(resp, '导出失败')
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${(title || '对话').replace(/[\\/:*?"<>|]/g, '_')}.md`
  a.click()
  URL.revokeObjectURL(url)
}

// ================= 长期记忆 =================

export interface MemoryItem {
  id: number
  content: string
  createdAt: string
}

export async function listMemory(): Promise<{ enabled: boolean; items: MemoryItem[] }> {
  const resp = await authRequest('/api/memory')
  if (!resp.ok) throw await errorOf(resp, '获取记忆失败')
  return resp.json()
}

export async function deleteMemoryItem(id: number): Promise<void> {
  const resp = await authRequest(`/api/memory/${id}`, { method: 'DELETE' })
  if (!resp.ok) throw await errorOf(resp, '删除失败')
}

export async function clearMemory(): Promise<void> {
  const resp = await authRequest('/api/memory', { method: 'DELETE' })
  if (!resp.ok) throw await errorOf(resp, '清空失败')
}

// ================= 情侣绑定 =================

export interface CoupleStatus {
  bound: boolean
  pending: boolean
  code: string | null
  partnerNickname: string | null
  anniversaryDate: string | null
  daysTogether: number | null
  daysToAnniversary: number | null
}

export async function getCoupleStatus(): Promise<CoupleStatus> {
  const resp = await authRequest('/api/couple')
  if (!resp.ok) throw await errorOf(resp, '获取情侣状态失败')
  return resp.json()
}

export async function generateCoupleCode(): Promise<string> {
  const resp = await authRequest('/api/couple/code', { method: 'POST' })
  if (!resp.ok) throw await errorOf(resp, '生成绑定码失败')
  const data = await resp.json()
  return data.code
}

export async function bindCouple(code: string): Promise<CoupleStatus> {
  const resp = await authRequest('/api/couple/bind', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  })
  if (!resp.ok) throw await errorOf(resp, '绑定失败')
  return resp.json()
}

export async function setAnniversary(date: string): Promise<CoupleStatus> {
  const resp = await authRequest('/api/couple', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ anniversaryDate: date }),
  })
  if (!resp.ok) throw await errorOf(resp, '保存失败')
  return resp.json()
}

export async function unbindCouple(): Promise<void> {
  const resp = await authRequest('/api/couple', { method: 'DELETE' })
  if (!resp.ok) throw await errorOf(resp, '解绑失败')
}

// ================= 每日情话 =================

export async function fetchDailyQuote(refresh = false): Promise<{ date: string; quote: string }> {
  const resp = await authRequest(`/api/daily-quote${refresh ? '?refresh=true' : ''}`)
  if (!resp.ok) throw await errorOf(resp, '获取今日情话失败')
  return resp.json()
}

// ================= 知识库 =================

export async function uploadKnowledge(file: File): Promise<{ file_name: string; chunks: number }> {
  const form = new FormData()
  form.append('file', file)
  const resp = await authRequest('/knowledge/upload', { method: 'POST', body: form })
  if (!resp.ok) throw await errorOf(resp, `上传失败: ${resp.status}`)
  return resp.json()
}

export interface KnowledgeDoc {
  file_name: string
  doc_id: string
  chunks: number
}

export async function listKnowledge(): Promise<KnowledgeDoc[]> {
  const resp = await authRequest('/knowledge/list')
  if (!resp.ok) throw await errorOf(resp, `获取列表失败: ${resp.status}`)
  return resp.json()
}

export async function deleteKnowledge(docId: string): Promise<void> {
  const resp = await authRequest(`/knowledge/${encodeURIComponent(docId)}`, { method: 'DELETE' })
  if (!resp.ok) throw await errorOf(resp, `删除失败: ${resp.status}`)
}
