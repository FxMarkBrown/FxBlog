<script setup lang="ts">
import type { InputInst } from 'naive-ui'
import { message } from '@/utils/feedback'
import { getArticlesApi } from '@/api/article'
import { getTagsApi } from '@/api/tags'
import type { ArticleSummary, TagSummary } from '@/types/article'
import type { PageResult } from '@/types/common'
import { unwrapResponseData } from '@/utils/response'

const router = useRouter()
const uiStore = useUiStore()
const loading = ref(false)
const searchResults = ref<ArticleSummary[]>([])
const tags = ref<TagSummary[]>([])
const total = ref(0)
const searchInput = ref<InputInst | null>(null)
const params = reactive({
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

// NModal 显隐双向绑定：关闭时走 close() 以重置关键词与结果
const searchVisible = computed({
  get: () => uiStore.searchVisible,
  set: (value) => {
    if (value) {
      uiStore.setSearchVisible(true)
    } else {
      close()
    }
  }
})

watch(
  () => uiStore.searchVisible,
  async (visible) => {
    if (!visible) {
      return
    }

    const response = await getTagsApi().catch(() => null)
    if (!response) {
      tags.value = []
      message.error('获取标签列表失败')
    } else {
      tags.value = unwrapResponseData<TagSummary[] | null>(response) || []
    }
    await nextTick()
    searchInput.value?.focus()
  }
)

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
})

/**
 * 处理分页
 * @param page 页码
 */
function handlePageChange(page: number) {
  params.pageNum = page
  void handleSearch()
}

/**
 * 搜索文章
 */
async function handleSearch() {
  if (!params.keyword.trim()) {
    searchResults.value = []
    total.value = 0
    return
  }

  loading.value = true
  try {
    const response = await getArticlesApi({ ...params })
    const page = unwrapResponseData<PageResult<ArticleSummary> | null>(response)
    searchResults.value = (page?.records || []).map((item) => ({
      ...item,
      title: truncateText(item.title, 50),
      summary: truncateText(item.summary || item.introduction || item.title, 150)
    }))
    total.value = Number(page?.total || 0)
  } catch (error) {
    searchResults.value = []
    total.value = 0
    message.error((error as Error)?.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

/**
 * 截断文本
 * @param text 文本
 * @param maxLength 最大长度
 * @returns 截断结果
 */
function truncateText(text: string | undefined, maxLength: number) {
  if (!text) {
    return ''
  }

  return text.length > maxLength ? `${text.substring(0, maxLength)}...` : text
}

/**
 * 高亮关键词
 * @param text 文本
 * @returns 高亮结果
 */
function highlightKeyword(text: string | undefined) {
  if (!params.keyword || !text) {
    return text || ''
  }

  let highlightedText = text
  for (const keyword of params.keyword.split(/\s+/).filter(Boolean)) {
    const regex = new RegExp(`(${escapeRegExp(keyword)})`, 'gi')
    highlightedText = highlightedText.replace(regex, '<mark>$1</mark>')
  }

  return highlightedText
}

/**
 * 转义正则字符串
 * @param value 字符串
 * @returns 转义结果
 */
function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 点击搜索结果
 * @param item 搜索结果
 */
function handleResultClick(item: ArticleSummary) {
  router.push(`/post/${item.id}`)
  close()
}

/**
 * 关闭搜索
 */
function close() {
  uiStore.setSearchVisible(false)
  params.keyword = ''
  params.pageNum = 1
  searchResults.value = []
  total.value = 0
}

/**
 * 处理键盘事件
 * @param event 键盘事件
 */
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
  }
}

/**
 * 清空搜索
 */
function clearSearch() {
  params.keyword = ''
  params.pageNum = 1
  searchResults.value = []
  total.value = 0
}

/**
 * 格式化日期
 * @param date 日期
 * @returns 日期字符串
 */
function formatDate(date?: string) {
  return date
    ? new Date(date).toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      })
    : ''
}
</script>

<template>
  <NModal
    v-model:show="searchVisible"
    preset="card"
    title="搜索"
    class="search-modal"
    :style="{ width: '650px' }"
  >
    <div class="search-input-row">
      <NInput
        ref="searchInput"
        v-model:value="params.keyword"
        class="search-input"
        size="large"
        round
        clearable
        placeholder="输入关键词搜索文章..."
        @keyup.enter="handleSearch"
        @clear="clearSearch"
      >
        <template #prefix>
          <i class="fas fa-search"></i>
        </template>
      </NInput>
      <span class="enter-tip">
        <i class="fas fa-level-down-alt fa-rotate-90"></i>
        按回车搜索
      </span>
    </div>

    <div v-if="searchResults.length === 0" class="hot-searches">
      <h4>
        <i class="fas fa-fire"></i>
        标签搜索
      </h4>
      <div class="hot-tags">
        <NuxtLink
          v-for="tag in tags"
          :key="tag.id"
          :to="`/tags?tagId=${tag.id}&tagName=${tag.name}`"
          class="hot-tag"
          @click="close"
        >
          {{ tag.name }}
        </NuxtLink>
      </div>
    </div>

    <div v-if="searchResults.length > 0" v-loading="loading" class="search-results">
      <div
        v-for="item in searchResults"
        :key="item.id"
        class="search-result-item"
        @click="handleResultClick(item)"
      >
        <div class="result-header">
          <h3 v-html="highlightKeyword(item.title)"></h3>
          <span class="result-date">{{ formatDate(item.createTime) }}</span>
        </div>
        <p v-html="highlightKeyword(item.summary)"></p>
        <div class="result-footer">
          <span class="result-category">
            <i class="fas fa-folder"></i>
            {{ item.categoryName }}
          </span>
          <span class="result-views">
            <i class="fas fa-eye"></i>
            {{ item.quantity }} 阅读
          </span>
        </div>
      </div>

      <div class="pagination-box">
        <NPagination
          v-if="total"
          :page="params.pageNum"
          :page-size="params.pageSize"
          :item-count="total"
          @update:page="handlePageChange"
        />
      </div>
    </div>

    <NEmpty v-if="searchResults.length === 0" description="输入关键词搜索文章.." />
  </NModal>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.search-input-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: 20px;

  .search-input {
    flex: 1;
    min-width: 0;

    :deep(.n-input__prefix) {
      color: var(--text-secondary);
    }
  }

  .enter-tip {
    flex-shrink: 0;
    color: var(--text-secondary);
    font-size: 12px;
    display: flex;
    align-items: center;
    opacity: 0.6;
    pointer-events: none;

    i {
      font-size: 14px;
      margin-right: 4px;
    }
  }

  @media (max-width: 600px) {
    .enter-tip {
      display: none;
    }
  }
}

.hot-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hot-tag {
  padding: 6px 12px;
  background-color: #f4f4f5;
  border-radius: 20px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;

  &:hover {
    color: #fff;
    background-color: $primary;
    transform: translateY(-2px);
  }
}

.hot-searches {
  margin-top: 30px;

  h4 {
    margin: 0 0 15px;
    color: #606266;
    font-size: 16px;
    display: flex;
    align-items: center;

    i {
      color: #ff9800;
      margin-right: 8px;
    }
  }
}

.search-results {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 10px;
  margin-top: $spacing-md;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: #e4e7ed;
    border-radius: 3px;

    &:hover {
      background-color: #c0c4cc;
    }
  }
}

.search-result-item {
  padding: 15px;
  cursor: pointer;
  border-radius: 8px;
  margin-bottom: 10px;
  transition: all 0.3s ease;
  border: 1px solid var(--border-color);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    border-color: var(--primary-color);
  }

  .result-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    h3 {
      margin: 0;
      font-size: 16px;
      color: $primary;
      flex: 1;
    }

    .result-date {
      font-size: 12px;
      color: #909399;
      margin-left: 10px;
    }
  }

  p {
    margin: 0 0 10px;
    font-size: 14px;
    color: #606266;
    line-height: 1.6;
  }

  .result-footer {
    display: flex;
    gap: 15px;
    font-size: 12px;
    color: #909399;

    i {
      margin-right: 4px;
    }

    .result-category,
    .result-views {
      display: flex;
      align-items: center;
    }
  }
}

.pagination-box {
  display: flex;
  justify-content: center;

  // 复刻原 el-pagination.is-background 观感：圆角页码、激活主色
  :deep(.n-pagination-item) {
    color: var(--text-secondary);
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 999px;
    transition: all 0.3s ease;

    &:hover {
      color: var(--primary-color);
      border-color: var(--primary-color);
    }

    &.n-pagination-item--active {
      background: var(--primary-color);
      color: #fff;
      border-color: var(--primary-color);
      font-weight: bold;

      &:hover {
        color: #fff;
      }
    }

    &.n-pagination-item--disabled {
      cursor: not-allowed;

      &:hover {
        color: var(--text-secondary);
        border-color: var(--border-color);
      }
    }
  }
}

:deep(mark) {
  background-color: rgba(202, 90, 210, 0.2);
  padding: 0 2px;
  border-radius: 2px;
  color: var(--primary-color);
  font-weight: 500;
  transition: all 0.3s ease;

  &:hover {
    background-color: rgba(202, 90, 210, 0.4);
  }
}
</style>
