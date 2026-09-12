<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { listMessages, openSseStream, type StoredMessage } from '../api'
import { conversations, ensureActiveConversation, persistenceEnabled, refreshList } from '../stores/conversations'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  rag?: boolean
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const guestChatId = ref('chat-' + Date.now().toString(36))
const streaming = ref(false)
const ragEnabled = ref(false)
const error = ref('')
const copiedIndex = ref(-1)
let closeStream: (() => void) | null = null

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

const currentTitle = computed(() => {
  if (!persistenceEnabled()) return '体验模式'
  return conversations.list.find((c) => c.id === conversations.activeId)?.title || '新对话'
})

// 切换会话 → 加载历史消息
watch(
  () => conversations.activeId,
  async (id) => {
    stopStreamInternal()
    messages.value = []
    error.value = ''
    if (!persistenceEnabled() || !id) return
    try {
      const stored: StoredMessage[] = await listMessages(id)
      messages.value = stored.map((m) => ({ role: m.role, content: m.content, rag: false }))
      void scrollToBottom()
    } catch {
      // 历史加载失败不阻塞聊天
    }
  },
)

async function scrollToBottom() {
  await nextTick()
  const box = document.querySelector('.msg-list')
  box?.scrollTo({ top: box.scrollHeight })
}

/** 轻量 Markdown 渲染：先转义 HTML 再转换加粗/行内代码，保证安全 */
function renderMd(text: string): string {
  let s = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  s = s.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/`([^`\n]+)`/g, '<code>$1</code>')
  s = s.replace(/^#{1,4}\s+(.+)$/gm, '<strong>$1</strong>')
  return s
}

function send(text?: string) {
  void streamSend(text ?? input.value, false)
}

async function streamSend(raw: string, regenerate: boolean) {
  const message = raw.trim()
  if (!message || streaming.value) return
  error.value = ''

  if (persistenceEnabled() && !conversations.activeId) {
    try {
      await ensureActiveConversation()
    } catch {
      error.value = '创建会话失败，请重试'
      return
    }
  }

  if (!regenerate) {
    messages.value.push({ role: 'user', content: message })
  }
  input.value = ''
  // 通过响应式数组索引写入，保证每个 token 都触发界面更新（流式逐字渲染）
  messages.value.push({ role: 'assistant', content: '', rag: ragEnabled.value })
  const replyIndex = messages.value.length - 1
  void scrollToBottom()
  streaming.value = true

  const wasDefaultTitle =
    persistenceEnabled() &&
    conversations.list.find((c) => c.id === conversations.activeId)?.title === '新对话'

  const params = new URLSearchParams({ message, chatId: currentChatId() })
  if (regenerate) params.set('regenerate', 'true')
  const endpoint = ragEnabled.value ? '/ai/love_chat/rag_stream' : '/ai/love_chat/stream'

  closeStream = openSseStream(
    `${endpoint}?${params.toString()}`,
    (token) => {
      messages.value[replyIndex].content += token
      void scrollToBottom()
    },
    () => {
      streaming.value = false
      closeStream = null
      if (!messages.value[replyIndex].content) {
        messages.value[replyIndex].content = '（没有收到回复，请重试）'
        error.value = '没有收到回复，请确认后端已启动并配置了 DASHSCOPE_API_KEY'
        return
      }
      if (persistenceEnabled()) {
        void refreshList()
        // 标题由后端异步生成，稍后再刷新一次侧边栏
        if (wasDefaultTitle) setTimeout(() => void refreshList(), 2500)
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
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'assistant' && !last.content) last.content = '（已停止生成）'
}

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
}
</script>

<template>
  <div class="chat-page">
    <div class="toolbar">
      <span class="conv-title">{{ currentTitle }}</span>
      <span v-if="!persistenceEnabled()" class="guest-badge">体验模式 · 历史不保存</span>
      <button v-if="!persistenceEnabled()" class="ghost-btn" @click="resetSession">新建会话</button>
    </div>

    <div class="msg-list">
      <div v-if="messages.length === 0" class="welcome">
        <h2>我是 AI 恋爱大师 💘</h2>
        <p>任何恋爱、脱单、约会问题都可以问我，也支持查天气、生成 PDF 恋爱报告、检索恋爱知识库。</p>
        <div class="suggestion-row">
          <button v-for="s in suggestions" :key="s" class="suggestion" @click="send(s)">
            {{ s }}
          </button>
        </div>
      </div>
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <div class="bubble" v-html="renderMd(m.content)"></div>
        <span v-if="streaming && i === messages.length - 1 && m.role === 'assistant'" class="cursor">▌</span>
        <span v-if="m.role === 'assistant' && m.rag" class="rag-badge">📚 知识库</span>
        <div
          v-if="m.role === 'assistant' && m.content && !(streaming && i === messages.length - 1)"
          class="msg-actions"
        >
          <button @click="copyMessage(i)">{{ copiedIndex === i ? '✅ 已复制' : '📋 复制' }}</button>
          <button v-if="i === messages.length - 1" @click="regenerate">🔄 重新生成</button>
        </div>
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
        :placeholder="ragEnabled ? '结合恋爱知识库回答，说说你的困惑…' : '说出你的困惑，恋爱大师为你解答…'"
        @keydown.enter.exact.prevent="send()"
        @input="autoGrow"
      ></textarea>
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
  color: #8888a6;
  font-size: 14px;
  font-weight: 600;
  color: #e6e6f2;
}
.conv-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.guest-badge {
  font-size: 12px;
  font-weight: 400;
  color: #8888a6;
  border: 1px solid #34345a;
  border-radius: 999px;
  padding: 2px 10px;
  white-space: nowrap;
}
.ghost-btn {
  margin-left: auto;
  background: none;
  border: 1px solid #3a3a55;
  color: #b6b6cc;
  border-radius: 8px;
  padding: 4px 12px;
  cursor: pointer;
}
.ghost-btn:hover {
  border-color: #ff6b9d;
  color: #fff;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.welcome {
  text-align: center;
  color: #a0a0c0;
  margin-top: 60px;
}
.welcome h2 {
  color: #f0f0fa;
}
.suggestion-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 24px;
}
.suggestion {
  background: #1e1e33;
  border: 1px solid #34345a;
  color: #c8c8e0;
  border-radius: 12px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
}
.suggestion:hover {
  border-color: #a76bff;
  color: #fff;
}
.msg {
  display: flex;
  margin-bottom: 14px;
  position: relative;
}
.msg.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
}
.bubble :deep(code) {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  padding: 1px 5px;
  font-family: Consolas, monospace;
  font-size: 13px;
}
.msg.user .bubble {
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg.assistant .bubble {
  background: #1e1e33;
  color: #e6e6f2;
  border-bottom-left-radius: 4px;
}
.cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.msg-actions {
  position: absolute;
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}
.msg.assistant .msg-actions {
  left: 0;
  bottom: -10px;
}
.msg:hover .msg-actions {
  opacity: 1;
}
.msg-actions button {
  border: 1px solid #34345a;
  background: #17172a;
  color: #8888a6;
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;
}
.msg-actions button:hover {
  color: #fff;
  border-color: #a76bff;
}
.rag-badge {
  align-self: flex-start;
  margin-top: 2px;
  font-size: 11px;
  color: #c9b6ff;
  background: rgba(167, 107, 255, 0.15);
  border: 1px solid rgba(167, 107, 255, 0.4);
  border-radius: 999px;
  padding: 2px 8px;
  white-space: nowrap;
}
.error-bar {
  background: #4a1f2e;
  color: #ff9db4;
  padding: 8px 16px;
  font-size: 13px;
}
.input-bar {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #2b2b3a;
  align-items: flex-end;
}
.rag-toggle {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  flex-shrink: 0;
  color: #8888a6;
  font-size: 12px;
  user-select: none;
  padding-bottom: 10px;
}
.rag-toggle .switch {
  width: 34px;
  height: 18px;
  border-radius: 999px;
  background: #2b2b45;
  border: 1px solid #3a3a55;
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
  background: #8888a6;
  transition: all 0.2s;
}
.rag-toggle.on {
  color: #e6e6f2;
}
.rag-toggle.on .switch {
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
  border-color: transparent;
}
.rag-toggle.on .knob {
  left: 18px;
  background: #fff;
}
.input-bar textarea {
  flex: 1;
  background: #1a1a2e;
  border: 1px solid #34345a;
  border-radius: 12px;
  color: #f0f0fa;
  padding: 10px 14px;
  font-size: 14px;
  outline: none;
  resize: none;
  font-family: inherit;
  line-height: 1.5;
  max-height: 150px;
}
.input-bar textarea:focus {
  border-color: #a76bff;
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
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
}
.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.stop-btn {
  background: #4a1f2e;
  color: #ff9db4;
}
@media (max-width: 860px) {
  .toolbar {
    padding-left: 52px;
  }
  .bubble {
    max-width: 86%;
  }
}
</style>
