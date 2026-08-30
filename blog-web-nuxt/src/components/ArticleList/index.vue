<script setup lang="ts">
import type { ArticleSummary } from '@/types/article'
import AppPagination from '@/components/Common/AppPagination.vue'
import { IMAGE_ERROR_PLACEHOLDER } from '@/utils/placeholders'

interface ArticleListProps {
  articles: ArticleSummary[]
  loading?: boolean
  total?: number
  params: {
    pageNum: number
    pageSize: number
  }
}

const props = defineProps<ArticleListProps>()

const emit = defineEmits<{
  articleClick: [id: number | string]
  pageChange: [page: number]
}>()

const router = useRouter()

/**
 * 跳转到系列详情页
 * @param id 系列ID
 */
function goToSeries(id: number) {
  router.push(`/series/${id}`)
}

/**
 * 处理图片异常
 * @param event 图片事件
 */
function handleImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.src = IMAGE_ERROR_PLACEHOLDER
}

/**
 * 提取文章标签名列表
 * @param post 文章
 * @returns 标签名数组
 */
function getTagNames(post: ArticleSummary): string[] {
  const raw = post.tags ?? post.labels
  if (!Array.isArray(raw)) {
    return []
  }
  return raw
    .map((tag) => String((tag as { name?: unknown })?.name ?? tag ?? ''))
    .filter((name) => name.length > 0)
}
</script>

<template>
  <div v-loading="props.loading" class="article-list-component">
    <TransitionGroup name="post-list" tag="div" class="posts-list">
      <article
        v-for="post in props.articles"
        :key="post.id"
        class="post-item"
        :class="{ 'no-cover': !post.cover }"
      >
        <div v-if="post.cover" class="post-cover" @click="emit('articleClick', post.id)">
          <img :src="post.cover" :alt="post.title" @error="handleImageError" />
        </div>

        <div class="post-content">
          <div class="post-top">
            <NAvatar v-if="post.avatar" :size="24" round :src="post.avatar" />
            <span v-if="post.nickname" class="post-author">{{ post.nickname }}</span>
            <span class="post-date">{{ formatTime(post.createTime) }}</span>
          </div>

          <h2 class="post-title" @click="emit('articleClick', post.id)">
            <span v-if="post.isStick" class="stick-tag">
              <i class="fas fa-thumbtack"></i>
              置顶
            </span>
            {{ post.title }}
          </h2>

          <div class="post-meta">
            <span class="meta-item meta-view">
              <i class="fas fa-fire"></i>
              {{ post.quantity || 0 }}
            </span>
            <span class="meta-item meta-comment">
              <i class="far fa-comment-dots"></i>
              {{ post.commentNum || 0 }}
            </span>
            <span class="meta-item meta-like">
              <i class="far fa-heart"></i>
              {{ post.likeNum || 0 }}
            </span>
          </div>

          <p class="post-excerpt">{{ post.summary }}</p>

          <div class="post-tags">
            <span
              v-if="post.seriesName && post.seriesId"
              class="series-pill"
              @click.stop="goToSeries(post.seriesId)"
            >
              <i class="fas fa-book-open"></i>
              {{ post.seriesName }}
            </span>
            <span v-if="post.categoryName" class="tag-pill">
              <i class="fas fa-folder-open"></i>
              {{ post.categoryName }}
            </span>
            <span v-for="tagName in getTagNames(post)" :key="tagName" class="tag-pill">
              <i class="fas fa-tag"></i>
              {{ tagName }}
            </span>
          </div>
        </div>
      </article>
    </TransitionGroup>

    <NEmpty v-if="!props.loading && props.articles.length === 0" description="暂无文章" />

    <div class="pagination-box">
      <AppPagination
        v-if="props.articles.length"
        :page="props.params.pageNum"
        :page-size="props.params.pageSize"
        :item-count="props.total || 0"
        @update:page="emit('pageChange', $event)"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.posts-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

// Poetize 横向大卡：封面与内容各占一半，奇偶左右交替
.post-item {
  display: flex;
  height: 280px;
  background: var(--card-bg);
  border-radius: $border-radius-md;
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition: all 0.3s ease;

  &:hover {
    box-shadow: var(--shadow-card-hover);
  }

  &:nth-child(even) {
    flex-direction: row-reverse;
  }

  &.no-cover {
    .post-content {
      flex: 1 1 100%;
    }
  }
}

.post-cover {
  flex: 0 0 50%;
  overflow: hidden;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: all 1s ease;
  }

  &:hover img {
    transform: scale(1.2);
  }
}

.post-content {
  flex: 1 1 50%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: $spacing-lg;
}

.post-top {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  color: var(--text-secondary);
  font-size: 0.9em;
}

.post-author {
  font-weight: 500;
}

.post-date {
  margin-left: auto;
}

.post-title {
  margin: $spacing-sm 0;
  font-size: 1.25em;
  line-height: 1.4;
  color: var(--text-primary);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: all 0.3s ease;

  &:hover {
    color: var(--accent-color);
  }
}

.stick-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.6em;
  background: linear-gradient(135deg, $secondary, color.adjust($secondary, $lightness: -10%));
  color: white;
  padding: 3px 8px;
  border-radius: 4px;
  margin-right: $spacing-sm;
  vertical-align: middle;

  i {
    transform: rotate(45deg);
  }
}

.post-meta {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  margin-bottom: $spacing-sm;
  color: var(--text-secondary);
  font-size: 0.9em;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
}

.meta-view i {
  color: $accent;
}

.meta-comment i {
  color: $secondary;
}

.meta-like i {
  color: #ff4d6d;
}

.post-excerpt {
  flex: 1;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  line-clamp: 4;
  overflow: hidden;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}

.tag-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-base;
  padding: 4px 12px;
  background: var(--hover-bg);
  border-radius: 999px;
  font-size: 0.85em;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--accent-color);
    color: #fff;
  }
}

// 主色调系列徽章：与灰底 tag-pill 区分（参考 stick-tag 的主色用法）
.series-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-base;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 0.85em;
  color: #fff;
  background: linear-gradient(135deg, $primary, color.adjust($primary, $lightness: -10%));
  box-shadow: 0 2px 8px rgba($primary, 0.3);
  cursor: pointer;
  transition: all 0.3s ease;

  i {
    font-size: 0.9em;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba($primary, 0.45);
  }
}

.pagination-box {
  display: flex;
  justify-content: center;
  margin-top: $spacing-lg;
}

// ≤700px：封面移到顶部，变纵向卡
@media (max-width: 700px) {
  .post-item,
  .post-item:nth-child(even) {
    flex-direction: column;
    height: auto;
  }

  .post-cover {
    flex: 0 0 auto;
    width: 100%;
    height: 180px;
  }
}

.post-list-enter-active {
  transition: all 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  transition-delay: calc(0.1s * var(--index));
}

.post-list-leave-active {
  transition: all 0.6s ease;
}

.post-list-enter-from,
.post-list-leave-to {
  opacity: 0;
  transform: translateY(30px);
}
</style>
