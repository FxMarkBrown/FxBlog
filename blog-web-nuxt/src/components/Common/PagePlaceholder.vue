<script setup lang="ts">
interface PagePlaceholderProps {
  title: string
  description: string
}

const props = defineProps<PagePlaceholderProps>()
</script>

<template>
  <section class="page-card page-placeholder">
    <h1>{{ props.title }}</h1>
    <p>{{ props.description }}</p>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.page-placeholder {
  padding: 32px;
}

.page-placeholder h1 {
  margin: 0 0 12px;
}

.page-placeholder p {
  margin: 0;
  color: $text-secondary;
  line-height: 1.8;
}
</style>
