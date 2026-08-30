import type { AlbumSummary, ArticleSummary } from '@/types/article'
import type { ApiResponse, PageResult } from '@/types/common'
import type { H3Event } from 'h3'

export interface ContentUrlEntry {
  loc: string
  lastmod?: string
}

/**
 * 收集全站内容 URL（供 sitemap.xml 与 IndexNow 提交共用）：
 * 静态页面 + 全部文章 + 全部相册，已按 URL 去重。
 * @param event 请求事件（用于读取 runtimeConfig）
 * @returns 内容条目（含最后更新时间）
 */
export async function collectContentUrls(event: H3Event): Promise<ContentUrlEntry[]> {
  const runtimeConfig = useRuntimeConfig(event)
  const siteUrl = String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(
    /\/+$/,
    ''
  )
  const apiServer = String(runtimeConfig.apiBaseServer || 'http://127.0.0.1:8800').replace(
    /\/+$/,
    ''
  )

  const entries: ContentUrlEntry[] = [
    { loc: `${siteUrl}/` },
    { loc: `${siteUrl}/archive` },
    { loc: `${siteUrl}/categories` },
    { loc: `${siteUrl}/tags` },
    { loc: `${siteUrl}/moments` },
    { loc: `${siteUrl}/photos` },
    { loc: `${siteUrl}/messages` },
    { loc: `${siteUrl}/friends` },
    { loc: `${siteUrl}/about` }
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

  if (articleResult.status === 'fulfilled') {
    for (const article of articleResult.value?.data?.records || []) {
      if (article?.id) {
        entries.push({
          loc: `${siteUrl}/post/${article.id}`,
          lastmod: String(article.createTime || '')
        })
      }
    }
  }

  if (albumResult.status === 'fulfilled') {
    for (const album of albumResult.value?.data || []) {
      if (album?.id) {
        entries.push({
          loc: `${siteUrl}/photos/${album.id}`,
          lastmod: String(album.createTime || '')
        })
      }
    }
  }

  const seen = new Set<string>()
  return entries.filter((entry) => {
    if (seen.has(entry.loc)) {
      return false
    }
    seen.add(entry.loc)
    return true
  })
}
