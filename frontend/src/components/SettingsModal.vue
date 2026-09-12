<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { settings } from '../stores/settings'
import { ui } from '../stores/ui'
import { auth, clearSession } from '../stores/auth'
import {
  bindCouple,
  confirmBindEmail,
  createMembershipOrder,
  deleteAccount,
  deleteMemoryItem,
  fetchAchievements,
  fetchMembership,
  fetchMembershipPlans,
  generateCoupleCode,
  getCoupleStatus,
  listMemory,
  patchMe,
  clearMemory,
  payMembershipOrder,
  requestBindEmail,
  setAnniversary,
  unbindCouple,
  type Achievement,
  type CoupleStatus,
  type MembershipPlan,
  type MembershipStatus,
  type MemoryItem,
} from '../api'
import { zhVoices, speakFull } from '../composables/useSpeech'

/** 人设音色（与后端 TtsService 保持一致） */
const personas = [
  { id: 'yujie', emoji: '🌙', label: '温柔御姐', description: '低柔从容 · 成熟魅力' },
  { id: 'xiaomei', emoji: '🍬', label: '邻家小妹', description: '甜美俏皮 · 元气满满' },
  { id: 'ceo', emoji: '🧊', label: '高冷总裁', description: '磁性低沉 · 冷静克制' },
  { id: 'nanda', emoji: '🎓', label: '清纯男大', description: '干净清爽 · 真诚少年' },
  { id: 'zhonger', emoji: '⚡', label: '中二少年', description: '热血中二 · 戏剧张力' },
  { id: 'jiejie', emoji: '☕', label: '知心姐姐', description: '温暖亲切 · 治愈抚慰' },
]

function preview(personaId: string) {
  settings.voicePersona = personaId
  speakFull('你好呀，我是你的专属恋爱顾问，很高兴遇见你。')
}

const router = useRouter()
const emit = defineEmits<{ close: [] }>()

const tab = ref<'appearance' | 'voice' | 'ball' | 'couple' | 'account' | 'achievements' | 'member'>('appearance')

const themes = [
  { value: 'light', label: '☀️ 浅色' },
  { value: 'dark', label: '🌙 深色' },
  { value: 'auto', label: '🖥️ 跟随系统' },
] as const

const accents = [
  { value: 'rose', label: '玫瑰粉', color: '#ff6b9d' },
  { value: 'ocean', label: '海洋蓝', color: '#4e8cff' },
  { value: 'mint', label: '薄荷绿', color: '#22b8a0' },
  { value: 'sunset', label: '日落橙', color: '#ff8a5c' },
  { value: 'violet', label: '星空紫', color: '#8a5cff' },
]

const models = [
  { value: 'qwen-plus', label: '均衡 · qwen-plus' },
  { value: 'qwen-turbo', label: '极速 · qwen-turbo' },
  { value: 'qwen-max', label: '最强 · qwen-max' },
] as const

const voices = ref<SpeechSynthesisVoice[]>([])
onMounted(() => {
  voices.value = zhVoices()
  void refreshMemory()
  if (auth.user) {
    void refreshCouple()
    void refreshAchievements()
    void refreshMembership()
  }
})

// ---------- 成就徽章 ----------
const achievements = ref<Achievement[]>([])
async function refreshAchievements() {
  if (!auth.enabled) return
  try {
    achievements.value = await fetchAchievements()
  } catch {
    // 成就加载失败不影响其它设置
  }
}

// ---------- 情侣绑定 ----------
const couple = ref<CoupleStatus | null>(null)
const myCode = ref('')
const partnerCodeInput = ref('')
const anniversaryInput = ref('')
const coupleError = ref('')
const codeCopied = ref(false)

async function refreshCouple() {
  coupleError.value = ''
  try {
    couple.value = await getCoupleStatus()
    if (couple.value.pending && couple.value.code) myCode.value = couple.value.code
    if (couple.value.anniversaryDate) anniversaryInput.value = couple.value.anniversaryDate
  } catch {
    // 静默
  }
}

async function makeCode() {
  coupleError.value = ''
  try {
    myCode.value = await generateCoupleCode()
    couple.value = await getCoupleStatus()
  } catch (e) {
    coupleError.value = e instanceof Error ? e.message : '生成失败'
  }
}

async function doBind() {
  coupleError.value = ''
  try {
    couple.value = await bindCouple(partnerCodeInput.value)
    partnerCodeInput.value = ''
  } catch (e) {
    coupleError.value = e instanceof Error ? e.message : '绑定失败'
  }
}

async function saveAnniversary() {
  coupleError.value = ''
  if (!anniversaryInput.value) return
  try {
    couple.value = await setAnniversary(anniversaryInput.value)
  } catch (e) {
    coupleError.value = e instanceof Error ? e.message : '保存失败'
  }
}

async function doUnbind() {
  if (!window.confirm('确定解除情侣绑定吗？双方的共享记忆注入会立即停止（各自记忆保留）。')) return
  coupleError.value = ''
  try {
    await unbindCouple()
    couple.value = await getCoupleStatus()
    myCode.value = ''
  } catch (e) {
    coupleError.value = e instanceof Error ? e.message : '解绑失败'
  }
}

async function copyCode() {
  if (!myCode.value) return
  try {
    await navigator.clipboard.writeText(myCode.value)
    codeCopied.value = true
    setTimeout(() => (codeCopied.value = false), 1200)
  } catch {
    // 剪贴板不可用时忽略
  }
}

// ---------- 记忆 ----------
const memoryItems = ref<MemoryItem[]>([])
const memoryLoading = ref(false)

// ---------- 会员订阅（模拟支付） ----------
const membership = ref<MembershipStatus | null>(null)
const plans = ref<MembershipPlan[]>([])
const payingPlan = ref('')
const memberError = ref('')

async function refreshMembership() {
  if (!auth.enabled || !auth.user) return
  try {
    membership.value = await fetchMembership()
    if (plans.value.length === 0) {
      plans.value = await fetchMembershipPlans()
    }
  } catch {
    memberError.value = '会员服务暂不可用'
  }
}

async function buy(planId: string) {
  if (payingPlan.value) return
  payingPlan.value = planId
  memberError.value = ''
  try {
    const order = await createMembershipOrder(planId)
    membership.value = await payMembershipOrder(order.id, order.priceFen)
  } catch (err) {
    memberError.value = err instanceof Error ? err.message : '支付失败，请稍后再试'
  } finally {
    payingPlan.value = ''
  }
}

function fmtVipUntil(iso: string | null): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

async function refreshMemory() {
  if (!auth.user) return
  memoryLoading.value = true
  try {
    const data = await listMemory()
    memoryItems.value = data.items
  } catch {
    // 静默
  } finally {
    memoryLoading.value = false
  }
}

async function removeMemory(id: number) {
  try {
    await deleteMemoryItem(id)
    memoryItems.value = memoryItems.value.filter((m) => m.id !== id)
  } catch {
    // 静默
  }
}

async function removeAllMemory() {
  if (!window.confirm('确定清空全部长期记忆吗？')) return
  try {
    await clearMemory()
    memoryItems.value = []
  } catch {
    // 静默
  }
}

async function toggleMemoryEnabled() {
  if (!auth.user) return
  try {
    auth.user = await patchMe({ memoryEnabled: !auth.user.memoryEnabled })
  } catch {
    // 静默
  }
}

// ---------- 账号 ----------
const nicknameInput = ref(auth.user?.nickname || '')
const nicknameSaved = ref(false)
async function saveNickname() {
  const nickname = nicknameInput.value.trim()
  if (!nickname || !auth.user) return
  try {
    auth.user = await patchMe({ nickname })
    nicknameSaved.value = true
    setTimeout(() => (nicknameSaved.value = false), 1500)
  } catch {
    // 静默
  }
}

function logout() {
  clearSession()
  ui.settingsOpen = false
  router.replace('/login')
}

// ---------- 邮箱绑定（找回密码通道） ----------
const emailInput = ref('')
const emailCode = ref('')
const emailMsg = ref('')
const emailCountdown = ref(0)
const emailBound = ref(false)
let emailTimer: number | undefined

async function sendBindCode() {
  if (emailCountdown.value > 0) return
  emailMsg.value = ''
  if (!emailInput.value.trim()) {
    emailMsg.value = '请先填写邮箱地址'
    return
  }
  try {
    emailMsg.value = await requestBindEmail(emailInput.value.trim())
    emailCountdown.value = 60
    emailTimer = window.setInterval(() => {
      emailCountdown.value--
      if (emailCountdown.value <= 0) window.clearInterval(emailTimer)
    }, 1000)
  } catch (err) {
    emailMsg.value = err instanceof Error ? err.message : '验证码发送失败'
  }
}

async function bindEmail() {
  emailMsg.value = ''
  if (!emailInput.value.trim() || !emailCode.value.trim()) {
    emailMsg.value = '请填写邮箱与验证码'
    return
  }
  try {
    auth.user = await confirmBindEmail(emailInput.value.trim(), emailCode.value.trim())
    emailBound.value = true
    emailMsg.value = '绑定成功，忘记密码时可用该邮箱自助找回'
    setTimeout(() => (emailBound.value = false), 2000)
  } catch (err) {
    emailMsg.value = err instanceof Error ? err.message : '绑定失败'
  }
}

// ---------- 注销账号 ----------
async function deleteMyAccount() {
  if (!auth.user) return
  const typed = window.prompt(
    `即将永久删除你的全部数据（会话、长期记忆、心情、朋友圈、会员），且无法恢复！\n请输入用户名「${auth.user.username}」确认:`,
  )
  if (typed !== auth.user.username) return
  try {
    await deleteAccount()
    clearSession()
    ui.settingsOpen = false
    router.replace('/')
  } catch (err) {
    window.alert(err instanceof Error ? err.message : '注销失败，请稍后再试')
  }
}

const isGuest = computed(() => !auth.user)
</script>

<template>
  <Teleport to="body">
    <div class="overlay" @click.self="emit('close')">
      <div class="modal">
        <header>
          <h2>⚙️ 设置</h2>
          <button class="close" @click="emit('close')">✕</button>
        </header>

        <nav class="tabs">
          <button :class="{ active: tab === 'appearance' }" @click="tab = 'appearance'">🎨 外观</button>
          <button :class="{ active: tab === 'voice' }" @click="tab = 'voice'">🔊 语音</button>
          <button :class="{ active: tab === 'ball' }" @click="tab = 'ball'">🫧 悬浮窗</button>
          <button :class="{ active: tab === 'couple' }" @click="tab = 'couple'">💕 情侣</button>
          <button :class="{ active: tab === 'achievements' }" @click="tab = 'achievements'">🏆 成就</button>
          <button :class="{ active: tab === 'account' }" @click="tab = 'account'">👤 记忆与账号</button>
          <button v-if="auth.enabled" :class="{ active: tab === 'member' }" @click="tab = 'member'">💎 会员</button>
        </nav>

        <div class="body">
          <!-- 外观 -->
          <section v-if="tab === 'appearance'" class="section">
            <div class="row-title">主题模式</div>
            <div class="segment">
              <button
                v-for="t in themes"
                :key="t.value"
                :class="{ active: settings.theme === t.value }"
                @click="settings.theme = t.value"
              >
                {{ t.label }}
              </button>
            </div>

            <div class="row-title">主题色</div>
            <div class="accent-row">
              <button
                v-for="a in accents"
                :key="a.value"
                class="accent-dot"
                :style="{ background: `linear-gradient(135deg, ${a.color}, ${a.color}bb)` }"
                :class="{ active: settings.accent === a.value }"
                :title="a.label"
                @click="settings.accent = a.value"
              ></button>
            </div>

            <div class="row-title">对话模型</div>
            <select v-model="settings.model" class="select">
              <option v-for="m in models" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
            <p class="hint">极速响应快、适合日常闲聊；最强回答质量高但稍慢、消耗更多额度。</p>
          </section>

          <!-- 语音 -->
          <section v-if="tab === 'voice'" class="section">
            <label class="switch-row">
              <div>
                <div class="row-title">语音朗读</div>
                <p class="hint">开启后工具栏出现语音开关，可自动朗读 AI 回复</p>
              </div>
              <span class="switch" :class="{ on: settings.voiceEnabled }" @click="settings.voiceEnabled = !settings.voiceEnabled">
                <span class="knob"></span>
              </span>
            </label>

            <label class="switch-row">
              <div>
                <div class="row-title">自动朗读新回答</div>
                <p class="hint">关闭后仍可点消息下方的「🔊 朗读」手动播放</p>
              </div>
              <span class="switch" :class="{ on: settings.autoRead }" @click="settings.autoRead = !settings.autoRead">
                <span class="knob"></span>
              </span>
            </label>

            <div class="row-title">朗读音色</div>
            <p class="hint">前 6 种为云端高拟真人声（按合成字数计费，费用极低）；浏览器默认免费但偏机械。</p>
            <div class="persona-grid">
              <div
                v-for="p in personas"
                :key="p.id"
                class="persona-card"
                :class="{ active: settings.voicePersona === p.id }"
                @click="settings.voicePersona = p.id"
              >
                <span class="p-emoji">{{ p.emoji }}</span>
                <span class="p-label">{{ p.label }}</span>
                <span class="p-desc">{{ p.description }}</span>
                <button class="p-play" title="试听" @click.stop="preview(p.id)">▶</button>
              </div>
              <div
                class="persona-card"
                :class="{ active: settings.voicePersona === '' }"
                @click="settings.voicePersona = ''"
              >
                <span class="p-emoji">🖥️</span>
                <span class="p-label">浏览器默认</span>
                <span class="p-desc">免费 · 音质一般</span>
                <button class="p-play" title="试听" @click.stop="preview('')">▶</button>
              </div>
            </div>

            <template v-if="settings.voicePersona === ''">
              <div class="row-title">浏览器音色</div>
              <select v-model="settings.voiceURI" class="select">
                <option value="">默认（跟随浏览器）</option>
                <option v-for="v in voices" :key="v.voiceURI" :value="v.voiceURI">
                  {{ v.name }}（{{ v.lang }}）
                </option>
              </select>
            </template>

            <div class="row-title">语速（{{ settings.voiceRate.toFixed(2) }}x）</div>
            <input v-model.number="settings.voiceRate" type="range" min="0.5" max="2" step="0.25" class="range" />
          </section>

          <!-- 悬浮窗 -->
          <section v-if="tab === 'ball'" class="section">
            <label class="switch-row">
              <div>
                <div class="row-title">显示悬浮球</div>
                <p class="hint">可拖动的💘悬浮球，点击展开快捷操作（新对话 / 朗读 / 设置）</p>
              </div>
              <span class="switch" :class="{ on: settings.floatBall }" @click="settings.floatBall = !settings.floatBall">
                <span class="knob"></span>
              </span>
            </label>
          </section>

          <!-- 情侣 -->
          <section v-if="tab === 'couple'" class="section">
            <template v-if="!isGuest">
              <p class="hint">绑定后 AI 会同时记得两个人的信息：你们共享彼此的长期记忆，还能设置恋爱纪念日，临近时聊天会收到贴心提醒。</p>

              <template v-if="couple?.bound">
                <div class="couple-card">
                  <div class="couple-emoji">💕</div>
                  <div>
                    <div class="row-title">已与「{{ couple.partnerNickname }}」绑定</div>
                    <p v-if="couple.daysTogether != null" class="days">
                      在一起 {{ couple.daysTogether }} 天 ❤️
                    </p>
                  </div>
                </div>

                <div class="row-title">恋爱纪念日</div>
                <div class="nickname-row">
                  <input v-model="anniversaryInput" type="date" class="select" />
                  <button class="mini-btn" @click="saveAnniversary">保存</button>
                </div>
                <p class="hint">纪念日当天与前几天，聊天页顶部会出现提醒。</p>

                <button class="danger-btn" @click="doUnbind">💔 解除绑定</button>
              </template>

              <template v-else-if="couple?.pending || myCode">
                <div class="row-title">我的绑定码</div>
                <div class="code-box">
                  <span class="code">{{ myCode || '……' }}</span>
                  <button class="mini-btn" @click="copyCode">{{ codeCopied ? '✅ 已复制' : '复制' }}</button>
                </div>
                <p class="hint">把绑定码发给你的另一半，TA 在「设置 → 💕 情侣」输入即可完成绑定。</p>
                <div class="row-title">或输入对方的绑定码</div>
                <div class="nickname-row">
                  <input
                    v-model="partnerCodeInput"
                    class="select"
                    placeholder="如 7KX2M9AB"
                    maxlength="12"
                    style="text-transform: uppercase"
                  />
                  <button class="mini-btn" :disabled="!partnerCodeInput.trim()" @click="doBind">绑定</button>
                </div>
              </template>

              <template v-else>
                <button class="couple-start" @click="makeCode">💕 生成我的绑定码</button>
              </template>

              <p v-if="coupleError" class="error-line">{{ coupleError }}</p>
            </template>
            <div v-else class="hint">体验模式下暂无情侣绑定功能。</div>
          </section>

          <!-- 我的成就 -->
          <section v-if="tab === 'achievements'" class="section">
            <template v-if="!isGuest">
              <div class="ach-grid">
                <div
                  v-for="a in achievements"
                  :key="a.id"
                  class="ach-card"
                  :class="{ unlocked: a.unlocked }"
                >
                  <div class="ach-emoji">{{ a.unlocked ? a.emoji : '🔒' }}</div>
                  <div class="ach-name">{{ a.name }}</div>
                  <div class="ach-desc">{{ a.description }}</div>
                  <div class="ach-progress-bar">
                    <div class="ach-progress" :style="{ width: (a.progress * 100).toFixed(0) + '%' }"></div>
                  </div>
                  <div class="ach-progress-text">{{ a.progressText }}</div>
                </div>
              </div>
            </template>
            <p v-else class="hint">登录后开启成就之旅～</p>
          </section>

          <!-- 记忆与账号 -->
          <section v-if="tab === 'account'" class="section">
            <template v-if="!isGuest">
              <label class="switch-row">
                <div>
                  <div class="row-title">长期记忆</div>
                  <p class="hint">开启后 AI 会自动记住你的喜好与重要信息，并在对话中运用</p>
                </div>
                <span
                  class="switch"
                  :class="{ on: auth.user?.memoryEnabled }"
                  @click="toggleMemoryEnabled"
                >
                  <span class="knob"></span>
                </span>
              </label>

              <div class="row-title">
                TA 记住了什么（{{ memoryItems.length }} 条）
                <button v-if="memoryItems.length" class="link-danger" @click="removeAllMemory">清空</button>
              </div>
              <div class="memory-list">
                <div v-if="memoryLoading" class="hint">加载中…</div>
                <div v-else-if="memoryItems.length === 0" class="hint">还没有记忆。多聊聊，AI 会自动记住关键信息。</div>
                <div v-for="m in memoryItems" :key="m.id" class="memory-item">
                  <span>{{ m.content }}</span>
                  <button title="删除" @click="removeMemory(m.id)">🗑️</button>
                </div>
              </div>

              <div class="row-title">昵称</div>
              <div class="nickname-row">
                <input v-model="nicknameInput" class="select" maxlength="32" />
                <button class="mini-btn" @click="saveNickname">{{ nicknameSaved ? '✅ 已保存' : '保存' }}</button>
              </div>

              <div class="row-title">
                常用邮箱（找回密码用）
                <span class="email-state">{{ auth.user?.email ? '已绑定 ' + auth.user?.email : '未绑定' }}</span>
              </div>
              <div class="nickname-row">
                <input v-model="emailInput" class="select" type="email" placeholder="you@example.com" />
                <button class="mini-btn" :disabled="emailCountdown > 0" @click="sendBindCode">
                  {{ emailCountdown > 0 ? emailCountdown + 's' : '获取验证码' }}
                </button>
              </div>
              <div class="nickname-row">
                <input v-model="emailCode" class="select" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
                <button class="mini-btn" @click="bindEmail">{{ emailBound ? '✅ 已绑定' : '绑定' }}</button>
              </div>
              <p v-if="emailMsg" class="hint">{{ emailMsg }}</p>

              <button class="danger-btn" @click="logout">⏻ 退出登录</button>
              <button class="danger-btn delete-account" @click="deleteMyAccount">🗑️ 注销账号（永久删除全部数据）</button>
            </template>
            <div v-else class="hint">体验模式下暂无账号与记忆功能。</div>
            <div class="legal-links">
              <a href="/agreement" target="_blank">用户服务协议</a>
              <span>·</span>
              <a href="/privacy" target="_blank">隐私政策</a>
            </div>
          </section>

          <!-- 会员 -->
          <section v-if="tab === 'member'" class="section">
            <template v-if="isGuest">
              <p class="hint">登录后即可开通会员。</p>
            </template>
            <template v-else>
              <div class="member-status" :class="{ vip: membership?.vip }">
                <template v-if="membership?.vip">
                  <div class="member-title">💎 VIP 会员</div>
                  <p class="hint">有效期至 {{ fmtVipUntil(membership.vipUntil) }} · 畅聊无限次 · AI 限流额度翻倍</p>
                </template>
                <template v-else>
                  <div class="member-title">免费版</div>
                  <p class="hint">
                    今日免费对话 {{ membership?.dailyUsed ?? 0 }}/{{ membership?.dailyLimit ?? 20 }} 条，升级 VIP 畅聊无限次
                  </p>
                </template>
              </div>

              <div class="plan-grid">
                <button
                  v-for="p in plans"
                  :key="p.id"
                  class="plan-card"
                  :disabled="payingPlan === p.id"
                  @click="buy(p.id)"
                >
                  <span class="plan-label">{{ p.label }}</span>
                  <span class="plan-price">¥{{ (p.priceFen / 100).toFixed(0) }}</span>
                  <span class="plan-days">{{ p.days }} 天有效</span>
                  <span class="plan-cta">{{ payingPlan === p.id ? '支付中…' : '立即开通（模拟支付）' }}</span>
                </button>
              </div>
              <p v-if="memberError" class="member-error">{{ memberError }}</p>
              <p class="hint">支付为演示用的模拟回调，未接入真实支付渠道；会员权益仅在本应用内生效。</p>
            </template>
          </section>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.modal {
  width: 460px;
  max-width: 100%;
  max-height: 86vh;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 18px;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 10px;
}
header h2 {
  margin: 0;
  font-size: 17px;
  color: var(--text);
}
.close {
  border: none;
  background: transparent;
  color: var(--text-4);
  font-size: 15px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
}
.close:hover {
  background: var(--hover);
  color: var(--text);
}
.tabs {
  display: flex;
  gap: 4px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border);
}
.tabs button {
  border: none;
  background: transparent;
  color: var(--text-4);
  padding: 9px 12px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 8px 8px 0 0;
  border-bottom: 2px solid transparent;
}
.tabs button.active {
  color: var(--a1);
  border-bottom-color: var(--a1);
}
.body {
  padding: 18px 20px 22px;
  overflow-y: auto;
}
.section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ach-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
  margin-top: 10px;
}
.ach-card {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
  opacity: 0.62;
}
.ach-card.unlocked {
  opacity: 1;
  border-color: rgba(255, 107, 157, 0.5);
  background: linear-gradient(160deg, rgba(255, 107, 157, 0.1), rgba(167, 107, 255, 0.1));
}
.ach-emoji {
  font-size: 26px;
}
.ach-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.ach-desc {
  font-size: 11px;
  color: var(--text-4);
  min-height: 15px;
}
.ach-progress-bar {
  width: 100%;
  height: 4px;
  border-radius: 999px;
  background: var(--hover);
  overflow: hidden;
  margin-top: 4px;
}
.ach-progress {
  height: 100%;
  border-radius: 999px;
  background: var(--accent-grad);
  transition: width 0.3s;
}
.ach-progress-text {
  font-size: 11px;
  color: var(--text-4);
}
.row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-top: 12px;
}
.row-title:first-child {
  margin-top: 0;
}
.hint {
  font-size: 12px;
  color: var(--text-5);
  margin: 2px 0 8px;
  line-height: 1.6;
}
.segment {
  display: flex;
  background: var(--bg-input);
  border-radius: 10px;
  padding: 4px;
  gap: 4px;
}
.segment button {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-4);
  padding: 8px 0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.segment button.active {
  background: var(--accent-grad);
  color: #fff;
  font-weight: 600;
}
.accent-row {
  display: flex;
  gap: 12px;
  padding: 6px 0 2px;
}
.accent-dot {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  outline: 2px solid transparent;
  outline-offset: 2px;
}
.accent-dot.active {
  outline-color: var(--text-3);
}
.select {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  color: var(--text);
  padding: 9px 12px;
  font-size: 13px;
  outline: none;
}
.range {
  width: 100%;
  accent-color: var(--a1);
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
}
.switch {
  width: 40px;
  height: 22px;
  border-radius: 999px;
  background: var(--hover);
  border: 1px solid var(--border-strong);
  position: relative;
  transition: all 0.2s;
  flex-shrink: 0;
}
.switch .knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--text-4);
  transition: all 0.2s;
}
.switch.on {
  background: var(--accent-grad);
  border-color: transparent;
}
.switch.on .knob {
  left: 20px;
  background: #fff;
}
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}
.memory-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-2);
}
.memory-item button {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  opacity: 0.7;
}
.memory-item button:hover {
  opacity: 1;
}
.nickname-row {
  display: flex;
  gap: 8px;
}
.mini-btn {
  border: none;
  background: var(--accent-grad);
  color: #fff;
  border-radius: 10px;
  padding: 0 18px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.link-danger {
  border: none;
  background: transparent;
  color: var(--danger-text);
  font-size: 12px;
  cursor: pointer;
}
.danger-btn {
  margin-top: 18px;
  border: 1px solid var(--danger-text);
  background: transparent;
  color: var(--danger-text);
  border-radius: 10px;
  padding: 10px 0;
  font-size: 13px;
  cursor: pointer;
}
.danger-btn:hover {
  background: var(--danger-bg);
}
.legal-links {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  font-size: 12px;
  color: var(--text-4);
}
.legal-links a {
  color: var(--text-3);
  text-decoration: none;
  border-bottom: 1px dashed var(--border-strong);
  cursor: pointer;
}
.legal-links a:hover {
  color: var(--a1);
}

/* 手机端：弹窗改为贴底面板，标签行可横向滚动 */
@media (max-width: 500px) {
  .overlay {
    padding: 0;
    align-items: flex-end;
  }
  .modal {
    width: 100%;
    max-height: 92dvh;
    border-radius: 18px 18px 0 0;
    border-bottom: none;
    padding-bottom: env(safe-area-inset-bottom);
  }
  .tabs {
    overflow-x: auto;
    scrollbar-width: none;
  }
  .tabs::-webkit-scrollbar {
    display: none;
  }
  .ach-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.couple-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px 16px;
}
.couple-emoji {
  font-size: 30px;
}
.days {
  font-size: 13px;
  color: var(--a1);
  margin: 2px 0 0;
}
.code-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--bg-card);
  border: 1px dashed var(--a2);
  border-radius: 12px;
  padding: 12px 14px;
}
.code {
  flex: 1;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--text);
  font-family: Consolas, monospace;
}
.couple-start {
  border: 1px dashed var(--a2);
  background: transparent;
  color: var(--text);
  border-radius: 12px;
  padding: 14px 0;
  font-size: 14px;
  cursor: pointer;
}
.couple-start:hover {
  background: var(--hover);
}
.error-line {
  color: var(--danger-text);
  font-size: 12px;
  margin: 8px 0 0;
}
.persona-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.persona-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.persona-card:hover {
  border-color: var(--a2);
}
.persona-card.active {
  border-color: var(--a1);
  background: linear-gradient(135deg, rgba(255, 107, 157, 0.1), rgba(167, 107, 255, 0.1));
}
.p-emoji {
  font-size: 18px;
}
.p-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.p-desc {
  font-size: 11px;
  color: var(--text-5);
}
.p-play {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--border-strong);
  background: var(--bg-input);
  color: var(--text-3);
  font-size: 10px;
  cursor: pointer;
}
.p-play:hover {
  background: var(--accent-grad);
  color: #fff;
  border-color: transparent;
}

/* ---------- 会员 ---------- */
.member-status {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px 16px;
  background: var(--bg);
}
.member-status.vip {
  border-color: rgba(255, 190, 80, 0.45);
  background: linear-gradient(135deg, rgba(255, 200, 90, 0.12), rgba(255, 120, 160, 0.1));
}
.member-title {
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
}
.plan-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 12px;
}
.plan-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  border: 1px solid var(--border-strong);
  background: var(--bg-card);
  color: var(--text);
  border-radius: 14px;
  padding: 12px;
  cursor: pointer;
  text-align: left;
}
.plan-card:hover {
  border-color: var(--a1);
}
.plan-card:disabled {
  opacity: 0.6;
  cursor: wait;
}
.plan-label {
  font-size: 12px;
  color: var(--text-3);
}
.plan-price {
  font-size: 20px;
  font-weight: 700;
}
.plan-days {
  font-size: 11px;
  color: var(--text-4);
}
.plan-cta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--a1);
}
.member-error {
  color: var(--danger-text);
  font-size: 13px;
  margin: 8px 0 0;
}
.email-state {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-4);
  margin-left: 6px;
}
.delete-account {
  margin-top: 8px;
  opacity: 0.85;
}
.delete-account:hover {
  opacity: 1;
}
@media (max-width: 500px) {
  .plan-grid {
    grid-template-columns: 1fr;
  }
}
</style>
