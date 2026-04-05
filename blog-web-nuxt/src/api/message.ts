import type {ApiResponse, PageResult} from '@/types/common'
import type {MessageItem, NotificationItem} from '@/types/article'

// 获取留言列表
export function getMessagesApi() {
  return useApiClient()<ApiResponse<MessageItem[]>>('/api/message/list')
}

// 发送留言
export function addMessageApi(data: MessageItem) {
  return useApiClient()<ApiResponse<unknown>>('/api/message/add', {
    method: 'POST',
    body: data
  })
}

// 获取系统通知未读状态
export function getSystemNoticeApi() {
  return useApiClient()<ApiResponse<boolean>>('/api/notifications/is-unread')
}

// 分页获取消息通知。
export function getNotificationsApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<NotificationItem>>>('/api/notifications/page', {
    query
  })
}

// 标记单条消息通知为已读。
export function markNotificationAsReadApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/notifications/read/${id}`)
}

// 标记全部消息通知为已读。
export function markAllNotificationsAsReadApi() {
  return useApiClient()<ApiResponse<unknown>>('/api/notifications/read/all')
}

// 删除指定消息通知。
export function deleteNotificationApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/notifications/delete/${id}`, {
    method: 'DELETE'
  })
}

// 获取各分类未读消息数量。
export function getUnreadNotificationsCountApi() {
  return useApiClient()<ApiResponse<Record<string, { num?: number }>>>('/api/notifications/unReadNum')
}
