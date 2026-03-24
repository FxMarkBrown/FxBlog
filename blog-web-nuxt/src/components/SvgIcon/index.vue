<script setup lang="ts">
const props = defineProps<{
  iconClass: string
  className?: string
}>()

const iconModules = import.meta.glob('@/assets/icons/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true
}) as Record<string, string>

const svgContent = computed(() => {
  const matchedEntry = Object.entries(iconModules).find(([path]) => path.endsWith(`/${props.iconClass}.svg`))
  return matchedEntry?.[1] || ''
})

const svgClass = computed(() => (props.className ? `svg-icon ${props.className}` : 'svg-icon'))
</script>

<template>
  <span :class="svgClass" aria-hidden="true" v-html="svgContent"></span>
</template>

<style scoped lang="scss">
.svg-icon {
  display: inline-flex;
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
  overflow: hidden;

  :deep(svg) {
    width: 100%;
    height: 100%;
    fill: currentColor;
  }
}
</style>
