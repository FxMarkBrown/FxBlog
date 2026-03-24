<script setup lang="ts">
import ImagePreview from '@/components/Common/ImagePreview.vue'
import { usePageSeo } from '@/composables/useSeo'

interface PreviewHandlerItem {
  image: HTMLImageElement
  handler: () => void
}

const siteStore = useSiteStore()
const contentRef = ref<HTMLElement | null>(null)
const imagePreview = ref<InstanceType<typeof ImagePreview> | null>(null)
const previewHandlers = ref<PreviewHandlerItem[]>([])

const aboutContent = computed(() => String(siteStore.websiteInfo.aboutMe || ''))

usePageSeo({
  title: '关于',
  description: '关于页面'
})

onMounted(async () => {
  if (!siteStore.loaded) {
    await siteStore.fetchWebsiteInfo().catch(() => null)
  }
})

watch(
  aboutContent,
  async () => {
    await nextTick()
    initImagePreview()
  },
  { flush: 'post' }
)

onBeforeUnmount(() => {
  cleanupImagePreview()
})

/**
 * 给富文本中的图片挂上预览点击事件。
 */
function initImagePreview() {
  if (!import.meta.client) {
    return
  }

  cleanupImagePreview()
  const contentElement = contentRef.value
  if (!contentElement) {
    return
  }

  const images = Array.from(contentElement.getElementsByTagName('img'))
  images.forEach((image) => {
    image.style.cursor = 'zoom-in'
    const handler = () => {
      imagePreview.value?.show(image.currentSrc || image.src)
    }
    image.addEventListener('click', handler)
    previewHandlers.value.push({ image, handler })
  })
}

/**
 * 清理已绑定的图片预览事件，避免重复监听。
 */
function cleanupImagePreview() {
  previewHandlers.value.forEach(({ image, handler }) => {
    image.removeEventListener('click', handler)
  })
  previewHandlers.value = []
}
</script>

<template>
  <div class="about-page">
    <ElCard class="about-card">
      <div ref="contentRef" class="about-content" v-html="aboutContent"></div>
    </ElCard>
    <ImagePreview ref="imagePreview" />
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.about-page {
  max-width: 1200px;
  margin: $spacing-lg auto $spacing-md;

  @include responsive(lg) {
    padding: $spacing-sm;
  }
}

.about-card {
  border-radius: 20px;
}

.about-content {
  padding: $spacing-lg;
  line-height: 1.8;
  color: var(--text-primary);
}

.about-content :deep(img) {
  max-width: 100%;
  height: auto;
}

@include responsive(sm) {
  .about-content {
    padding: $spacing-md;
  }

  .about-content :deep(img) {
    width: 100% !important;
  }
}
</style>
