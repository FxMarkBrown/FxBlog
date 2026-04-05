export default defineEventHandler((event) => {
  const requestUrl = event.node.req.url
  if (!requestUrl) {
    return
  }

  const queryIndex = requestUrl.indexOf('?')
  const pathname = queryIndex === -1 ? requestUrl : requestUrl.slice(0, queryIndex)
  const query = queryIndex === -1 ? '' : requestUrl.slice(queryIndex)
  const normalizedPathname = pathname === '//' ? '/' : pathname.replace(/\/{2,}/g, '/')

  if (normalizedPathname !== pathname) {
    event.node.req.url = `${normalizedPathname}${query}`
  }
})
