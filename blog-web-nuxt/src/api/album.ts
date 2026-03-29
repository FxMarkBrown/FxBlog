import type {ApiResponse} from '@/types/common'
import type {AlbumPhoto, AlbumSummary} from '@/types/article'

// 获取相册列表
export function getAlbumListApi() {
  return useApiClient()<ApiResponse<AlbumSummary[]>>('/api/album/list')
}

// 获取相册详情
export function getAlbumDetailApi(id: number | string) {
  return useApiClient()<ApiResponse<AlbumSummary>>(`/api/album/detail/${id}`)
}

// 获取相册照片
export function getAlbumPhotosApi(id: number | string) {
  return useApiClient()<ApiResponse<AlbumPhoto[]>>(`/api/album/photos/${id}`)
}

// 验证相册密码
export function verifyAlbumPasswordApi(id: number | string, password: string) {
  return useApiClient()<ApiResponse<boolean>>(`/api/album/verify/${id}`, {
    query: {
      password
    }
  })
}
