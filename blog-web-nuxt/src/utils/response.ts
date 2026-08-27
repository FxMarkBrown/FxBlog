import type { ApiResponse } from '@/types/common'

export function unwrapResponseData<T>(response: ApiResponse<T> | T): T {
  if (response && typeof response === 'object' && 'data' in response) {
    return (response as ApiResponse<T>).data as T
  }

  return response as T
}
