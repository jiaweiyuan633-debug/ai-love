<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { openSseStream } from '../api'
import { renderMarkdown } from '../utils/markdown'

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
      <div v-if="output" class="md-body" v-html="renderMarkdown(output)"></div>
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
  color: var(--text-2);
}
.header h1 {
  color: var(--text);
  font-size: 22px;
}
.desc {
  font-size: 13px;
  color: var(--text-4);
}
.input-bar {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.input-bar input {
  flex: 1;
  background: var(--bg-input);
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  color: var(--text);
  padding: 10px 14px;
  font-size: 14px;
  outline: none;
}
.input-bar input:focus {
  border-color: var(--a2);
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
  background: linear-gradient(135deg, #6b9dff, var(--a2));
}
.run-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.stop-btn {
  background: var(--danger-bg);
  color: var(--danger-text);
}
.example-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}
.example {
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  color: var(--text-2);
  border-radius: 12px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}
.example:hover {
  border-color: var(--a2);
  color: #fff;
}
.example:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.error-bar {
  background: var(--danger-bg);
  color: var(--danger-text);
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-top: 12px;
}
.yumanus-output {
  flex: 1;
  overflow-y: auto;
  margin-top: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 16px;
}
.yumanus-output .md-body {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text-2);
  word-break: break-word;
}
.md-body :deep(p) {
  margin: 0 0 8px;
}
.md-body :deep(p:last-child) {
  margin-bottom: 0;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 20px;
}
.md-body :deep(code) {
  background: var(--code-bg);
  border-radius: 4px;
  padding: 1px 5px;
  font-family: Consolas, monospace;
  font-size: 12.5px;
}
.md-body :deep(pre) {
  background: var(--code-bg);
  border-radius: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  margin: 6px 0;
  white-space: pre;
}
.md-body :deep(blockquote) {
  border-left: 3px solid var(--a2);
  padding: 2px 10px;
  margin: 6px 0;
  color: var(--text-3);
  background: var(--bg-soft);
  border-radius: 4px;
}
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  font-size: 15px;
  margin: 10px 0 6px;
  color: var(--text);
}
.md-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 10px 0;
}
.placeholder {
  text-align: center;
  color: var(--text-5);
  margin-top: 60px;
}
</style>
