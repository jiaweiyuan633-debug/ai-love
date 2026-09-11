<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const tabs = [
  { name: 'chat', label: 'AI 恋爱大师', path: '/' },
  { name: 'yumanus', label: 'YuManus 智能体', path: '/yumanus' },
  { name: 'knowledge', label: '恋爱知识库', path: '/knowledge' },
]
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">💘 AI 恋爱大师</div>
      <nav class="nav">
        <router-link
          v-for="tab in tabs"
          :key="tab.name"
          :to="tab.path"
          class="nav-link"
          :class="{ active: route.name === tab.name }"
        >
          {{ tab.label }}
        </router-link>
      </nav>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.app-header {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 12px 24px;
  border-bottom: 1px solid #2b2b3a;
  background: #17172a;
  color: #f4f4fa;
}
.brand {
  font-size: 18px;
  font-weight: 700;
}
.nav {
  display: flex;
  gap: 8px;
}
.nav-link {
  padding: 6px 14px;
  border-radius: 999px;
  color: #b6b6cc;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.15s;
}
.nav-link:hover {
  color: #fff;
  background: #2b2b45;
}
.nav-link.active {
  color: #fff;
  background: linear-gradient(135deg, #ff6b9d, #a76bff);
}
.app-main {
  flex: 1;
  overflow: hidden;
  background: #101020;
}
</style>
