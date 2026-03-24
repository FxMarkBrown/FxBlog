import request from '@/utils/request'

// 获取 RAG 当前运行状态与基础配置。
export function getAiRagStatusApi() {
  return request({
    url: '/sys/ai/rag/status',
    method: 'get'
  })
}

// 手动提交一次文章 RAG 全量重建任务。
export function submitAiRagRebuildApi(params: { publishedOnly?: boolean }) {
  return request({
    url: '/sys/ai/rag/rebuild',
    method: 'post',
    params
  })
}
