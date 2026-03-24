import request from '@/utils/request'


// 获取文件列表
export function getFileListApi(params: any) {
  return request({
    url: '/file/list',
    method: 'get',
    params
  })
}

// 获取实际存在的文件类型列表
export function getFileExtOptionsApi() {
  return request({
    url: '/file/extOptions',
    method: 'get'
  })
}

// 上传文件
export function uploadApi(data: FormData, source = 'common') {
  return request({
    url: '/file/upload',
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data,
    params: { source }
  })
}

// 修改文件名或存储路径
export function renameFileApi(data: any) {
  return request({
    url: '/file/rename',
    method: 'put',
    data
  })
}

// 原地替换文件
export function replaceFileApi(id: string, file: File) {
  const data = new FormData()
  data.append('id', id)
  data.append('file', file)
  return request({
    url: '/file/replace',
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data
  })
}

// 删除文件
export function deleteFileApi(url: string) {
  return request({
    url: `/file/delete`,
    method: 'get',
    params: { url:url }
  })
}

// 获取云存储配置
export function getOssConfigApi() {
  return request({
    url: '/file/getOssConfig',
    method: 'get'
  })
}

// 添加云存储配置
export function addOssApi(data: any) {
  return request({
    url: '/file/addOss',
    method: 'post', 
    data
  })
}
// 更新云存储配置
export function updateOssApi(data: any) {
  return request({
    url: '/file/updateOss',
    method: 'put', 
    data
  })
}




