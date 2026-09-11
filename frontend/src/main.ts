import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import './style.css'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import YuManusView from './views/YuManusView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'chat', component: ChatView },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView },
    { path: '/yumanus', name: 'yumanus', component: YuManusView },
  ],
})

createApp(App).use(router).mount('#app')
