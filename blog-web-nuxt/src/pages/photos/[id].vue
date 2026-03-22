<script setup lang="ts">
import PhotoDetailView from '@/views/photos/detail.vue'

definePageMeta({
  validate: (route) => !!route.params.id
})
</script>

<template>
  <PhotoDetailView />
</template>
