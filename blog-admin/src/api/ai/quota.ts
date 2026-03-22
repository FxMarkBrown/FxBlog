import request from '@/utils/request'

export function getAiQuotaListApi(params: any) {
  return request({
    url: '/sys/ai/quota/list',
    method: 'get',
    params
  })
}

export function getAiQuotaLogListApi(params: any) {
  return request({
    url: '/sys/ai/quota/log/list',
    method: 'get',
    params
  })
}

export function getAiQuotaRuleApi() {
  return request({
    url: '/sys/ai/quota/rule',
    method: 'get'
  })
}

export function updateAiQuotaRuleApi(data: any) {
  return request({
    url: '/sys/ai/quota/rule',
    method: 'put',
    data
  })
}

export function updateAiQuotaManualApi(data: any) {
  return request({
    url: '/sys/ai/quota/manual',
    method: 'put',
    data
  })
}
