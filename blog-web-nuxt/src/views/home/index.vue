<script setup lang="ts">
import {ElMessage} from 'element-plus'
import {getAllCategoriesApi, getArticlesApi, getCarouselArticlesApi} from '@/api/article'
import ArticleList from '@/components/ArticleList/index.vue'
import Sidebar from '@/components/Sidebar/index.vue'
import {usePageSeo} from '@/composables/useSeo'
import type {ArticleCategoryGroup, ArticleSummary} from '@/types/article'
import type {PageResult} from '@/types/common'
import {unwrapResponseData} from '@/utils/response'
import Carousel from '@/views/home/components/carousel.vue'
import MomentsList from '@/views/home/components/moments.vue'

const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const siteStore = useSiteStore()
const loading = ref(false)
const total = ref(0)
const postsSection = ref<HTMLElement | null>(null)
const sidebarReady = ref(false)
const momentsReady = ref(false)
const params = reactive({
  pageNum: 1,
  pageSize: 10,
  categoryId: null as number | null
})
const articleList = ref<ArticleSummary[]>([])
const carouselSlides = ref<ArticleSummary[]>([])
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
const categories = ref<CategoryTab[]>([{ ...defaultCategory }])

usePageSeo({
  title: () => `${siteStore.websiteInfo.name || siteStore.websiteInfo.title || runtimeConfig.public.siteName}`,
  description: () => siteStore.websiteInfo.summary || siteStore.websiteInfo.description || '个人知识库',
  image: () => siteStore.websiteInfo.logo || runtimeConfig.public.seoImage
})

async function bootstrapHome() {
  await getArticleList()
  setTimeout(() => {
    void getCarouselArticles()
    void getAllCategories()
    momentsReady.value = true
    sidebarReady.value = true
  }, 160)
}

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
async function handleClick(tabName?: string | number) {
  const currentTabName = String(tabName ?? activeName.value)
  params.categoryId = currentTabName === 'all' ? null : normalizeCategoryId(currentTabName)
  params.pageNum = 1
  activeName.value = currentTabName
  await getArticleList()
}

/**
 * 切换页码
 * @param page 页码
 */
async function changePage(page: number) {
  params.pageNum = page
  await getArticleList()

  if (!import.meta.client) {
    return
  }

  const postsSectionTop = postsSection.value?.offsetTop ?? 80
  window.scrollTo({
    top: Math.max(postsSectionTop - 80, 0),
    behavior: 'smooth'
  })
}

/**
 * 获取文章列表
 */
async function getArticleList() {
  loading.value = true
  try {
    const requestParams: Record<string, unknown> = {
      ...params
    }

    if (requestParams.categoryId === null) {
      delete requestParams.categoryId
    }

    const response = await getArticlesApi(requestParams)
    const page = unwrapResponseData<PageResult<ArticleSummary> | null>(response)
    articleList.value = page?.records || []
    total.value = Number(page?.total || 0)
  } catch (error) {
    if (import.meta.client) {
      ElMessage.error((error as Error)?.message || '获取文章列表失败')
    }
  } finally {
    loading.value = false
  }
}

/**
 * 获取轮播文章
 */
async function getCarouselArticles() {
  const response = await getCarouselArticlesApi().catch(() => null)
  carouselSlides.value = unwrapResponseData<ArticleSummary[] | null>(response) || []
}

/**
 * 获取全部分类
 */
async function getAllCategories() {
  const response = await getAllCategoriesApi().catch(() => null)
  const icons = ['far fa-file-alt', 'fas fa-book-open', 'fas fa-feather-alt', 'fas fa-mug-hot', 'fas fa-bookmark', 'fas fa-pen-fancy']
  const categoriesData = unwrapResponseData<ArticleCategoryGroup[] | null>(response) || []

  categories.value = [
    { ...defaultCategory },
    ...categoriesData.map<CategoryTab>((category, index) => ({
      id: category.id ?? `category-${index}`,
      name: String(category.name || category.categoryName || '未命名分类'),
      icon: icons[index % icons.length] ?? defaultCategory.icon
    }))
  ]
}

onMounted(() => {
  void bootstrapHome()
})
</script>

<template>
  <div class="home">
    <div class="content-layout">
      <main class="home-main-content">
        <Carousel v-if="carouselSlides.length > 0" :slides="carouselSlides" @article-click="goToPost" />
        <MomentsList v-if="momentsReady" />

        <div ref="postsSection">
          <ElTabs v-model="activeName" @tab-change="handleClick">
            <ElTabPane v-for="category in categories" :key="category.id" :name="String(category.id)">
              <template #label>
                <span class="label-info">
                  <i :class="category.icon"></i>
                  {{ category.name }}
                </span>
              </template>
              <ArticleList
                :articles="articleList"
                :loading="loading"
                :total="total"
                :params="params"
                @article-click="goToPost"
                @page-change="changePage"
              />
            </ElTabPane>
          </ElTabs>
        </div>
      </main>
      <Sidebar v-if="sidebarReady" />
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

  .carousel {
    margin-bottom: $spacing-xl;
    width: 100%;
    max-height: 480px;

    @include responsive(md) {
      margin-bottom: $spacing-xl;
      max-height: 280px;

      :deep(h3) {
        font-size: 1.2em;
      }
    }
  }
}

:deep(.el-tabs__nav-scroll) {
  overflow-x: auto !important;

  &::-webkit-scrollbar {
    display: none;
  }
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.label-info {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  color: var(--text-primary);
}
</style>
