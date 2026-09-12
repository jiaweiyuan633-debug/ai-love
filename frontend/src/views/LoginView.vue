<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, register, requestPasswordReset, confirmPasswordReset } from '../api'
import { auth, setSession } from '../stores/auth'

const router = useRouter()
const mode = ref<'login' | 'register' | 'reset'>('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const info = ref('')
const loading = ref(false)
// 找回密码
const resetEmail = ref('')
const resetCode = ref('')
const newPassword = ref('')
const countdown = ref(0)
let timer: number | undefined

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    if (mode.value === 'reset') {
      const message = await confirmPasswordReset(
        username.value.trim(),
        resetEmail.value.trim(),
        resetCode.value.trim(),
        newPassword.value,
      )
      info.value = message
      mode.value = 'login'
      password.value = ''
      return
    }
    const result =
      mode.value === 'login'
        ? await login(username.value.trim(), password.value)
        : await register(username.value.trim(), password.value, nickname.value.trim() || undefined)
    setSession(result.token, result.user)
    router.replace('/')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '请求失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  error.value = ''
  info.value = ''
}

function startReset() {
  mode.value = 'reset'
  error.value = ''
  info.value = ''
}

function backToLogin() {
  mode.value = 'login'
  error.value = ''
  info.value = ''
}

async function getCode() {
  if (countdown.value > 0) return
  error.value = ''
  info.value = ''
  if (!username.value.trim() || !resetEmail.value.trim()) {
    error.value = '请先填写用户名和绑定的邮箱'
    return
  }
  try {
    info.value = await requestPasswordReset(username.value.trim(), resetEmail.value.trim())
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="logo">💘</span>
        <h1>AI 恋爱大师</h1>
        <p class="slogan">懂你的恋爱顾问，记得你说过的每句话</p>
      </div>

      <div v-if="mode !== 'reset'" class="mode-tabs">
        <button :class="{ active: mode === 'login' }" @click="switchMode">登录</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode">注册</button>
      </div>
      <p v-else class="reset-title">找回密码</p>

      <form @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="username" autocomplete="username" placeholder="字母 / 数字 / 下划线 / 中文" />
        </label>

        <template v-if="mode === 'reset'">
          <label class="field">
            <span>绑定的邮箱</span>
            <input v-model="resetEmail" type="email" autocomplete="email" placeholder="注册时绑定的邮箱" />
          </label>
          <label class="field">
            <span>验证码</span>
            <div class="code-line">
              <input v-model="resetCode" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
              <button type="button" class="code-btn" :disabled="countdown > 0" @click="getCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </button>
            </div>
          </label>
          <label class="field">
            <span>新密码</span>
            <input
              v-model="newPassword"
              type="password"
              autocomplete="new-password"
              placeholder="至少 8 位，含字母和数字"
            />
          </label>
        </template>
        <template v-else>
          <label v-if="mode === 'register'" class="field">
            <span>昵称（可选）</span>
            <input v-model="nickname" autocomplete="nickname" placeholder="恋爱大师怎么称呼你？" />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              placeholder="至少 8 位，含字母和数字"
            />
          </label>
          <p v-if="mode === 'login' && auth.passwordResetEnabled" class="forgot">
            <a @click="startReset">忘记密码？</a>
          </p>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="info" class="info">{{ info }}</p>

        <button
          class="submit"
          type="submit"
          :disabled="loading
            || !username.trim()
            || (mode === 'reset'
              ? !resetEmail.trim() || !resetCode.trim() || !newPassword
              : !password)"
        >
          {{ loading ? '请稍候…' : mode === 'login' ? '登录' : mode === 'register' ? '注册并登录' : '重置密码' }}
        </button>
      </form>

      <p v-if="mode !== 'reset'" class="tip">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <a @click="switchMode">{{ mode === 'login' ? '立即注册' : '去登录' }}</a>
      </p>
      <p v-else class="tip">
        想起密码了？<a @click="backToLogin">返回登录</a>
      </p>

      <p class="legal">
        {{ mode === 'register' ? '注册即代表同意' : '继续使用即代表同意' }}
        <router-link to="/agreement" target="_blank">《用户服务协议》</router-link>
        和
        <router-link to="/privacy" target="_blank">《隐私政策》</router-link>
      </p>

      <p class="ai-notice">内容由 AI 生成 · 仅供陪伴与参考 · 不能替代真实人际关系与专业帮助</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(800px 400px at 20% 0%, rgba(255, 107, 157, 0.18), transparent),
    radial-gradient(800px 500px at 90% 100%, rgba(167, 107, 255, 0.2), transparent),
    var(--bg);
  padding: 24px;
}
.login-card {
  width: 400px;
  max-width: 100%;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 36px 32px 24px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
}
.brand {
  text-align: center;
  margin-bottom: 24px;
}
.logo {
  font-size: 40px;
  display: block;
}
.brand h1 {
  margin: 8px 0 4px;
  font-size: 22px;
  color: var(--text);
}
.slogan {
  margin: 0;
  font-size: 13px;
  color: var(--text-4);
}
.mode-tabs {
  display: flex;
  background: var(--bg);
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 20px;
}
.mode-tabs button {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-4);
  padding: 8px 0;
  border-radius: 9px;
  cursor: pointer;
  font-size: 14px;
}
.mode-tabs button.active {
  background: var(--accent-grad);
  color: #fff;
  font-weight: 600;
}
.field {
  display: block;
  margin-bottom: 14px;
}
.field span {
  display: block;
  font-size: 13px;
  color: var(--text-3);
  margin-bottom: 6px;
}
.field input {
  width: 100%;
  background: var(--bg);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  color: var(--text);
  padding: 10px 12px;
  font-size: 14px;
  outline: none;
}
.field input:focus {
  border-color: var(--a2);
}
.error {
  color: var(--danger-text);
  font-size: 13px;
  margin: 4px 0 8px;
}
.info {
  color: var(--a2);
  font-size: 13px;
  margin: 4px 0 8px;
}
.reset-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  margin: 0 0 16px;
}
.forgot {
  text-align: right;
  font-size: 12px;
  margin: -4px 0 8px;
}
.forgot a {
  color: var(--text-4);
  cursor: pointer;
}
.forgot a:hover {
  color: var(--a1);
}
.code-line {
  display: flex;
  gap: 8px;
}
.code-line input {
  flex: 1;
  background: var(--bg);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  color: var(--text);
  padding: 10px 12px;
  font-size: 14px;
  outline: none;
}
.code-btn {
  border: 1px solid var(--border-strong);
  background: var(--bg-card);
  color: var(--text-2);
  border-radius: 10px;
  padding: 0 12px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.code-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.submit {
  width: 100%;
  border: none;
  border-radius: 10px;
  padding: 11px 0;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--accent-grad);
  cursor: pointer;
  margin-top: 6px;
}
.submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.tip {
  text-align: center;
  font-size: 13px;
  color: var(--text-4);
  margin: 16px 0 0;
}
.tip a {
  color: var(--danger-text);
  cursor: pointer;
}
.legal {
  text-align: center;
  font-size: 12px;
  color: var(--text-5);
  margin: 12px 0 0;
  line-height: 1.6;
}
.legal a {
  color: var(--text-4);
  text-decoration: none;
}
.legal a:hover {
  color: var(--a1);
}
.ai-notice {
  text-align: center;
  font-size: 11px;
  color: var(--text-5);
  margin: 18px 0 0;
}
</style>
