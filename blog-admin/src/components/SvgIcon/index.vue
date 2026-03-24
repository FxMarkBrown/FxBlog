<template>
  <svg :class="svgClass" :style="svgStyle" aria-hidden="true">
    <use :href="iconName" />
  </svg>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  name: string
  color?: string
  mr?: number
  size?: number | string
}>(), {
  color: '',
  mr: 0,
  size: 20
})

const iconName = computed(() => `#icon-${props.name}`)
const svgClass = computed(() => props.name ? `svg-icon icon-${props.name}` : 'svg-icon')
const svgStyle = computed(() => ({
  color: props.color || undefined,
  marginRight: props.mr ? `${props.mr}px` : undefined,
  width: typeof props.size === 'number' ? `${props.size}px` : props.size,
  height: typeof props.size === 'number' ? `${props.size}px` : props.size
}))
</script>

<style lang="scss">
.svg-icon {
  fill: currentColor;
  vertical-align: middle;
  flex-shrink: 0;
}
</style>
