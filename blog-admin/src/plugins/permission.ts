import router from '@/router'
import {usePermissionStore} from '@/store/modules/permission'
import {useSettingsStore, useUserStore} from '@/store'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import {getToken, setToken} from '@/utils/auth'

NProgress.configure({ showSpinner: false })

const ADMIN_ROLE = 'admin'

function getSiteUrl() {
  return import.meta.env.VITE_APP_SITE_URL || '/'
}

function redirectToSite() {
  window.location.replace(getSiteUrl())
}

function getRouteToken(to: any) {
  return typeof to.query?.token === 'string' ? to.query.token : ''
}

function createCleanRoute(to: any) {
  const query = { ...to.query }
  delete query.token
  return {
    path: to.path,
    query,
    hash: to.hash,
    replace: true,
  }
}

function isAdminUser(user: any) {
  return Array.isArray(user?.roles) && user.roles.includes(ADMIN_ROLE)
}

export function setupPermission() {
  router.beforeEach(async (to, from, next) => {
    const dynamicTitle = useSettingsStore().dynamicTitle
    if (dynamicTitle && to.meta.title) {
      document.title = to.meta.title as string
    }

    NProgress.start()

    const routeToken = getRouteToken(to)
    if (routeToken) {
      setToken(routeToken)
    }

    const hasToken = getToken()
    if (!hasToken) {
      redirectToSite()
      NProgress.done()
      return
    }

    const userStore = useUserStore()
    const permissionStore = usePermissionStore()

    if (!userStore.user.nickname) {
      try {
        await userStore.getUserInfo()
        if (!isAdminUser(userStore.user)) {
          await userStore.resetToken()
          redirectToSite()
          NProgress.done()
          return
        }

        const accessRoutes = await permissionStore.generateRoutes()
        if (Array.isArray(accessRoutes) && accessRoutes.length > 0) {
          accessRoutes.forEach((route: any) => {
            if (route?.meta?.isExternal) {
              return
            }
            router.addRoute(route)
          })
        }

        next(routeToken ? createCleanRoute(to) : { ...to, replace: true })
      } catch (error) {
        console.error('Permission error:', error)
        await userStore.resetToken()
        redirectToSite()
        NProgress.done()
      }
      return
    }

    if (!isAdminUser(userStore.user)) {
      await userStore.resetToken()
      redirectToSite()
      NProgress.done()
      return
    }

    if (routeToken) {
      next(createCleanRoute(to))
      return
    }

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
