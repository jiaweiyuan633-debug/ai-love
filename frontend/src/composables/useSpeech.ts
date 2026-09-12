import { ref } from 'vue'
import { settings } from '../stores/settings'

/**
 * 语音朗读（豆包式）：基于浏览器 Web Speech API。
 * - 流式期间用 feedSpeech 喂增量文本，凑满一句立即朗读（边生成边说）；
 * - speakFull 朗读完整消息；
 * - stopSpeech 立即停止。
 * 模块级单例：整个应用共享同一个合成队列与状态。
 */

export const speaking = ref(false)

let voiceBuffer = ''
let voices: SpeechSynthesisVoice[] = []

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

function pickVoice(): SpeechSynthesisVoice | null {
  if (!voices.length) loadVoices()
  if (settings.voiceURI) {
    const matched = voices.find((v) => v.voiceURI === settings.voiceURI)
    if (matched) return matched
  }
  return voices.find((v) => v.lang.toLowerCase().startsWith('zh')) || voices[0] || null
}

function speakText(text: string) {
  const s = synth()
  if (!s || !text.trim()) return
  const utter = new SpeechSynthesisUtterance(text)
  const voice = pickVoice()
  if (voice) {
    utter.voice = voice
    utter.lang = voice.lang
  } else {
    utter.lang = 'zh-CN'
  }
  utter.rate = settings.voiceRate
  utter.onstart = () => (speaking.value = true)
  utter.onend = () => {
    if (!s.speaking && !s.pending) speaking.value = false
  }
  s.speak(utter)
}

/** 按句子边界切分（中英文标点） */
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

const SENTENCE_ENDS = '。！？!?…；;\n'

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
    splitSentences(complete).forEach(speakText)
  }
}

/** 流结束：把缓冲区里的残句读掉 */
export function finishSpeech() {
  if (voiceBuffer.trim()) {
    speakText(voiceBuffer)
  }
  voiceBuffer = ''
}

export function stopSpeech() {
  voiceBuffer = ''
  synth()?.cancel()
  speaking.value = false
}

/** 朗读一条完整消息（切句排队，保证响应及时） */
export function speakFull(text: string) {
  stopSpeech()
  splitSentences(text).forEach(speakText)
}

/** 中文音色列表（设置面板用） */
export function zhVoices(): SpeechSynthesisVoice[] {
  if (!voices.length) loadVoices()
  return voices.filter((v) => v.lang.toLowerCase().startsWith('zh'))
}
