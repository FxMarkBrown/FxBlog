import type {MaybeRefOrGetter} from 'vue'
import {computed, toValue} from 'vue'

interface UsePageSeoOptions {
  title: MaybeRefOrGetter<string>
  description?: MaybeRefOrGetter<string>
  path?: MaybeRefOrGetter<string>
  image?: MaybeRefOrGetter<unknown>
  type?: MaybeRefOrGetter<'website' | 'article'>
  noindex?: MaybeRefOrGetter<boolean>
}

/**
 * 为页面注入统一 SEO 元信息。
 */
export function usePageSeo(options: UsePageSeoOptions) {
  const route = useRoute()
  const runtimeConfig = useRuntimeConfig()
  const siteStore = useSiteStore()

  const siteName = computed(() =>
    String(siteStore.websiteInfo.name || siteStore.websiteInfo.title || runtimeConfig.public.siteName || 'Open Source Blog')
  )
  const siteDescription = computed(() =>
    String(siteStore.websiteInfo.summary || siteStore.websiteInfo.description || runtimeConfig.public.siteDescription || '')
  )
  const siteUrl = computed(() => String(runtimeConfig.public.siteUrl || 'http://localhost:3000').replace(/\/+$/, ''))
  const title = computed(() => String(toValue(options.title) || siteName.value))
  const description = computed(() => String(toValue(options.description) || siteDescription.value || siteName.value))
  const type = computed(() => toValue(options.type) || 'website')
  const noindex = computed(() => Boolean(toValue(options.noindex)))
  const canonicalPath = computed(() => normalizeSeoPath(String(toValue(options.path) || route.path || '/')))
  const canonicalUrl = computed(() => `${siteUrl.value}${canonicalPath.value}`)
  const imageUrl = computed(() => normalizeSeoImage(toValue(options.image), siteUrl.value, runtimeConfig.public.seoImage))
  const robots = computed(() => (noindex.value ? 'noindex, nofollow' : 'index, follow'))
  const twitterCard = computed(() => (imageUrl.value ? 'summary_large_image' : 'summary'))

  useSeoMeta({
    title: () => title.value,
    description: () => description.value,
    robots: () => robots.value,
    ogTitle: () => title.value,
    ogDescription: () => description.value,
    ogSiteName: () => siteName.value,
    ogType: () => type.value,
    ogUrl: () => canonicalUrl.value,
    ogImage: () => imageUrl.value,
    twitterCard: () => twitterCard.value,
    twitterTitle: () => title.value,
    twitterDescription: () => description.value,
    twitterImage: () => imageUrl.value
  })

  useHead(() => ({
    link: [
      {
        rel: 'canonical',
        href: canonicalUrl.value
      }
    ]
  }))
}

/**
 * 为不应被索引的页面注入 noindex SEO 元信息。
 */
export function useNoIndexSeo(options: Omit<UsePageSeoOptions, 'noindex'>) {
  usePageSeo({
    ...options,
    noindex: true
  })
}

/**
 * 规范化 SEO 路径。
 */
function normalizeSeoPath(path: string) {
  if (!path || path === '/') {
    return '/'
  }

  return path.startsWith('/') ? path : `/${path}`
}

/**
 * 规范化 SEO 图片地址。
 */
function normalizeSeoImage(image: unknown, siteUrl: string, fallbackImage: string) {
  const candidate = resolveSeoImageCandidate(image) || resolveSeoImageCandidate(fallbackImage)
  if (!candidate) {
    return undefined
  }

  if (/^https?:\/\//i.test(candidate)) {
    return candidate
  }

  const normalizedPath = candidate.startsWith('/') ? `/${candidate.replace(/^\/+/, '')}` : `/${candidate}`
  return `${siteUrl}${normalizedPath}`
}

function resolveSeoImageCandidate(value: unknown) {
  if (typeof value === 'string') {
    return sanitizeSeoImageString(value)
  }

  if (value && typeof value === 'object' && 'url' in value) {
    return sanitizeSeoImageString(String((value as { url?: string }).url || ''))
  }

  return ''
}

function sanitizeSeoImageString(value: string) {
  const candidate = value.trim()
  if (!candidate || candidate === '[object Object]' || candidate === 'undefined' || candidate === 'null') {
    return ''
  }

  if (/^https?:\/\//i.test(candidate)) {
    return candidate
  }

  return candidate.startsWith('//') ? `/${candidate.replace(/^\/+/, '')}` : candidate
}
