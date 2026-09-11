<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { openSseStream } from '../api'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  rag?: boolean
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const chatId = ref('chat-' + Date.now().toString(36))
const streaming = ref(false)
const ragEnabled = ref(false)
const error = ref('')
let closeStream: (() => void) | null = null

const suggestions = [
  '我喜欢上一个同事，怎么自然地开始聊天？',
  '第一次约会该选什么地点？',
  '异地恋如何维系感情？',
  '帮我分析：TA 已读不回说明什么？',
]

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
  const message = (text ?? input.value).trim()
  if (!message || streaming.value) return
  input.value = ''
  error.value = ''
  messages.value.push({ role: 'user', content: message })
  // 通过响应式数组索引写入，保证每个 token 都触发界面更新（流式逐字渲染）
  messages.value.push({ role: 'assistant', content: '', rag: ragEnabled.value })
  const replyIndex = messages.value.length - 1
  scrollToBottom()
  streaming.value = true

  const endpoint = ragEnabled.value ? '/ai/love_chat/rag_stream' : '/ai/love_chat/stream'
  closeStream = openSseStream(
    `${endpoint}?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId.value)}`,
    (token) => {
      messages.value[replyIndex].content += token
      scrollToBottom()
    },
    () => {
      streaming.value = false
      closeStream = null
      if (!messages.value[replyIndex].content) {
        messages.value[replyIndex].content = '（没有收到回复，请重试）'
        error.value = '没有收到回复，请确认后端已启动并配置了 DASHSCOPE_API_KEY'
      }
    },
    () => {
      streaming.value = false
      closeStream = null
      error.value = '连接失败，请确认后端 8101 已启动'
    },
  )
}

function stop() {
  closeStream?.()
  streaming.value = false
}

function resetSession() {
  stop()
  messages.value = []
  chatId.value = 'chat-' + Date.now().toString(36)
}
</script>

<template>
  <div class="chat-page">
    <div class="toolbar">
      <span class="session">会话：{{ chatId }}</span>
      <button class="ghost-btn" @click="resetSession">新建会话</button>
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
      <div
        v-for="(m, i) in messages"
        :key="i"
        class="msg"
        :class="m.role"
      >
        <div class="bubble" v-html="renderMd(m.content)"></div>
        <span
          v-if="streaming && i === messages.length - 1 && m.role === 'assistant'"
          class="cursor"
        >▌</span>
        <span v-if="m.role === 'assistant' && m.rag" class="rag-badge">📚 知识库</span>
      </div>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <div class="input-bar">
      <label class="rag-toggle" :class="{ on: ragEnabled }" title="开启后，回答前会先检索你上传到恋爱知识库的文档">
        <input v-model="ragEnabled" type="checkbox" hidden />
        <span class="switch"><span class="knob"></span></span>
        <span class="rag-label">📚 知识库增强</span>
      </label>
      <input
        v-model="input"
        :placeholder="ragEnabled ? '结合恋爱知识库回答，说说你的困惑…' : '说出你的困惑，恋爱大师为你解答…'"
        @keydown.enter="send()"
      />
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
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  color: #8888a6;
  font-size: 13px;
}
.session {
  font-family: monospace;
}
.ghost-btn {
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
  align-items: center;
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
.input-bar input {
  flex: 1;
  background: #1a1a2e;
  border: 1px solid #34345a;
  border-radius: 12px;
  color: #f0f0fa;
  padding: 10px 14px;
  font-size: 14px;
  outline: none;
}
.input-bar input:focus {
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
</style>
