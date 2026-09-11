<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { openSseStream } from '../api'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const chatId = ref('chat-' + Date.now().toString(36))
const streaming = ref(false)
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

function send(text?: string) {
  const message = (text ?? input.value).trim()
  if (!message || streaming.value) return
  input.value = ''
  error.value = ''
  messages.value.push({ role: 'user', content: message })
  const reply = ref('')
  const placeholder = { role: 'assistant' as const, content: reply.value }
  messages.value.push(placeholder)
  scrollToBottom()
  streaming.value = true

  let received = false
  closeStream = openSseStream(
    `/ai/love_chat/stream?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId.value)}`,
    (token) => {
      received = true
      reply.value += token
      placeholder.content = reply.value
      scrollToBottom()
    },
    () => {
      streaming.value = false
      if (!received) {
        error.value = '没有收到回复，请确认后端已启动并配置了 DASHSCOPE_API_KEY'
      }
      closeStream = null
    },
    () => {
      streaming.value = false
      error.value = '连接中断，请稍后重试'
      closeStream = null
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
        <div class="bubble">{{ m.content }}<span v-if="streaming && i === messages.length - 1 && m.role === 'assistant'" class="cursor">▌</span></div>
      </div>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <div class="input-bar">
      <input
        v-model="input"
        placeholder="说出你的困惑，恋爱大师为你解答…"
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
