import type { GlobalThemeOverrides } from 'naive-ui'

/**
 * Naive UI 主题令牌覆盖。
 * 深色模式通过 n-config-provider 的 darkTheme 切换，此处令牌明暗通用。
 */
export const naiveFontFamily =
  "'LXGW WenKai', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', serif"

export const naiveThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#39c5bb',
    primaryColorHover: '#54cfc6',
    primaryColorPressed: '#2fa89e',
    primaryColorSuppl: '#39c5bb',
    infoColor: '#03a9f4',
    infoColorHover: '#2bb8f6',
    infoColorPressed: '#0288c7',
    borderRadius: '10px',
    borderRadiusSmall: '6px',
    fontFamily: naiveFontFamily
  },
  Button: {
    borderRadiusMedium: '999px',
    borderRadiusSmall: '999px',
    borderRadiusLarge: '999px'
  },
  Pagination: {
    itemBorderRadius: '999px'
  }
}
