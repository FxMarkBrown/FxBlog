import Cookies from 'js-cookie'

const TOKEN_KEY = 'blog_token'

export function getToken() {
  return Cookies.get(TOKEN_KEY) || ''
}

export function setToken(token: string) {
  return Cookies.set(TOKEN_KEY, token, { expires: 7 })
}

export function removeToken() {
  return Cookies.remove(TOKEN_KEY)
}

export function setCookieExpires(key: string, value: string | number, expires: number) {
  return Cookies.set(key, String(value), { expires })
}

export function getCookie(key: string) {
  return Cookies.get(key)
}

export function removeCookie(key: string) {
  return Cookies.remove(key)
}
