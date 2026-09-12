<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  fetchCarePending,
  fetchDailyQuote,
  fetchPersonas,
  fetchSuggestions,
  getCoupleStatus,
  listMessages,
  openSseStream,
  exportConversation,
  patchConversationPersona,
  type CareMessage,
  type CoupleStatus,
  type Persona,
  type StoredMessage,
} from '../api'
import { conversations, ensureActiveConversation, newConversation, persistenceEnabled, refreshList, setActive } from '../stores/conversations'
import { auth } from '../stores/auth'
import { settings } from '../stores/settings'
import { renderMarkdown } from '../utils/markdown'
import { feedSpeech, finishSpeech, speakFull, speaking, stopSpeech } from '../composables/useSpeech'
import { useVoiceInput, voiceInputSupported } from '../composables/useVoiceInput'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  rag?: boolean
  ts?: number
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const guestChatId = ref('chat-' + Date.now().toString(36))
const streaming = ref(false)
const ragEnabled = ref(false)
const error = ref('')
const copiedIndex = ref(-1)
const followUps = ref<string[]>([])
let closeStream: (() => void) | null = null

// ---------- 角色（双模式） ----------
const personas = ref<Persona[]>([])
const pickedMode = ref<'advisor' | 'companion'>('advisor')
const guestPersona = ref('jiejie')
const guestGreeting = ref('')
const care = ref<CareMessage | null>(null)

const activeConv = computed(() =>
  conversations.list.find((c) => c.id === conversations.activeId),
)
const activePersonaId = computed(() => {
  if (!persistenceEnabled()) return guestPersona.value
  return activeConv.value?.persona || 'jiejie'
})
const activeMode = computed(() => activeConv.value?.mode || 'advisor')
const activePersona = computed(() =>
  personas.value.find((p) => p.id === activePersonaId.value) || null,
)

/** 空会话时展示角色选择（体验模式也一样，选中后本地展示开场白） */
function pickPersona(p: Persona, mode: 'advisor' | 'companion') {
  settings.voicePersona = p.id
  if (!persistenceEnabled()) {
    guestPersona.value = p.id
    guestGreeting.value = mode === 'companion' ? p.companionGreeting : p.advisorGreeting
    messages.value = [{ role: 'assistant', content: guestGreeting.value, ts: Date.now() }]
    return
  }
  void (async () => {
    try {
      const conv = activeConv.value
      if (conv && conv.messageCount === 0) {
        // 空会话：直接改绑角色，陪伴模式后端会补发开场白
        await patchConversationPersona(conv.id, p.id, mode)
        await refreshList()
        // activeId 未变，手动重载历史以显示开场白
        const stored: StoredMessage[] = await listMessages(conv.id)
        messages.value = stored.map((m) => ({
          role: m.role,
          content: m.content,
          rag: false,
          ts: Date.parse(m.createdAt) || undefined,
        }))
      } else {
        // 从欢迎屏/换角色进入：新建会话（watch 自动加载开场白）
        await newConversation(p.id, mode)
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : '选择角色失败'
    }
  })()
}

/** 试听角色声音：临时切换朗读角色并读开场白 */
function previewVoice(p: Persona) {
  settings.voicePersona = p.id
  speakFull(p.advisorGreeting)
}

/** 回应角色的主动关怀：预填一句感谢，用户可修改后发送 */
function replyCare() {
  input.value = '谢谢你的关心，听到你这么说很开心～'
  care.value = null
  void nextTick(() => {
    const el = document.querySelector<HTMLInputElement>('.input-bar textarea')
    el?.focus()
  })
}

const suggestions = [
  '我喜欢上一个同事，怎么自然地开始聊天？',
  '第一次约会该选什么地点？',
  '异地恋如何维系感情？',
  '帮我分析：TA 已读不回说明什么？',
]

/** 持久化模式跟随侧边栏选中的会话；体验模式用本地随机会话 ID */
function currentChatId(): string {
  return persistenceEnabled() ? conversations.activeId : guestChatId.value
}

// 朗读音色跟随当前会话的角色（体验模式跟随所选角色）
watch(activePersonaId, (id) => {
  if (id) settings.voicePersona = id
}, { immediate: true })

const currentTitle = computed(() => {
  if (!persistenceEnabled()) return '体验模式'
  return conversations.list.find((c) => c.id === conversations.activeId)?.title || '新对话'
})

// 切换会话 → 加载历史消息。
// suppressConvWatch：发送首条消息时 ensureActiveConversation 会新建会话并改变 activeId，
// 此时不应清空正在组装的消息列表（竞态防护）。
let suppressConvWatch = false
watch(
  () => conversations.activeId,
  (id) => {
    if (suppressConvWatch) return
    void loadHistory(id)
  },
)

async function loadHistory(id: string) {
  stopStreamInternal()
  streaming.value = false
  messages.value = []
  error.value = ''
    if (!persistenceEnabled() || !id) return
    try {
      const stored: StoredMessage[] = await listMessages(id)
      messages.value = stored.map((m) => ({
        role: m.role,
        content: m.content,
        rag: false,
        ts: Date.parse(m.createdAt) || undefined,
      }))
      followUps.value = []
      void scrollToBottom()
    } catch {
      // 历史加载失败不阻塞聊天
    }
}

// 整页刷新时 activeId 从 localStorage 恢复，watch 不会触发，需要手动加载一次
onMounted(() => {
  void fetchPersonas().then((list) => (personas.value = list)).catch(() => undefined)
  if (persistenceEnabled() && conversations.activeId) {
    void loadHistory(conversations.activeId)
  }
  if (persistenceEnabled()) {
    // 纪念日提醒
    getCoupleStatus()
      .then((c) => (coupleInfo.value = c))
      .catch(() => undefined)
    // 每日情话：每天首次进入时展示
    const today = new Date().toISOString().slice(0, 10)
    if (localStorage.getItem('ailove:quoteDate') !== today) {
      fetchDailyQuote()
        .then((d) => {
          dailyQuote.value = d.quote
          showQuote.value = true
        })
        .catch(() => undefined)
    }
    // 角色的主动关怀（离线留言）
    fetchCarePending(activePersonaId.value)
      .then((msg) => {
        if (msg) care.value = msg
      })
      .catch(() => undefined)
  }
})

// ---------- 纪念日提醒 ----------
const coupleInfo = ref<CoupleStatus | null>(null)
const anniversaryBanner = computed(() => {
  const c = coupleInfo.value
  if (!c?.bound || c.daysToAnniversary == null) return ''
  if (c.daysToAnniversary === 0) {
    return `💝 今天是你们和「${c.partnerNickname}」的恋爱纪念日！快送上祝福吧 🎉`
  }
  if (c.daysToAnniversary <= 7) {
    return `💝 距离你们的恋爱纪念日还有 ${c.daysToAnniversary} 天，要不要提前准备点惊喜？`
  }
  return ''
})

// ---------- 每日情话 ----------
const dailyQuote = ref('')
const showQuote = ref(false)

function dismissQuote() {
  localStorage.setItem('ailove:quoteDate', new Date().toISOString().slice(0, 10))
  showQuote.value = false
}

async function refreshQuote() {
  try {
    const d = await fetchDailyQuote(true)
    dailyQuote.value = d.quote
    showQuote.value = true
  } catch {
    // 静默
  }
}

async function scrollToBottom() {
  await nextTick()
  const box = document.querySelector('.msg-list')
  box?.scrollTo({ top: box.scrollHeight })
}

function fmtTs(ts?: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const meInitial = computed(() => auth.user?.nickname?.slice(0, 1) || '我')

/** 流结束后拉取追问建议；会话已切换则丢弃 */
function loadFollowUps(convId: string | null) {
  if (!persistenceEnabled() || !convId) return
  void (async () => {
    const list = await fetchSuggestions(convId)
    if (conversations.activeId === convId) followUps.value = list
  })()
}

function send(text?: string) {
  void streamSend(text ?? input.value, false)
}

async function streamSend(raw: string, regenerate: boolean) {
  const message = raw.trim()
  if (!message || streaming.value) return
  error.value = ''
  followUps.value = []

  if (persistenceEnabled() && !conversations.activeId) {
    suppressConvWatch = true
    try {
      await ensureActiveConversation()
    } catch {
      error.value = '创建会话失败，请重试'
      return
    } finally {
      suppressConvWatch = false
    }
  }

  if (!regenerate) {
    messages.value.push({ role: 'user', content: message, ts: Date.now() })
  }
  input.value = ''
  // 通过响应式数组索引写入，保证每个 token 都触发界面更新（流式逐字渲染）
  messages.value.push({ role: 'assistant', content: '', rag: ragEnabled.value, ts: Date.now() })
  const replyIndex = messages.value.length - 1
  void scrollToBottom()
  streaming.value = true
  const convIdForReply = conversations.activeId

  const wasDefaultTitle =
    persistenceEnabled() &&
    conversations.list.find((c) => c.id === conversations.activeId)?.title === '新对话'

  const params = new URLSearchParams({ message, chatId: currentChatId() })
  params.set('persona', activePersonaId.value)
  params.set('mode', activeMode.value)
  if (regenerate) params.set('regenerate', 'true')
  const endpoint = ragEnabled.value ? '/ai/love_chat/rag_stream' : '/ai/love_chat/stream'

  closeStream = openSseStream(
    `${endpoint}?${params.toString()}`,
    (token) => {
      const reply = messages.value[replyIndex]
      if (!reply) return // 会话被切换后消息列表已重建，丢弃过期 token
      reply.content += token
      feedSpeech(token)
      void scrollToBottom()
    },
    () => {
      streaming.value = false
      closeStream = null
      finishSpeech()
      const reply = messages.value[replyIndex]
      if (!reply) return // 会话已切换
      if (!reply.content) {
        reply.content = '（没有收到回复，请重试）'
        error.value = '没有收到回复，请确认后端已启动并配置了 DASHSCOPE_API_KEY'
        return
      }
      if (persistenceEnabled()) {
        void refreshList()
        // 标题由后端异步生成，稍后再刷新一次侧边栏
        if (wasDefaultTitle) setTimeout(() => void refreshList(), 2500)
        loadFollowUps(convIdForReply)
      }
    },
    (msg) => {
      streaming.value = false
      closeStream = null
      error.value = msg || '连接失败，请确认后端 8101 已启动'
    },
  )
}

function stopStreamInternal() {
  closeStream?.()
  closeStream = null
}

function stop() {
  stopStreamInternal()
  stopSpeech()
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'assistant' && !last.content) last.content = '（已停止生成）'
}

function toggleVoice() {
  settings.voiceEnabled = !settings.voiceEnabled
  if (!settings.voiceEnabled) stopSpeech()
}

async function doExport() {
  if (!conversations.activeId) return
  try {
    await exportConversation(conversations.activeId, currentTitle.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '导出失败'
  }
}

// 语音输入：识别结果追加到输入框（可编辑后再发送）
const { listening: micListening, interim: micInterim, toggle: toggleMic } = useVoiceInput({
  onFinal: (text) => {
    input.value = input.value ? `${input.value} ${text}` : text
  },
  onError: (msg) => {
    error.value = msg
  },
})

const inputPlaceholder = computed(() => {
  if (micInterim.value) return `🎤 ${micInterim.value}`
  return ragEnabled.value ? '结合恋爱知识库回答，说说你的困惑…' : '说出你的困惑，恋爱大师为你解答…'
})

/** 重新生成：丢弃最后一条回复，服务端会先删除库中最后一轮问答再重新作答 */
function regenerate() {
  if (streaming.value) return
  const lastUser = [...messages.value].reverse().find((m) => m.role === 'user')
  if (!lastUser) return
  if (messages.value.length && messages.value[messages.value.length - 1].role === 'assistant') {
    messages.value.pop()
  }
  void streamSend(lastUser.content, true)
}

async function copyMessage(index: number) {
  try {
    await navigator.clipboard.writeText(messages.value[index].content)
    copiedIndex.value = index
    setTimeout(() => (copiedIndex.value = -1), 1200)
  } catch {
    // 剪贴板不可用时忽略
  }
}

function autoGrow(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 150) + 'px'
}

function resetSession() {
  stopStreamInternal()
  streaming.value = false
  messages.value = []
  guestChatId.value = 'chat-' + Date.now().toString(36)
  // 回到角色选择
  guestGreeting.value = ''
  care.value = null
}
</script>

<template>
  <div class="chat-page">
    <div class="toolbar">
      <span class="conv-title">{{ currentTitle }}</span>
      <span v-if="!persistenceEnabled()" class="guest-badge">体验模式 · 历史不保存</span>
      <button
        v-else-if="activePersona && conversations.activeId"
        class="persona-chip"
        title="换一个角色开始新会话"
        @click="setActive('')"
      >{{ activePersona.emoji }} {{ activePersona.name }} · {{ activeMode === 'companion' ? '陪伴' : '顾问' }}</button>
      <div class="toolbar-right">
        <button
          v-if="persistenceEnabled() && conversations.activeId"
          class="voice-btn"
          title="导出当前对话为 Markdown"
          @click="doExport"
        >⬇️ 导出</button>
        <button
          class="voice-btn"
          :class="{ on: settings.voiceEnabled, speaking }"
          :title="settings.voiceEnabled ? '关闭语音朗读' : '开启语音朗读（AI 会自动读出回答）'"
          @click="toggleVoice"
        >
          {{ speaking ? '🗣️ 朗读中' : settings.voiceEnabled ? '🔊 语音开' : '🔇 语音关' }}
        </button>
        <button v-if="!persistenceEnabled()" class="ghost-btn" @click="resetSession">新建会话</button>
      </div>
    </div>

    <div v-if="anniversaryBanner" class="notice-banner anniversary">{{ anniversaryBanner }}</div>
    <div v-if="showQuote && dailyQuote" class="notice-banner quote">
      <span class="q-text">💌 今日情话：{{ dailyQuote }}</span>
      <span class="q-actions">
        <button @click="refreshQuote">换一句</button>
        <button title="今天不再显示" @click="dismissQuote">✕</button>
      </span>
    </div>

    <div v-if="care && messages.length" class="care-card">
      <div class="care-avatar" :style="activePersona ? { background: `linear-gradient(135deg, ${activePersona.color}, #a76bff)` } : {}">
        {{ activePersona?.emoji || '💘' }}
      </div>
      <div class="care-body">
        <div class="care-title">{{ activePersona?.name || 'TA' }} 悄悄给你发来一条消息</div>
        <div class="care-content">{{ care.content }}</div>
        <button class="care-reply" @click="replyCare">回一句</button>
      </div>
    </div>

    <div class="msg-list">
      <div v-if="messages.length === 0" class="welcome">
        <h2>选择一位角色开始 💘</h2>
        <p>每位角色都有独特的性格与声音，可随时切换两种模式：恋爱顾问帮你出主意，暖心陪伴陪你聊聊天。</p>
        <div class="mode-row">
          <button :class="{ on: pickedMode === 'advisor' }" @click="pickedMode = 'advisor'">🎯 恋爱顾问</button>
          <button :class="{ on: pickedMode === 'companion' }" @click="pickedMode = 'companion'">💞 暖心陪伴</button>
        </div>
        <div class="persona-grid">
          <div
            v-for="p in personas"
            :key="p.id"
            class="persona-card"
            @click="pickPersona(p, pickedMode)"
          >
            <div class="p-avatar" :style="{ background: `linear-gradient(135deg, ${p.color}, #a76bff)` }">
              {{ p.emoji }}
            </div>
            <div class="p-name">{{ p.name }}</div>
            <div class="p-tagline">{{ p.tagline }}</div>
            <button class="p-preview" title="试听角色声音" @click.stop="previewVoice(p)">▶ 试听</button>
          </div>
        </div>
        <div v-if="pickedMode === 'advisor'" class="suggestion-row">
          <button v-for="s in suggestions" :key="s" class="suggestion" @click="send(s)">
            {{ s }}
          </button>
        </div>
      </div>
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <div v-if="m.role === 'assistant'" class="avatar ai">💘</div>
        <div class="msg-main">
          <div
            v-if="m.role === 'assistant' && streaming && i === messages.length - 1 && !m.content"
            class="bubble thinking"
          >
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
          <div
            v-else
            class="bubble"
            v-html="renderMarkdown(
              streaming && i === messages.length - 1 && m.role === 'assistant' ? m.content + ' ▌' : m.content,
            )"
          ></div>
          <div class="msg-meta">
            <span v-if="m.ts" class="msg-time">{{ fmtTs(m.ts) }}</span>
            <span v-if="m.role === 'assistant' && m.rag" class="rag-badge">📚 知识库</span>
          </div>
          <div
            v-if="m.role === 'assistant' && m.content && !(streaming && i === messages.length - 1)"
            class="msg-actions"
          >
            <button @click="copyMessage(i)">{{ copiedIndex === i ? '✅ 已复制' : '📋 复制' }}</button>
            <button v-if="i === messages.length - 1" @click="regenerate">🔄 重新生成</button>
            <button @click="speakFull(m.content)">🔊 朗读</button>
          </div>
        </div>
        <div v-if="m.role === 'user'" class="avatar me">{{ meInitial }}</div>
      </div>
      <div v-if="followUps.length && !streaming" class="follow-ups">
        <div class="fu-label">接着问：</div>
        <button v-for="f in followUps" :key="f" class="follow-up" @click="send(f)">{{ f }}</button>
      </div>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <div class="input-bar">
      <label class="rag-toggle" :class="{ on: ragEnabled }" title="开启后，回答前会先检索你上传到恋爱知识库的文档">
        <input v-model="ragEnabled" type="checkbox" hidden />
        <span class="switch"><span class="knob"></span></span>
        <span class="rag-label">📚 知识库增强</span>
      </label>
      <textarea
        v-model="input"
        rows="1"
        :placeholder="inputPlaceholder"
        @keydown.enter.exact.prevent="send()"
        @input="autoGrow"
      ></textarea>
      <button
        v-if="voiceInputSupported"
        class="mic-btn"
        :class="{ listening: micListening }"
        :title="micListening ? '停止语音输入' : '语音输入'"
        @click="toggleMic"
      >🎙️</button>
      <button v-if="streaming" class="stop-btn" @click="stop">停止</button>
      <button v-else class="send-btn" :disabled="!input.trim()" @click="send()">发送</button>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 860px;
  margin: 0 auto;
  width: 100%;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  color: var(--text-4);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
}
.conv-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.guest-badge {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-4);
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  padding: 2px 10px;
  white-space: nowrap;
}
.ghost-btn {
  margin-left: auto;
  background: none;
  border: 1px solid var(--border-strong);
  color: var(--text-3);
  border-radius: 8px;
  padding: 4px 12px;
  cursor: pointer;
}
.toolbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.voice-btn {
  border: 1px solid var(--border-strong);
  background: none;
  color: var(--text-4);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.voice-btn.on {
  color: #ffd76b;
  border-color: #b98a2f;
}
.voice-btn.speaking {
  color: var(--bg);
  background: linear-gradient(135deg, #ffd76b, #ff9d5c);
  border-color: transparent;
  animation: pulse 1.2s infinite;
}
@keyframes pulse {
  50% { opacity: 0.75; }
}
.ghost-btn {
  background: none;
}
.ghost-btn:hover {
  border-color: var(--a1);
  color: #fff;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.notice-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 10px 16px 0;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
}
.notice-banner.anniversary {
  background: linear-gradient(135deg, rgba(255, 107, 157, 0.16), rgba(167, 107, 255, 0.16));
  border: 1px solid rgba(255, 107, 157, 0.45);
  color: var(--text);
}
.notice-banner.quote {
  background: var(--bg-card);
  border: 1px dashed var(--a2);
  color: var(--text-2);
}
.notice-banner .q-text {
  flex: 1;
  min-width: 0;
}
.notice-banner .q-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.notice-banner .q-actions button {
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-4);
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;
}
.notice-banner .q-actions button:hover {
  color: var(--text);
  border-color: var(--a2);
}
.welcome {
  text-align: center;
  color: var(--text-3);
  margin-top: 36px;
}
.welcome h2 {
  color: var(--text);
}
.mode-row {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 16px;
}
.mode-row button {
  border: 1px solid var(--border-strong);
  background: var(--bg-card);
  color: var(--text-2);
  border-radius: 999px;
  padding: 8px 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.mode-row button.on {
  background: var(--accent-grad);
  border-color: transparent;
  color: #fff;
}
.persona-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  max-width: 720px;
  margin: 20px auto 0;
}
.persona-card {
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  border-radius: 16px;
  padding: 16px 12px 12px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.persona-card:hover {
  border-color: var(--a2);
  transform: translateY(-2px);
}
.p-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  margin-bottom: 2px;
}
.p-name {
  color: var(--text);
  font-weight: 600;
  font-size: 15px;
}
.p-tagline {
  color: var(--text-4);
  font-size: 12px;
  text-align: center;
  min-height: 18px;
}
.p-preview {
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-4);
  border-radius: 999px;
  padding: 3px 12px;
  font-size: 12px;
  cursor: pointer;
  margin-top: 2px;
}
.p-preview:hover {
  color: #fff;
  border-color: var(--a2);
}
.persona-chip {
  border: 1px solid var(--border-strong);
  background: var(--bg-card);
  color: var(--text-2);
  border-radius: 999px;
  padding: 3px 12px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.persona-chip:hover {
  border-color: var(--a2);
  color: #fff;
}
.care-card {
  display: flex;
  gap: 12px;
  margin: 12px 16px 0;
  padding: 14px;
  background: var(--bg-card);
  border: 1px solid var(--a2);
  border-radius: 14px;
}
.care-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background: var(--accent-grad);
}
.care-title {
  font-size: 12px;
  color: var(--text-4);
  margin-bottom: 4px;
}
.care-content {
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
}
.care-reply {
  margin-top: 8px;
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-2);
  border-radius: 999px;
  padding: 3px 14px;
  font-size: 12px;
  cursor: pointer;
}
.care-reply:hover {
  color: #fff;
  border-color: var(--a2);
}
.suggestion-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 24px;
}
.suggestion {
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  color: var(--text-2);
  border-radius: 12px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
}
.suggestion:hover {
  border-color: var(--a2);
  color: #fff;
}
.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  position: relative;
  align-items: flex-start;
}
.msg.user {
  justify-content: flex-end;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  flex-shrink: 0;
  user-select: none;
  margin-top: 2px;
}
.avatar.ai {
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
}
.avatar.me {
  background: var(--bg-card);
  color: var(--text-2);
  border: 1px solid var(--border-strong);
  font-size: 14px;
  font-weight: 600;
}
.msg-main {
  max-width: 72%;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.msg.user .msg-main {
  align-items: flex-end;
}
.bubble {
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.6;
  word-break: break-word;
  font-size: 14px;
}
.msg.user .bubble {
  background: var(--accent-grad);
  color: #fff;
  border-bottom-right-radius: 4px;
  white-space: pre-wrap;
}
.msg.assistant .bubble {
  background: var(--bg-card);
  color: var(--text-2);
  border-bottom-left-radius: 4px;
}
/* 富 Markdown 元素样式（marked 渲染产物） */
.bubble :deep(p) {
  margin: 0 0 6px;
}
.bubble :deep(p:last-child) {
  margin-bottom: 0;
}
.bubble :deep(ul),
.bubble :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}
.bubble :deep(li) {
  margin: 2px 0;
}
.bubble :deep(blockquote) {
  border-left: 3px solid var(--a2);
  padding: 2px 10px;
  margin: 6px 0;
  color: var(--text-3);
  background: var(--bg-soft);
  border-radius: 4px;
}
.bubble :deep(a) {
  color: var(--a2);
}
.bubble :deep(h1),
.bubble :deep(h2),
.bubble :deep(h3),
.bubble :deep(h4) {
  font-size: 15px;
  margin: 8px 0 4px;
}
.bubble :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 8px 0;
}
.bubble :deep(table) {
  border-collapse: collapse;
  margin: 6px 0;
}
.bubble :deep(th),
.bubble :deep(td) {
  border: 1px solid var(--border-strong);
  padding: 4px 8px;
  font-size: 13px;
}
.bubble :deep(pre) {
  background: var(--code-bg);
  border-radius: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  margin: 6px 0;
  white-space: pre;
}
.bubble :deep(code) {
  background: var(--code-bg);
  border-radius: 4px;
  padding: 1px 5px;
  font-family: Consolas, monospace;
  font-size: 13px;
}
.bubble :deep(pre code) {
  background: none;
  padding: 0;
}
.bubble.thinking {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 14px 16px;
}
.bubble.thinking .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-4);
  animation: think 1.2s infinite;
}
.bubble.thinking .dot:nth-child(2) {
  animation-delay: 0.2s;
}
.bubble.thinking .dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes think {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.msg-time {
  font-size: 11px;
  color: var(--text-4);
}
.follow-ups {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 4px 0 12px 44px;
}
.fu-label {
  font-size: 12px;
  color: var(--text-4);
}
.follow-up {
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  color: var(--text-2);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.follow-up:hover {
  border-color: var(--a2);
  color: #fff;
}
.msg-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}
.msg:hover .msg-actions {
  opacity: 1;
}
.msg-actions button {
  border: 1px solid var(--border-strong);
  background: var(--bg-soft);
  color: var(--text-4);
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;
}
.msg-actions button:hover {
  color: #fff;
  border-color: var(--a2);
}
.rag-badge {
  font-size: 11px;
  color: var(--a2);
  background: rgba(167, 107, 255, 0.15);
  border: 1px solid rgba(167, 107, 255, 0.4);
  border-radius: 999px;
  padding: 2px 8px;
  white-space: nowrap;
}
.error-bar {
  background: var(--danger-bg);
  color: var(--danger-text);
  padding: 8px 16px;
  font-size: 13px;
}
.input-bar {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  align-items: flex-end;
}
.rag-toggle {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  flex-shrink: 0;
  color: var(--text-4);
  font-size: 12px;
  user-select: none;
  padding-bottom: 10px;
}
.rag-toggle .switch {
  width: 34px;
  height: 18px;
  border-radius: 999px;
  background: var(--hover);
  border: 1px solid var(--border-strong);
  position: relative;
  transition: all 0.2s;
  flex-shrink: 0;
}
.rag-toggle .knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--text-4);
  transition: all 0.2s;
}
.rag-toggle.on {
  color: var(--text-2);
}
.rag-toggle.on .switch {
  background: var(--accent-grad);
  border-color: transparent;
}
.rag-toggle.on .knob {
  left: 18px;
  background: #fff;
}
.input-bar textarea {
  flex: 1;
  background: var(--bg-input);
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  color: var(--text);
  padding: 10px 14px;
  font-size: 14px;
  outline: none;
  resize: none;
  font-family: inherit;
  line-height: 1.5;
  max-height: 150px;
}
.input-bar textarea:focus {
  border-color: var(--a2);
}
.send-btn,
.stop-btn {
  border: none;
  border-radius: 12px;
  padding: 10px 20px;
  font-size: 14px;
  cursor: pointer;
  color: #fff;
  flex-shrink: 0;
}
.send-btn {
  background: var(--accent-grad);
}
.mic-btn {
  border: 1px solid var(--border-strong);
  background: transparent;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  font-size: 16px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}
.mic-btn:hover {
  border-color: var(--a1);
}
.mic-btn.listening {
  background: var(--accent-grad);
  border-color: transparent;
  animation: pulse 1.2s infinite;
}
.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.stop-btn {
  background: var(--danger-bg);
  color: var(--danger-text);
}
@media (max-width: 860px) {
  .toolbar {
    padding-left: 52px;
  }
  .msg-main {
    max-width: 86%;
  }
}
</style>
