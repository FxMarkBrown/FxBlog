const THEME_MODE_KEY = 'theme-mode'
const THEME_MODE_EXPIRE_KEY = 'theme-mode-expire-at'
const THEME_TIMER_KEY = '__fxmbThemeTimer__'

function getScheduledThemeMode(date = new Date()) {
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

function applyThemeMode(mode: 'dark' | 'light') {
  if (!import.meta.client) {
    return
  }

  if (mode === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else {
    document.documentElement.removeAttribute('data-theme')
  }

  window.dispatchEvent(new CustomEvent('theme-change', { detail: { mode } }))
}

export function getThemeMode() {
  if (!import.meta.client) {
    return 'light'
  }

  const savedMode = localStorage.getItem(THEME_MODE_KEY)
  const expireAt = Number(localStorage.getItem(THEME_MODE_EXPIRE_KEY) || 0)

  if (savedMode && expireAt > Date.now()) {
    return savedMode as 'dark' | 'light'
  }

  return getScheduledThemeMode()
}

export function setThemeMode(mode: 'dark' | 'light') {
  if (!import.meta.client) {
    return
  }

  localStorage.setItem(THEME_MODE_KEY, mode)
  localStorage.setItem(THEME_MODE_EXPIRE_KEY, String(getNextThemeBoundary().getTime()))
  applyThemeMode(mode)
}

function scheduleAutoThemeSwitch() {
  if (!import.meta.client) {
    return
  }

  const themeWindow = window as Window & { [THEME_TIMER_KEY]?: number }
  if (themeWindow[THEME_TIMER_KEY]) {
    clearTimeout(themeWindow[THEME_TIMER_KEY])
  }

  const nextBoundary = getNextThemeBoundary()
  const delay = Math.max(nextBoundary.getTime() - Date.now(), 1000)
  themeWindow[THEME_TIMER_KEY] = window.setTimeout(() => {
    setThemeMode(getScheduledThemeMode())
    scheduleAutoThemeSwitch()
  }, delay)
}

export function initTheme() {
  const mode = getThemeMode()
  applyThemeMode(mode)
  scheduleAutoThemeSwitch()
  return mode === 'dark'
}
