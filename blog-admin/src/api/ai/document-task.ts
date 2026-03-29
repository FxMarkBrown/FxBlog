import request from '@/utils/request'

export function getAiDocumentTaskListApi(params: any) {
  return request({
    url: '/sys/ai/document-task/list',
    method: 'get',
    params
  })
}

export function getAiDocumentTaskThreadsApi(taskId: number | string, params: any) {
  return request({
    url: `/sys/ai/document-task/threads/${taskId}`,
    method: 'get',
    params
  })
}

export function getAiDocumentTaskMessagesApi(threadId: number | string, params: any) {
  return request({
    url: `/sys/ai/document-task/messages/${threadId}`,
    method: 'get',
    params
  })
}

export function deleteAiDocumentTaskApi(ids: number[] | string[] | number | string) {
  return request({
    url: `/sys/ai/document-task/delete/${ids}`,
    method: 'delete'
  })
}
