<script setup lang="ts">
import { getSeriesArticlesApi, getSeriesListApi } from '@/api/series'
import ArticleList from '@/components/ArticleList/index.vue'
import PagePlaceholder from '@/components/Common/PagePlaceholder.vue'
import Sidebar from '@/components/Sidebar/index.vue'
import { usePageSeo } from '@/composables/useSeo'
import type { ArticleSummary, SeriesInfo } from '@/types/article'
import { unwrapResponseData } from '@/utils/response'

const route = useRoute()
const router = useRouter()

const seriesId = computed(() => Number(route.params.id || 0))

const { data: seriesDetailData, pending: seriesPending } = await useAsyncData(
  () => `series-detail:${seriesId.value}`,
  async () => {
    if (!seriesId.value) {
      return null
    }

    try {
      const [listResponse, articlesResponse] = await Promise.all([
        getSeriesListApi(),
        getSeriesArticlesApi(seriesId.value)
      ])
      const list = unwrapResponseData<SeriesInfo[] | null>(listResponse) || []
      const matched = list.find((item) => Number(item.id) === seriesId.value)
      if (!matched) {
        return null
      }

      return {
        series: {
          id: Number(matched.id),
          name: String(matched.name || ''),
          description: String(matched.description || '')
        },
        articles: unwrapResponseData<ArticleSummary[] | null>(articlesResponse) || []
      }
    } catch {
      return null
    }
  },
  {
    watch: [seriesId]
  }
)

const series = computed(() => seriesDetailData.value?.series || null)
const articles = computed(() => seriesDetailData.value?.articles || [])
const loading = computed(() => seriesPending.value)

// 系列不存在时直接抛出 404，走统一错误页
if (!series.value) {
  throw createError({ statusCode: 404, statusMessage: '系列不存在' })
}

usePageSeo({
  title: () => `${series.value?.name || '系列'} - 系列`,
  description: () =>
    series.value?.description || `阅读系列「${series.value?.name || ''}」的全部文章`,
  path: () => `/series/${seriesId.value}`
})

const listParams = computed(() => ({
  pageNum: 1,
  pageSize: Math.max(articles.value.length, 1)
}))

/**
 * 跳转到文章详情页。
 */
function goToPost(id: number | string) {
  router.push(`/post/${id}`)
}
</script>

<template>
  <div class="series-detail-page">
    <div class="content-layout">
      <main class="main-content">
        <template v-if="series">
          <div class="series-header content-card">
            <div class="breadcrumb">
              <NuxtLink to="/" class="crumb">首页</NuxtLink>
              <i class="fas fa-chevron-right separator"></i>
              <NuxtLink to="/series" class="crumb">系列</NuxtLink>
              <i class="fas fa-chevron-right separator"></i>
              <span class="crumb current">{{ series.name }}</span>
            </div>

            <div class="series-info">
              <h1 class="series-name">
                <i class="fas fa-book-open"></i>
                {{ series.name }}
              </h1>
              <p v-if="series.description" class="series-description">{{ series.description }}</p>
              <span class="article-count">
                <i class="fas fa-file-alt"></i>
                共 {{ articles.length }} 篇文章
              </span>
            </div>
          </div>

          <div v-loading="loading" class="articles-wrapper">
            <ArticleList
              :articles="articles"
              :total="articles.length"
              :params="listParams"
              @article-click="goToPost"
            />
          </div>
        </template>

        <PagePlaceholder v-else title="系列不存在" description="该系列可能已被删除或暂时不可见。" />
      </main>
      <Sidebar />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.series-detail-page {
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
}

.content-card {
  background: var(--card-bg);
  border-radius: $border-radius-lg;
  box-shadow: $shadow-md;
  padding: $spacing-lg;
}

.series-header {
  margin-bottom: $spacing-lg;

  .breadcrumb {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    font-size: 0.85em;
    color: var(--text-secondary);
    margin-bottom: $spacing-md;

    .crumb {
      color: var(--text-secondary);
      text-decoration: none;
      transition: color 0.3s ease;

      &:hover {
        color: $primary;
      }

      &.current {
        color: var(--text-primary);
        cursor: default;

        &:hover {
          color: var(--text-primary);
        }
      }
    }

    .separator {
      font-size: 0.7em;
      opacity: 0.5;
    }
  }

  .series-info {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  .series-name {
    margin: 0;
    font-size: 1.6em;
    color: $primary;
    display: flex;
    align-items: center;
    gap: $spacing-sm;

    i {
      font-size: 0.85em;
      opacity: 0.85;
    }
  }

  .series-description {
    margin: 0;
    color: var(--text-secondary);
    line-height: 1.7;
  }

  .article-count {
    align-self: flex-start;
    display: inline-flex;
    align-items: center;
    gap: $spacing-xs;
    padding: $spacing-xs $spacing-md;
    background: linear-gradient(120deg, rgba($primary, 0.1), rgba($primary, 0.05));
    border: 1px solid rgba($primary, 0.15);
    border-radius: $border-radius-lg;
    color: var(--text-secondary);
    font-size: 0.85em;

    i {
      color: $primary;
    }
  }
}

.articles-wrapper {
  animation: seriesFadeIn 0.5s ease-out;
}

@keyframes seriesFadeIn {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@include responsive(sm) {
  .series-detail-page {
    padding: $spacing-md;
  }

  .content-card {
    padding: $spacing-md;
    border-radius: 0;
    margin: -$spacing-md;
  }

  .series-header {
    margin-bottom: $spacing-md;

    .series-name {
      font-size: 1.3em;
    }
  }
}
</style>
