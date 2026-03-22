import type { ApiResponse, PageResult } from '@/types/common'
import type { MomentSummary } from '@/types/article'

// 获取说说列表
export function getMomentsApi(params: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<MomentSummary>>>('/api/moment/list', { params })
}
