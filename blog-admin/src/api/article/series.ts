import request from '@/utils/request'

// 获取系列列表
export function getSeriesListApi(params: any) {
  return request({
    url: '/sys/series/list',
    method: 'get',
    params
  })
}

// 获取全部系列
export function getSeriesAllApi() {
  return request({
    url: '/sys/series/all',
    method: 'get'
  })
}

// 新增系列
export function addSeriesApi(data: any) {
  return request({
    url: '/sys/series',
    method: 'post',
    data
  })
}

// 修改系列
export function updateSeriesApi(data: any) {
  return request({
    url: '/sys/series',
    method: 'put',
    data
  })
}

// 删除系列
export function deleteSeriesApi(ids: any) {
  return request({
    url: `/sys/series/delete/${ids}`,
    method: 'delete'
  })
}
