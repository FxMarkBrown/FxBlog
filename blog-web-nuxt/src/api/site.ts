import type {ApiResponse} from '@/types/common'
import type {WebsiteInfo} from '@/types/article'

// 获取网站配置
export function getWebConfigApi() {
  return useApiClient()<ApiResponse<WebsiteInfo>>('/api/webConfig')
}

// 获取公告信息
export function getNoticeApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/api/getNotice')
}

// 获取天气效果配置
export function getWeatherEffectApi() {
  return useApiClient()<ApiResponse<Record<string, unknown> | null>>('/api/weather/effect')
}

// 上报访问信息
export function reportApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/api/report')
}
