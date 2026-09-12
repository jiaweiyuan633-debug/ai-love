import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import './style.css'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import YuManusView from './views/YuManusView.vue'
import LoginView from './views/LoginView.vue'
import { auth } from './stores/auth'
import { fetchAuthStatus, fetchMe } from './api'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'chat', component: ChatView },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView },
    { path: '/yumanus', name: 'yumanus', component: YuManusView },
    { path: '/login', name: 'login', component: LoginView },
  ],
})

// 登录守卫：先探测后端是否启用用户系统（体验模式直接放行），再校验登录态
router.beforeEach(async (to) => {
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
