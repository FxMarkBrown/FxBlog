import naive from 'naive-ui'

/**
 * 全量注册 Naive UI 组件（模板中直接使用 <n-xxx>）。
 * 主题令牌由 layout 中的 n-config-provider + utils/naive-theme.ts 注入。
 */
export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.use(naive)
})
