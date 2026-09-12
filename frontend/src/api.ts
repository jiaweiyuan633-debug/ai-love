import { auth, clearSession } from './stores/auth'

/**
 * API 基础地址：Web 部署为同源相对路径（空串）；
 * Tauri 桌面/移动端打包时经 VITE_API_BASE 指向云端前端域名（nginx 统一反代接口）。
 */
const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/+$/, '') ?? ''

function withBase(path: string): string {
  return API_BASE + path
}

export interface User {
  id: number
  username: string
  nickname: string
  memoryEnabled: boolean
  email: string | null
}

export interface TokenResponse {
  token: string
  user: User
}

// ================= 认证 API（独立于 authHeaders，登录前无 token） =================

export async function fetchAuthStatus(): Promise<boolean> {
  const resp = await fetch(withBase('/auth/status'))
  if (!resp.ok) return false
  const data = await resp.json()
  // 忘记密码入口依赖后端邮件服务配置
  auth.passwordResetEnabled = !!data.passwordResetEnabled
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
  const resp = await fetch(withBase('/auth/login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!resp.ok) throw await errorOf(resp, '登录失败')
  return resp.json()
}

export async function register(username: string, password: string, nickname?: string): Promise<TokenResponse> {
  const resp = await fetch(withBase('/auth/register'), {
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

// ================= 注销账号 / 邮箱绑定 / 找回密码 =================

/** 注销当前账号：服务端删除全部用户数据，前端随后需 clearSession */
export async function deleteAccount(): Promise<void> {
  const resp = await authRequest('/auth/me', { method: 'DELETE' })
  if (!resp.ok) throw await errorOf(resp, '注销失败')
}

export async function requestBindEmail(email: string): Promise<string> {
  const resp = await authRequest('/auth/me/email/request', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  const data = await resp.json().catch(() => null)
  if (!resp.ok) throw new Error(data?.error || '验证码发送失败')
  return data.message
}

export async function confirmBindEmail(email: string, code: string): Promise<User> {
  const resp = await authRequest('/auth/me/email/confirm', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code }),
  })
  const data = await resp.json().catch(() => null)
  if (!resp.ok) throw new Error(data?.error || '绑定失败')
  return data
}

export async function requestPasswordReset(username: string, email: string): Promise<string> {
  const resp = await fetch(withBase('/auth/password/reset/request'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email }),
  })
  const data = await resp.json().catch(() => null)
  if (!resp.ok) throw new Error(data?.error || '验证码发送失败')
  return data.message
}

export async function confirmPasswordReset(
  username: string,
  email: string,
  code: string,
  newPassword: string,
): Promise<string> {
  const resp = await fetch(withBase('/auth/password/reset/confirm'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, code, newPassword }),
  })
  const data = await resp.json().catch(() => null)
  if (!resp.ok) throw new Error(data?.error || '重置失败')
  return data.message
}

// ================= 通用请求（自动附带 Authorization；401 自动登出） =================

function authHeaders(): Record<string, string> {
  const h: Record<string, string> = {}
  if (auth.token) h.Authorization = `Bearer ${auth.token}`
  return h
}

function authRequest(path: string, options: RequestInit = {}): Promise<Response> {
  return fetch(withBase(path), {
    ...options,
    headers: { ...authHeaders(), ...(options.headers as Record<string, string>) },
  }).then((resp) => {
    // token 过期/无效：统一登出并回登录页（与 SSE 流内的处理保持一致）
    if (resp.status === 401 && auth.enabled) {
      clearSession()
      window.location.assign('/login')
    }
    return resp
  })
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
  onMeta?: () => void,
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
      const resp = await fetch(withBase(url), { headers: authHeaders(), signal: controller.signal })
      if (!resp.ok || !resp.body) {
        if (resp.status === 401 && auth.enabled && !url.startsWith('/auth/')) {
          clearSession()
          window.location.assign('/login')
          return
        }
        // 读取后端 JSON 错误体（限流 429 / AI 上游 502 / 内容审核 422 等友好文案）
        let message = `连接失败 (${resp.status})`
        try {
          const data = await resp.json()
          if (data?.error) message = data.error
        } catch {
          // 非 JSON 错误体，保留默认文案
        }
        finish(() => onError?.(message))
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
          const lines = raw.split('\n')
          const data = lines
            .filter((l) => l.startsWith('data:'))
            .map((l) => (l.startsWith('data: ') ? l.slice(6) : l.slice(5)))
            .join('\n')
          // 事件帧（后端 ai-meta：AI 生成内容隐式标识，无 data 行）——旧版本前端解析不到 data 会自然忽略
          const evt = lines.find((l) => l.startsWith('event:'))?.slice(6)?.trim()
          if (evt === 'ai-meta') onMeta?.()
          if (!data) continue
          if (data === '[DONE]') {
            finish(onDone)
            return
          }
          // 服务端 AI 上游报错帧（内容审核拦截/服务不可用），此后流正常收尾
          if (data.startsWith('[ERROR] ')) {
            finish(() => onError?.(data.slice(8) || 'AI 服务暂时不可用'))
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
  persona: string
  mode: 'advisor' | 'companion'
  updatedAt: string
  messageCount: number
}

export interface Persona {
  id: string
  name: string
  emoji: string
  tagline: string
  color: string
  advisorGreeting: string
  companionGreeting: string
}

export async function fetchPersonas(): Promise<Persona[]> {
  const resp = await authRequest('/api/personas')
  if (!resp.ok) throw await errorOf(resp, '获取角色列表失败')
  return resp.json()
}

export async function createConversation(
  title?: string,
  persona?: string,
  mode?: 'advisor' | 'companion',
): Promise<Conversation> {
  const resp = await authRequest('/api/conversations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, persona, mode }),
  })
  if (!resp.ok) throw await errorOf(resp, '创建会话失败')
  return resp.json()
}

export interface StoredMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  /** AI 生成内容隐式标识（《人工智能生成合成内容标识办法》），随消息存储与导出 */
  aiGenerated?: boolean
  createdAt: string
}

export async function listConversations(): Promise<Conversation[]> {
  const resp = await authRequest('/api/conversations')
  if (!resp.ok) throw await errorOf(resp, '获取会话列表失败')
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

/** 空会话改绑角色/模式（已开始聊天的会话后端会拒绝） */
export async function patchConversationPersona(
  id: string,
  persona: string,
  mode: 'advisor' | 'companion',
): Promise<void> {
  const resp = await authRequest(`/api/conversations/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ persona, mode }),
  })
  if (!resp.ok) throw await errorOf(resp, '更换角色失败')
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

/** 按最后一轮问答生成追问建议；失败静默返回空数组（不阻塞聊天） */
export async function fetchSuggestions(id: string): Promise<string[]> {
  try {
    const resp = await authRequest(`/api/conversations/${id}/suggestions`, { method: 'POST' })
    if (!resp.ok) return []
    const data = await resp.json()
    return Array.isArray(data.suggestions) ? data.suggestions : []
  } catch {
    return []
  }
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

// ================= AI 朋友圈 =================

export interface MomentComment {
  id: number
  role: 'user' | 'ai'
  content: string
}

export interface Moment {
  id: number
  persona: string
  content: string
  liked: boolean
  date: string
  comments: MomentComment[]
}

export async function listMoments(persona: string): Promise<Moment[]> {
  const resp = await authRequest(`/api/moments?persona=${encodeURIComponent(persona)}`)
  if (!resp.ok) throw await errorOf(resp, '获取朋友圈失败')
  return resp.json()
}

export async function toggleMomentLike(id: number): Promise<boolean> {
  const resp = await authRequest(`/api/moments/${id}/like`, { method: 'POST' })
  if (!resp.ok) throw await errorOf(resp, '点赞失败')
  const data = await resp.json()
  return !!data.liked
}

export async function commentMoment(id: number, content: string): Promise<MomentComment[]> {
  const resp = await authRequest(`/api/moments/${id}/comments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  })
  if (!resp.ok) throw await errorOf(resp, '评论失败')
  const data = await resp.json()
  return data.comments
}

// ================= 主动关怀 =================

export interface CareMessage {
  type: 'away' | 'morning' | 'night'
  content: string
}

/** 无主动消息时返回 null */
export async function fetchCarePending(persona: string): Promise<CareMessage | null> {
  try {
    const resp = await authRequest(`/api/care/pending?persona=${encodeURIComponent(persona)}`)
    if (!resp.ok) return null
    const data = await resp.json()
    return data.type ? (data as CareMessage) : null
  } catch {
    return null
  }
}

// ================= 心情打卡 =================

export interface MoodEntry {
  date: string
  score: number
  note: string | null
}

export interface MoodStatus {
  checkedToday: boolean
  todayScore: number | null
  note: string | null
  streak: number
  recent7: MoodEntry[]
}

export async function fetchMoodStatus(): Promise<MoodStatus> {
  const resp = await authRequest('/api/mood')
  if (!resp.ok) throw await errorOf(resp, '获取心情状态失败')
  return resp.json()
}

export async function checkInMood(
  score: number,
  note?: string,
  persona?: string,
): Promise<{ reply: string; status: MoodStatus }> {
  const resp = await authRequest(`/api/mood?persona=${encodeURIComponent(persona || 'jiejie')}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ score, note }),
  })
  if (!resp.ok) throw await errorOf(resp, '打卡失败')
  return resp.json()
}

// ================= 成就徽章 =================

export interface Achievement {
  id: string
  name: string
  emoji: string
  description: string
  unlocked: boolean
  progress: number
  progressText: string
}

export async function fetchAchievements(): Promise<Achievement[]> {
  const resp = await authRequest('/api/achievements')
  if (!resp.ok) throw await errorOf(resp, '获取成就失败')
  return resp.json()
}

// ================= 会员订阅（模拟支付） =================

export interface MembershipPlan {
  id: string
  label: string
  priceFen: number
  days: number
}

export interface MembershipStatus {
  vip: boolean
  plan: string | null
  vipUntil: string | null
  dailyUsed: number
  dailyLimit: number
}

export interface MembershipOrder {
  id: string
  plan: string
  priceFen: number
  status: 'pending' | 'paid'
}

export async function fetchMembership(): Promise<MembershipStatus> {
  const resp = await authRequest('/api/membership')
  if (!resp.ok) throw await errorOf(resp, '获取会员状态失败')
  return resp.json()
}

export async function fetchMembershipPlans(): Promise<MembershipPlan[]> {
  const resp = await authRequest('/api/membership/plans')
  if (!resp.ok) throw await errorOf(resp, '获取套餐失败')
  return resp.json()
}

export async function createMembershipOrder(plan: string): Promise<MembershipOrder> {
  const resp = await authRequest('/api/membership/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ plan }),
  })
  if (!resp.ok) throw await errorOf(resp, '下单失败')
  return resp.json()
}

/** 模拟支付：携带凭证与订单金额（分），后端网关校验金额一致才入账 */
export async function payMembershipOrder(id: string, priceFen: number): Promise<MembershipStatus> {
  const resp = await authRequest(`/api/membership/orders/${encodeURIComponent(id)}/pay`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ credential: 'MOCK-TICKET', amountFen: priceFen }),
  })
  if (!resp.ok) throw await errorOf(resp, '支付失败')
  return resp.json()
}
