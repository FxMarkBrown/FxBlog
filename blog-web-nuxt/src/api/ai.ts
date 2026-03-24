import type { ApiResponse } from '@/types/common'
import { getToken } from '@/utils/cookie'

// 获取 AI 会话可用模型列表。
export function getConversationModelOptionsApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>[]>>('/api/ai/conversation/models')
}

// 创建新的全局 AI 会话。
export function createGlobalConversationApi(data: Record<string, unknown> = {}) {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/api/ai/conversation/global', {
    method: 'POST',
    body: data
  })
}

// 创建绑定文章上下文的 AI 会话。
export function createArticleConversationApi(articleId: number | string, data: Record<string, unknown> = {}) {
  return useApiClient()<ApiResponse<Record<string, unknown>>>(`/api/ai/conversation/article/${articleId}`, {
    method: 'POST',
    body: data
  })
}

// 获取单个会话的详情信息。
export function getConversationDetailApi(conversationId: number | string) {
  return useApiClient()<ApiResponse<Record<string, unknown>>>(`/api/ai/conversation/detail/${conversationId}`)
}

// 分页获取会话列表。
export function getConversationPageApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/api/ai/conversation/page', {
    query
  })
}

// 获取指定会话的消息列表。
export function getConversationMessagesApi(conversationId: number | string, query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<Record<string, unknown>>>(`/api/ai/conversation/messages/${conversationId}`, {
    query
  })
}

// 获取当前用户的 AI 额度概览。
export function getConversationQuotaApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/api/ai/conversation/quota')
}

// 发送流式消息，并逐段消费服务端 SSE 事件。
export async function streamConversationMessageApi(
  conversationId: number | string,
  data: Record<string, unknown>,
  handlers: {
    onUser?: (payload: Record<string, unknown>) => void
    onDelta?: (payload: Record<string, unknown>) => void
    onDone?: (payload: Record<string, unknown>) => void
    onError?: (error: Error) => void
  } = {},
  signal?: AbortSignal
) {
  const config = useRuntimeConfig()
  const target = resolveStreamRequestUrl(conversationId, config.apiBaseServer)
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

      if (event.event === 'user' && handlers.onUser) {
        handlers.onUser(event.data)
      }
      if (event.event === 'delta' && handlers.onDelta) {
        handlers.onDelta(event.data)
      }
      if (event.event === 'done' && handlers.onDone) {
        handlers.onDone(event.data)
      }
      if (event.event === 'error') {
        streamError = new Error(String(event.data?.errorMessage || '流式请求失败'))
        handlers.onError?.(streamError)
      }
    })
  }

  if (buffer.trim()) {
    const event = parseSseChunk(buffer)
    if (event?.event === 'done' && handlers.onDone) {
      handlers.onDone(event.data)
    }
  }

  if (streamError) {
    throw streamError
  }
}

// 根据当前运行环境拼接流式请求地址。
function resolveStreamRequestUrl(conversationId: number | string, apiBaseServer: string) {
  const normalizedPath = `/api/ai/conversation/stream/${conversationId}`
  if (import.meta.client) {
    return normalizedPath
  }

  const normalizedBase = String(apiBaseServer || 'http://127.0.0.1:8800').trim()
  const baseUrl = normalizedBase.endsWith('/') ? normalizedBase : `${normalizedBase}/`
  return new URL(normalizedPath.slice(1), baseUrl).toString()
}

// 解析单个 SSE 数据块。
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

// 重命名指定会话。
export function renameConversationApi(conversationId: number | string, title: string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/ai/conversation/rename/${conversationId}`, {
    method: 'PUT',
    query: { title }
  })
}

// 删除指定会话。
export function deleteConversationApi(conversationId: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/ai/conversation/delete/${conversationId}`, {
    method: 'DELETE'
  })
}
