<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
import type { Conversation } from '../api'

const route = useRoute()
const router = useRouter()

const navs = [
  { name: 'chat', icon: '💬', label: '恋爱大师' },
  { name: 'yumanus', icon: '🤖', label: 'YuManus 智能体' },
  { name: 'knowledge', icon: '📚', label: '恋爱知识库' },
]

const editingId = ref('')
const editTitle = ref('')

onMounted(refreshList)

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
      </div>
    </template>
    <div v-else class="guest-tip">
      体验模式：登录后可保存对话历史与长期记忆
    </div>

    <div class="footer">
      <template v-if="auth.user">
        <div class="avatar">{{ auth.user.nickname.slice(0, 1) }}</div>
        <span class="nickname" :title="auth.user.username">{{ auth.user.nickname }}</span>
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
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #17172a;
  border-right: 1px solid #2b2b3a;
  padding: 14px 12px;
  gap: 12px;
  overflow: hidden;
}
.brand {
  font-size: 16px;
  font-weight: 700;
  color: #f4f4fa;
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
  color: #b6b6cc;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.15s;
}
.nav-link:hover {
  color: #fff;
  background: #2b2b45;
}
.nav-link.active {
  color: #fff;
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
}
.new-conv {
  border: 1px dashed #3a3a55;
  background: transparent;
  color: #c8c8e0;
  border-radius: 10px;
  padding: 9px 0;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.new-conv:hover {
  border-color: #ff6b9d;
  color: #fff;
}
.conv-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.section-label {
  font-size: 11px;
  color: #8888a6;
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
  color: #c8c8e0;
  font-size: 13px;
  transition: background 0.15s;
}
.conv-item:hover {
  background: #22223a;
}
.conv-item.active {
  background: #2b2b45;
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
  color: #6a6a88;
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
  background: #3a3a55;
}
.rename-input {
  width: 100%;
  background: #101020;
  border: 1px solid #a76bff;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  padding: 4px 6px;
  outline: none;
}
.empty-tip {
  color: #6a6a88;
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
  color: #6a6a88;
  font-size: 12px;
  line-height: 1.8;
  border: 1px dashed #2b2b45;
  border-radius: 10px;
  padding: 12px;
}
.footer {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #2b2b3a;
  padding-top: 12px;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
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
  color: #e6e6f2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.icon-btn {
  border: none;
  background: transparent;
  color: #8888a6;
  cursor: pointer;
  font-size: 15px;
  padding: 4px;
  border-radius: 6px;
}
.icon-btn:hover {
  color: #ff9db4;
  background: #2b2b45;
}
.guest-label {
  color: #6a6a88;
  font-size: 12px;
}
</style>
