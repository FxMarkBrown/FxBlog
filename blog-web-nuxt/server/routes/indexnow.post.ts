import { collectContentUrls } from '../utils/content-urls'

/**
 * IndexNow 提交接口（Bing / Yandex 等搜索引擎的即时收录协议）。
 * 用法：POST /indexnow?token=<NUXT_INDEXNOW_SECRET>
 *   - 不带 body：提交全站 URL（静态页 + 文章 + 相册）
 *   - body 为 { "urls": ["https://…/post/1"] }：只提交指定 URL（最多 10000 条）
 * token 用于防止接口被随意触发；未配置 NUXT_INDEXNOW_SECRET 时接口关闭。
 */
export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const secret = String(runtimeConfig.indexnowSecret || '')
  const token = String(getQuery(event).token || '')

  if (!secret || token !== secret) {
    throw createError({ statusCode: 403, statusMessage: 'Forbidden' })
  }

  const key = String(runtimeConfig.indexnowKey || '')
  const siteUrl = String(runtimeConfig.public.siteUrl || '').replace(/\/+$/, '')
  if (!key || !siteUrl) {
    throw createError({ statusCode: 500, statusMessage: 'IndexNow 未配置 key 或站点地址' })
  }

  const body = await readBody<{ urls?: string[] }>(event).catch(() => null)
  const customUrls = Array.isArray(body?.urls)
    ? body.urls.filter((url) => typeof url === 'string' && url.startsWith(siteUrl))
    : []
  const urlList =
    customUrls.length > 0 ? customUrls : (await collectContentUrls(event)).map((entry) => entry.loc)

  if (urlList.length === 0) {
    return { submitted: 0, message: '没有可提交的 URL' }
  }

  // IndexNow 单次最多提交 10000 条
  const batch = urlList.slice(0, 10000)

  try {
    const response = await $fetch.raw('https://api.indexnow.org/indexnow', {
      method: 'POST',
      headers: { 'content-type': 'application/json; charset=utf-8' },
      body: {
        host: new URL(siteUrl).host,
        key,
        keyLocation: `${siteUrl}/${key}.txt`,
        urlList: batch
      }
    })

    return { submitted: batch.length, status: response.status }
  } catch (error) {
    const failure = error as { response?: { status?: number }; message?: string }
    throw createError({
      statusCode: 502,
      statusMessage: `IndexNow 提交失败：${failure?.response?.status || failure?.message || '未知错误'}`
    })
  }
})
