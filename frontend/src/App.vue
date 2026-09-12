<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import FloatingBall from './components/FloatingBall.vue'
import SettingsModal from './components/SettingsModal.vue'
import { settings } from './stores/settings'
import { ui } from './stores/ui'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
const sidebarOpen = ref(false)
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
</style>
