import type {ApiResponse} from '@/types/common'

export interface UploadedFileDetail {
  id?: string
  url?: string
  thUrl?: string
  filename?: string
  originalFilename?: string
  source?: string
  [key: string]: unknown
}

// 上传文件。
export function uploadFileApi(data: FormData, source: string) {
  return useApiClient()<ApiResponse<UploadedFileDetail>>('/file/upload', {
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
