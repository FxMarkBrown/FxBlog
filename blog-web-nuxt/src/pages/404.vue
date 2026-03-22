<script setup lang="ts">
import NotFoundView from '@/views/404/index.vue'

definePageMeta({
  hideAnnouncement: true
})

useSeoMeta({
  title: '404',
  description: '页面不存在'
})
</script>

<template>
  <NotFoundView />
</template>
