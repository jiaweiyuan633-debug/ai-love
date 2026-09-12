import vue from '@vitejs/plugin-vue'
import { defineConfig, type Plugin } from 'vite'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * 发版缓存自动失效：构建完成后把 public/sw.js 里的 __SW_VERSION__
 * 替换为构建时间戳，浏览器检测到 SW 字节变化即更新缓存版本。
 */
function swVersionStamp(): Plugin {
  return {
    name: 'sw-version-stamp',
    apply: 'build',
    closeBundle() {
      const swPath = fileURLToPath(new URL('./dist/sw.js', import.meta.url))
      const stamped = readFileSync(swPath, 'utf8').replace(
        /__SW_VERSION__/g,
        Date.now().toString(36),
      )
      writeFileSync(swPath, stamped)
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), swVersionStamp()],
  server: {
    port: 5173,
    proxy: {
      '/ai': 'http://localhost:8101',
      '/knowledge': 'http://localhost:8101',
      '/api': 'http://localhost:8101',
      '/auth': 'http://localhost:8101',
    },
  },
})
