<script setup lang="ts">
import Announcement from '@/components/Announcement/index.vue'
import BlogFooter from '@/layout/Footer/index.vue'
import BlogHeader from '@/layout/Header/index.vue'

const route = useRoute()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const shouldPrefetchOnServer = import.meta.server && !import.meta.dev

if (shouldPrefetchOnServer) {
  await siteStore.fetchWebsiteInfo().catch(() => null)
}

const routeViewKey = computed(() => (route.path === '/ai' ? route.path : route.fullPath))

let unreadDebounce: ReturnType<typeof setTimeout> | undefined

watch(
  () => route.fullPath,
  () => {
    if (!authStore.token) {
      siteStore.isUnread = false
      return
    }

    clearTimeout(unreadDebounce)
    unreadDebounce = setTimeout(() => {
      siteStore.fetchUnreadStatus().catch(() => null)
    }, 500)
  }
)

onMounted(() => {
  if (!shouldPrefetchOnServer) {
    void siteStore.fetchWebsiteInfo().catch(() => null)
  }

  setTimeout(() => {
    void siteStore.fetchNotice().catch(() => null)
  }, 120)

  if (authStore.token) {
    setTimeout(() => {
      void authStore.fetchUserInfo().catch(() => null)
      void siteStore.fetchUnreadStatus().catch(() => null)
    }, 200)
  }

  void siteStore.reportVisit().catch(() => null)
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
