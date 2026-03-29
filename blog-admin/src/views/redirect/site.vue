<template>
  <div />
</template>

<script setup lang="ts">
import {onMounted} from 'vue'

onMounted(() => {
  window.location.replace(import.meta.env.VITE_APP_SITE_URL || '/')
})
</script>
