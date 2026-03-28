<script setup lang="ts">
import { useNoIndexSeo } from '@/composables/useSeo'

const runtimeConfig = useRuntimeConfig()

useNoIndexSeo({
  title: () => `文档任务 - ${runtimeConfig.public.siteName}`,
  description: '面向文档解析、上下文注入和问答的 AI 文档任务工作区'
})
</script>

<template>
  <section class="ai-document-page">
    <div class="ai-document-shell">
      <div class="document-card">
        <span class="document-badge">文档任务</span>
        <h1>文档工作区还在接入中</h1>
        <p>
          下一阶段这里会承载文档上传、解析结果预览、原文与 Markdown 双栏对照，以及基于选区和上下文片段的问答。
        </p>
        <div class="document-actions">
          <NuxtLink to="/ai" class="document-link secondary">返回工作台</NuxtLink>
          <NuxtLink to="/ai/chat" class="document-link">先去一般对话任务</NuxtLink>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ai-document-page {
  padding: 40px 20px 56px;
}

.ai-document-shell {
  width: min(920px, 100%);
  margin: 0 auto;
}

.document-card {
  padding: 34px;
  border-radius: 28px;
  border: 1px solid var(--border-color);
  background:
    linear-gradient(135deg, rgba(59, 130, 246, 0.08), rgba(16, 185, 129, 0.06)),
    var(--card-bg);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);

  h1 {
    margin: 12px 0 14px;
    color: var(--text-primary);
    font-size: clamp(1.9rem, 4vw, 2.5rem);
  }

  p {
    margin: 0;
    line-height: 1.8;
    color: var(--text-secondary);
  }
}

.document-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
  font-size: 0.86rem;
  font-weight: 600;
}

.document-actions {
  display: flex;
  gap: 12px;
  margin-top: 26px;
  flex-wrap: wrap;
}

.document-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 144px;
  padding: 12px 18px;
  border-radius: 14px;
  text-decoration: none;
  background: linear-gradient(135deg, #14b8a6, #0ea5e9);
  color: #fff;
  font-weight: 600;

  &.secondary {
    background: transparent;
    color: var(--text-primary);
    border: 1px solid var(--border-color);
  }
}
</style>
