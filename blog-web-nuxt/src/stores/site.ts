import type { WebsiteInfo } from '@/types/article'
import { getSystemNoticeApi } from '@/api/message'
import { getNoticeApi, getWebConfigApi, reportApi } from '@/api/site'
import { unwrapResponseData } from '@/utils/response'

export const useSiteStore = defineStore('site', () => {
  const runtimeConfig = useRuntimeConfig()
  const defaultSiteName = String(runtimeConfig.public.siteName || 'Open Source Blog')
  const defaultRecordNum = String(runtimeConfig.public.recordNum || '')
  const websiteInfo = ref<WebsiteInfo>({
    showList: [],
    name: defaultSiteName,
    title: defaultSiteName,
    recordNum: defaultRecordNum,
    articleCount: 0,
    likeCount: 0,
    profileAvatar: '',
    profileName: '管理员',
    profileSignature: ''
  })
  const loaded = ref(false)
  const notice = ref<Record<string, unknown> | null>(null)
  const isUnread = ref(false)
  const visitorAccess = ref(0)
  const siteAccess = ref(0)

  /**
   * 获取站点配置
   */
  async function fetchWebsiteInfo(force = false) {
    if (loaded.value && !force) {
      return websiteInfo.value
    }

    const response = await getWebConfigApi()
    const info = unwrapResponseData<WebsiteInfo | null>(response) || {}
    const extra = response.extra || {}
    websiteInfo.value = {
      ...info,
      name: String(info.name || info.title || defaultSiteName),
      title: String(info.title || info.name || defaultSiteName),
      recordNum: String(info.recordNum || defaultRecordNum),
      articleCount: Number(extra.articleCount ?? info.articleCount ?? 0),
      likeCount: Number(extra.likeCount ?? info.likeCount ?? 0),
      profileAvatar: String(extra.adminAvatar || info.profileAvatar || info.authorAvatar || info.logo || ''),
      profileName: String(extra.adminNickname || info.profileName || info.author || '管理员'),
      profileSignature: String(extra.adminSignature || info.profileSignature || info.authorInfo || '')
    }
    visitorAccess.value = Number(extra.visitorCount || visitorAccess.value || 0)
    siteAccess.value = Number(extra.blogViewsCount || siteAccess.value || 0)
    loaded.value = true
    return websiteInfo.value
  }

  /**
   * 获取公告
   */
  async function fetchNotice() {
    const response = await getNoticeApi()
    notice.value = unwrapResponseData<Record<string, unknown> | null>(response)
    return notice.value
  }

  /**
   * 获取未读状态
   */
  async function fetchUnreadStatus() {
    const token = useCookie<string | null>('blog_token')
    if (!token.value) {
      isUnread.value = false
      return false
    }

    const response = await getSystemNoticeApi()
    isUnread.value = Boolean(unwrapResponseData<boolean | null>(response))
    return isUnread.value
  }

  /**
   * 上报访问并刷新统计
   */
  async function reportVisit() {
    await reportApi()
    if (import.meta.client && siteAccess.value > 0) {
      siteAccess.value += 1
    }
    return null
  }

  return {
    websiteInfo,
    loaded,
    notice,
    isUnread,
    visitorAccess,
    siteAccess,
    fetchWebsiteInfo,
    fetchNotice,
    fetchUnreadStatus,
    reportVisit
  }
})
