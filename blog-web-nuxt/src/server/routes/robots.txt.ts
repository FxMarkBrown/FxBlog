/**
 * 动态输出 robots.txt，确保 sitemap 地址与运行环境一致。
 */
export default defineEventHandler((event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const siteUrl = String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(/\/+$/, '')

  setHeader(event, 'content-type', 'text/plain; charset=UTF-8')

  return `User-Agent: *
Allow: /

Sitemap: ${siteUrl}/sitemap.xml
`
})
