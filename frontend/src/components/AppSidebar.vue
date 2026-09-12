<script setup lang="ts">
import { watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  conversations,
  newConversation,
  persistenceEnabled,
  refreshList,
  removeConversation,
  renameConv,
  setActive,
} from '../stores/conversations'
import { auth, clearSession } from '../stores/auth'
import { ui } from '../stores/ui'
import { exportConversation, searchConversations, type Conversation, type ConversationSearchHit } from '../api'

const route = useRoute()
const router = useRouter()

const navs = [
  { name: 'chat', icon: '💬', label: '恋爱大师' },
  { name: 'yumanus', icon: '🤖', label: 'YuManus 智能体' },
  { name: 'knowledge', icon: '📚', label: '恋爱知识库' },
]

const editingId = ref('')
const editTitle = ref('')

// 整页刷新时本组件先于路由守卫完成认证探测而挂载，
// 因此监听 auth.enabled 变为 true 时再加载会话列表
watch(
  () => auth.enabled,
  (enabled) => {
    if (enabled) void refreshList()
  },
  { immediate: true },
)

function openConv(id: string) {
  setActive(id)
  if (route.name !== 'chat') router.push('/')
}

async function createNew() {
  if (!persistenceEnabled()) {
    router.push('/')
    return
  }
  try {
    if (route.name !== 'chat') router.push('/')
    await newConversation()
  } catch {
    // 创建失败保持现状
  }
}

function startRename(conv: Conversation) {
  editingId.value = conv.id
  editTitle.value = conv.title
}

async function saveRename() {
  const id = editingId.value
  const title = editTitle.value.trim()
  editingId.value = ''
  if (!id || !title) return
  try {
    await renameConv(id, title)
  } catch {
    // 失败静默
  }
}

async function onDelete(conv: Conversation) {
  if (!window.confirm(`确定删除会话「${conv.title}」吗？历史消息将一并删除。`)) return
  try {
    await removeConversation(conv.id)
  } catch {
    // 失败静默
  }
}

function logout() {
  clearSession()
  router.replace('/login')
}

// ---------- 搜索 ----------
const searchQ = ref('')
const results = ref<ConversationSearchHit[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const q = searchQ.value.trim()
  if (!q) {
    results.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      results.value = await searchConversations(q)
    } catch {
      results.value = []
    }
  }, 300)
}

function jumpToResult(hit: ConversationSearchHit) {
  setActive(hit.conversationId)
  if (route.name !== 'chat') router.push('/')
}

async function doExport(conv: Conversation) {
  try {
    await exportConversation(conv.id, conv.title)
  } catch {
    // 失败静默
  }
}

function fmtTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return sameDay ? hm : `${d.getMonth() + 1}月${d.getDate()}日`
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">💘 AI 恋爱大师</div>

    <nav class="nav">
      <router-link
        v-for="nav in navs"
        :key="nav.name"
        :to="nav.name === 'chat' ? '/' : `/${nav.name}`"
        class="nav-link"
        :class="{ active: route.name === nav.name }"
      >
        <span class="icon">{{ nav.icon }}</span>{{ nav.label }}
      </router-link>
    </nav>

    <template v-if="persistenceEnabled()">
      <button class="new-conv" @click="createNew">＋ 新建对话</button>

      <div class="conv-section">
        <input
          v-model="searchQ"
          class="search-box"
          placeholder="🔍 搜索对话内容"
          @input="onSearchInput"
        />
        <template v-if="searchQ.trim()">
          <div class="section-label">搜索结果（{{ results.length }}）</div>
          <div class="conv-list">
            <div
              v-for="(r, i) in results"
              :key="i"
              class="conv-item search-item"
              @click="jumpToResult(r)"
            >
              <span class="conv-title">{{ r.conversationTitle }} · {{ r.role === 'user' ? '我' : '大师' }}</span>
              <span class="snippet">{{ r.snippet }}</span>
            </div>
            <div v-if="results.length === 0" class="empty-tip">没有匹配的对话内容</div>
          </div>
        </template>
        <template v-else>
          <div class="section-label">历史对话</div>
          <div class="conv-list">
            <div
              v-for="conv in conversations.list"
              :key="conv.id"
              class="conv-item"
              :class="{ active: conv.id === conversations.activeId && route.name === 'chat' }"
              @click="openConv(conv.id)"
            >
              <template v-if="editingId === conv.id">
                <input
                  v-model="editTitle"
                  class="rename-input"
                  autofocus
                  @keydown.enter.prevent="saveRename"
                  @keydown.esc="editingId = ''"
                  @blur="saveRename"
                  @click.stop
                />
              </template>
              <template v-else>
                <span class="conv-title">{{ conv.title }}</span>
                <span class="conv-actions" @click.stop>
                  <button title="导出 Markdown" @click="doExport(conv)">⬇️</button>
                  <button title="重命名" @click="startRename(conv)">✏️</button>
                  <button title="删除" @click="onDelete(conv)">🗑️</button>
                </span>
              </template>
              <span class="conv-time">{{ fmtTime(conv.updatedAt) }}</span>
            </div>
            <div v-if="conversations.list.length === 0" class="empty-tip">
              还没有对话，点上方「新建对话」开始吧
            </div>
          </div>
        </template>
      </div>
    </template>
    <div v-else class="guest-tip">
      体验模式：登录后可保存对话历史与长期记忆
    </div>

    <div class="footer">
      <template v-if="auth.user">
        <div class="avatar">{{ auth.user.nickname.slice(0, 1) }}</div>
        <span class="nickname" :title="auth.user.username">{{ auth.user.nickname }}</span>
        <button class="icon-btn" title="设置" @click="ui.settingsOpen = true">⚙️</button>
        <button class="icon-btn" title="退出登录" @click="logout">⏻</button>
      </template>
      <template v-else>
        <span class="guest-label">未登录</span>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 248px;
  height: 100%; /* 撑满屏幕高度：对话列表再短，用户卡片也固定在左下角 */
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-soft);
  border-right: 1px solid var(--border);
  padding: 14px 12px;
  gap: 12px;
  overflow: hidden;
}
.brand {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  padding: 2px 6px;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nav-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--text-3);
  text-decoration: none;
  font-size: 14px;
  transition: all 0.15s;
}
.nav-link:hover {
  color: #fff;
  background: var(--hover);
}
.nav-link.active {
  color: #fff;
  background: var(--accent-grad);
}
.new-conv {
  border: 1px dashed var(--border-strong);
  background: transparent;
  color: var(--text-2);
  border-radius: 10px;
  padding: 9px 0;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.new-conv:hover {
  border-color: var(--a1);
  color: #fff;
}
.conv-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.search-box {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  color: var(--text);
  font-size: 12px;
  padding: 7px 10px;
  outline: none;
  margin-bottom: 8px;
}
.search-box:focus {
  border-color: var(--a2);
}
.snippet {
  display: block;
  font-size: 11px;
  color: var(--text-5);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.section-label {
  font-size: 11px;
  color: var(--text-4);
  padding: 0 6px 6px;
  letter-spacing: 1px;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.conv-item {
  position: relative;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--text-2);
  font-size: 13px;
  transition: background 0.15s;
}
.conv-item:hover {
  background: var(--hover);
}
.conv-item.active {
  background: var(--hover);
  color: #fff;
}
.conv-title {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 46px;
}
.conv-time {
  display: block;
  font-size: 11px;
  color: var(--text-5);
  margin-top: 2px;
}
.conv-actions {
  position: absolute;
  right: 8px;
  top: 8px;
  display: none;
  gap: 2px;
}
.conv-item:hover .conv-actions {
  display: flex;
}
.conv-actions button {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  padding: 2px;
  border-radius: 4px;
}
.conv-actions button:hover {
  background: var(--border-strong);
}
.rename-input {
  width: 100%;
  background: var(--bg);
  border: 1px solid var(--a2);
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  padding: 4px 6px;
  outline: none;
}
.empty-tip {
  color: var(--text-5);
  font-size: 12px;
  padding: 8px 6px;
  line-height: 1.6;
}
.guest-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--text-5);
  font-size: 12px;
  line-height: 1.8;
  border: 1px dashed var(--hover);
  border-radius: 10px;
  padding: 12px;
}
.footer {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--accent-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.nickname {
  flex: 1;
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.icon-btn {
  border: none;
  background: transparent;
  color: var(--text-4);
  cursor: pointer;
  font-size: 15px;
  padding: 4px;
  border-radius: 6px;
}
.icon-btn:hover {
  color: var(--danger-text);
  background: var(--hover);
}
.guest-label {
  color: var(--text-5);
  font-size: 12px;
}
</style>
