import type { AlbumSummary, ArticleSummary } from '@/types/article'
import type { ApiResponse, PageResult } from '@/types/common'

type SitemapEntry = {
  loc: string
  lastmod?: string
  changefreq?: string
  priority?: string
}

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const siteUrl = String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(/\/+$/, '')
  const apiServer = String(runtimeConfig.apiBaseServer || 'http://127.0.0.1:8800').replace(/\/+$/, '')

  const staticEntries: SitemapEntry[] = [
    { loc: `${siteUrl}/`, changefreq: 'daily', priority: '1.0' },
    { loc: `${siteUrl}/archive`, changefreq: 'daily', priority: '0.9' },
    { loc: `${siteUrl}/categories`, changefreq: 'weekly', priority: '0.8' },
    { loc: `${siteUrl}/tags`, changefreq: 'weekly', priority: '0.8' },
    { loc: `${siteUrl}/moments`, changefreq: 'daily', priority: '0.8' },
    { loc: `${siteUrl}/photos`, changefreq: 'weekly', priority: '0.8' },
    { loc: `${siteUrl}/messages`, changefreq: 'daily', priority: '0.7' },
    { loc: `${siteUrl}/friends`, changefreq: 'weekly', priority: '0.7' },
    { loc: `${siteUrl}/about`, changefreq: 'monthly', priority: '0.6' }
  ]

  const [articleResult, albumResult] = await Promise.allSettled([
    $fetch<ApiResponse<PageResult<ArticleSummary>>>(`${apiServer}/api/article/list`, {
      query: {
        pageNum: 1,
        pageSize: 1000
      }
    }),
    $fetch<ApiResponse<AlbumSummary[]>>(`${apiServer}/api/album/list`)
  ])

  const dynamicEntries: SitemapEntry[] = []

  if (articleResult.status === 'fulfilled') {
    const records = articleResult.value?.data?.records || []
    for (const article of records) {
      if (!article?.id) {
        continue
      }

      dynamicEntries.push({
        loc: `${siteUrl}/post/${article.id}`,
        lastmod: String(article.createTime || ''),
        changefreq: 'weekly',
        priority: '0.8'
      })
    }
  }

  if (albumResult.status === 'fulfilled') {
    const records = albumResult.value?.data || []
    for (const album of records) {
      if (!album?.id) {
        continue
      }

      dynamicEntries.push({
        loc: `${siteUrl}/photos/${album.id}`,
        lastmod: String(album.createTime || ''),
        changefreq: 'weekly',
        priority: '0.7'
      })
    }
  }

  const xml = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ...dedupeEntries([...staticEntries, ...dynamicEntries]).map((entry) => {
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

function dedupeEntries(entries: SitemapEntry[]) {
  const seen = new Set<string>()
  return entries.filter((entry) => {
    if (seen.has(entry.loc)) {
      return false
    }

    seen.add(entry.loc)
    return true
  })
}

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
