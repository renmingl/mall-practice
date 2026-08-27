import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 管理后台脚手架：dev 走 Vite 代理转发网关（/api → mall-gateway:8080），无跨域问题
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_URL || 'http://localhost:8080',
          changeOrigin: true
        },
        // 商品图片直连 mall-product（上传返回 /uploads/... 相对路径）
        '/uploads': {
          target: 'http://localhost:8500',
          changeOrigin: true
        }
      }
    }
  }
})
