import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
const devProxyOrigin = process.env.VITE_DEV_PROXY_ORIGIN || 'http://localhost:5173'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        headers: {
          Origin: devProxyOrigin,
        },
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Origin', devProxyOrigin)
          })
        },
      },
    },
  },
})
