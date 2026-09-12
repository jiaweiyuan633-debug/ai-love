import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
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
