<script setup lang="ts">
import { getAllCategoriesApi, getArticlesApi } from '@/api/article'
import ArticleList from '@/components/ArticleList/index.vue'
import Hero from '@/components/Hero/index.vue'
import Sidebar from '@/components/Sidebar/index.vue'
import { usePageSeo } from '@/composables/useSeo'
import type { ArticleCategoryGroup, ArticleSummary } from '@/types/article'
import type { PageResult } from '@/types/common'
import { message } from '@/utils/feedback'
import { unwrapResponseData } from '@/utils/response'
import MomentsList from '@/views/home/components/moments.vue'

const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const siteStore = useSiteStore()
const postsSection = ref<HTMLElement | null>(null)
const sidebarReady = ref(false)
const momentsReady = ref(false)
const params = reactive({
  pageNum: 1,
  pageSize: 10,
  categoryId: null as number | null
})
const activeName = ref('all')
type CategoryTab = {
  id: string | number
  name: string
  icon: string
}

const defaultCategory: CategoryTab = {
  id: 'all',
  name: '全部',
  icon: 'fas fa-layer-group'
}

usePageSeo({
  title: () =>
    `${siteStore.websiteInfo.name || siteStore.websiteInfo.title || runtimeConfig.public.siteName}`,
  description: () =>
    siteStore.websiteInfo.summary || siteStore.websiteInfo.description || '个人知识库',
  image: () => siteStore.websiteInfo.logo || runtimeConfig.public.seoImage
})

// 站点级 WebSite 结构化数据
useHead(() => {
  const siteUrl = String(runtimeConfig.public.siteUrl || '').replace(/\/+$/, '')
  return {
    script: [
      {
        type: 'application/ld+json',
        children: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'WebSite',
          name:
            siteStore.websiteInfo.name ||
            siteStore.websiteInfo.title ||
            runtimeConfig.public.siteName,
          url: siteUrl,
          description:
            siteStore.websiteInfo.summary || siteStore.websiteInfo.description || undefined,
          inLanguage: 'zh-CN'
        })
      }
    ]
  }
})

const {
  data: articlePageData,
  pending: articlePending,
  error: articleError
} = await useAsyncData(
  () => `home-articles:${params.categoryId ?? 'all'}:${params.pageNum}:${params.pageSize}`,
  async () => {
    const requestParams: Record<string, unknown> = {
      ...params
    }

    if (requestParams.categoryId === null) {
      delete requestParams.categoryId
    }

    const response = await getArticlesApi(requestParams)
    const page = unwrapResponseData<PageResult<ArticleSummary> | null>(response)
    return {
      records: page?.records || [],
      total: Number(page?.total || 0)
    }
  },
  {
    watch: [() => params.pageNum, () => params.categoryId]
  }
)

const { data: categoriesData } = await useAsyncData('home-categories', async () => {
  const response = await getAllCategoriesApi().catch(() => null)
  const icons = [
    'far fa-file-alt',
    'fas fa-book-open',
    'fas fa-feather-alt',
    'fas fa-mug-hot',
    'fas fa-bookmark',
    'fas fa-pen-fancy'
  ]
  const categoriesList = unwrapResponseData<ArticleCategoryGroup[] | null>(response) || []

  return [
    { ...defaultCategory },
    ...categoriesList.map<CategoryTab>((category, index) => ({
      id: category.id ?? `category-${index}`,
      name: String(category.name || category.categoryName || '未命名分类'),
      icon: icons[index % icons.length] ?? defaultCategory.icon
    }))
  ]
})

const articleList = computed(() => articlePageData.value?.records || [])
const total = computed(() => articlePageData.value?.total || 0)
const loading = computed(() => articlePending.value)
const categories = computed<CategoryTab[]>(() => categoriesData.value || [{ ...defaultCategory }])

// SSR 阶段的错误直接体现在空列表上；客户端重新拉取失败时提示
watch(articleError, (error) => {
  if (import.meta.client && error) {
    message.error((error as Error)?.message || '获取文章列表失败')
  }
})

/**
 * 规范化分类 ID
 * @param value 分类值
 * @returns 分类 ID
 */
function normalizeCategoryId(value: unknown) {
  const parsedValue = Number(value)
  return Number.isInteger(parsedValue) && parsedValue > 0 ? parsedValue : null
}

/**
 * 跳转文章详情
 * @param id 文章 ID
 */
function goToPost(id: number | string) {
  router.push(`/post/${id}`)
}

/**
 * 切换分类标签
 * @param tabName 标签名
 */
function handleClick(tabName?: string | number) {
  const currentTabName = String(tabName ?? activeName.value)
  params.categoryId = currentTabName === 'all' ? null : normalizeCategoryId(currentTabName)
  params.pageNum = 1
  activeName.value = currentTabName
}

/**
 * 切换页码
 * @param page 页码
 */
async function changePage(page: number) {
  params.pageNum = page
  await nextTick()

  if (!import.meta.client) {
    return
  }

  const postsSectionTop = postsSection.value?.offsetTop ?? 80
  window.scrollTo({
    top: Math.max(postsSectionTop - 80, 0),
    behavior: 'smooth'
  })
}

onMounted(() => {
  setTimeout(() => {
    momentsReady.value = true
    sidebarReady.value = true
  }, 160)
})
</script>

<template>
  <div class="home-page">
    <!-- Hero 放在 .home 容器外，避免 .home 的 padding 在大图四周留下白边 -->
    <Hero />
    <div class="home">
      <div class="content-layout">
        <main class="home-main-content">
          <MomentsList v-if="momentsReady" />

          <div ref="postsSection" class="posts-section">
            <div class="category-bar">
              <button
                v-for="category in categories"
                :key="category.id"
                type="button"
                class="category-pill"
                :class="{ active: activeName === String(category.id) }"
                @click="handleClick(category.id)"
              >
                <i :class="category.icon"></i>
                {{ category.name }}
              </button>
            </div>

            <ArticleList
              :articles="articleList"
              :loading="loading"
              :total="total"
              :params="params"
              @article-click="goToPost"
              @page-change="changePage"
            />
          </div>
        </main>
        <Sidebar v-if="sidebarReady" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.home {
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  padding: $spacing-lg;

  @include responsive(lg) {
    padding: $spacing-sm;
  }
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: $spacing-lg * 2;
  padding: 0 $spacing-xl;
  margin-bottom: $spacing-xl * 2;
  min-height: calc(100vh - 80px);
  align-items: start;

  @include responsive(lg) {
    grid-template-columns: 1fr;
    padding: $spacing-sm;
  }
}

.home-main-content {
  min-width: 0;
  width: 100%;
  height: 100%;
}

.category-bar {
  display: flex;
  gap: 10px;
  padding: $spacing-base 2px;
  margin-bottom: $spacing-lg;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.category-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 8px 18px;
  border: none;
  border-radius: 999px;
  background: var(--hover-bg);
  color: var(--text-secondary);
  font-size: 0.95em;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    color: var(--accent-color);
  }

  &.active,
  &.active:hover {
    background: var(--primary-color);
    color: #fff;
  }
}
</style>
