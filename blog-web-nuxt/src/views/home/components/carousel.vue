<script setup lang="ts">
import type {ArticleSummary} from '@/types/article'
import {IMAGE_ERROR_PLACEHOLDER} from '@/utils/placeholders'

const props = defineProps<{
  slides: ArticleSummary[]
}>()

const emit = defineEmits<{
  articleClick: [id: number | string]
}>()
</script>

<template>
  <ElCarousel v-if="props.slides?.length" :interval="5000" class="custom-carousel">
    <ElCarouselItem v-for="slide in props.slides" :key="slide.id">
      <img :src="slide.cover || IMAGE_ERROR_PLACEHOLDER" :alt="slide.title">
      <div class="slide-content">
        <h3>{{ slide.title }}</h3>
        <p>{{ slide.summary || slide.introduction }}</p>
        <button class="read-more" @click="emit('articleClick', slide.id)">
          阅读更多
          <i class="fas fa-arrow-right"></i>
        </button>
      </div>
    </ElCarouselItem>
  </ElCarousel>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.custom-carousel {
  width: 100%;
  border-radius: $border-radius-lg;
  overflow: hidden;
  box-shadow: $shadow-lg;
  margin-bottom: $spacing-lg;

  :deep(.el-carousel__container) {
    height: 400px;

    @include responsive(sm) {
      height: 280px;
    }
  }
}

.el-carousel__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.slide-content {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: $spacing-xl;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.2) 20%, rgba(0, 0, 0, 0.8));
  color: white;

  h3 {
    font-size: 2.2em;
    margin-bottom: $spacing-md;
    font-weight: 600;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  }

  p {
    margin-bottom: $spacing-lg;
    opacity: 0.9;
    font-size: 1.2em;
    max-width: 800px;
  }
}

.read-more {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-lg;
  background: $primary;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: color.adjust($primary, $lightness: -10%);
    transform: translateX(5px);
  }
}
</style>
