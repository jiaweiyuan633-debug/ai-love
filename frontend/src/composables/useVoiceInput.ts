import { onUnmounted, ref } from 'vue'

const SR: any =
  typeof window !== 'undefined'
    ? (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    : null

/** 当前浏览器是否支持语音输入（Chrome/Edge/安卓支持，Firefox 不支持） */
export const voiceInputSupported = !!SR

export interface VoiceInputOptions {
  /** 一句识别完成（final 结果）时回调 */
  onFinal: (text: string) => void
  onError?: (message: string) => void
}

/**
 * 语音输入（点按开始/停止的连续听写）：基于 Web Speech API 的 SpeechRecognition。
 * - 连续听写：浏览器在静音后自动断句结束，这里静默重启保持连续；
 * - interim 结果实时上屏（作为占位提示），final 结果回调给调用方。
 */
export function useVoiceInput(options: VoiceInputOptions) {
  const listening = ref(false)
  /** 正在说的半句话（实时预览） */
  const interim = ref('')
  let recognition: any = null
  let stoppedManually = false
  let failed = false

  function start() {
    if (!SR || listening.value) return
    stoppedManually = false
    failed = false
    recognition = new SR()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true

    recognition.onresult = (event: any) => {
      let finalText = ''
      let interimText = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        if (result.isFinal) finalText += result[0].transcript
        else interimText += result[0].transcript
      }
      if (finalText.trim()) options.onFinal(finalText.trim())
      interim.value = interimText
    }

    recognition.onerror = (event: any) => {
      if (event.error === 'no-speech') return // 静默超时，交给 onend 重启
      failed = true
      const messages: Record<string, string> = {
        'not-allowed': '麦克风权限被拒绝，请在浏览器地址栏允许麦克风访问',
        'service-not-allowed': '浏览器不允许使用语音识别服务',
        network: '语音识别服务网络异常，请稍后重试',
        'audio-capture': '未检测到麦克风设备',
      }
      options.onError?.(messages[event.error] || '语音识别出错')
    }

    recognition.onend = () => {
      interim.value = ''
      if (stoppedManually || failed) {
        listening.value = false
        return
      }
      // 静默重启，保持连续听写
      try {
        recognition.start()
      } catch {
        listening.value = false
      }
    }

    recognition.start()
    listening.value = true
  }

  function stop() {
    stoppedManually = true
    try {
      recognition?.stop()
    } catch {
      // 忽略重复停止
    }
    listening.value = false
    interim.value = ''
  }

  function toggle() {
    if (listening.value) stop()
    else start()
  }

  onUnmounted(stop)

  return { listening, interim, start, stop, toggle }
}
