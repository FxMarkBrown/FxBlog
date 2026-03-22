<script setup lang="ts">
import { getThemeMode, initTheme, setThemeMode } from '@/utils/theme'

const route = useRoute()
const router = useRouter()
const isDarkMode = ref(false)
const showBackToTop = ref(false)
let scrollFrame = 0

const showArticleAiEntry = computed(() => route.path.startsWith('/post/') && !!route.params.id)

/**
 * 同步主题状态
 */
function syncThemeState() {
  isDarkMode.value = getThemeMode() === 'dark'
}

/**
 * 切换主题
 */
function toggleTheme() {
  isDarkMode.value = !isDarkMode.value
  setThemeMode(isDarkMode.value ? 'dark' : 'light')
}

/**
 * 打开文章 AI
 */
function openArticleAi() {
  if (!showArticleAiEntry.value) {
    return
  }

  router.push({
    path: '/ai',
    query: {
      mode: 'article',
      articleId: String(route.params.id || '')
    }
  })
}

/**
 * 回到顶部
 */
function scrollToTop() {
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  })
}

/**
 * 处理滚动
 */
function handleScroll() {
  showBackToTop.value = window.pageYOffset > 300
}

/**
 * 合并滚动更新
 */
function queueScrollUpdate() {
  if (scrollFrame) {
    return
  }

  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    handleScroll()
  })
}

onMounted(() => {
  isDarkMode.value = initTheme()
  window.addEventListener('theme-change', syncThemeState)
  handleScroll()
  window.addEventListener('scroll', queueScrollUpdate, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', syncThemeState)
  window.removeEventListener('scroll', queueScrollUpdate)
  if (scrollFrame) {
    window.cancelAnimationFrame(scrollFrame)
    scrollFrame = 0
  }
})
</script>

<template>
  <div class="floating-buttons" :class="{ 'show-top': showBackToTop }">
    <ElTooltip
      v-if="showArticleAiEntry"
      content="问问这篇文章"
      placement="left"
      effect="light"
      popper-class="floating-tooltip"
      :teleported="false"
    >
      <button class="float-btn ai-btn" title="问问这篇文章" @click="openArticleAi">
        <i class="fas fa-robot"></i>
      </button>
    </ElTooltip>

    <ElTooltip content="切换主题" placement="left" effect="light" popper-class="floating-tooltip" :teleported="false">
      <button class="float-btn theme-btn" title="切换主题" @click="toggleTheme">
        <i :class="['fas', isDarkMode ? 'fa-sun' : 'fa-moon']"></i>
      </button>
    </ElTooltip>

    <ElTooltip content="回到顶部" placement="left" effect="light" popper-class="floating-tooltip" :teleported="false">
      <button v-show="showBackToTop" class="float-btn top-btn" title="回到顶部" @click="scrollToTop">
        <i class="fas fa-arrow-up"></i>
      </button>
    </ElTooltip>
  </div>
</template>

<style lang="scss" scoped>
.floating-buttons {
  position: fixed;
  right: 20px;
  bottom: 100px;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  z-index: 1000;
}

.float-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: var(--card-bg);
  color: var(--text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-lg;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0.8;

  &:hover {
    opacity: 1;
    transform: translateY(-2px);
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
  }

  i {
    font-size: 1.2em;
  }
}

:deep(.floating-tooltip.el-popper) {
  background: var(--card-bg) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14) !important;
}

:deep(.floating-tooltip.el-popper .el-popper__arrow::before) {
  background: var(--card-bg) !important;
  border-color: var(--border-color) !important;
}

.ai-btn {
  background: linear-gradient(135deg, #14b8a6, #0ea5e9);
  color: white;
}

.theme-btn {
  background: linear-gradient(135deg, $primary, $secondary);
  color: white;
}

.top-btn {
  transform: translateY(100px);
  opacity: 0;
  visibility: hidden;
  background: var(--card-bg);
  border: 1px solid var(--border-color);

  .show-top & {
    transform: translateY(0);
    opacity: 0.8;
    visibility: visible;
    animation: bounce-in 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
  }
}

@keyframes bounce-in {
  0% {
    transform: translateY(100px) scale(0.3);
    opacity: 0;
  }
  50% {
    transform: translateY(-10px) scale(1.1);
  }
  70% {
    transform: translateY(5px) scale(0.95);
  }
  100% {
    transform: translateY(0) scale(1);
    opacity: 0.8;
  }
}

@include responsive(sm) {
  .floating-buttons {
    right: 15px;
    bottom: 80px;
  }

  .float-btn {
    width: 36px;
    height: 36px;
  }
}
</style>
