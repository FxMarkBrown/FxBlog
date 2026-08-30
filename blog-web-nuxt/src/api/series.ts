import type { ApiResponse } from '@/types/common'
import type { ArticleSummary, SeriesInfo } from '@/types/article'

// 获取全部系列
export function getSeriesListApi() {
  return useApiClient()<ApiResponse<SeriesInfo[]>>('/api/series/list')
}

// 获取系列内全部文章（create_time 正序）
export function getSeriesArticlesApi(id: number | string) {
  return useApiClient()<ApiResponse<ArticleSummary[]>>(`/api/series/${id}/articles`)
}
