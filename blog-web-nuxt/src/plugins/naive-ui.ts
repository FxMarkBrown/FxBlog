import naive from 'naive-ui'

/**
 * 全量注册 Naive UI 组件（模板中直接使用 <n-xxx>）。
 * 主题令牌由 layout 中的 n-config-provider + utils/naive-theme.ts 注入。
 *
 * 服务端渲染时必须挂 @css-render/vue3-ssr 的适配器：css-render 默认把样式
 * 挂载到 document.head，SSR 下没有 document 会直接抛错（如分页组件的
 * Popselect/Follower 在服务端渲染时触发 "document is not defined"）。
 * setup() 让样式改为收集进 SSR 上下文，再在 app:rendered 钩子里注入 <head>。
 */
export default defineNuxtPlugin(async (nuxtApp) => {
  nuxtApp.vueApp.use(naive)

  if (import.meta.server) {
    const { setup } = await import('@css-render/vue3-ssr')
    const { collect } = setup(nuxtApp.vueApp)
    nuxtApp.hooks.hook('app:rendered', ({ ssrContext }) => {
      const css = collect()
      if (css && ssrContext) {
        ssrContext.head.push({ style: [{ innerHTML: css }] })
      }
    })
  }
})
