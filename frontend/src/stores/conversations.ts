import { reactive } from 'vue'
import { createConversation, deleteConversation, listConversations, renameConversation, type Conversation } from '../api'
import { auth } from './auth'

interface ConversationsState {
  list: Conversation[]
  activeId: string
}

const ACTIVE_KEY = 'ailove:activeConv'

export const conversations = reactive<ConversationsState>({
  list: [],
  activeId: localStorage.getItem(ACTIVE_KEY) || '',
})

/** 体验模式（未启用持久化）下整个模块不工作 */
export const persistenceEnabled = () => auth.enabled === true

export async function refreshList() {
  if (!persistenceEnabled()) return
  try {
    conversations.list = await listConversations()
    // 当前会话若已被删除，切换到最新一个
    if (conversations.activeId && !conversations.list.some((c) => c.id === conversations.activeId)) {
      setActive(conversations.list[0]?.id || '')
    }
  } catch {
    // 忽略：列表加载失败不影响聊天
  }
}

export function setActive(id: string) {
  conversations.activeId = id
  if (id) localStorage.setItem(ACTIVE_KEY, id)
  else localStorage.removeItem(ACTIVE_KEY)
}

export async function newConversation(
  persona?: string,
  mode?: 'advisor' | 'companion',
): Promise<Conversation> {
  const conv = await createConversation(undefined, persona, mode)
  await refreshList()
  setActive(conv.id)
  return conv
}

export async function removeConversation(id: string) {
  await deleteConversation(id)
  await refreshList()
}

export async function renameConv(id: string, title: string) {
  await renameConversation(id, title)
  await refreshList()
}

/** 确保有一个可用的当前会话（没有则创建），返回其 id */
export async function ensureActiveConversation(): Promise<string> {
  if (conversations.activeId && conversations.list.some((c) => c.id === conversations.activeId)) {
    return conversations.activeId
  }
  const existing = conversations.list[0]
  if (existing) {
    setActive(existing.id)
    return existing.id
  }
  const conv = await newConversation()
  return conv.id
}
