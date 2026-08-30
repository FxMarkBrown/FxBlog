<script setup lang="ts">
import SeriesDetailView from '@/views/series/detail.vue'

definePageMeta({
  validate: (route) => !!route.params.id
})
</script>

<template>
  <SeriesDetailView />
</template>
