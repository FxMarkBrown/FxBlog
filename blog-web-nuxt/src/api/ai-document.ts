import type { ApiResponse } from '@/types/common'
import type { DocumentNodeAnswer, DocumentParseResult, DocumentTaskDetail, DocumentTaskListItem } from '@/types/ai-document'

export function getDocumentTaskListApi() {
  return useApiClient()<ApiResponse<DocumentTaskListItem[]>>('/api/ai/document/tasks')
}

export function createDocumentTaskApi(data: Record<string, unknown> = {}) {
  return useApiClient()<ApiResponse<DocumentTaskDetail>>('/api/ai/document/tasks', {
    method: 'POST',
    body: data
  })
}

export function createMockDocumentTaskApi(data: Record<string, unknown> = {}) {
  return useApiClient()<ApiResponse<DocumentTaskDetail>>('/api/ai/document/dev/tasks/mock', {
    method: 'POST',
    body: data
  })
}

export function renameDocumentTaskApi(taskId: number | string, data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<Record<string, unknown>>>(`/api/ai/document/tasks/${taskId}`, {
    method: 'PATCH',
    body: data
  })
}

export function deleteDocumentTaskApi(taskId: number | string) {
  return useApiClient()<ApiResponse<null>>(`/api/ai/document/tasks/${taskId}`, {
    method: 'DELETE'
  })
}

export function getDocumentTaskDetailApi(taskId: number | string) {
  return useApiClient()<ApiResponse<DocumentTaskDetail>>(`/api/ai/document/tasks/${taskId}`)
}

export function getDocumentTaskResultApi(taskId: number | string) {
  return useApiClient()<ApiResponse<DocumentParseResult>>(`/api/ai/document/tasks/${taskId}/result`)
}

export function askDocumentNodeApi(taskId: number | string, nodeId: string, data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<DocumentNodeAnswer>>(`/api/ai/document/tasks/${taskId}/nodes/${nodeId}/ask`, {
    method: 'POST',
    body: data
  })
}
