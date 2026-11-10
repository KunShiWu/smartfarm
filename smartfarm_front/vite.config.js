import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      // 后端接口代理:后端就绪后取消注释并修改 target
      // '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
