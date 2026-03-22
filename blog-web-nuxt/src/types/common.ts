export interface ApiResponse<T> {
  code?: number
  msg?: string
  message?: string
  data?: T
  extra?: Record<string, unknown>
  [key: string]: unknown
}

export interface PageResult<T> {
  records?: T[]
  total?: number
  pageNum?: number
  pageSize?: number
  [key: string]: unknown
}
