import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import './style.css'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import MomentsView from './views/MomentsView.vue'
import YuManusView from './views/YuManusView.vue'
import LoginView from './views/LoginView.vue'
import AgreementView from './views/AgreementView.vue'
import { auth } from './stores/auth'
import { fetchAuthStatus, fetchMe } from './api'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'chat', component: ChatView },
    { path: '/moments', name: 'moments', component: MomentsView },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView },
    { path: '/yumanus', name: 'yumanus', component: YuManusView },
    { path: '/login', name: 'login', component: LoginView },
    // 法律文档页：未登录也要能从登录页访问
    { path: '/agreement', name: 'agreement', component: AgreementView, meta: { public: true } },
    { path: '/privacy', name: 'privacy', component: AgreementView, meta: { public: true } },
  ],
})

// 登录守卫：先探测后端是否启用用户系统（体验模式直接放行），再校验登录态
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (auth.enabled === null) {
    auth.enabled = await fetchAuthStatus().catch(() => false)
    if (auth.enabled && auth.token) {
      try {
        auth.user = await fetchMe()
      } catch {
        // token 失效：留在守卫逻辑里走登录页
      }
    }
  }
  if (to.name === 'login') {
    return auth.enabled && !auth.user ? true : { name: 'chat' }
  }
  if (auth.enabled && !auth.user) {
    return { name: 'login' }
  }
  return true
})

createApp(App).use(router).mount('#app')

// PWA：仅生产构建注册 Service Worker（开发环境避免干扰 HMR；Tauri 原生壳内不适用）
const isTauri = '__TAURI_INTERNALS__' in window || '__TAURI__' in window
if (import.meta.env.PROD && !isTauri && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // 注册失败不影响功能
    })
  })
}
