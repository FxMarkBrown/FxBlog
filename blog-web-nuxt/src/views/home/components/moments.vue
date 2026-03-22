<script setup lang="ts">
import { getMomentsApi } from '@/api/moments'
import type { MomentSummary } from '@/types/article'
import { unwrapResponseData } from '@/utils/response'

const router = useRouter()
const moments = ref<MomentSummary[]>([])
const currentIndex = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await getMomentsList()
  startRotation()
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})

/**
 * 获取说说列表
 */
async function getMomentsList() {
  const response = await getMomentsApi({ pageSize: 5, pageNum: 1 }).catch(() => null)
  const page = unwrapResponseData<{ records?: MomentSummary[] } | null>(response)
  moments.value = page?.records || []
  currentIndex.value = 0
}

/**
 * 跳转说说页
 */
function goToMoments() {
  router.push('/moments')
}

/**
 * 开始轮播
 */
function startRotation() {
  if (timer) {
    clearInterval(timer)
  }

  if (moments.value.length <= 1) {
    timer = null
    return
  }

  timer = setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % moments.value.length
  }, 4000)
}
</script>

<template>
  <div v-if="moments.length > 0" class="moments-list">
    <div class="moments-content">
      <div class="moments-row">
        <div class="moments-header">
          <i class="fas fa-comment-dots"></i>
          <span>最新说说:</span>
        </div>
        <Transition name="fade" mode="out-in">
          <div :key="currentIndex" class="moment-item">
            <span class="moment-text" @click="goToMoments" v-html="moments[currentIndex]?.content || ''" />
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.moments-list {
  background: var(--card-bg);
  border-radius: $border-radius-lg;
  padding: $spacing-md $spacing-lg;
  margin-bottom: $spacing-xl;
  box-shadow: $shadow-sm;
}

.moments-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.moments-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;

  i {
    color: $primary;
    font-size: 1.2em;
  }
}

.moment-item {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
  max-height: 20px;
  overflow: hidden;
}

.moment-text {
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
  cursor: pointer;
  display: inline-block;

  &:hover {
    color: $primary;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: all 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.fade-enter-to,
.fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
