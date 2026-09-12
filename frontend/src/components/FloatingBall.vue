<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { settings } from '../stores/settings'
import { ui } from '../stores/ui'
import { newConversation, persistenceEnabled } from '../stores/conversations'
import { stopSpeech } from '../composables/useSpeech'
/**
 * 悬浮窗助手（参考豆包悬浮球）：可拖动、点击展开快捷面板，
 * 位置记忆在 localStorage，可在设置里关闭。
 */
const router = useRouter()
const open = ref(false)
const pos = ref<{ x: number; y: number }>({ x: -1, y: -1 }) // -1 表示使用默认右下位置
const dragging = ref(false)
let moved = false
let startPointer = { x: 0, y: 0 }
let startPos = { x: 0, y: 0 }

const POS_KEY = 'ailove:ballPos'

onMounted(() => {
  try {
    const saved = JSON.parse(localStorage.getItem(POS_KEY) || 'null')
    if (saved && typeof saved.x === 'number') pos.value = saved
  } catch {
    // 忽略损坏的本地数据
  }
})

const style = computed(() => {
  if (pos.value.x < 0) {
    return { right: '26px', bottom: '110px' }
  }
  return { left: `${pos.value.x}px`, top: `${pos.value.y}px` }
})

function onPointerDown(e: PointerEvent) {
  dragging.value = true
  moved = false
  startPointer = { x: e.clientX, y: e.clientY }
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  startPos = { x: rect.left, y: rect.top }
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - startPointer.x
  const dy = e.clientY - startPointer.y
  if (!moved && Math.abs(dx) + Math.abs(dy) > 5) moved = true
  if (!moved) return
  const size = 52
  const x = Math.min(Math.max(dx + startPos.x, 8), window.innerWidth - size - 8)
  const y = Math.min(Math.max(dy + startPos.y, 8), window.innerHeight - size - 8)
  pos.value = { x, y }
}

function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
  if (moved) {
    localStorage.setItem(POS_KEY, JSON.stringify(pos.value))
  } else {
    open.value = !open.value
  }
}

async function newChat() {
  open.value = false
  router.push('/')
  if (persistenceEnabled()) {
    try {
      await newConversation()
    } catch {
      // 失败保持现状
    }
  }
}

function toggleVoice() {
  settings.voiceEnabled = !settings.voiceEnabled
  if (!settings.voiceEnabled) stopSpeech()
}

function openSettings() {
  open.value = false
  ui.settingsOpen = true
}

function hideBall() {
  open.value = false
  settings.floatBall = false
}
</script>

<template>
  <div class="float-ball-wrap" :style="style">
    <transition name="pop">
      <div v-if="open" class="ball-panel">
        <button @click="newChat">💬 新的对话</button>
        <button @click="toggleVoice">
          {{ settings.voiceEnabled ? '🔇 关闭朗读' : '🔊 开启朗读' }}
        </button>
        <button @click="openSettings">⚙️ 打开设置</button>
        <button class="dim" @click="hideBall">🙈 隐藏悬浮球</button>
      </div>
    </transition>
    <div
      class="ball"
      :class="{ dragging, open }"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      {{ open ? '✕' : '💘' }}
    </div>
  </div>
</template>

<style scoped>
.float-ball-wrap {
  position: fixed;
  z-index: 90;
  touch-action: none;
  user-select: none;
}
.ball {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--accent-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  cursor: grab;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  transition: transform 0.15s ease;
}
.ball:hover {
  transform: scale(1.08);
}
.ball.dragging {
  cursor: grabbing;
  transform: scale(1.12);
}
.ball.open {
  transform: scale(1.08);
}
.ball-panel {
  position: absolute;
  bottom: 62px;
  right: 0;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 6px;
  display: flex;
  flex-direction: column;
  min-width: 148px;
  gap: 2px;
}
.ball-panel button {
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  text-align: left;
  padding: 9px 12px;
  border-radius: 9px;
  cursor: pointer;
  white-space: nowrap;
}
.ball-panel button:hover {
  background: var(--hover);
  color: var(--text);
}
.ball-panel button.dim {
  color: var(--text-5);
}
.pop-enter-active,
.pop-leave-active {
  transition: all 0.15s ease;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.96);
}
</style>
