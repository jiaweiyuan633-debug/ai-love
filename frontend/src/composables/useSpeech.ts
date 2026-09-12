import { ref } from 'vue'
import { settings } from '../stores/settings'
import { auth, onUnauthorized } from '../stores/auth'

/**
 * 语音朗读——双引擎：
 * 1. 人设引擎（默认）：后端 CosyVoice 高拟真 TTS（温柔御姐/邻家小妹/高冷总裁/清纯男大/中二少年/知心姐姐），
 *    按句请求 /api/tts 返回 mp3，HTMLAudioElement 顺序播放，入队即预取掩盖延迟；
 * 2. 浏览器引擎（免费兜底）：Web Speech API speechSynthesis。
 * 模块级单例：整个应用共享同一个播放队列与状态。
 */

export const speaking = ref(false)

const SENTENCE_ENDS = '。！？!?…；;\n'

let voiceBuffer = ''
let voices: SpeechSynthesisVoice[] = []

// ---------- 播放队列（双引擎统一） ----------
let queue: string[] = []
let pumping = false
let currentAudio: HTMLAudioElement | null = null
let generation = 0 // 每次 stop 递增，使旧播放任务失效

function synth(): SpeechSynthesis | null {
  return typeof window !== 'undefined' ? window.speechSynthesis : null
}

function loadVoices() {
  const s = synth()
  if (!s) return
  voices = s.getVoices()
}

if (typeof window !== 'undefined' && window.speechSynthesis) {
  loadVoices()
  // Chrome 中音色列表异步加载
  window.speechSynthesis.onvoiceschanged = loadVoices
}

export function zhVoices(): SpeechSynthesisVoice[] {
  if (!voices.length) loadVoices()
  return voices.filter((v) => v.lang.toLowerCase().startsWith('zh'))
}

function pickVoice(): SpeechSynthesisVoice | null {
  if (!voices.length) loadVoices()
  if (settings.voiceURI) {
    const matched = voices.find((v) => v.voiceURI === settings.voiceURI)
    if (matched) return matched
  }
  return voices.find((v) => v.lang.toLowerCase().startsWith('zh')) || voices[0] || null
}

// ---------- 人设引擎：后端 TTS ----------

const ttsCache = new Map<string, Promise<Blob>>()
const inflightControllers = new Set<AbortController>()

async function fetchTts(text: string): Promise<Blob> {
  const key = `${settings.voicePersona}|${settings.voiceRate}|${text}`
  const hit = ttsCache.get(key)
  if (hit) return hit
  const controller = new AbortController()
  inflightControllers.add(controller)
  const promise = (async () => {
    const resp = await fetch('/api/tts', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
      },
      body: JSON.stringify({ text, persona: settings.voicePersona, rate: settings.voiceRate }),
      signal: controller.signal,
    })
    if (resp.status === 401 && auth.enabled) {
      onUnauthorized()
      throw new Error('登录已过期')
    }
    if (!resp.ok) throw new Error('语音合成失败')
    return resp.blob()
  })()
  ttsCache.set(key, promise)
  promise.catch(() => ttsCache.delete(key)).finally(() => inflightControllers.delete(controller))
  // 简单容量控制
  if (ttsCache.size > 100) {
    const first = ttsCache.keys().next().value
    if (first) ttsCache.delete(first)
  }
  return promise
}

// ---------- 按句切分 ----------

function splitSentences(text: string): string[] {
  const parts: string[] = []
  let rest = text
  for (;;) {
    const m = rest.match(/[。！？!?…；;\n]/)
    if (!m || m.index === undefined) break
    const sentence = rest.slice(0, m.index + 1)
    if (sentence.trim()) parts.push(sentence)
    rest = rest.slice(m.index + 1)
  }
  if (rest.trim()) parts.push(rest)
  return parts
}

// ---------- 引擎播放实现 ----------

function playPersona(text: string, gen: number): Promise<void> {
  return new Promise((resolve) => {
    void (async () => {
      try {
        const blob = await fetchTts(text)
        if (gen !== generation) return resolve()
        const url = URL.createObjectURL(blob)
        const audio = new Audio(url)
        currentAudio = audio
        const done = () => {
          URL.revokeObjectURL(url)
          resolve()
        }
        audio.onended = done
        audio.onerror = done
        await audio.play().catch(done)
      } catch {
        resolve()
      }
    })()
  })
}

function playBrowser(text: string, gen: number): Promise<void> {
  return new Promise((resolve) => {
    const s = synth()
    if (!s || gen !== generation) return resolve()
    const utter = new SpeechSynthesisUtterance(text)
    const voice = pickVoice()
    if (voice) {
      utter.voice = voice
      utter.lang = voice.lang
    } else {
      utter.lang = 'zh-CN'
    }
    utter.rate = settings.voiceRate
    let settled = false
    const settle = () => {
      if (settled) return
      settled = true
      resolve()
    }
    utter.onend = settle
    utter.onerror = settle
    // 兜底：个别环境 onend 不触发（无音频输出），按字数估算超时
    setTimeout(settle, Math.max(6000, text.length * 700))
    s.speak(utter)
  })
}

function enqueue(text: string) {
  queue.push(text)
  // 人设引擎：入队即预取合成，播放时直接命中缓存
  if (settings.voicePersona) void fetchTts(text).catch(() => undefined)
  void pump()
}

async function pump() {
  if (pumping) return
  pumping = true
  speaking.value = true
  const gen = generation
  try {
    while (queue.length > 0) {
      if (gen !== generation) return
      const text = queue.shift()!
      if (settings.voicePersona) {
        await playPersona(text, gen)
      } else {
        await playBrowser(text, gen)
      }
    }
  } finally {
    pumping = false
    if (gen === generation) speaking.value = false
  }
}

// ---------- 对外 API ----------

/** 流式增量喂入：凑满完整句子立即入队朗读，半句留在缓冲区 */
export function feedSpeech(text: string) {
  if (!settings.voiceEnabled || !settings.autoRead) return
  voiceBuffer += text
  let lastEnd = -1
  for (let i = 0; i < voiceBuffer.length; i++) {
    if (SENTENCE_ENDS.includes(voiceBuffer[i])) lastEnd = i
  }
  if (lastEnd >= 0) {
    const complete = voiceBuffer.slice(0, lastEnd + 1)
    voiceBuffer = voiceBuffer.slice(lastEnd + 1)
    splitSentences(complete).forEach(enqueue)
  }
}

/** 流结束：把缓冲区里的残句读掉 */
export function finishSpeech() {
  if (voiceBuffer.trim()) {
    enqueue(voiceBuffer)
  }
  voiceBuffer = ''
}

export function stopSpeech() {
  generation++
  queue = []
  voiceBuffer = ''
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
  synth()?.cancel()
  inflightControllers.forEach((c) => c.abort())
  inflightControllers.clear()
  speaking.value = false
}

/** 朗读一条完整消息（切句排队，保证响应及时） */
export function speakFull(text: string) {
  stopSpeech()
  splitSentences(text).forEach(enqueue)
}
