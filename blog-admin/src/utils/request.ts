import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/store/modules/user'


const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 5000,
  headers: { "Content-Type": "application/json;charset=utf-8" },
})

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 二进制数据则直接返回
    // if (res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer') {
    //   return res.data
    // }
    if (res.code !== 200) {
      if (res.code === 401) {
        ElMessage.warning('登录状态已失效，正在返回前台')
        const userStore = useUserStore()
        userStore.resetToken().finally(() => {
          window.location.replace(import.meta.env.VITE_APP_SITE_URL || '/')
        })
      } else {
        ElMessage.error(res.message || '请求错误')
      }
      return Promise.reject(new Error(res.message || '请求错误'))
    }
    
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.resetToken().finally(() => {
        window.location.replace(import.meta.env.VITE_APP_SITE_URL || '/')
      })
    } else if (error.response?.status === 500) {
      ElMessage.error('后端接口连接异常')
    }else{
      ElMessage.error('请求错误')
    }
    return Promise.reject(error)
  }
)

export default service 