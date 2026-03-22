import type { ApiResponse } from '@/types/common'
import type { TagSummary } from '@/types/article'

// 获取标签列表
export function getTagsApi() {
  return useApiClient()<ApiResponse<TagSummary[]>>('/api/tag/list')
}

// 获取编辑器分类列表。
export function getCategoriesApi() {
  return useApiClient()<ApiResponse<Array<{ id: number | string; name: string }>>>('/sys/category/all')
}
