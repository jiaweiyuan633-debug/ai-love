<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, register } from '../api'
import { setSession } from '../stores/auth'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
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

      <div class="mode-tabs">
        <button :class="{ active: mode === 'login' }" @click="switchMode">登录</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode">注册</button>
      </div>

      <form @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="username" autocomplete="username" placeholder="字母 / 数字 / 下划线 / 中文" />
        </label>
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
            placeholder="至少 6 位"
          />
        </label>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="submit" type="submit" :disabled="loading || !username.trim() || !password">
          {{ loading ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p class="tip">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <a @click="switchMode">{{ mode === 'login' ? '立即注册' : '去登录' }}</a>
      </p>
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
</style>
