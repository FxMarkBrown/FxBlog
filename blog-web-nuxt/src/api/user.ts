import type { ApiResponse, PageResult } from '@/types/common'

// 获取个人中心摘要信息。
export function getUserProfileApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/sys/user/profile')
}

// 更新个人资料。
export function updateProfileApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<unknown>>('/protal/user/updateProfile', {
    method: 'PUT',
    body: data
  })
}

// 修改密码。
export function updatePasswordApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<unknown>>('/sys/user/updatePwd', {
    method: 'PUT',
    body: data
  })
}

// 获取我的评论。
export function getMyCommentApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<Record<string, unknown>>>>('/protal/user/comment', {
    query
  })
}

// 删除我的评论或回复。
export function delMyCommentApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/protal/user/delMyComment/${id}`, {
    method: 'DELETE'
  })
}

// 获取我的回复。
export function getMyReplyApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<Record<string, unknown>>>>('/protal/user/myReply', {
    query
  })
}

// 获取我的点赞。
export function getMyLikeApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<Record<string, unknown>>>>('/protal/user/myLike', {
    query
  })
}

// 获取我的收藏。
export function getMyFavoriteApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<Record<string, unknown>>>>('/protal/user/myFavorite', {
    query
  })
}

// 获取我的 AI 额度流水。
export function getMyAiQuotaLogApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<Record<string, unknown>>>>('/protal/user/aiQuotaLog', {
    query
  })
}

// 获取我的反馈列表。
export function getMyFeedbackApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<Record<string, unknown>>>>('/sys/feedback/list', {
    query
  })
}

// 提交反馈。
export function addFeedbackApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<unknown>>('/sys/feedback/add', {
    method: 'POST',
    body: data
  })
}

// 执行签到。
export function signInApi() {
  return useApiClient()<ApiResponse<unknown>>('/sign/')
}

// 获取今日签到状态。
export function getSignInStatusApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/sign/isSignedToday')
}

// 获取签到统计。
export function getSignInStatsApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/sign/getSignDays')
}
