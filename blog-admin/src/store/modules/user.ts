import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getUserInfoApi, logoutApi } from '@/api/system/auth'
import { store } from '@/store'
import { removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const createDefaultUserState = () => ({
    roles: [],
    intro: null,
    avatar: null,
    nickname: null,
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

export function useUserStoreHook() {
  return useUserStore(store)
}
