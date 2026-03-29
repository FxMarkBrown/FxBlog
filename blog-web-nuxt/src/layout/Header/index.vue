<script setup lang="ts">
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const uiStore = useUiStore()
const headerRef = ref<HTMLElement | null>(null)
const isHeaderVisible = ref(true)
const lastScrollTop = ref(0)
const activeDropdown = ref<string | null>(null)
const showDropdown = ref(false)
let scrollFrame = 0
const siteTitle = computed(() => String(siteStore.websiteInfo.title || siteStore.websiteInfo.name || runtimeConfig.public.siteName || 'Open Source Blog'))
const showUserSection = computed(() => authStore.isLoggedIn)
const headerUserName = computed(() => String(authStore.userInfo?.nickname || authStore.userInfo?.username || '已登录用户'))
const headerUserRole = computed(() => authStore.isAdmin ? '管理员' : '普通用户')
const headerUserAvatar = computed(() => String(authStore.userInfo?.avatar || siteStore.websiteInfo.touristAvatar || ''))

interface MenuEntry {
  key?: string
  name: string
  path: string
  icon: string
  colorClass: string
  external?: boolean
  children?: MenuEntry[]
}

const menuItems: MenuEntry[] = [
  { name: '首页', path: '/', icon: 'fas fa-home', colorClass: 'home-link' },
  {
    name: '归档',
    path: '/archive',
    icon: 'fas fa-archive',
    colorClass: 'archive-link',
    children: [
      { name: '归档', path: '/archive', icon: 'fas fa-clock', colorClass: 'clock-link' },
      { name: '分类', path: '/categories', icon: 'fas fa-folder', colorClass: 'category-link' },
      { name: '标签', path: '/tags', icon: 'fas fa-tags', colorClass: 'tag-link' }
    ]
  },
  { name: '说说', path: '/moments', icon: 'fas fa-comment-dots', colorClass: 'talk-link' },
  { name: '相册', path: '/photos', icon: 'fas fa-images', colorClass: 'photos-link' },
  { name: '留言', path: '/messages', icon: 'fas fa-envelope', colorClass: 'message-link' },
  { name: '友链', path: '/friends', icon: 'fas fa-users', colorClass: 'friend-link' },
  { name: 'AI', path: '/ai', icon: 'fas fa-robot', colorClass: 'ai-link' },
  { key: 'about', name: '关于', path: '/about', icon: 'fas fa-info-circle', colorClass: 'about-link' }
]

const filteredMenuItems = computed(() => {
  const adminBaseUrl = runtimeConfig.public.adminUrl || 'http://localhost:3001'
  const adminPath = authStore.token
    ? `${adminBaseUrl}${adminBaseUrl.includes('?') ? '&' : '?'}token=${encodeURIComponent(authStore.token)}`
    : adminBaseUrl

  return menuItems.map((item) => {
    const resolvedItem =
      item.key === 'about' && authStore.isAdmin
        ? {
            name: '后台',
            path: adminPath,
            icon: 'fas fa-tv',
            colorClass: 'admin-link',
            external: true
          }
        : item

    return {
      ...resolvedItem,
      path: resolvedItem.children?.[0]?.path || resolvedItem.path
    }
  })
})

/**
 * 处理滚动
 */
function handleScroll() {
  if (!import.meta.client) {
    return
  }

  const currentScrollTop = window.pageYOffset || document.documentElement.scrollTop
  if (currentScrollTop < 100) {
    if (!isHeaderVisible.value) {
      isHeaderVisible.value = true
    }
    lastScrollTop.value = currentScrollTop
    return
  }

  const nextVisible = currentScrollTop <= lastScrollTop.value
  if (nextVisible !== isHeaderVisible.value) {
    isHeaderVisible.value = nextVisible
  }
  lastScrollTop.value = currentScrollTop
}

/**
 * 合并滚动更新
 */
function queueScrollUpdate() {
  if (scrollFrame || !import.meta.client) {
    return
  }

  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    handleScroll()
  })
}

/**
 * 打开移动菜单
 */
function handleOpenMobileMenu() {
  uiStore.setMobileMenuVisible(true)
}

/**
 * 打开搜索框
 */
function handleSearch() {
  uiStore.setSearchVisible(true)
}

/**
 * 鼠标移入菜单
 * @param item 菜单项
 */
function handleMouseEnter(item: MenuEntry) {
  if (item.children?.length) {
    activeDropdown.value = item.name
  }
}

/**
 * 鼠标移出菜单
 */
function handleMouseLeave() {
  activeDropdown.value = null
}

/**
 * 跳转登录页
 */
function handleLogin() {
  router.push('/login')
}

/**
 * 处理头像区域移入。
 */
async function handleAvatarMouseEnter() {
  showDropdown.value = true

  if (!authStore.token || authStore.userInfo) {
    return
  }

  await authStore.fetchUserInfo().catch(() => null)
}

/**
 * 退出登录
 */
async function handleLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  showDropdown.value = false
  siteStore.isUnread = false
  if (route.path !== '/') {
    await router.push('/')
  }
}

/**
 * 处理下拉菜单项点击
 * @param item 菜单项
 */
function handleDropdownItemClick(item: MenuEntry) {
  activeDropdown.value = null
  if (item.external && import.meta.client) {
    window.open(item.path, '_blank')
    return
  }

  router.push(item.path)
}

/**
 * 判断子菜单是否激活
 * @param child 子菜单
 * @returns 是否激活
 */
function isChildActive(child: MenuEntry) {
  return route.path === child.path
}

/**
 * 判断菜单是否激活
 * @param item 菜单项
 * @returns 是否激活
 */
function isActive(item: MenuEntry) {
  if (item.external) {
    return false
  }

  if (item.children?.length) {
    return item.children.some((child) => isChildActive(child))
  }

  if (item.path === '/ai') {
    return route.path === '/ai' || route.path.startsWith('/ai/')
  }

  return route.path === item.path
}

/**
 * 是否显示消息角标
 * @returns 是否显示
 */
function showBadge() {
  return authStore.isLoggedIn && siteStore.isUnread
}

/**
 * 处理文档点击
 * @param event 点击事件
 */
function handleDocumentClick(event: MouseEvent) {
  const userSection = headerRef.value?.querySelector('.user-section')
  const target = event.target as Node | null
  if (userSection && target && !userSection.contains(target)) {
    showDropdown.value = false
  }
}

onMounted(() => {
  window.addEventListener('scroll', queueScrollUpdate, { passive: true })
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', queueScrollUpdate)
  document.removeEventListener('click', handleDocumentClick)
  if (scrollFrame) {
    window.cancelAnimationFrame(scrollFrame)
    scrollFrame = 0
  }
})
</script>

<template>
  <header ref="headerRef" class="site-header" :class="{ 'header-hidden': !isHeaderVisible }">
    <nav class="navbar">
      <button class="menu-btn" @click="handleOpenMobileMenu">
        <i class="fas fa-bars"></i>
      </button>

      <div class="nav-left">
        <NuxtLink to="/" class="logo">
          <span class="logo-text">{{ siteTitle }}</span>
        </NuxtLink>
      </div>

      <div class="nav-center">
        <div
          v-for="item in filteredMenuItems"
          :key="item.path"
          class="nav-item"
          @mouseleave="handleMouseLeave"
        >
          <a
            v-if="item.external"
            :href="item.path"
            target="_blank"
            rel="noopener noreferrer"
            class="nav-link"
            :class="{ 'has-dropdown': item.children, active: isActive(item), [item.colorClass]: true }"
            @mouseenter="handleMouseEnter(item)"
          >
            <i :class="item.icon"></i>
            {{ item.name }}
            <i v-if="item.children" class="fas fa-chevron-down dropdown-icon"></i>
          </a>
          <NuxtLink
            v-else
            :to="item.path"
            class="nav-link"
            :class="{ 'has-dropdown': item.children, active: isActive(item), [item.colorClass]: true }"
            @mouseenter="handleMouseEnter(item)"
          >
            <i :class="item.icon"></i>
            {{ item.name }}
            <i v-if="item.children" class="fas fa-chevron-down dropdown-icon"></i>
          </NuxtLink>

          <div v-if="item.children" class="dropdown-menu" :class="{ active: activeDropdown === item.name }">
            <a
              v-for="child in item.children"
              :key="child.path"
              href="javascript:void(0)"
              class="dropdown-item"
              :class="{ active: isChildActive(child) }"
              @click="handleDropdownItemClick(child)"
            >
              <i :class="child.icon"></i>
              {{ child.name }}
            </a>
          </div>
        </div>
      </div>

      <div class="nav-right">
        <button class="search-btn" title="搜索" aria-label="搜索" @click="handleSearch">
          <i class="fas fa-search"></i>
        </button>

        <NuxtLink to="/notifications" class="message-btn">
          <i class="far fa-bell"></i>
          <span v-if="showBadge()" class="message-count" />
        </NuxtLink>

        <button class="mobile-search-btn" @click="handleSearch">
          <i class="fas fa-search"></i>
        </button>

        <div class="user-info">
          <div v-if="showUserSection" class="user-section" @mouseleave="showDropdown = false">
            <div class="avatar" @mouseenter="handleAvatarMouseEnter">
              <ElAvatar :src="headerUserAvatar" />
            </div>
            <div v-show="showDropdown" class="user-dropdown">
              <div class="dropdown-header">
                <img :src="headerUserAvatar" :alt="headerUserName" />
                <div class="user-details">
                  <span class="username">{{ headerUserName }}</span>
                  <span class="role">{{ headerUserRole }}</span>
                </div>
              </div>
              <div class="dropdown-divider"></div>
              <NuxtLink to="/user/profile" class="dropdown-item" @click="showDropdown = false">
                <i class="fas fa-user"></i>
                个人中心
              </NuxtLink>
              <div class="dropdown-item" @click="handleLogout">
                <i class="fas fa-sign-out-alt"></i>
                退出登录
              </div>
            </div>
          </div>
          <div v-else class="avatar" @click="handleLogin">
            <ElAvatar class="avatar-icon" :src="String(siteStore.websiteInfo.touristAvatar || '')" />
          </div>
        </div>
      </div>
    </nav>
  </header>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.site-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: rgba(var(--surface-rgb), 0.65);
  backdrop-filter: blur(10px) saturate(180%);
  -webkit-backdrop-filter: blur(10px) saturate(180%);
  border-bottom: 1px solid rgba(var(--border-color-rgb), 0.08);
  transition: all 0.3s ease;
  transform: translateY(0);

  &.header-hidden {
    transform: translateY(-100%);
    box-shadow: none;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to bottom, rgba(var(--surface-rgb), 0.05), rgba(var(--surface-rgb), 0));
    pointer-events: none;
  }
}

.navbar {
  padding: $spacing-sm $spacing-xl;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  height: 64px;
  position: relative;
}

.nav-left {
  margin-right: 50px;

  .logo {
    display: flex;
    align-items: center;
    text-decoration: none;
    gap: $spacing-sm;

    .logo-text {
      font-size: 1.2em;
      font-weight: 700;
      background: linear-gradient(135deg, $primary, $secondary);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-family: 'Poppins', sans-serif;
      letter-spacing: -0.5px;
    }

    @media screen and (max-width: 1400px) {
      .logo-text {
        max-width: 160px;
        font-size: 1.1em;
      }
    }

    @media screen and (max-width: 1200px) {
      .logo-text {
        max-width: 140px;
        font-size: 1em;
      }
    }

    @media screen and (max-width: 1000px) {
      .logo-text {
        max-width: 120px;
        font-size: 0.9em;
      }
    }
  }
}

.nav-center {
  display: flex;
  gap: $spacing-md;
  flex: 1;
  font-size: 1em;

  .nav-item {
    position: relative;
    white-space: nowrap;

    &:hover {
      .dropdown-menu {
        opacity: 1;
        visibility: visible;
        transform: translateX(-50%) translateY(0);
        pointer-events: auto;
      }

      .nav-link .dropdown-icon {
        transform: rotate(180deg);
      }
    }
  }

  .nav-link {
    color: var(--text-secondary);
    text-decoration: none;
    font-weight: 500;
    transition: all 0.3s ease;
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm;
    border-radius: $border-radius-md;
    font-size: 0.95em;
    position: relative;
    height: 40px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: 'Poppins', sans-serif;
    letter-spacing: 0.2px;

    &:hover,
    &.active {
      color: $primary;
    }

    &.has-dropdown {
      padding-right: $spacing-lg;

      .dropdown-icon {
        position: absolute;
        right: $spacing-sm;
        font-size: 0.8em;
        transition: transform 0.3s ease;
      }
    }

    &.home-link i { color: #4CAF50; }
    &.archive-link i { color: #9C27B0; }
    &.clock-link i { color: #00BCD4; }
    &.category-link i { color: #FF9800; }
    &.tag-link i { color: #E91E63; }
    &.talk-link i { color: #2196F3; }
    &.photos-link i { color: #9b36f4; }
    &.message-link i { color: #009688; }
    &.friend-link i { color: #3F51B5; }
    &.ai-link i { color: #00ACC1; }
    &.about-link i { color: #795548; }
    &.admin-link i { color: #5C6BC0; }

    &:hover i {
      transform: scale(1.1);
      animation: iconFloat 0.6s ease-in-out;
    }

    i {
      transition: transform 0.3s ease;
      font-size: 1.2em;
    }
  }

  @media screen and (max-width: 1400px) {
    .nav-link {
      font-size: 0.85em;
      padding: $spacing-sm $spacing-sm;
      gap: $spacing-xs;
    }
  }

  @media screen and (max-width: 1200px) {
    gap: $spacing-sm;

    .nav-link {
      font-size: 0.8em;
      padding: $spacing-xs $spacing-sm;

      i {
        font-size: 1em;
      }

      .dropdown-icon {
        display: none;
      }
    }
  }

  @media screen and (max-width: 1000px) {
    .nav-link {
      padding: $spacing-xs $spacing-xs;
      font-size: 0.75em;

      i {
        display: none;
      }
    }
  }
}

.nav-right {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-left: auto;
  position: relative;
  right: 0;

  .search-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    padding: 0;
    background: var(--hover-bg);
    border-radius: 20px;
    color: var(--text-secondary);
    text-decoration: none;
    transition: all 0.3s ease;
    border: 1px solid var(--border-color);

    i {
      font-size: 0.9em;
      transition: transform 0.3s ease;
    }

    &:hover {
      background: var(--surface);
      color: $primary;
      border-color: $primary;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba($primary, 0.1);

      i {
        transform: scale(1.1);
      }
    }
  }

  .message-btn {
    position: relative;
    display: flex;
    align-items: center;
    padding: 8px;
    color: var(--text-secondary);
    text-decoration: none;
    transition: all 0.3s ease;
    border-radius: 50%;

    i {
      font-size: 1.2em;
      transition: transform 0.3s ease;
    }

    &:hover {
      color: $primary;
      background: var(--hover-bg);

      i {
        transform: scale(1.1);
      }
    }

    .message-count {
      position: absolute;
      top: 2px;
      right: 2px;
      background: red;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      transform: none;
      padding: 0;
    }
  }

  .user-info {
    .user-section {
      position: relative;

      &::after {
        content: '';
        position: absolute;
        top: 100%;
        left: 0;
        width: 100%;
        height: 8px;
        background: transparent;
      }
    }

    .avatar {
      width: 40px;
      height: 40px;
      cursor: pointer;
      border-radius: 50%;
      overflow: hidden;
      transition: transform 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 2px solid $primary;

      &:hover {
        transform: scale(1.05);
        background: var(--hover-bg);
      }

      .avatar-icon {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .user-dropdown {
      position: absolute;
      top: calc(100% + 8px);
      right: 0;
      width: 240px;
      background: var(--surface);
      border-radius: 12px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
      overflow: hidden;
      z-index: 1000;
      animation: slideDown 0.2s ease;

      .dropdown-header {
        padding: 20px;
        display: flex;
        align-items: center;
        gap: 12px;
        background: linear-gradient(135deg, $primary, $secondary);
        color: #fff;

        img {
          width: 48px;
          height: 48px;
          border-radius: 50%;
          border: 2px solid rgba(255, 255, 255, 0.8);
          object-fit: cover;
        }

        .user-details {
          .username {
            display: block;
            font-weight: 500;
            font-size: 16px;
            color: inherit;
          }

          .role {
            display: block;
            font-size: 13px;
            opacity: 0.8;
            margin-top: 2px;
            color: inherit;
          }
        }
      }

      .dropdown-divider {
        height: 1px;
        background: var(--border-color);
        margin: 8px 0;
      }

      .dropdown-item {
        padding: 12px 20px;
        display: flex;
        align-items: center;
        gap: 12px;
        color: var(--text-primary);
        text-decoration: none;
        transition: all 0.2s;
        cursor: pointer;

        i {
          font-size: 16px;
          opacity: 0.8;
        }

        &:hover {
          background: var(--hover-bg);
          color: $primary;

          i {
            opacity: 1;
          }
        }
      }
    }
  }
}

.menu-btn,
.mobile-search-btn {
  display: none;
  background: none;
  border: none;
  padding: $spacing-sm;
  font-size: 1.2em;
  color: $text-secondary;
  cursor: pointer;
  transition: color 0.3s ease;

  &:hover {
    color: $primary;
  }
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(15px);
  background: var(--surface);
  border-radius: $border-radius-md;
  box-shadow: $shadow-lg;
  width: max-content;
  min-width: 120px;
  opacity: 0;
  visibility: hidden;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
  padding: $spacing-xs 0;

  &::before {
    content: '';
    position: absolute;
    top: -8px;
    left: 0;
    width: 100%;
    height: 8px;
    background: transparent;
  }

  .dropdown-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: 8px $spacing-md;
    height: 36px;
    color: var(--text-secondary);
    text-decoration: none;
    transition: all 0.3s ease;
    white-space: nowrap;
    font-size: 0.9em;

    i {
      width: 16px;
      text-align: center;
      font-size: 0.9em;
      color: var(--text-secondary);
      margin-right: 8px;
      font-family: 'Font Awesome 6 Free', 'Font Awesome 5 Free' !important;
      font-weight: 900;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    &:hover,
    &.active {
      color: $primary;
      background: var(--hover-bg);

      i {
        color: $primary;
      }
    }
  }

  &.active {
    opacity: 1;
    visibility: visible;
    transform: translateX(-50%) translateY(0);
    pointer-events: auto;
    display: block;
    animation: dropdownEnter 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
}

@include responsive(lg) {
  .navbar {
    padding: $spacing-sm $spacing-md;
  }

  .menu-btn,
  .mobile-search-btn {
    display: block;
  }

  .nav-center {
    display: none;
  }

  .nav-right .search-btn,
  .nav-right .message-btn,
  .nav-right .user-info {
    display: none;
  }

  .nav-left {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    margin-right: 0;
    width: calc(100% - 132px);
    display: flex;
    justify-content: center;

    .logo {
      max-width: 100%;
    }

    .logo .logo-text {
      max-width: 100%;
      font-size: 0.98rem;
      display: block;
      text-align: center;
      letter-spacing: -0.7px;
      overflow: visible;
      text-overflow: clip;
    }
  }
}

@include responsive(md) {
  .navbar {
    padding: $spacing-sm $spacing-md;
  }

  .menu-btn,
  .mobile-search-btn {
    display: block;
  }

  .nav-center,
  .nav-right .search-btn,
  .nav-right .user-info,
  .nav-right .message-btn {
    display: none;
  }

  .nav-left {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    margin-right: 0;
    width: calc(100% - 124px);
    display: flex;
    justify-content: center;

    .logo {
      max-width: 100%;
    }

    .logo .logo-text {
      max-width: 100%;
      font-size: 0.94rem;
      display: block;
      text-align: center;
      letter-spacing: -0.8px;
      overflow: visible;
      text-overflow: clip;
    }
  }
}

@media screen and (max-width: 480px) {
  .nav-left .logo .logo-text {
    max-width: 100%;
    font-size: 0.82rem;
    letter-spacing: -1px;
    overflow: visible;
    text-overflow: clip;
  }
}

@keyframes dropdownEnter {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes iconFloat {
  0% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
  100% {
    transform: translateY(0);
  }
}

:root[data-theme='dark'] {
  .site-header {
    background: rgba(var(--surface-rgb), 0.75);
    backdrop-filter: blur(10px) saturate(160%);
    -webkit-backdrop-filter: blur(10px) saturate(160%);
    border-bottom-color: rgba(var(--border-color-rgb), 0.15);
  }

  .user-dropdown {
    background: var(--surface);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);

    .dropdown-item {
      color: var(--text-primary);

      &:hover {
        background: var(--hover-bg);
      }
    }
  }

  .nav-link {
    &.home-link i { color: #81C784; }
    &.archive-link i { color: #CE93D8; }
    &.clock-link i { color: #4DD0E1; }
    &.category-link i { color: #FFB74D; }
    &.tag-link i { color: #F06292; }
    &.talk-link i { color: #64B5F6; }
    &.message-link i { color: #4DB6AC; }
    &.friend-link i { color: #7986CB; }
    &.about-link i { color: #A1887F; }
  }
}
</style>
