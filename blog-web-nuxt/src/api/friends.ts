import type {ApiResponse} from '@/types/common'
import type {FriendApplyPayload, FriendItem} from '@/types/article'

// 获取友链列表
export function getFriendsApi() {
  return useApiClient()<ApiResponse<FriendItem[]>>('/api/friend/list')
}

// 申请友链
export function applyFriendApi(data: FriendApplyPayload) {
  return useApiClient()<ApiResponse<unknown>>('/api/friend/apply', {
    method: 'POST',
    body: data
  })
}
