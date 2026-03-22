import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { ConfigEnv, UserConfig, loadEnv } from 'vite'
import AutoImport from 'unplugin-auto-import/vite'
import { svgBuilder } from './src/plugins/svgBuilder'

export default defineConfig(({ command, mode }: ConfigEnv): UserConfig => {
  // 获取环境变量
  const env = loadEnv(mode, process.cwd())
  
  return {
    base: env.VITE_APP_BASE_PATH || '/',
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler',
          charset: false
        },
      },
    },
    plugins: [vue(), svgBuilder(path.resolve(__dirname, 'src/assets/icons/')), AutoImport({
      imports: [
        'vue',
        'vue-router',
        'pinia'
      ],
      dts: 'src/auto-imports.d.ts',
      // 可以选择是否自动导入 Vue 的组合式 API
      vueTemplate: true,
      // 自动导入目录下的模块
      dirs: [
        './src/composables',
        './src/stores'
      ],
    })],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_APP_PORT) || 3000,
      open: false,
      proxy: {
        '/api': {
          target: env.VITE_APP_API_URL,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        },
        '/static': {
          target: env.VITE_APP_API_URL,
          changeOrigin: true
        }
      }
    }
  }
})
