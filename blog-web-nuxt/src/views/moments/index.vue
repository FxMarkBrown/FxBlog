<script setup lang="ts">
import { getMomentsApi } from '@/api/moments'
import ImagePreview from '@/components/Common/ImagePreview.vue'
import { usePageSeo } from '@/composables/useSeo'
import type { MomentSummary } from '@/types/article'
import type { PageResult } from '@/types/common'
import { IMAGE_ERROR_PLACEHOLDER } from '@/utils/placeholders'
import { unwrapResponseData } from '@/utils/response'
import { formatTime } from '@/utils/time'

type MomentItem = MomentSummary & {
  id: number | string
  content: string
  createTime: string
  avatar: string
  nickname: string
  images: string[]
}

const runtimeConfig = useRuntimeConfig()
const loading = ref(false)
const imagePreview = ref<InstanceType<typeof ImagePreview> | null>(null)
const params = reactive({
  pageNum: 1,
  pageSize: 10
})

usePageSeo({
  title: () => `说说 - ${runtimeConfig.public.siteName}`,
  description: '查看最新说说动态'
})

/**
 * 解析说说图片列表字段。
 */
function parseImages(images: unknown) {
  if (Array.isArray(images)) {
    return images.map((item) => String(item)).filter(Boolean)
  }

  if (!images) {
    return []
  }

  return String(images)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

/**
 * 标准化说说列表数据。
 */
function normalizeMoments(records: MomentSummary[]) {
  return records.map((moment, index) => ({
    ...moment,
    id: moment.id || `moment-${index}`,
    content: String(moment.content || ''),
    createTime: String(moment.createTime || ''),
    avatar: String(moment.avatar || IMAGE_ERROR_PLACEHOLDER),
    nickname: String(moment.nickname || '博主'),
    images: parseImages(moment.images)
  }))
}

/**
 * 处理头像加载失败。
 */
function handleAvatarError(event: Event) {
  const target = event.target as HTMLImageElement
  target.src = IMAGE_ERROR_PLACEHOLDER
}

const { data: momentsPageData, pending: momentsPending } = await useAsyncData(
  () => `moments:${params.pageNum}:${params.pageSize}`,
  async () => {
    const response = await getMomentsApi({ ...params })
    const page = unwrapResponseData<PageResult<MomentSummary> | null>(response)
    return {
      records: normalizeMoments(page?.records || []),
      total: Number(page?.total || 0)
    }
  },
  {
    watch: [() => params.pageNum, () => params.pageSize]
  }
)

const moments = computed(() => momentsPageData.value?.records || [])
const total = computed(() => momentsPageData.value?.total || 0)

watch(momentsPending, (value) => {
  loading.value = value
}, { immediate: true })

/**
 * 切换说说分页并回到顶部。
 */
async function handlePageChange(page: number) {
  params.pageNum = page

  if (!import.meta.client) {
    return
  }

  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  })
}

/**
 * 预览说说图片。
 */
function previewImage(images: string[], index: number) {
  imagePreview.value?.show(images, index)
}
</script>

<template>
  <div class="moments-container">
    <div v-loading="loading" class="moments-list">
      <div v-for="moment in moments" :key="moment.id" class="moment-item">
        <div class="user-avatar">
          <img :src="moment.avatar" :alt="moment.nickname" class="avatar" @error="handleAvatarError">
          <div class="mobile-user-info">
            <span class="name">{{ moment.nickname }}</span>
            <span class="time">
              <i class="fas fa-clock"></i>
              {{ formatTime(moment.createTime) }}
            </span>
          </div>
        </div>
        <div class="moment-main">
          <div class="moment-header">
            <span class="name">{{ moment.nickname }}</span>
            <span class="time">
              <i class="fas fa-clock"></i>
              {{ formatTime(moment.createTime) }}
            </span>
          </div>
          <div class="moment-content-wrapper">
            <div class="moment-content" v-html="moment.content"></div>
            <div v-if="moment.images.length" class="moment-images">
              <img
                v-for="(img, index) in moment.images"
                :key="img"
                :src="img"
                :alt="`${moment.nickname}-${index + 1}`"
                @click="previewImage(moment.images, index)"
              >
            </div>
          </div>
        </div>
      </div>
      <div class="pagination-box">
        <ElPagination
          v-if="moments.length"
          background
          :current-page="params.pageNum"
          :page-size="params.pageSize"
          layout="prev, pager, next"
          :total="total"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <ImagePreview ref="imagePreview" />
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.moments-container {
  max-width: 800px;
  margin: 0 auto;
  min-height: calc(100vh - 70px);
}

.moment-item {
  border-radius: $border-radius-sm * 3;
  padding: $spacing-lg;
  margin-bottom: $spacing-sm;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  display: flex;
  gap: 16px;

  @media screen and (max-width: 768px) {
    flex-direction: column;
    gap: 12px;
    padding: $spacing-md;
  }
}

.user-avatar {
  flex-shrink: 0;
  width: 46px;

  .avatar {
    width: 46px;
    height: 46px;
    border-radius: $border-radius-md;
    object-fit: cover;
  }

  @media screen and (max-width: 768px) {
    width: auto;
    display: flex;
    align-items: center;
    gap: 12px;

    .avatar {
      width: 40px;
      height: 40px;
    }
  }
}

.moment-main {
  flex: 1;
  min-width: 0;

  @media screen and (max-width: 768px) {
    .moment-header {
      display: none;
    }
  }
}

.moment-header {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 10px;

  .name {
    font-size: 15px;
    font-weight: 500;
    color: var(--text-primary);
  }

  .time {
    font-size: 13px;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 4px;

    i {
      color: $primary;
    }
  }
}

.moment-content-wrapper {
  background: var(--card-bg);
  padding: 16px;
  border-radius: 0 $border-radius-lg * 2 $border-radius-lg * 2 $border-radius-lg * 2;

  @media screen and (max-width: 768px) {
    border-radius: $border-radius-lg;
  }

  .moment-content {
    color: var(--text-primary);
    line-height: 1.8;
    font-size: 15px;
    white-space: pre-wrap;
    word-break: break-word;

    :deep(li) {
      margin-left: 30px;
    }
  }

  .moment-images {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 8px;
    margin-top: 16px;

    @media screen and (max-width: 768px) {
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    }

    img {
      width: 100%;
      aspect-ratio: 16 / 9;
      object-fit: cover;
      border-radius: 8px;
      cursor: zoom-in;
    }
  }
}

.mobile-user-info {
  display: none;
  flex-direction: column;

  @media screen and (max-width: 768px) {
    display: flex;
  }

  .name {
    font-size: 15px;
    font-weight: 500;
    color: var(--text-primary);
  }

  .time {
    font-size: 13px;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: 2px;

    i {
      color: $primary;
    }
  }
}

.pagination-box {
  margin-top: $spacing-lg;
}
</style>
