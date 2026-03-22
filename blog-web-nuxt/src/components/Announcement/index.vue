<script setup lang="ts">
import { getCookie, setCookieExpires } from '@/utils/cookie'

const siteStore = useSiteStore()
const visible = ref(false)
const notice = ref<Record<string, unknown>>({})

const route = useRoute()

const topNotice = computed(() => {
  const topList = (siteStore.notice?.top as Array<Record<string, unknown>> | undefined) || []
  return topList.find((item) => getValidNotices([item]).length > 0) || null
})

watch(
  topNotice,
  (value) => {
    if (!value) {
      notice.value = {}
      visible.value = false
      return
    }

    notice.value = value
    if (import.meta.client) {
      visible.value = getCookie('notice') !== String(value.id || '')
    }
  },
  { immediate: true }
)

/**
 * 获取有效公告
 * @param list 公告列表
 * @returns 有效公告
 */
function getValidNotices(list: Array<Record<string, unknown>>) {
  return (list || []).filter((item) => {
    const content = String(item?.content || '')
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/gi, ' ')
      .trim()
    return content.length > 0
  })
}

/**
 * 关闭公告
 */
function close() {
  if (!notice.value?.id) {
    visible.value = false
    return
  }

  setCookieExpires('notice', String(notice.value.id), 365)
  visible.value = false
}
</script>

<template>
  <Transition name="slide-fade">
    <div v-if="visible && !route.meta.hideAnnouncement" class="announcement-container">
      <div class="announcement-content">
        <div class="announcement-wrapper">
          <div class="announcement-icon">
            <i class="fas fa-bullhorn"></i>
          </div>
          <div class="announcement-text">
            <span v-html="String(notice.content || '')" />
          </div>
        </div>
        <div class="announcement-close" @click="close">
          <i class="fas fa-times"></i>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
.announcement-container {
  width: 100%;
  background: $primary;
  padding: 10px 0;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.announcement-content {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  font-size: 0.95em;
  position: relative;
}

.announcement-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  overflow: hidden;
  max-width: 800px;
  position: relative;
  backdrop-filter: blur(8px);
  border-radius: 4px;
}

.announcement-icon {
  position: absolute;
  left: 30px;
  font-size: 16px;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  z-index: 1;
  color: #f59e0b;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.announcement-text {
  width: calc(100% - 70px);
  overflow: hidden;
  margin-left: 70px;
  position: relative;
  display: flex;
  justify-content: center;
  letter-spacing: 0.3px;

  span {
    white-space: nowrap;
    animation: scroll 20s linear infinite;
    padding-left: 100%;
    font-weight: 500;
    color: rgba(255, 255, 255, 0.95);
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);

    &:hover {
      animation-play-state: paused;
    }
  }
}

.announcement-close {
  position: absolute;
  right: 24px;
  margin-left: 16px;
  cursor: pointer;
  opacity: 0.8;
  transition: all 0.3s ease;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);

  &:hover {
    opacity: 1;
    background: rgba(255, 255, 255, 0.3);
    transform: rotate(90deg);
  }

  i {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.9);
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.1);
    opacity: 1;
  }
  100% {
    transform: scale(1);
    opacity: 0.8;
  }
}

@keyframes scroll {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-100%);
  }
}

.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
