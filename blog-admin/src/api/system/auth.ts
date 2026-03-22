import request from '@/utils/request'

export function logoutApi() {
  return request({
    url: '/auth/logout',
    method: 'post',
  })
}

export function getUserInfoApi() {
  return request({
    url: '/auth/info',
    method: 'get',
    params: {
      source: 'admin'
    }
  })
}

export function getRouters() {
  return request({
    url: '/sys/menu/routers',
    method: 'get'
  })
}
