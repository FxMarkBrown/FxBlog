export function useApiClient() {
  const config = useRuntimeConfig()
  const requestHeaders = import.meta.server ? useRequestHeaders(['cookie']) : {}
  const token = useCookie<string | null>('blog_token')
  const baseURL = import.meta.server ? config.apiBaseServer : '/'
  const headers: Record<string, string> = {}

  if (requestHeaders.cookie) {
    headers.cookie = requestHeaders.cookie
  }

  if (token.value) {
    headers.Authorization = token.value
  }

  return $fetch.create({
    baseURL,
    headers,
    credentials: 'include'
  })
}
