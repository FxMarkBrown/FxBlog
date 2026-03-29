<script setup lang="ts">
import { useNoIndexSeo } from '@/composables/useSeo'

const runtimeConfig = useRuntimeConfig()
const authStore = useAuthStore()

useNoIndexSeo({
  title: () => `AI - ${runtimeConfig.public.siteName}`,
  description: 'AI 工作台，统一进入一般对话与文档任务'
})

const taskCards = computed(() => [
  {
    key: 'chat',
    title: '一般对话',
    subtitle: '面向站内问答、文章上下文问答与流式回复的常规会话入口',
    path: '/ai/chat',
    icon: 'fas fa-comments',
    badge: '已可用',
    available: true
  },
  {
    key: 'document',
    title: '文档任务',
    subtitle: '上传文档后进入全屏结构画布，围绕节点展开、引用、预览与追问',
    path: '/ai/document',
    icon: 'fas fa-file-lines',
    badge: '已可用',
    available: true
  }
])
</script>

<template>
  <section class="ai-workbench-page">
    <div class="ai-workbench-shell">
      <header class="hero-card">
        <div class="hero-copy">
          <span class="hero-badge">AI 工作台</span>
          <h1>选择你的 AI 工作入口</h1>
          <p>
            一般对话用于常规聊天与站内知识问答，文档任务用于结构化解析、画布浏览和节点级上下文对话。
          </p>
        </div>
        <div class="hero-meta">
          <span class="meta-item">当前用户：{{ authStore.isLoggedIn ? '已登录' : '未登录' }}</span>
          <span class="meta-item">任务类型：2</span>
        </div>
      </header>

      <div class="task-grid">
        <NuxtLink
          v-for="card in taskCards"
          :key="card.key"
          :to="card.path"
          class="task-card"
          :class="{ available: card.available }"
        >
          <div class="task-card__icon">
            <i :class="card.icon"></i>
          </div>
          <div class="task-card__body">
            <div class="task-card__top">
              <h2>{{ card.title }}</h2>
              <span class="task-card__badge">{{ card.badge }}</span>
            </div>
            <p>{{ card.subtitle }}</p>
          </div>
          <div class="task-card__action">
            <span>进入</span>
            <i class="fas fa-arrow-right"></i>
          </div>
        </NuxtLink>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.ai-workbench-page {
  padding: 40px 20px 56px;
}

.ai-workbench-shell {
  width: min(1120px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 32px;
  border-radius: 28px;
  border: 1px solid var(--border-color);
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.12), rgba(14, 165, 233, 0.08)),
    var(--card-bg);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}

.hero-copy {
  max-width: 720px;

  h1 {
    margin: 10px 0 14px;
    font-size: clamp(2rem, 4vw, 2.8rem);
    line-height: 1.1;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    line-height: 1.75;
    color: var(--text-secondary);
  }
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(13, 148, 136, 0.14);
  color: #0f766e;
  font-size: 0.86rem;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.hero-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 10px 16px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--card-bg) 74%, rgba(34, 197, 94, 0.2));
  border: 1px solid color-mix(in srgb, var(--border-color) 64%, rgba(45, 212, 191, 0.36));
  color: var(--text-secondary);
  white-space: nowrap;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(12px);
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.task-card {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 180px;
  padding: 24px;
  border-radius: 24px;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
  color: inherit;
  text-decoration: none;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.06);
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 20px 44px rgba(15, 23, 42, 0.1);
    border-color: rgba(14, 165, 233, 0.28);
  }
}

.task-card__icon {
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.16), rgba(59, 130, 246, 0.14));
  color: #0f766e;
  font-size: 1.4rem;
}

.task-card__body {
  flex: 1;

  p {
    margin: 0;
    line-height: 1.72;
    color: var(--text-secondary);
  }
}

.task-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;

  h2 {
    margin: 0;
    font-size: 1.3rem;
    color: var(--text-primary);
  }
}

.task-card__badge {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
  font-size: 0.8rem;
  font-weight: 600;
}

.task-card__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-width: 110px;
  min-height: 46px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(34, 211, 238, 0.22);
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.18), rgba(45, 212, 191, 0.18));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
  color: #0891b2;
  font-weight: 600;
  transition: transform 0.24s ease, border-color 0.24s ease, background 0.24s ease;
}

.task-card:hover .task-card__action {
  transform: translateX(2px);
  border-color: rgba(14, 165, 233, 0.34);
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.24), rgba(45, 212, 191, 0.24));
}

:global(:root[data-theme='dark'] .meta-item) {
  background: color-mix(in srgb, var(--card-bg) 82%, rgba(34, 197, 94, 0.12));
  border-color: rgba(56, 189, 248, 0.22);
  color: rgba(226, 232, 240, 0.92);
  box-shadow: 0 16px 34px rgba(2, 6, 23, 0.34);
}

:global(:root[data-theme='dark'] .task-card__action) {
  color: #67e8f9;
  border-color: rgba(103, 232, 249, 0.24);
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.22), rgba(20, 184, 166, 0.2));
}

:global(:root[data-theme='dark'] .task-card:hover .task-card__action) {
  border-color: rgba(103, 232, 249, 0.4);
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.28), rgba(45, 212, 191, 0.24));
}

@media (max-width: 900px) {
  .hero-card {
    flex-direction: column;
  }

  .hero-meta {
    align-items: flex-start;
  }

  .task-grid {
    grid-template-columns: 1fr;
  }

  .task-card {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .task-card__action {
    min-width: 96px;
    margin-left: 76px;
  }
}
</style>
