import {getUserInfoApi, logoutApi} from '@/api/auth'
import type {LoginUserInfo} from '@/types/auth'

function shouldClearAuthForError(error: unknown) {
  const statusCode = Number(
    (error as { status?: number; statusCode?: number; response?: { status?: number } })?.status
    || (error as { status?: number; statusCode?: number; response?: { status?: number } })?.statusCode
    || (error as { status?: number; statusCode?: number; response?: { status?: number } })?.response?.status
    || 0
  )

  return statusCode === 401
}

export const useAuthStore = defineStore('auth', () => {
  const tokenCookie = useCookie<string | null>('blog_token', {
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
    sameSite: 'lax'
  })
  const token = ref(tokenCookie.value ?? '')
  const userInfo = ref<LoginUserInfo | null>(null)
  const loaded = ref(false)

  if (import.meta.client && !userInfo.value) {
    const cachedUser = sessionStorage.getItem('user')
    if (cachedUser) {
      userInfo.value = JSON.parse(cachedUser) as LoginUserInfo
    }
  }

  const isLoggedIn = computed(() => Boolean(token.value))
  const isAdmin = computed(() => {
    if (!userInfo.value) {
      return false
    }

    if (userInfo.value.role === 'admin') {
      return true
    }

    return Array.isArray(userInfo.value.roles) && userInfo.value.roles.includes('admin')
  })

  /**
   * 设置 token
   * @param value token
   */
  function setToken(value: string) {
    token.value = value
    tokenCookie.value = value
  }

  /**
   * 设置用户信息
   * @param value 用户信息
   */
  function setUserInfo(value: LoginUserInfo | null) {
    userInfo.value = value

    if (!import.meta.client) {
      return
    }

    if (value) {
      sessionStorage.setItem('user', JSON.stringify(value))
      return
    }

    sessionStorage.removeItem('user')
  }

  /**
   * 清理登录态
   */
  function clearAuth() {
    token.value = ''
    tokenCookie.value = null
    setUserInfo(null)
  }

  /**
   * 获取用户信息
   * @param force 是否强制刷新
   * @returns 用户信息
   */
  async function fetchUserInfo(force = false) {
    if (!token.value) {
      clearAuth()
      loaded.value = true
      return null
    }

    if (userInfo.value && !force) {
      loaded.value = true
      return userInfo.value
    }

    try {
      const response = await getUserInfoApi('web')
      if (Number(response?.code || 0) !== 200 || !response.data) {
        throw new Error(String(response?.message || '获取用户信息失败'))
      }

      setUserInfo(response.data)
      loaded.value = true
      return userInfo.value
    } catch (error) {
      loaded.value = true
      if (shouldClearAuthForError(error)) {
        clearAuth()
      }
      throw error
    }
  }

  /**
   * 退出登录
   */
  async function logout() {
    if (token.value) {
      await logoutApi().catch(() => null)
    }

    clearAuth()
  }

  return {
    token,
    userInfo,
    loaded,
    isLoggedIn,
    isAdmin,
    setToken,
    setUserInfo,
    clearAuth,
    fetchUserInfo,
    logout
  }
})
