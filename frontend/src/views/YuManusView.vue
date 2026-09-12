<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { openSseStream } from '../api'

const task = ref('')
const output = ref('')
const running = ref(false)
const error = ref('')
let closeStream: (() => void) | null = null

const examples = [
  '帮我调研一下北京今天的天气，再查一下玫瑰的花语，最后给出一份约会建议',
  '搜索恋爱知识库里关于异地恋的建议并总结',
]

async function scrollToBottom() {
  await nextTick()
  const box = document.querySelector('.yumanus-output')
  box?.scrollTo({ top: box.scrollHeight })
}

function run(text?: string) {
  const t = (text ?? task.value).trim()
  if (!t || running.value) return
  task.value = ''
  output.value = ''
  error.value = ''
  running.value = true

  let received = false
  closeStream = openSseStream(
    `/ai/yumanus/stream?task=${encodeURIComponent(t)}`,
    (token) => {
      received = true
      output.value += token + '\n'
      scrollToBottom()
    },
    () => {
      running.value = false
      if (!received) {
        error.value = '没有收到任何过程输出，请确认后端已启动并配置了 DASHSCOPE_API_KEY'
      }
      closeStream = null
    },
    (msg) => {
      running.value = false
      error.value = msg || '连接中断，请稍后重试'
      closeStream = null
    },
  )
}

function stop() {
  closeStream?.()
  running.value = false
}
</script>

<template>
  <div class="yumanus-page">
    <div class="header">
      <h1>🤖 YuManus 自主规划智能体</h1>
      <p class="desc">
        给它一个任务，它会自主完成「计划 → 行动 → 观察」循环：自动调用天气查询、
        知识库检索、PDF 报告生成、MCP 远程工具（图片搜索 / 花语查询），全过程实时展示。
      </p>
    </div>

    <div class="input-bar">
      <input
        v-model="task"
        placeholder="描述一个任务，例如：查一下上海天气，结合花语给我一份约会攻略"
        @keydown.enter="run()"
      />
      <button v-if="running" class="stop-btn" @click="stop">停止</button>
      <button v-else class="run-btn" :disabled="!task.trim()" @click="run()">执行任务</button>
    </div>

    <div class="example-row">
      <button v-for="e in examples" :key="e" class="example" :disabled="running" @click="run(e)">
        {{ e }}
      </button>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <div class="yumanus-output" :class="{ empty: !output }">
      <pre v-if="output">{{ output }}</pre>
      <div v-else class="placeholder">
        {{ running ? 'YuManus 正在思考…' : '执行过程将在这里实时展示' }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.yumanus-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 28px 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  color: #c8c8e0;
}
.header h1 {
  color: #f0f0fa;
  font-size: 22px;
}
.desc {
  font-size: 13px;
  color: #8888a6;
}
.input-bar {
  display: flex;
  gap: 10px;
  margin-top: 18px;
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
.run-btn,
.stop-btn {
  border: none;
  border-radius: 12px;
  padding: 10px 20px;
  font-size: 14px;
  cursor: pointer;
  color: #fff;
}
.run-btn {
  background: linear-gradient(135deg, #6b9dff, #a76bff);
}
.run-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.stop-btn {
  background: #4a1f2e;
  color: #ff9db4;
}
.example-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}
.example {
  background: #1e1e33;
  border: 1px solid #34345a;
  color: #c8c8e0;
  border-radius: 12px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}
.example:hover {
  border-color: #a76bff;
  color: #fff;
}
.example:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.error-bar {
  background: #4a1f2e;
  color: #ff9db4;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-top: 12px;
}
.yumanus-output {
  flex: 1;
  overflow-y: auto;
  margin-top: 16px;
  background: #141426;
  border: 1px solid #2b2b3a;
  border-radius: 14px;
  padding: 16px;
}
.yumanus-output pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
  color: #d6d6ea;
}
.placeholder {
  text-align: center;
  color: #6a6a88;
  margin-top: 60px;
}
</style>
