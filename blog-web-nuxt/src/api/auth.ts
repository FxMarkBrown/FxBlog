import type { LoginUserInfo } from '@/types/auth'
import type { ApiResponse } from '@/types/common'

// 登录
export function loginApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<LoginUserInfo>>('/auth/login', {
    method: 'POST',
    body: data
  })
}

// 退出登录
export function logoutApi() {
  return useApiClient()<ApiResponse<null>>('/auth/logout', {
    method: 'POST'
  })
}

// 获取当前登录用户信息
export function getUserInfoApi(source = 'web') {
  return useApiClient()<ApiResponse<LoginUserInfo>>('/auth/info', {
    method: 'GET',
    query: { source }
  })
}

// 发送邮箱验证码
export function sendEmailCodeApi(email: string) {
  return useApiClient()<ApiResponse<boolean>>('/api/sendEmailCode', {
    method: 'GET',
    query: { email }
  })
}

// 邮箱注册
export function registerApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<boolean>>('/api/email/register', {
    method: 'POST',
    body: data
  })
}

// 忘记密码
export function forgotPasswordApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<boolean>>('/api/email/forgot', {
    method: 'POST',
    body: data
  })
}

// 获取微信登录二维码
export function getWechatLoginCodeApi() {
  return useApiClient()<ApiResponse<string | Record<string, unknown>>>('/api/wechat/getCode')
}

// 查询微信登录状态
export function getWechatIsLoginApi(code: string) {
  return useApiClient()<ApiResponse<LoginUserInfo>>(`/api/wechat/isLogin/${code}`)
}

// 获取第三方授权地址
export function getAuthRenderApi(source: string) {
  return useApiClient()<ApiResponse<string>>(`/api/auth/render/${source}`)
}

// 获取验证码
export function getCaptchaApi() {
  return useApiClient()<ApiResponse<Record<string, unknown>>>('/auth/getCaptcha')
}

// 获取验证码开关
export function getCaptchaSwitchApi() {
  return useApiClient()<ApiResponse<boolean | Record<string, unknown>>>('/sys/config/getConfigByKey/slider_verify_switch')
}
