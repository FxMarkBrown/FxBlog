import type {ApiResponse} from '@/types/common'

// 获取字典数据。
export function getDictDataApi(dictType: string | string[]) {
  const normalizedType = Array.isArray(dictType) ? dictType.join(',') : dictType
  return useApiClient()<ApiResponse<Record<string, unknown>[]>>(`/sys/dictData/selectDataByDictTypeCache/${normalizedType}`)
}
