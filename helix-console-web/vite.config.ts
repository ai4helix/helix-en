import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => ({
  base: mode === 'production' ? '/helix/' : '/',
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()]
    }),
    Components({ resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        charset: false
      }
    }
  },
  server: {
    host: true,
    port: 5374,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8286',
        changeOrigin: true
      },
      '/manage/': {
        target: 'http://127.0.0.1:8286',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 4374,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8286',
        changeOrigin: true
      },
      '/manage/': {
        target: 'http://127.0.0.1:8286',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          x6: ['@antv/x6', '@antv/x6-vue-shape'],
          element: ['element-plus']
        }
      }
    }
  }
}))
