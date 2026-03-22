<script setup lang="ts">
import { getArchivesApi } from '@/api/article'
import Sidebar from '@/components/Sidebar/index.vue'
import { usePageSeo } from '@/composables/useSeo'
import type { ArchiveGroup, ArticleSummary } from '@/types/article'
import { unwrapResponseData } from '@/utils/response'

const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const loading = ref(false)
const archives = ref<Array<{ year: string; posts: ArticleSummary[] }>>([])
const collapsedYears = reactive<Record<string, boolean>>({})
const visibleYears = reactive<Record<string, boolean>>({})
const yearGroupElements = new Map<string, HTMLElement>()
let yearObserver: IntersectionObserver | null = null

usePageSeo({
  title: () => `归档 - ${runtimeConfig.public.siteName}`,
  description: '文章归档列表'
})

await getArchives()

/**
 * 获取归档列表
 */
async function getArchives() {
  loading.value = true
  try {
    const response = await getArchivesApi()
    const result = unwrapResponseData<Array<{ year: string; posts: ArticleSummary[] }> | ArchiveGroup[] | null>(response) || []
    archives.value = normalizeArchives(result)
    for (const item of archives.value) {
      collapsedYears[item.year] = false
      visibleYears[item.year] = false
    }
  } finally {
    loading.value = false
  }
}

/**
 * 格式化月份
 * @param date 日期
 * @returns 月份
 */
function formatMonth(date?: string) {
  return date ? new Date(date).toLocaleString('zh-CN', { month: 'short' }) : ''
}

/**
 * 格式化日
 * @param date 日期
 * @returns 日期
 */
function formatDay(date?: string) {
  return date ? new Date(date).getDate().toString().padStart(2, '0') : ''
}

/**
 * 跳转文章详情
 * @param id 文章 ID
 */
function goToPost(id: number | string) {
  router.push(`/post/${id}`)
}

/**
 * 切换年份折叠
 * @param year 年份
 */
function toggleYear(year: string) {
  collapsedYears[year] = !collapsedYears[year]
}

/**
 * 记录年份块元素
 * @param year 年份
 * @param element 元素
 */
function setYearGroupRef(year: string, element: Element | null) {
  if (!element || !(element instanceof HTMLElement)) {
    yearGroupElements.delete(year)
    return
  }

  element.dataset.year = year
  yearGroupElements.set(year, element)
  yearObserver?.observe(element)
}

/**
 * 展开动画
 * @param element 元素
 */
function startTransition(element: Element) {
  const target = element as HTMLElement
  target.style.height = 'auto'
  const height = target.scrollHeight
  target.style.height = '0px'
  target.offsetHeight
  target.style.height = `${height}px`
}

/**
 * 收起动画
 * @param element 元素
 */
function endTransition(element: Element) {
  const target = element as HTMLElement
  target.style.height = `${target.scrollHeight}px`
  target.offsetHeight
  target.style.height = '0px'
}

/**
 * 清理过渡内联样式
 * @param element 元素
 */
function clearTransitionHeight(element: Element) {
  const target = element as HTMLElement
  target.style.height = 'auto'
}

/**
 * 归一化归档结构
 * @param payload 接口数据
 * @returns 页面可用归档结构
 */
function normalizeArchives(payload: Array<{ year: string; posts: ArticleSummary[] }> | ArchiveGroup[]) {
  return payload
    .map((item) => {
      const typedItem = item as ArchiveGroup & { year?: string; posts?: ArticleSummary[] }
      return {
        year: String(typedItem.year || typedItem.date || ''),
        posts: typedItem.posts || typedItem.articleList || []
      }
    })
    .filter((item) => item.year && item.posts.length > 0)
}

onMounted(() => {
  yearObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) {
          continue
        }

        const year = (entry.target as HTMLElement).dataset.year
        if (year) {
          visibleYears[year] = true
        }
        yearObserver?.unobserve(entry.target)
      }
    },
    {
      threshold: 0.15,
      rootMargin: '0px 0px -10% 0px'
    }
  )

  for (const [year, element] of yearGroupElements.entries()) {
    yearObserver.observe(element)
  }
})

onBeforeUnmount(() => {
  yearObserver?.disconnect()
  yearObserver = null
})
</script>

<template>
  <div class="archives-page">
    <div class="content-layout">
      <main class="main-content">
        <ElCard v-loading="loading" class="content-card">
          <div class="timeline">
            <div
              v-for="item in archives"
              :key="item.year"
              :ref="(element) => setYearGroupRef(item.year, element)"
              class="year-group"
              :class="{ 'is-visible': visibleYears[item.year] }"
            >
              <div class="year-header" @click="toggleYear(item.year)">
                <span class="year">{{ item.year }}</span>
                <span class="toggle-icon" :class="{ 'is-open': !collapsedYears[item.year] }">
                  <i class="fas fa-chevron-down"></i>
                </span>
              </div>
              <Transition name="expand" @enter="startTransition" @after-enter="clearTransitionHeight" @leave="endTransition">
                <div v-show="!collapsedYears[item.year]" class="posts-list">
                  <div v-for="post in item.posts" :key="post.id" class="post-item" @click="goToPost(post.id)">
                    <div class="post-date">
                      <span class="month">{{ formatMonth(post.createTime) }}</span>
                      <span class="day">{{ formatDay(post.createTime) }}</span>
                    </div>
                    <div class="post-info">
                      <h3 class="post-title">{{ post.title }}</h3>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>
          </div>
        </ElCard>
      </main>
      <Sidebar />
    </div>
  </div>
</template>

<style scoped lang="scss">
.archives-page {
  max-width: 1400px;
  margin: 0 auto;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: $spacing-xl * 2;
  padding: $spacing-lg;
  min-height: calc(100vh - 80px);
  align-items: start;

  @include responsive(lg) {
    grid-template-columns: 1fr;
    padding: $spacing-lg;
  }

  @include responsive(md) {
    padding: $spacing-md;
  }
}

.main-content {
  min-width: 0;
  height: 100%;
}

.timeline {
  position: relative;
  padding-left: $spacing-xl * 2;
  margin-top: $spacing-lg;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 2px;
    background: linear-gradient(to bottom, $primary, $secondary);
    animation: grow 1s ease-out forwards;
  }
}

.year-group {
  margin-bottom: $spacing-xl * 2;

  opacity: 0;
  transform: translateY(24px);
  transition: opacity 0.5s ease, transform 0.5s ease;

  &.is-visible {
    opacity: 1;
    transform: translateY(0);
  }

  &:last-child {
    margin-bottom: 0;
  }
}

.year-header {
  margin-bottom: $spacing-xl;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  padding-right: $spacing-md;

  &::before {
    content: '';
    position: absolute;
    left: -$spacing-xl * 2;
    top: 50%;
    width: $spacing-lg;
    height: 2px;
    background: $primary;
    animation: slideRight 0.5s ease-out forwards;
  }

  .year {
    font-size: 1.8em;
    font-weight: 700;
    color: var(--text-primary);
  }

  .toggle-icon {
    transition: transform 0.3s ease;

    &.is-open {
      transform: rotate(-180deg);
    }

    i {
      font-size: 0.8em;
      color: var(--text-secondary);
    }
  }

  &:hover {
    .toggle-icon i {
      color: $primary;
    }
  }
}

.post-item {
  display: flex;
  align-items: center;
  gap: $spacing-xl;
  padding: $spacing-md;
  border-radius: $border-radius-md;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--hover-bg);
    transform: translateX(10px);

    .post-date {
      border-color: $primary;
      background: rgba($primary, 0.1);
      animation: pulse 1.5s infinite;
    }
  }

  .post-date {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: $spacing-xs;
    border: 1px solid var(--border-color);
    border-radius: $border-radius-sm;
    transition: all 0.3s ease;
    min-width: 60px;

    .month {
      font-size: 0.9em;
      color: var(--text-secondary);
    }

    .day {
      font-size: 1.2em;
      font-weight: 600;
      color: var(--text-primary);
    }
  }

  .post-info {
    flex: 1;
    min-width: 0;

    .post-title {
      color: var(--text-primary);
      font-size: 1em;
      margin-bottom: 0;
      transition: color 0.3s ease;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      &:hover {
        color: $primary;
      }
    }
  }
}

@include responsive(md) {
  .archives-page {
    padding: $spacing-lg;
  }

  .content-card {
    padding: $spacing-lg;
  }

  .timeline {
    padding-left: $spacing-xl;
  }

  .year-header::before {
    left: -$spacing-xl;
  }
}

@include responsive(sm) {
  .archives-page {
    padding: $spacing-md;
  }

  .content-card {
    padding: $spacing-md;
    border-radius: 0;
    margin: -$spacing-md;
  }

  .post-item {
    gap: $spacing-md;

    .post-date {
      min-width: 50px;
    }
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba($primary, 0.4);
  }
  70% {
    box-shadow: 0 0 0 10px rgba($primary, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba($primary, 0);
  }
}

@keyframes grow {
  from {
    height: 0;
  }
  to {
    height: 100%;
  }
}

@keyframes slideRight {
  from {
    width: 0;
    opacity: 0;
  }
  to {
    width: $spacing-lg;
    opacity: 1;
  }
}

.posts-list {
  overflow: hidden;
  transition: height 0.3s ease-in-out;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease-in-out;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
}
</style>
