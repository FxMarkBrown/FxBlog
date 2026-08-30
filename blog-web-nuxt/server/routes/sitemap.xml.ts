import { collectContentUrls, type ContentUrlEntry } from '../utils/content-urls'

type SitemapEntry = ContentUrlEntry & {
  changefreq?: string
  priority?: string
}

// 静态页的抓取频率与权重（动态内容条目不覆盖）
const STATIC_META: Record<string, { changefreq: string; priority: string }> = {
  '/': { changefreq: 'daily', priority: '1.0' },
  '/archive': { changefreq: 'daily', priority: '0.9' },
  '/categories': { changefreq: 'weekly', priority: '0.8' },
  '/tags': { changefreq: 'weekly', priority: '0.8' },
  '/moments': { changefreq: 'daily', priority: '0.8' },
  '/photos': { changefreq: 'weekly', priority: '0.8' },
  '/messages': { changefreq: 'daily', priority: '0.7' },
  '/friends': { changefreq: 'weekly', priority: '0.7' },
  '/about': { changefreq: 'monthly', priority: '0.6' }
}

const ARTICLE_META = { changefreq: 'weekly', priority: '0.8' }
const ALBUM_META = { changefreq: 'weekly', priority: '0.7' }

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const siteUrl = String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(
    /\/+$/,
    ''
  )

  const entries = (await collectContentUrls(event)).map<SitemapEntry>((entry) => {
    const path = entry.loc.replace(siteUrl, '')
    const meta = STATIC_META[path] || (path.startsWith('/post/') ? ARTICLE_META : ALBUM_META)
    return { ...entry, ...meta }
  })

  const xml = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ...entries.map((entry) => {
      const lines = ['  <url>', `    <loc>${escapeXml(entry.loc)}</loc>`]

      if (entry.lastmod) {
        lines.push(`    <lastmod>${escapeXml(toIsoDate(entry.lastmod))}</lastmod>`)
      }
      if (entry.changefreq) {
        lines.push(`    <changefreq>${entry.changefreq}</changefreq>`)
      }
      if (entry.priority) {
        lines.push(`    <priority>${entry.priority}</priority>`)
      }

      lines.push('  </url>')
      return lines.join('\n')
    }),
    '</urlset>'
  ].join('\n')

  setHeader(event, 'content-type', 'application/xml; charset=UTF-8')
  return xml
})

function toIsoDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? new Date().toISOString() : date.toISOString()
}

function escapeXml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}
