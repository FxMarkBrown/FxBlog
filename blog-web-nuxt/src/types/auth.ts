export interface LoginUserInfo {
  id?: number | string
  username?: string
  nickname?: string
  avatar?: string
  sex?: number
  signature?: string
  token?: string
  permissions?: string[]
  roles?: string[]
  role?: string
  [key: string]: unknown
}
