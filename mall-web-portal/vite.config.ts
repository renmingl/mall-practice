import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { mockPlugin, mockHandlers } from './mock'

// 前台商城脚手架：dev 走 Vite 代理转发网关（/api → mall-gateway:8080），无跨域问题
// VITE_MOCK=true 时进入 Mock 演示模式：本地拦截 /api 返回演示数据，无需启动任何后端即可浏览全部页面
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const mock = env.VITE_MOCK === 'true'
  return {
    plugins: [vue(), mock ? mockPlugin(mockHandlers) : null],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    server: {
      host: '0.0.0.0',
      port: 5174,
      strictPort: true, // 端口被占用时直接报错（不自动顺延，避免出现非预期端口）
      proxy: mock
        ? {}
        : {
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
