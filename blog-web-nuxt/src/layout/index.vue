<script setup lang="ts">
import Announcement from '@/components/Announcement/index.vue'
import BlogFooter from '@/layout/Footer/index.vue'
import BlogHeader from '@/layout/Header/index.vue'

const route = useRoute()
const authStore = useAuthStore()
const siteStore = useSiteStore()

await Promise.all([
  siteStore.fetchWebsiteInfo().catch(() => null),
  siteStore.fetchNotice().catch(() => null),
  authStore.token ? authStore.fetchUserInfo().catch(() => null) : Promise.resolve(null),
  authStore.token ? siteStore.fetchUnreadStatus().catch(() => null) : Promise.resolve(null)
])

const routeViewKey = computed(() => (route.path === '/ai' ? route.path : route.fullPath))

watch(
  () => route.fullPath,
  async () => {
    if (!authStore.token) {
      siteStore.isUnread = false
      return
    }

    await siteStore.fetchUnreadStatus().catch(() => null)
  }
)

onMounted(async () => {
  await siteStore.reportVisit().catch(() => null)
  await siteStore.fetchWebsiteInfo(true).catch(() => null)
})
</script>

<template>
  <div class="app-shell">
    <BlogHeader />
    <main class="app-content">
      <Announcement />
      <Transition name="page" mode="out-in">
        <div :key="routeViewKey" class="page-shell">
          <slot />
        </div>
      </Transition>
    </main>
    <BlogFooter />
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-content {
  flex: 1;
}

.page-shell {
  min-height: 100%;
}

.page-enter-active {
  animation: fade-in 0.3s;
}

.page-leave-active {
  animation: fade-out 0.3s;
}

@keyframes fade-in {
  0% {
    opacity: 0;
    transform: scale(0.95);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes fade-out {
  0% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0;
    transform: scale(1.05);
  }
}
</style>
