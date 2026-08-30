<script setup lang="ts">
/**
 * 全站统一分页：封装 NPagination。
 * - 移动端（≤640px）把页码槽位从 9 降到 5，防止页码条溢出屏幕
 * - 胶囊页码样式集中在这里，各页面不再重复书写
 * props/事件（page、page-size、item-count、update:page 等）经属性透传直达 NPagination。
 */
const MOBILE_PAGE_SLOT = 5
const DESKTOP_PAGE_SLOT = 9

const pageSlot = ref(DESKTOP_PAGE_SLOT)

/**
 * 按视口宽度同步页码槽位数
 */
function syncPageSlot() {
  pageSlot.value = window.innerWidth <= 640 ? MOBILE_PAGE_SLOT : DESKTOP_PAGE_SLOT
}

onMounted(() => {
  syncPageSlot()
  window.addEventListener('resize', syncPageSlot, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncPageSlot)
})
</script>

<template>
  <NPagination class="app-pagination" :page-slot="pageSlot" />
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

// 复刻原 el-pagination.is-background 观感：圆角页码、激活主色
:deep(.n-pagination-item) {
  color: var(--text-secondary);
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 999px;
  transition: all 0.3s ease;

  &:hover {
    color: $primary;
    border-color: $primary;
  }

  &.n-pagination-item--active {
    background: $primary;
    color: #fff;
    border-color: $primary;
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

// 窄屏：缩小页码按钮尺寸，进一步降低溢出风险
@media (max-width: 640px) {
  :deep(.n-pagination-item) {
    min-width: 32px;
    height: 32px;
    padding: 0 4px;
    font-size: 13px;
    margin: 0 2px;
  }
}
</style>
