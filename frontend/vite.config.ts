import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/** 本地开发时将 API 请求转发给 Spring Boot，保持前端同源认证行为。 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
