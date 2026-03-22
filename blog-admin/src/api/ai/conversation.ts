import request from '@/utils/request'

export function getAiConversationListApi(params: any) {
  return request({
    url: '/sys/ai/conversation/list',
    method: 'get',
    params
  })
}

export function getAiConversationMessagesApi(conversationId: number | string, params: any) {
  return request({
    url: `/sys/ai/conversation/messages/${conversationId}`,
    method: 'get',
    params
  })
}

export function deleteAiConversationApi(ids: number[] | string[] | number | string) {
  return request({
    url: `/sys/ai/conversation/delete/${ids}`,
    method: 'delete'
  })
}
