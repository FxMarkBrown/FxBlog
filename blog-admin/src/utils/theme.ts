import type { SettingsState } from '@/store/modules/settings'

const THEME_MODE_KEY = 'admin-theme-mode'
const THEME_MODE_EXPIRE_KEY = 'admin-theme-mode-expire-at'
const THEME_TIMER_KEY = '__fxmbAdminThemeTimer__'

type ThemeMode = SettingsState['theme']

type ThemeStore = {
  theme: ThemeMode
  saveSettings: (settings: Partial<SettingsState>) => void
}

function getScheduledThemeMode(date = new Date()): ThemeMode {
  const hour = date.getHours()
  return hour >= 18 || hour < 6 ? 'dark' : 'light'
}

function getNextThemeBoundary(date = new Date()) {
  const next = new Date(date)
  const hour = date.getHours()

  if (hour < 6) {
    next.setHours(6, 0, 0, 0)
    return next
  }

  if (hour < 18) {
    next.setHours(18, 0, 0, 0)
    return next
  }

  next.setDate(next.getDate() + 1)
  next.setHours(6, 0, 0, 0)
  return next
}

function applyThemeMode(store: ThemeStore, mode: ThemeMode) {
  if (store.theme === mode) {
    document.documentElement.setAttribute('data-theme', mode)
    document.documentElement.classList.toggle('dark', mode === 'dark')
    return
  }

  store.saveSettings({ theme: mode })
}

function scheduleAutoThemeSwitch(store: ThemeStore) {
  const win = window as Window & { [THEME_TIMER_KEY]?: number }
  if (win[THEME_TIMER_KEY]) {
    clearTimeout(win[THEME_TIMER_KEY])
  }

  const nextBoundary = getNextThemeBoundary()
  const delay = Math.max(nextBoundary.getTime() - Date.now(), 1000)

  win[THEME_TIMER_KEY] = window.setTimeout(() => {
    setThemeMode(store, getScheduledThemeMode())
    scheduleAutoThemeSwitch(store)
  }, delay)
}

export function getThemeMode(): ThemeMode {
  const savedMode = localStorage.getItem(THEME_MODE_KEY) as ThemeMode | null
  const expireAt = Number(localStorage.getItem(THEME_MODE_EXPIRE_KEY) || 0)

  if ((savedMode === 'light' || savedMode === 'dark') && expireAt > Date.now()) {
    return savedMode
  }

  return getScheduledThemeMode()
}

export function setThemeMode(store: ThemeStore, mode: ThemeMode) {
  localStorage.setItem(THEME_MODE_KEY, mode)
  localStorage.setItem(THEME_MODE_EXPIRE_KEY, String(getNextThemeBoundary().getTime()))
  applyThemeMode(store, mode)
}

export function initTheme(store: ThemeStore) {
  applyThemeMode(store, getThemeMode())
  scheduleAutoThemeSwitch(store)
}
