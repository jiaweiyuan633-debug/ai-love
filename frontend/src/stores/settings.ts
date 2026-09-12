import { reactive, watch } from 'vue'

export interface AppSettings {
  // 外观
  theme: 'light' | 'dark' | 'auto'
  accent: string
  // 语音朗读
  voiceEnabled: boolean
  autoRead: boolean
  /** 朗读引擎：'' = 浏览器内置（免费）；其余为后端高拟真人设 id（yujie/xiaomei/ceo/nanda/zhonger/jiejie） */
  voicePersona: string
  voiceURI: string
  voiceRate: number
  // 悬浮窗
  floatBall: boolean
  // 对话模型
  model: 'qwen-plus' | 'qwen-turbo' | 'qwen-max'
}

const KEY = 'ailove:settings'

const defaults: AppSettings = {
  theme: 'dark',
  accent: 'rose',
  voiceEnabled: false,
  autoRead: true,
  voicePersona: 'jiejie',
  voiceURI: '',
  voiceRate: 1,
  floatBall: true,
  model: 'qwen-plus',
}

function load(): AppSettings {
  try {
    return { ...defaults, ...(JSON.parse(localStorage.getItem(KEY) || '{}') as Partial<AppSettings>) }
  } catch {
    return { ...defaults }
  }
}

export const settings = reactive<AppSettings>(load())

// 任意设置变化自动持久化
watch(settings, () => {
  localStorage.setItem(KEY, JSON.stringify(settings))
})

/** 把主题应用到 <html> 的 data-theme / data-accent（全站 CSS 变量随之切换） */
export function applyTheme() {
  const el = document.documentElement
  const prefersLight =
    typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: light)').matches
  const theme = settings.theme === 'auto' ? (prefersLight ? 'light' : 'dark') : settings.theme
  el.dataset.theme = theme
  el.dataset.accent = settings.accent
}

// 跟随系统时监听系统主题变化
if (typeof window !== 'undefined') {
  window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => {
    if (settings.theme === 'auto') applyTheme()
  })
}

watch([() => settings.theme, () => settings.accent], applyTheme, { immediate: true })
