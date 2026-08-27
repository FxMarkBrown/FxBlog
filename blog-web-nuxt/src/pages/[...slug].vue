<script setup lang="ts">
import NotFoundView from '@/views/404/index.vue'
import { useNoIndexSeo } from '@/composables/useSeo'

definePageMeta({
  hideAnnouncement: true
})

useNoIndexSeo({
  title: '404',
  description: '页面不存在'
})

const event = useRequestEvent()

if (event) {
  setResponseStatus(event, 404, 'Page Not Found')
}
</script>

<template>
  <NotFoundView />
</template>
