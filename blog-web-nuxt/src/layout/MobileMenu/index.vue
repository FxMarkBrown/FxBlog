<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const uiStore = useUiStore()
const siteTitle = computed(() => String(siteStore.websiteInfo.title || siteStore.websiteInfo.name || runtimeConfig.public.siteName || 'Open Source Blog'))

interface MobileMenuEntry {
  path: string
  title: string
  icon: string
  external?: boolean
}

const routes: MobileMenuEntry[] = [
  { path: '/', title: '首页', icon: 'fas fa-home' },
  { path: '/archive', title: '归档', icon: 'fas fa-archive' },
  { path: '/categories', title: '分类', icon: 'fas fa-folder' },
  { path: '/tags', title: '标签', icon: 'fas fa-tags' },
  { path: '/moments', title: '说说', icon: 'fas fa-comment-dots' },
  { path: '/photos', title: '相册', icon: 'fas fa-images' },
  { path: '/messages', title: '留言', icon: 'fas fa-envelope' },
  { path: '/friends', title: '友链', icon: 'fas fa-users' },
  { path: '/ai', title: '对话', icon: 'fas fa-robot' }
]

const shortcutEntries = computed(() => {
  if (!authStore.isLoggedIn) {
    return []
  }

  return [
    {
      path: '/notifications',
      title: '消息通知',
      icon: 'far fa-bell',
      badge: siteStore.isUnread
    },
    {
      path: '/user/profile',
      title: '个人中心',
      icon: 'fas fa-user'
    }
  ]
})

const authEntry = computed<MobileMenuEntry | null>(() => {
  if (authStore.isLoggedIn) {
    return null
  }

  return {
    path: '/login',
    title: '登录',
    icon: 'fas fa-right-to-bracket'
  }
})

const aboutEntry = computed<MobileMenuEntry>(() => {
  if (!authStore.isAdmin) {
    return {
      path: '/about',
      title: '关于',
      icon: 'fas fa-info-circle'
    }
  }

  const adminBaseUrl = runtimeConfig.public.adminUrl || 'http://localhost:3001'
  const adminPath = authStore.token
    ? `${adminBaseUrl}${adminBaseUrl.includes('?') ? '&' : '?'}token=${encodeURIComponent(authStore.token)}`
    : adminBaseUrl

  return {
    path: adminPath,
    title: '后台',
    icon: 'fas fa-tv',
    external: true
  }
})

/**
 * 关闭菜单
 */
function closeMenu() {
  uiStore.setMobileMenuVisible(false)
}

/**
 * 退出登录
 */
async function handleLogout() {
  await authStore.logout()
  siteStore.isUnread = false
  closeMenu()
  if (route.path !== '/') {
    await router.push('/')
  }
}
</script>

<template>
  <ClientOnly>
    <ElDrawer
      :model-value="uiStore.mobileMenuVisible"
      class="mobile-menu-drawer"
      direction="ltr"
      :with-header="false"
      size="clamp(224px, 72vw, 304px)"
      @update:model-value="uiStore.setMobileMenuVisible"
    >
      <div class="mobile-menu">
        <div class="menu-header">
          <h2 class="site-name">{{ siteTitle }}</h2>
        </div>
        <div class="menu-content">
          <TransitionGroup name="menu-item">
            <NuxtLink
              v-for="menu in routes"
              :key="menu.path"
              :to="menu.path"
              class="menu-item"
              :class="{ active: route.path === menu.path }"
              @click="closeMenu"
            >
              <i :class="menu.icon"></i>
              <span>{{ menu.title }}</span>
            </NuxtLink>

            <NuxtLink
              v-for="entry in shortcutEntries"
              :key="entry.path"
              :to="entry.path"
              class="menu-item shortcut-item"
              :class="{ active: route.path === entry.path }"
              @click="closeMenu"
            >
              <i :class="entry.icon"></i>
              <span>{{ entry.title }}</span>
              <span v-if="entry.badge" class="menu-badge"></span>
            </NuxtLink>

            <NuxtLink
              v-if="authEntry"
              :key="authEntry.path"
              :to="authEntry.path"
              class="menu-item shortcut-item"
              :class="{ active: route.path === authEntry.path }"
              @click="closeMenu"
            >
              <i :class="authEntry.icon"></i>
              <span>{{ authEntry.title }}</span>
            </NuxtLink>

            <a
              v-if="aboutEntry.external"
              :key="`about-${aboutEntry.title}`"
              :href="aboutEntry.path"
              target="_blank"
              rel="noopener noreferrer"
              class="menu-item shortcut-item"
              @click="closeMenu"
            >
              <i :class="aboutEntry.icon"></i>
              <span>{{ aboutEntry.title }}</span>
            </a>
            <NuxtLink
              v-else
              :key="`about-${aboutEntry.title}`"
              :to="aboutEntry.path"
              class="menu-item shortcut-item"
              :class="{ active: route.path === aboutEntry.path }"
              @click="closeMenu"
            >
              <i :class="aboutEntry.icon"></i>
              <span>{{ aboutEntry.title }}</span>
            </NuxtLink>

            <button v-if="authStore.isLoggedIn" key="logout" type="button" class="menu-item menu-action" @click="handleLogout">
              <i class="fas fa-sign-out-alt"></i>
              <span>退出登录</span>
            </button>
          </TransitionGroup>
        </div>
        <div class="menu-footer">
          <p>© {{ new Date().getFullYear() }} {{ siteTitle }}</p>
        </div>
      </div>
    </ElDrawer>
  </ClientOnly>
</template>

<style scoped lang="scss">
:deep(.mobile-menu-drawer .el-drawer__body) {
  padding: 0;
  background: var(--card-bg);
}

:deep(.mobile-menu-drawer .el-drawer) {
  box-shadow: 12px 0 36px rgba(15, 23, 42, 0.16);
  border-right: 1px solid var(--border-color);
  border-radius: 0 22px 22px 0 !important;
  overflow: hidden !important;
  background: var(--card-bg);
}

:deep(.mobile-menu-drawer .el-drawer__body) {
  border-radius: inherit;
  overflow: hidden;
}

.mobile-menu {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
}

.menu-header {
  padding: 24px 20px 18px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 12px;
}

.site-name {
  font-size: 1.18rem;
  font-weight: 600;
  line-height: 1.28;
  letter-spacing: 0.01em;
  color: var(--text-primary);
  margin: 0;
}

.menu-content {
  flex: 1;
  padding: 14px 14px 18px;
  overflow-y: auto;
}

.menu-item {
  display: flex;
  align-items: center;
  min-height: 54px;
  padding: 14px 16px;
  color: var(--text-primary);
  text-decoration: none;
  border-radius: 14px;
  margin-bottom: 10px;
  transition: all 0.3s ease;
}

.menu-item i {
  width: 20px;
  margin-right: 14px;
  font-size: 18px;
  text-align: center;
  transition: transform 0.3s ease;
}

.menu-item span {
  flex: 1;
  font-size: 0.96rem;
  font-weight: 500;
  line-height: 1.35;
}

.shortcut-item {
  margin-top: 2px;
}

.menu-action {
  width: 100%;
  background: none;
  border: 0;
  font: inherit;
  color: var(--text-primary);
  border: none;
  cursor: pointer;
  text-align: left;
}

.menu-action i {
  color: #ef4444;
}

.menu-action span {
  color: var(--text-primary);
}

.menu-badge {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex: 0 0 auto;
}

.menu-item:hover {
  background-color: var(--hover-bg);
  transform: translateX(4px);
}

.menu-item:hover i {
  transform: scale(1.1);
}

.menu-item.active {
  background-color: var(--primary-color, #1890ff);
  color: #fff;
}

.menu-footer {
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px));
  text-align: center;
  color: var(--text-secondary);
  font-size: 0.84rem;
  line-height: 1.35;
  border-top: 1px solid var(--border-color);
}

.menu-item-enter-active,
.menu-item-leave-active {
  transition: all 0.3s ease;
}

.menu-item-enter-from,
.menu-item-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

@include responsive(sm) {
  :deep(.mobile-menu-drawer.el-drawer__container .el-drawer) {
    width: clamp(224px, 72vw, 304px) !important;
  }
}
</style>
