import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

type LocalEnvMap = Record<string, string>

/**
 * 解析单个环境变量文件。
 */
function parseEnvFile(filePath: string) {
  if (!existsSync(filePath)) {
    return {}
  }

  const content = readFileSync(filePath, 'utf8')
  const parsed: LocalEnvMap = {}

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) {
      continue
    }

    const separatorIndex = line.indexOf('=')
    if (separatorIndex === -1) {
      continue
    }

    const key = line.slice(0, separatorIndex).trim()
    let value = line.slice(separatorIndex + 1).trim()

    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
      value = value.slice(1, -1)
    }

    parsed[key] = value
  }

  return parsed
}

/**
 * 合并基础环境文件与当前模式环境文件。
 */
function loadProjectEnv() {
  const mode = process.env.NODE_ENV === 'production' ? 'production' : 'development'
  const rootDir = fileURLToPath(new URL('.', import.meta.url))

  return {
    ...parseEnvFile(`${rootDir}/.env`),
    ...parseEnvFile(`${rootDir}/.env.${mode}`)
  }
}

const localEnv = loadProjectEnv()

/**
 * 读取环境变量，优先级为 shell > 本地环境文件 > 默认值。
 */
function readEnvValue(key: string, fallback: string) {
  const value = process.env[key] ?? localEnv[key]
  return value === undefined ? fallback : value
}

const apiServer = readEnvValue('NUXT_API_BASE_SERVER', 'http://127.0.0.1:8800')
const siteUrl = readEnvValue('NUXT_PUBLIC_SITE_URL', 'http://localhost:3000')
const siteName = readEnvValue('NUXT_PUBLIC_SITE_NAME', 'Open Source Blog')
const siteDescription = readEnvValue('NUXT_PUBLIC_SITE_DESCRIPTION', '个人知识库与生活博客')
const seoImage = readEnvValue('NUXT_PUBLIC_SEO_IMAGE', '/favicon.ico')
const proxiedPrefixes = ['/api', '/auth', '/sys', '/portal', '/file', '/static', '/notifications', '/sign']
const devProxyEntries = Object.fromEntries(
  proxiedPrefixes.map((prefix) => [
    prefix,
    {
      target: apiServer,
      changeOrigin: true
    }
  ])
)

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: false },
  srcDir: 'src/',
  modules: ['@pinia/nuxt'],
  css: ['@/styles/global.scss'],
  vite: {
    server: {
      proxy: devProxyEntries
    },
    plugins: [
      AutoImport({
        resolvers: [ElementPlusResolver({ importStyle: false })]
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: false })]
      })
    ],
    optimizeDeps: {
      include: [
        'highlight.js',
        'js-cookie',
        'marked',
        'md-editor-v3',
        'qrcode',
        'vue-danmaku',
        'vue-cropper',
        'element-plus',
        'element-plus/es',
        'ogl'
      ]
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: '@use "sass:color" as color;@use "@/styles/variables.scss" as *;@use "@/styles/mixins.scss" as *;'
        }
      }
    }
  },
  alias: {
    '@util': fileURLToPath(new URL('./src/utils', import.meta.url)),
    '@assets': fileURLToPath(new URL('./src/assets', import.meta.url))
  },
  runtimeConfig: {
    apiBaseServer: apiServer,
    public: {
      siteUrl,
      siteName,
      siteDescription,
      seoImage,
      recordNum: readEnvValue('NUXT_PUBLIC_RECORD_NUM', ''),
      adminUrl: readEnvValue('NUXT_PUBLIC_ADMIN_URL', 'http://localhost:3001')
    }
  },
  routeRules: {},
  app: {
    head: {
      title: siteName,
      htmlAttrs: {
        lang: 'zh-CN'
      },
      viewport: 'width=device-width, initial-scale=1',
      link: [
        {
          rel: 'icon',
          href: '/favicon.ico'
        }
      ],
      meta: [
        {
          name: 'description',
          content: siteDescription
        },
        {
          property: 'og:site_name',
          content: siteName
        },
        {
          property: 'og:type',
          content: 'website'
        },
        {
          property: 'og:image',
          content: seoImage
        },
        {
          name: 'twitter:card',
          content: 'summary_large_image'
        },
        {
          name: 'twitter:image',
          content: seoImage
        }
      ]
    }
  }
})
