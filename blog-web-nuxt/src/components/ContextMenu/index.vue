<script setup lang="ts">
import {ElMessage} from 'element-plus'
import {getThemeMode, setThemeMode} from '@/utils/theme'

const router = useRouter()
const visible = ref(false)
const x = ref(0)
const y = ref(0)
const menuWidth = ref(0)
const menuHeight = ref(0)
const isDark = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const excludeSelectors = ['.chat-messages', '.image-preview']

const adjustedX = computed(() => {
  if (!import.meta.client) {
    return x.value
  }
  return x.value + menuWidth.value > window.innerWidth ? x.value - menuWidth.value : x.value
})

const adjustedY = computed(() => {
  if (!import.meta.client) {
    return y.value
  }
  return y.value + menuHeight.value > window.innerHeight ? y.value - menuHeight.value : y.value
})

onMounted(() => {
  isDark.value = getThemeMode() === 'dark'
  document.addEventListener('contextmenu', show)
  document.addEventListener('click', hide)
  window.addEventListener('theme-change', syncTheme)
})

onBeforeUnmount(() => {
  document.removeEventListener('contextmenu', show)
  document.removeEventListener('click', hide)
  window.removeEventListener('theme-change', syncTheme)
})

/**
 * 同步主题
 * @param event 主题事件
 */
function syncTheme(event: Event) {
  const customEvent = event as CustomEvent<{ mode?: string }>
  isDark.value = customEvent.detail?.mode === 'dark'
}

/**
 * 显示右键菜单
 * @param event 鼠标事件
 */
function show(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const isInExcludeArea = excludeSelectors.some((selector) => target?.closest(selector))
  if (isInExcludeArea) {
    return
  }

  event.preventDefault()
  x.value = event.clientX
  y.value = event.clientY
  visible.value = true

  nextTick(() => {
    if (!menuRef.value) {
      return
    }
    menuWidth.value = menuRef.value.offsetWidth
    menuHeight.value = menuRef.value.offsetHeight
  })
}

/**
 * 隐藏右键菜单
 */
function hide() {
  visible.value = false
}

/**
 * 刷新页面
 */
function handleRefresh() {
  window.location.reload()
  hide()
}

/**
 * 返回上页
 */
function handleBack() {
  router.back()
  hide()
}

/**
 * 前进下页
 */
function handleForward() {
  router.forward()
  hide()
}

/**
 * 复制链接
 */
async function handleCopyUrl() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  } finally {
    hide()
  }
}

/**
 * 切换主题
 */
function toggleTheme() {
  const mode = isDark.value ? 'light' : 'dark'
  isDark.value = mode === 'dark'
  setThemeMode(mode)
  hide()
}
</script>

<template>
  <div
    v-show="visible"
    ref="menuRef"
    class="context-menu"
    :class="{ 'menu-show': visible }"
    :style="{ left: `${adjustedX}px`, top: `${adjustedY}px` }"
  >
    <div class="menu-item" @click="handleRefresh">
      <i class="fas fa-sync" style="color: #409eff"></i>
      刷新页面
    </div>
    <div class="menu-item" @click="handleBack">
      <i class="fas fa-arrow-left" style="color: #67c23a"></i>
      返回上页
    </div>
    <div class="menu-item" @click="handleForward">
      <i class="fas fa-arrow-right" style="color: #e6a23c"></i>
      前进下页
    </div>
    <div class="menu-item" @click="handleCopyUrl">
      <i class="fas fa-copy" style="color: #909399"></i>
      复制链接
    </div>
    <div class="divider"></div>
    <div class="menu-item" @click="toggleTheme">
      <i :class="['fas', isDark ? 'fa-sun' : 'fa-moon']" :style="{ color: isDark ? '#E6A23C' : '#409EFF' }"></i>
      {{ isDark ? '浅色模式' : '深色模式' }}
    </div>
  </div>
</template>

<style scoped>
.context-menu {
  position: fixed;
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 5px 0;
  z-index: 9999;
  opacity: 0;
  transform: scale(0.95);
  transform-origin: top left;
}

.menu-show {
  animation: showMenu 0.2s ease forwards;
}

@keyframes showMenu {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.menu-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}

.menu-item:hover {
  background-color: var(--hover-bg);
}

.menu-item i {
  width: 14px;
}

.divider {
  height: 1px;
  background-color: var(--border-color);
  margin: 5px 0;
}
</style>
