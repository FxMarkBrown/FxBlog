export default defineEventHandler((event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const siteUrl = String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(
    /\/+$/,
    ''
  )

  setHeader(event, 'content-type', 'text/plain; charset=UTF-8')

  return `User-Agent: *
Allow: /
Disallow: /login
Disallow: /editor
Disallow: /user
Disallow: /profile
Disallow: /ai
Disallow: /notifications
Disallow: /404

Sitemap: ${siteUrl}/sitemap.xml
`
})
