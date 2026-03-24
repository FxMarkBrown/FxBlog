declare module 'js-cookie' {
  export interface CookieAttributes {
    expires?: number | Date
    path?: string
    domain?: string
    secure?: boolean
    sameSite?: 'strict' | 'Strict' | 'lax' | 'Lax' | 'none' | 'None'
  }

  export interface CookiesStatic {
    get(name: string): string | undefined
    get(): Record<string, string>
    set(name: string, value: string, attributes?: CookieAttributes): string | undefined
    remove(name: string, attributes?: CookieAttributes): void
  }

  const Cookies: CookiesStatic
  export default Cookies
}
