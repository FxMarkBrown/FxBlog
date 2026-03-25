export default defineEventHandler((event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const siteUrl = String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(/\/+$/, '')

  setHeader(event, 'content-type', 'text/plain; charset=UTF-8')

  return `User-Agent: *
Allow: /

Sitemap: ${siteUrl}/sitemap.xml
`
})
