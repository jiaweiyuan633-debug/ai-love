<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import FloatingBall from './components/FloatingBall.vue'
import SettingsModal from './components/SettingsModal.vue'
import { settings } from './stores/settings'
import { ui } from './stores/ui'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
const sidebarOpen = ref(false)

// ---------- 健康使用提醒（连续使用每 2 小时弹一次，当日第二次起不再弹） ----------
const healthNotice = ref(false)
let healthTimer: ReturnType<typeof setTimeout> | null = null

const HEALTH_KEY = 'ailove:healthNotice'

function showHealthNotice() {
  const today = new Date().toISOString().slice(0, 10)
  try {
    const saved = JSON.parse(localStorage.getItem(HEALTH_KEY) || 'null')
    if (saved?.date === today) return // 今天已经提醒过
  } catch {
    // 忽略损坏数据
  }
  healthNotice.value = true
  localStorage.setItem(HEALTH_KEY, JSON.stringify({ date: today }))
}

function dismissHealthNotice() {
  healthNotice.value = false
}

onMounted(() => {
  healthTimer = setTimeout(showHealthNotice, 2 * 60 * 60 * 1000)
})

onBeforeUnmount(() => {
  if (healthTimer) clearTimeout(healthTimer)
})
</script>

<template>
  <router-view v-if="isLogin" />
  <div v-else class="app-shell">
    <!-- 移动端抽屉遮罩 -->
    <div v-if="sidebarOpen" class="drawer-mask" @click="sidebarOpen = false"></div>
    <div class="sidebar-wrap" :class="{ open: sidebarOpen }">
      <AppSidebar />
    </div>
    <button class="mobile-menu" title="菜单" @click="sidebarOpen = !sidebarOpen">☰</button>
    <main class="app-main">
      <router-view />
    </main>
    <FloatingBall v-if="settings.floatBall" />
    <SettingsModal v-if="ui.settingsOpen" @close="ui.settingsOpen = false" />
    <div v-if="healthNotice" class="health-mask" @click.self="dismissHealthNotice">
      <div class="health-dialog">
        <div class="health-emoji">🌿</div>
        <h3>你已经连续使用两小时啦</h3>
        <p>
          本应用内容由 AI 生成，仅供陪伴与参考，不能替代真实的人际关系与专业心理帮助。
          建议休息一下眼睛，和身边的人聊聊天，或出门走走 🚶
        </p>
        <button class="health-btn" @click="dismissHealthNotice">好的，我知道了</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}
.sidebar-wrap {
  height: 100%;
}
.drawer-mask {
  display: none;
}
.mobile-menu {
  display: none;
}
@media (max-width: 860px) {
  .sidebar-wrap {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 50;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
    box-shadow: 8px 0 24px rgba(0, 0, 0, 0.4);
  }
  .sidebar-wrap.open {
    transform: translateX(0);
  }
  .drawer-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 40;
    background: rgba(0, 0, 0, 0.5);
  }
  .mobile-menu {
    display: block;
    position: fixed;
    left: 10px;
    top: 10px;
    z-index: 30;
    border: 1px solid var(--border);
    background: var(--bg-soft);
    color: var(--text-2);
    border-radius: 8px;
    width: 34px;
    height: 34px;
    cursor: pointer;
  }
}
.app-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  background: var(--bg);
}
.health-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}
.health-dialog {
  width: min(400px, 88vw);
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 26px 24px;
  text-align: center;
}
.health-emoji {
  font-size: 40px;
}
.health-dialog h3 {
  color: var(--text);
  margin: 10px 0 8px;
}
.health-dialog p {
  color: var(--text-3);
  font-size: 13.5px;
  line-height: 1.8;
}
.health-btn {
  margin-top: 16px;
  border: none;
  border-radius: 10px;
  background: var(--accent-grad);
  color: #fff;
  padding: 9px 26px;
  font-size: 14px;
  cursor: pointer;
}
</style>
