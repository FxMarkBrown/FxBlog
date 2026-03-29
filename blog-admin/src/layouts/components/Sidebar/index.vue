<template>
    <div class="sidebar-container" :class="settingsStore.sidebarStyle">
      <div v-if="settingsStore.showLogo" class="logo-container" :class="{ 'dark': settingsStore.theme === 'dark' }">
        <span v-show="!isCollapse" class="logo-text" :class="{ 'light': settingsStore.theme === 'dark' ? false : settingsStore.sidebarStyle === 'light' }">FxMarkBrown's Blog</span>
      </div>
      <el-scrollbar>
        <el-menu style="height: 100%;"
          :default-active="String(activeMenu)"
          :collapse="isCollapse"
          :background-color="settingsStore.theme === 'dark' ? '#1d1e1f' : (settingsStore.sidebarStyle === 'light' ? '#ffffff' : '#304156')"
          :text-color="settingsStore.theme === 'dark' ? '#bfcbd9' : (settingsStore.sidebarStyle === 'light' ? '#303133' : '#bfcbd9')"
          :active-text-color="settingsStore.themeColor"
          :collapse-transition="false"
          @select="handleSelect"
          :unique-opened="true"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <menu-item v-if="!route.meta?.hidden" :route="route" :base-path="route.path" />
          </template>
        </el-menu>
      </el-scrollbar>
    </div>
  </template>
  
  <script setup lang="ts">
  import {computed} from 'vue'
  import {useRoute, useRouter} from 'vue-router'
  import {ElMessage} from 'element-plus'
  import {usePermissionStore} from '@/store/modules/permission'
  import {useSettingsStore} from '@/store/modules/settings'
  import {isExternal} from '@/utils/validate'
  import MenuItem from './MenuItem.vue'

  const route = useRoute()
  const permissionStore = usePermissionStore()
  const settingsStore = useSettingsStore()
  const router = useRouter()
  // 从 props 接收折叠状态
  defineProps({
    isCollapse: {
      type: Boolean,
      default: false
    }
  })
  
  // 获取路由菜单
  const menuRoutes = computed(() => {
    const routes = permissionStore.routes
    return routes.map(route => {
      // 如果是根路由且包含 dashboard 子路由
      if (route.path === '/' && route.children) {
        const dashboardRoute = route.children.find(child => child.path === 'dashboard')
        if (dashboardRoute) {
          // 将 dashboard 提升为一级路由
          return {
            ...dashboardRoute,
            path: '/dashboard',
            children: undefined
          }
        }
      }
      return route
    })
  })
  // 当前激活的菜单
  const activeMenu = computed(() => {
    const { meta, path } = route
    if (meta?.activeMenu) {
      return meta.activeMenu
    }
    return path
  })
  
  // 添加 select 事件处理函数
  const openExternalLink = (url: string) => {
    const openedWindow = window.open(url, '_blank', 'noopener,noreferrer')
    if (!openedWindow) {
      ElMessage.error('链接打开失败，请检查浏览器拦截设置')
    }
  }

  const handleSelect = (index: string) => {
    if (isExternal(index)) {
      openExternalLink(index)
      return
    }
    
    // 内部路由跳转
    if (route.path !== index) {
      router.push(index)
    }
  }
  </script>
  
  <style lang="scss" scoped>
  .sidebar-container {
    height: 100%;
    background-color: v-bind('settingsStore.theme === "dark" ? "#1d1e1f" : (settingsStore.sidebarStyle === "light" ? "#ffffff" : "#304156")');
    
    .logo-container {
      height: 60px;
      display: flex;
      align-items: center;
      padding: 0 20px;
      justify-content: center;
      background-color: v-bind('settingsStore.theme === "dark" ? "#1d1e1f" : (settingsStore.sidebarStyle === "light" ? "#ffffff" : "#304156")');
      
      .logo-text {
        color: #fff;
        font-size: 18px;
        font-weight: 600;
        white-space: nowrap;
        
        &.light {
          color: #303133;
        }
      }
    }

    :deep(.el-menu) {
      border-right: none;

      // 一级菜单样式
      .el-menu-item, .el-sub-menu__title {
        height: 56px;
        line-height: 56px;
        
        .el-icon {
          width: 24px;
          text-align: center;
          font-size: 18px;
          margin-right: 12px;
        }
      }

      // 激活状态
      .el-menu-item.is-active {
        background-color: v-bind('`${settingsStore.themeColor}1a`');
        &::before {
          content: '';
          position: absolute;
          left: 0;
          top: 50%;
          transform: translateY(-50%);
          width: 4px;
          height: 20px;
          background-color: v-bind('settingsStore.themeColor');
          border-radius: 0 4px 4px 0;
        }
      }

      // 悬停效果
      .el-menu-item:hover, .el-sub-menu__title:hover {
        background-color: v-bind('`${settingsStore.themeColor}0a`');
      }
    }
  }

  // 折叠状态样式
  :deep(.el-menu--collapse) {
    width: 64px;

    .el-menu-item, .el-sub-menu__title {
      padding: 0 20px !important;
      
      .el-icon {
        margin: 0 !important;
        width: 24px !important;
        text-align: center;
      }
    }

    // 隐藏文字和箭头
    .el-sub-menu__title span,
    .el-menu-item span,
    .el-sub-menu__title .el-sub-menu__icon-arrow {
      display: none;
    }
  }

  :deep(.el-scrollbar__view) {
    height: 100% !important;
  }

  // 子菜单样式
  :deep(.el-menu .el-menu) {
    background-color: v-bind('settingsStore.theme === "dark" ? "#181818" : (settingsStore.sidebarStyle === "light" ? "#ffffff" : "#263445")');
    .el-menu-item {
      height: 50px;
      line-height: 50px;
      
      &.is-active {
        background-color: v-bind('`${settingsStore.themeColor}1a`');
      }
    }
  }
  </style>
