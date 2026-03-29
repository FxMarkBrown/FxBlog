import type {ApiResponse, PageResult} from '@/types/common'
import type {
  DocumentNodeMessage,
  DocumentNodeThread,
  DocumentParseResult,
  DocumentTaskDetail,
  DocumentTaskListItem
} from '@/types/ai-document'
import {getToken} from '@/utils/cookie'

export function getDocumentTaskListApi() {
  return useApiClient()<ApiResponse<DocumentTaskListItem[]>>('/api/ai/document/tasks')
}

export function createDocumentTaskApi(data: Record<string, unknown> = {}) {
  return useApiClient()<ApiResponse<DocumentTaskDetail>>('/api/ai/document/tasks', {
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

export function getDocumentNodeThreadApi(taskId: number | string, nodeId: string) {
  return useApiClient()<ApiResponse<DocumentNodeThread | null>>(`/api/ai/document/tasks/${taskId}/nodes/${nodeId}/thread`)
}

export function getDocumentNodeMessagesApi(taskId: number | string, nodeId: string, query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<DocumentNodeMessage>>>(`/api/ai/document/tasks/${taskId}/nodes/${nodeId}/messages`, {
    query
  })
}

export async function streamDocumentNodeApi(
  taskId: number | string,
  nodeId: string,
  data: Record<string, unknown>,
  handlers: {
    onMeta?: (payload: Record<string, unknown>) => void
    onDelta?: (payload: Record<string, unknown>) => void
    onDone?: (payload: Record<string, unknown>) => void
    onError?: (error: Error) => void
  } = {},
  signal?: AbortSignal
) {
  const config = useRuntimeConfig()
  const target = resolveStreamRequestUrl(taskId, nodeId, config.apiBaseServer)
  const response = await fetch(target.toString(), {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Authorization: getToken() || ''
    },
    body: JSON.stringify(data || {}),
    signal
  })

  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    const error = new Error(payload?.message || '流式请求失败') as Error & { status?: number }
    error.status = response.status
    throw error
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('当前浏览器不支持流式读取')
  }

  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let streamError: Error | null = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''
    chunks.forEach((chunk) => {
      const event = parseSseChunk(chunk)
      if (!event) {
        return
      }
      if (event.event === 'meta') {
        handlers.onMeta?.(event.data)
      }
      if (event.event === 'delta') {
        handlers.onDelta?.(event.data)
      }
      if (event.event === 'done') {
        handlers.onDone?.(event.data)
      }
      if (event.event === 'error') {
        streamError = new Error(String(event.data?.errorMessage || '流式请求失败'))
        handlers.onError?.(streamError)
      }
    })
  }

  if (buffer.trim()) {
    const event = parseSseChunk(buffer)
    if (event?.event === 'done') {
      handlers.onDone?.(event.data)
    }
  }

  if (streamError) {
    throw streamError
  }
}

function resolveStreamRequestUrl(taskId: number | string, nodeId: string, apiBaseServer: string) {
  const normalizedPath = `/api/ai/document/tasks/${taskId}/nodes/${nodeId}/stream`
  if (import.meta.client) {
    return normalizedPath
  }

  const normalizedBase = String(apiBaseServer || 'http://127.0.0.1:8800').trim()
  const baseUrl = normalizedBase.endsWith('/') ? normalizedBase : `${normalizedBase}/`
  return new URL(normalizedPath.slice(1), baseUrl).toString()
}

function parseSseChunk(chunk: string) {
  const lines = chunk.split('\n')
  let event = 'message'
  const dataLines: string[] = []

  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  })

  if (!dataLines.length) {
    return null
  }

  const rawData = dataLines.join('\n')
  return {
    event,
    data: JSON.parse(rawData) as Record<string, unknown>
  }
}
