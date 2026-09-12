<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'

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
    border: 1px solid #2b2b3a;
    background: #17172a;
    color: #c8c8e0;
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
  background: #101020;
}
</style>
