import { computed, ref } from 'vue'
import { darkTheme } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'

/**
 * Naive UI 主题令牌覆盖。
 * 深色模式通过 darkTheme 切换，此处令牌明暗通用。
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

/**
 * 全站共享的 naive 明暗状态，由 utils/theme.ts 的 theme-change 事件驱动。
 * app.vue 的 n-config-provider 与 utils/feedback.ts 的 discrete API 共用，
 * 保证 teleport 弹层和命令式 message/dialog 都跟随主题。
 */
export const naiveIsDark = ref(false)

/** 在客户端初始化主题同步（app.vue onMounted 调用一次） */
export function initNaiveThemeSync() {
  if (!import.meta.client) {
    return
  }
  naiveIsDark.value = document.documentElement.hasAttribute('data-theme')
  window.addEventListener('theme-change', (event) => {
    naiveIsDark.value = (event as CustomEvent<{ mode: string }>).detail?.mode === 'dark'
  })
}

/** n-config-provider 的响应式 props（v-bind 使用；discrete API 内部 unref 同样支持） */
export const naiveConfigProviderProps = computed(() => ({
  theme: naiveIsDark.value ? darkTheme : null,
  themeOverrides: naiveThemeOverrides
}))
