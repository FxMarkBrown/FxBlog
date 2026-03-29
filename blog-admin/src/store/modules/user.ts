import {ref} from 'vue'
import {defineStore} from 'pinia'
import {getUserInfoApi, logoutApi} from '@/api/system/auth'
import {removeToken} from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  interface UserState {
    roles: string[]
    intro: string | null
    avatar: string | null
    nickname: string | null
    username: string | null
    permissions: string[]
    [key: string]: unknown
  }

  const createDefaultUserState = (): UserState => ({
    roles: [],
    intro: null,
    avatar: null,
    nickname: null,
    username: null,
    permissions: []
  })

  const user = ref(createDefaultUserState())

  function getUserInfo() {
    return new Promise<any>((resolve, reject) => {
      getUserInfoApi()
        .then(({ data }) => {
          if (!data) {
            reject('用户信息校验失败')
            return
          }
          Object.assign(user.value, { ...data })
          resolve(data)
        })
        .catch((error) => {
          reject(error)
        })
    })
  }

  function logout() {
    return new Promise<void>((resolve) => {
      logoutApi()
        .catch(() => undefined)
        .finally(() => {
          removeToken()
          user.value = createDefaultUserState()
          resolve()
        })
    })
  }

  function resetToken() {
    return new Promise<void>((resolve) => {
      removeToken()
      user.value = createDefaultUserState()
      resolve()
    })
  }

  return {
    user,
    getUserInfo,
    logout,
    resetToken,
  }
})