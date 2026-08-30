<script setup lang="ts">
import { getSeriesListApi } from '@/api/series'
import PagePlaceholder from '@/components/Common/PagePlaceholder.vue'
import Sidebar from '@/components/Sidebar/index.vue'
import { usePageSeo } from '@/composables/useSeo'
import type { SeriesInfo } from '@/types/article'
import { unwrapResponseData } from '@/utils/response'

const router = useRouter()
const runtimeConfig = useRuntimeConfig()

usePageSeo({
  title: () => `系列 - ${runtimeConfig.public.siteName}`,
  description: '按系列阅读成体系的专栏文章'
})

/**
 * 标准化系列列表数据。
 */
function normalizeSeries(payload: SeriesInfo[]) {
  return payload
    .map((item) => ({
      ...item,
      id: Number(item.id || 0),
      name: String(item.name || ''),
      description: String(item.description || ''),
      articleCount: Number(item.articleCount || 0)
    }))
    .filter((item) => item.id && item.name)
}

const { data: seriesData, pending: seriesPending } = await useAsyncData('series-list', async () => {
  const response = await getSeriesListApi()
  return normalizeSeries(unwrapResponseData<SeriesInfo[] | null>(response) || [])
})

const series = computed(() => seriesData.value || [])
const loading = computed(() => seriesPending.value)

/**
 * 跳转到系列详情页。
 */
function goToSeries(id: number) {
  router.push(`/series/${id}`)
}
</script>

<template>
  <div class="series-page">
    <div class="content-layout">
      <main class="main-content">
        <div v-if="series.length || loading" class="content-card">
          <div class="page-header">
            <h2 class="page-title">
              <i class="fas fa-book-open"></i>
              系列
            </h2>
            <p class="subtitle">共 {{ series.length }} 个系列</p>
          </div>

          <div v-if="series.length" class="series-grid">
            <div
              v-for="item in series"
              :key="item.id"
              class="series-card"
              @click="goToSeries(item.id)"
            >
              <h3 class="series-name">
                <i class="fas fa-book-open"></i>
                {{ item.name }}
              </h3>
              <p class="series-description">{{ item.description || '暂无简介' }}</p>
              <div class="series-meta">
                <span class="article-count">
                  <i class="fas fa-file-alt"></i>
                  {{ item.articleCount }} 篇文章
                </span>
                <i class="fas fa-arrow-right go-arrow"></i>
              </div>
            </div>
          </div>

          <div v-if="loading" class="loading-state">
            <i class="fas fa-spinner fa-spin"></i>
            加载中...
          </div>
        </div>

        <PagePlaceholder v-else title="暂无系列" description="博主还没有创建任何系列，敬请期待。" />
      </main>
      <Sidebar />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.series-page {
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

.content-card {
  background: var(--card-bg);
  border-radius: $border-radius-lg;
  box-shadow: $shadow-md;
  padding: $spacing-lg;
}

.page-header {
  margin-bottom: $spacing-lg;
  padding-bottom: $spacing-sm;
  border-bottom: 2px solid rgba($primary, 0.1);
  position: relative;

  &::after {
    content: '';
    position: absolute;
    bottom: -2px;
    left: 0;
    width: 50px;
    height: 2px;
    background: $primary;
  }

  .page-title {
    font-size: 1.5em;
    color: var(--text-primary);
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin: 0;

    i {
      color: $primary;
      font-size: 1em;
      width: 24px;
      text-align: center;
      opacity: 0.8;
    }
  }

  .subtitle {
    margin: $spacing-xs 0 0;
    color: var(--text-secondary);
    font-size: 0.9em;
  }
}

.series-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-lg;
}

.series-card {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  padding: $spacing-lg;
  border: 1px solid var(--border-color);
  border-radius: $border-radius-md;
  background: var(--card-bg);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba($primary, 0.4);
    box-shadow: 0 6px 16px rgba($primary, 0.12);

    .series-name i {
      transform: scale(1.1);
    }

    .go-arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }

  .series-name {
    font-size: 1.1em;
    color: $primary;
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin: 0;

    i {
      font-size: 0.9em;
      opacity: 0.85;
      transition: transform 0.3s ease;
    }
  }

  .series-description {
    flex: 1;
    color: var(--text-secondary);
    font-size: 0.9em;
    line-height: 1.6;
    margin: 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    line-clamp: 2;
    overflow: hidden;
  }

  .series-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: var(--text-secondary);
    font-size: 0.85em;

    .article-count {
      display: inline-flex;
      align-items: center;
      gap: $spacing-xs;
      padding: $spacing-xs $spacing-sm;
      background: var(--hover-bg);
      border-radius: $border-radius-lg;

      i {
        color: $primary;
      }
    }

    .go-arrow {
      opacity: 0;
      transform: translateX(-6px);
      color: $primary;
      transition: all 0.3s ease;
    }
  }
}

.loading-state {
  text-align: center;
  padding: $spacing-xl;
  color: var(--text-secondary);
  font-size: 1.1em;

  i {
    margin-right: $spacing-sm;
  }
}

@include responsive(sm) {
  .series-page {
    padding: $spacing-md;
  }

  .content-card {
    padding: $spacing-md;
    border-radius: 0;
    margin: -$spacing-md;
  }

  .series-grid {
    grid-template-columns: 1fr;
  }
}
</style>
