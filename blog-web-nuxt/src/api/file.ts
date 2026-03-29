import type { ApiResponse } from '@/types/common'
import type { AnyRecord } from '@/types/common'

// 上传文件。
export function uploadFileApi(data: FormData, source: string) {
  return useApiClient()<ApiResponse<AnyRecord>>('/file/upload', {
    method: 'POST',
    body: data,
    query: { source }
  })
}

// 删除文件。
export function deleteFileApi(url: string) {
  return useApiClient()<ApiResponse<unknown>>('/file/delete', {
    query: { url }
  })
}
