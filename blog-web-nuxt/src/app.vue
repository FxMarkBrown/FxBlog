<script setup lang="ts">
import { getCookie, removeCookie } from '@/utils/cookie'

const WeatherDecor = defineAsyncComponent(() => import('@/components/WeatherDecor/index.vue'))
const MobileMenu = defineAsyncComponent(() => import('@/layout/MobileMenu/index.vue'))
const SearchDialog = defineAsyncComponent(() => import('@/components/Search/index.vue'))
const FloatingButtons = defineAsyncComponent(() => import('@/components/Common/FloatingButtons.vue'))
const Lantern = defineAsyncComponent(() => import('@/components/Lanterns/index.vue'))
const ContextMenu = defineAsyncComponent(() => import('@/components/ContextMenu/index.vue'))
import { initTheme } from '@/utils/theme'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const disableGlobalOverlays = computed(() => Boolean(route.meta.minimalShell))
const showFloatingButtons = computed(() => route.path !== '/ai')
let clickEffectHandler: ((event: MouseEvent) => void) | null = null

/**
 * 获取页面 key
 * @param currentRoute 当前路由
 * @returns 页面 key
 */
function getPageKey(currentRoute: { path: string; fullPath: string }) {
  return currentRoute.path === '/ai' ? currentRoute.path : currentRoute.fullPath
}

/**
 * 处理第三方登录回传 token
 */
async function handleThirdPartyLogin() {
  if (!import.meta.client) {
    return
  }

  const currentUrl = new URL(window.location.href)
  const token = currentUrl.searchParams.get('token')
  if (!token) {
    return
  }

  authStore.setToken(token)
  await authStore.fetchUserInfo(true).catch(() => null)
  await siteStore.fetchUnreadStatus().catch(() => null)
  currentUrl.searchParams.delete('token')
  await router.replace({
    path: currentUrl.pathname,
    query: Object.fromEntries(currentUrl.searchParams.entries())
  })
}

/**
 * 处理登录后跳转地址
 */
function handleRedirectUrl() {
  if (!import.meta.client) {
    return
  }

  const redirectUrl = getCookie('redirectUrl')
  if (!redirectUrl) {
    return
  }

  removeCookie('redirectUrl')
  window.location.href = redirectUrl
}

/**
 * 初始化点击涟漪效果
 */
function initCursorEffect() {
  if (!import.meta.client) {
    return
  }

  const container = document.querySelector('.cursor-container')
  if (!container) {
    return
  }

  clickEffectHandler = (event: MouseEvent) => {
    const cursor = document.createElement('div')
    cursor.className = 'cursor-fx'
    cursor.style.left = `${event.clientX}px`
    cursor.style.top = `${event.clientY}px`
    container.appendChild(cursor)
    cursor.addEventListener('animationend', () => {
      cursor.remove()
    })
  }

  document.addEventListener('click', clickEffectHandler)
}

onMounted(async () => {
  initTheme()
  initCursorEffect()
  await handleThirdPartyLogin()

  if (authStore.token && !authStore.userInfo) {
    await authStore.fetchUserInfo().catch(() => null)
    await siteStore.fetchUnreadStatus().catch(() => null)
  }

  handleRedirectUrl()
})

onBeforeUnmount(() => {
  if (clickEffectHandler) {
    document.removeEventListener('click', clickEffectHandler)
    clickEffectHandler = null
  }
})
</script>

<template>
  <div class="app-root">
    <ClientOnly>
      <WeatherDecor v-if="!disableGlobalOverlays" />
    </ClientOnly>
    <div class="app-shell">
      <NuxtLayout>
        <NuxtPage :page-key="getPageKey" />
      </NuxtLayout>
      <ClientOnly>
        <MobileMenu v-if="!disableGlobalOverlays" />
        <SearchDialog v-if="!disableGlobalOverlays" />
        <FloatingButtons v-if="!disableGlobalOverlays && showFloatingButtons" />
        <Lantern v-if="!disableGlobalOverlays" />
        <ContextMenu v-if="!disableGlobalOverlays" />
      </ClientOnly>
      <div class="cursor-container"></div>
    </div>
  </div>
</template>

<style lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.app-root {
  position: relative;
  isolation: isolate;
}

.app-shell {
  position: relative;
  z-index: 2;
}

.cursor-container {
  pointer-events: none;
  position: fixed;
  inset: 0;
  z-index: 9999;
}

.cursor-fx {
  pointer-events: none;
  position: fixed;
  width: 20px;
  height: 20px;
  border: 2px solid $primary;
  border-radius: 50%;
  transform: translate(-50%, -50%) scale(0);
  animation: cursor-fx 0.5s ease-out forwards;
}

@keyframes cursor-fx {
  0% {
    transform: translate(-50%, -50%) scale(0);
    opacity: 1;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.5);
    opacity: 0.5;
  }
  100% {
    transform: translate(-50%, -50%) scale(2);
    opacity: 0;
  }
}
</style>
