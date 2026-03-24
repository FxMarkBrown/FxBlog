<script setup lang="ts">
const siteStore = useSiteStore()
const startYear = 2025
const currentYear = new Date().getFullYear()
const runningTime = ref({ days: 0, hours: 0, minutes: 0, seconds: 0 })
const recordNumber = computed(() => String(siteStore.websiteInfo.recordNum || '').trim())
let timer: ReturnType<typeof setInterval> | null = null

/**
 * 计算运行时间
 */
function calculateRunningTime() {
  const now = new Date()
  const startDate = new Date('2025-02-13')
  const diff = now.getTime() - startDate.getTime()

  runningTime.value.days = Math.floor(diff / (1000 * 60 * 60 * 24))
  runningTime.value.hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  runningTime.value.minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  runningTime.value.seconds = Math.floor((diff % (1000 * 60)) / 1000)
}

onMounted(() => {
  calculateRunningTime()
  timer = setInterval(calculateRunningTime, 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<template>
  <footer class="site-footer">
    <div class="footer-content">
      <div class="footer-info">
        <div class="running-time">
          <span class="icon">⏱</span>
          本站居然运行了 <span class="time-value">{{ runningTime.days }}</span> 天
          <span class="time-value">{{ runningTime.hours }}</span> 时
          <span class="time-value">{{ runningTime.minutes }}</span> 分
          <span class="time-value">{{ runningTime.seconds }}</span> 秒
        </div>
        <div class="copyright">
          Copyright©{{ startYear }}-{{ currentYear }} {{ siteStore.websiteInfo.name }}
          <span v-if="recordNumber" class="divider">·</span>
          <a v-if="recordNumber" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener" class="record">
            {{ recordNumber }}
          </a>
        </div>
      </div>
    </div>
  </footer>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.site-footer {
  background: var(--card-bg);
  padding: $spacing-lg 0;
  margin-top: auto;
  border-top: 1px solid rgba(125, 125, 125, 0.1);
}

.footer-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 $spacing-xl;
}

.footer-info {
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.running-time {
  font-size: 0.875rem;
  color: var(--text-secondary);

  .icon {
    color: #4CAF50;
    margin-right: 4px;
  }
}

.time-value {
  color: #ff6b81;
  font-family: 'Fira Code', monospace;
}

.copyright {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.record {
  color: inherit;
  text-decoration: none;
  transition: color 0.2s ease;

  &:hover {
    color: $primary;
  }
}

@include responsive(sm) {
  .site-footer {
    padding: $spacing-md 0;
  }

  .footer-content {
    padding: 0 $spacing-md;
  }

  .copyright {
    font-size: 0.8125rem;
    gap: $spacing-xs;
  }

  .running-time {
    font-size: 0.8125rem;
  }
}
</style>
