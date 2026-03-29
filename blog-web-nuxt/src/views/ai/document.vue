<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDocumentTaskApi, createMockDocumentTaskApi, deleteDocumentTaskApi, getDocumentTaskListApi, renameDocumentTaskApi } from '@/api/ai-document'
import { uploadFileApi } from '@/api/file'
import { useNoIndexSeo } from '@/composables/useSeo'
import { unwrapResponseData } from '@/utils/response'
import type { DocumentTaskDetail, DocumentTaskListItem } from '@/types/ai-document'

const runtimeConfig = useRuntimeConfig()
const router = useRouter()
const authStore = useAuthStore()
const localMockEnabled = import.meta.dev

const loading = ref(false)
const creating = ref(false)
const tasks = ref<DocumentTaskListItem[]>([])
const uploadInputRef = ref<HTMLInputElement | null>(null)
const createMode = ref<'real' | 'mock'>('real')

useNoIndexSeo({
  title: () => `文档任务 - ${runtimeConfig.public.siteName}`,
  description: '面向结构化解析、节点画布与上下文问答的文档任务工作台'
})

async function loadPageData() {
  loading.value = true
  try {
    const listResponse = await getDocumentTaskListApi()
    const records = unwrapResponseData<DocumentTaskListItem[] | null>(listResponse)
    tasks.value = Array.isArray(records) ? records : []
  } catch (error) {
    ElMessage.error((error as Error)?.message || '文档任务加载失败')
  } finally {
    loading.value = false
  }
}

function openTask(taskId: number) {
  router.push(`/ai/document/${taskId}`)
}

function formatTaskStatus(status?: string) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'PARSED') {
    return '已解析'
  }
  if (normalized === 'PROCESSING') {
    return '解析中'
  }
  if (normalized === 'FAILED') {
    return '失败'
  }
  if (normalized === 'SUBMITTED') {
    return '已提交'
  }
  return normalized || '未知状态'
}

function statusClass(status?: string) {
  const normalized = String(status || '').toLowerCase()
  if (normalized === 'parsed') {
    return 'is-parsed'
  }
  if (normalized === 'processing') {
    return 'is-processing'
  }
  if (normalized === 'failed') {
    return 'is-failed'
  }
  return 'is-submitted'
}

async function handleRenameTask(task: DocumentTaskListItem) {
  try {
    const { value } = await ElMessageBox.prompt('输入新的文档任务名称', '重命名任务', {
      inputValue: task.title || task.fileName || '',
      inputPlaceholder: '请输入任务名称',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })

    const nextTitle = value.trim()
    if (!nextTitle) {
      ElMessage.warning('任务名称不能为空')
      return
    }
    if (nextTitle.length > 255) {
      ElMessage.warning('任务名称不能超过 255 个字符')
      return
    }
    if (nextTitle === (task.title || '').trim()) {
      return
    }

    await renameDocumentTaskApi(task.taskId, { title: nextTitle })
    task.title = nextTitle
    ElMessage.success('任务名称已更新')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error((error as Error)?.message || '重命名失败')
  }
}

async function handleDeleteTask(task: DocumentTaskListItem) {
  try {
    await ElMessageBox.confirm(`确认删除“${task.title || task.fileName || `文档任务 #${task.taskId}`}”吗？`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteDocumentTaskApi(task.taskId)
    tasks.value = tasks.value.filter(item => item.taskId !== task.taskId)
    ElMessage.success('任务已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error((error as Error)?.message || '删除失败')
  }
}

function triggerDocumentUpload(mode: 'real' | 'mock' = 'real') {
  createMode.value = mode
  uploadInputRef.value?.click()
}

async function createFixtureMockTask() {
  try {
    creating.value = true
    createMode.value = 'mock'
    const createResponse = await createMockDocumentTaskApi({
      title: 'MinerU Fixture Mock'
    })
    const created = unwrapResponseData<DocumentTaskDetail | null>(createResponse)
    ElMessage.success('Fixture Mock 文档任务已创建')
    await loadPageData()
    const nextTaskId = created?.taskId || tasks.value[0]?.taskId
    if (nextTaskId) {
      await router.push(`/ai/document/${nextTaskId}`)
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '创建 Fixture Mock 任务失败')
  } finally {
    creating.value = false
  }
}

async function handleDocumentUpload(event: Event) {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (!file) {
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    creating.value = true
    const uploadResponse = await uploadFileApi(formData, 'aiDocument/source')
    const uploaded = unwrapResponseData<Record<string, unknown> | null>(uploadResponse)
    const sourceUrl = String(uploaded?.url || '')
    const sourceFileId = String(uploaded?.id || '')
    if (!sourceUrl || !sourceFileId) {
      ElMessage.error('文件上传成功，但缺少文件记录信息')
      return
    }

    const requestBody = {
      title: file.name.replace(/\.[^.]+$/, '') || file.name,
      fileName: file.name,
      sourceUrl,
      sourceFileId
    }
    const createResponse = await createDocumentTaskApi(requestBody)
    const created = unwrapResponseData<DocumentTaskDetail | null>(createResponse)
    ElMessage.success('文档任务已创建')
    await loadPageData()
    const nextTaskId = created?.taskId || tasks.value[0]?.taskId
    if (nextTaskId) {
      await router.push(`/ai/document/${nextTaskId}`)
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '上传文档失败')
  } finally {
    creating.value = false
    if (input) {
      input.value = ''
    }
  }
}

onMounted(() => {
  if (!authStore.isLoggedIn) {
    void router.push('/login')
    return
  }

  void loadPageData()
})
</script>

<template>
  <section class="ai-document-page">
    <div class="ai-document-shell">
      <header class="document-hero">
        <div class="document-hero__copy">
          <span class="document-badge">文档任务</span>
          <h1>全屏画布式文档工作台</h1>
          <p>
            这里承载结构化解析、层级节点展开、原文预览与节点上下文问答。文档不会被平铺成普通阅读器，而会被组织成一张可操作的认知画布。
          </p>
        </div>
        <div class="document-hero__meta">
          <div class="hero-meta-card">
            <span class="meta-label">任务数量</span>
            <strong class="meta-value">{{ tasks.length }}</strong>
          </div>
          <button type="button" class="hero-create-btn" :disabled="creating" @click="triggerDocumentUpload('real')">
            <i :class="['fas', creating ? 'fa-spinner fa-spin' : 'fa-cloud-arrow-up']"></i>
            <span>{{ creating && createMode === 'real' ? '处理中' : '上传并创建真实任务' }}</span>
          </button>
          <button
            v-if="localMockEnabled"
            type="button"
            class="hero-create-btn is-secondary"
            :disabled="creating"
            @click="createFixtureMockTask"
          >
            <i :class="['fas', creating ? 'fa-spinner fa-spin' : 'fa-flask']"></i>
            <span>{{ creating && createMode === 'mock' ? '处理中' : '创建 Fixture Mock' }}</span>
          </button>
        </div>
      </header>
      <input
        ref="uploadInputRef"
        type="file"
        accept=".pdf,.doc,.docx,.ppt,.pptx,.md,.markdown,.txt"
        class="document-upload-input"
        @change="handleDocumentUpload"
      />

      <section class="document-section">
        <div class="document-section__header">
          <div>
            <h2>最近文档任务</h2>
            <p>真实任务通过上传创建；开发环境下的 Mock 任务直接基于现有 MinerU fixture 生成，不再走本地上传。</p>
          </div>
          <div class="document-section__actions">
            <button type="button" class="back-link as-button" @click="loadPageData">刷新列表</button>
            <NuxtLink to="/ai" class="back-link">返回 AI</NuxtLink>
          </div>
        </div>

        <div v-loading="loading" class="task-list">
          <article
            v-for="task in tasks"
            :key="task.taskId"
            class="task-item"
          >
            <div
              role="button"
              tabindex="0"
              class="task-item__surface"
              @click="openTask(task.taskId)"
              @keydown.enter.prevent="openTask(task.taskId)"
              @keydown.space.prevent="openTask(task.taskId)"
            >
              <div class="task-item__main">
                <div class="task-item__top">
                  <div class="task-item__headline">
                    <h3>{{ task.title || `文档任务 #${task.taskId}` }}</h3>
                    <span class="task-status" :class="statusClass(task.status)">{{ formatTaskStatus(task.status) }}</span>
                  </div>
                  <span class="task-item__pages">{{ task.pageCount || 0 }} 页</span>
                </div>
                <p>{{ task.fileName || '未命名文件' }}</p>
              </div>
              <div class="task-item__meta">
                <span v-if="task.expireAt">保留至 {{ task.expireAt }}</span>
                <span>{{ task.updateTime || task.createTime || '-' }}</span>
              </div>
            </div>
            <div class="task-item__actions">
              <button type="button" class="task-action" @click="handleRenameTask(task)">
                <i class="fas fa-pen"></i>
                <span>重命名</span>
              </button>
              <button type="button" class="task-action is-danger" @click="handleDeleteTask(task)">
                <i class="fas fa-trash"></i>
                <span>删除</span>
              </button>
            </div>
          </article>

          <div v-if="!loading && !tasks.length" class="task-empty">
            还没有文档任务，先上传一份文档开始创建。
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ai-document-page {
  padding: 36px 20px 56px;
}

.ai-document-shell {
  width: min(1180px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.document-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 32px;
  border-radius: 28px;
  border: 1px solid var(--border-color);
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.14), transparent 28%),
    linear-gradient(135deg, rgba(14, 165, 233, 0.08), rgba(16, 185, 129, 0.08)),
    var(--card-bg);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}

.document-hero__copy {
  max-width: 760px;

  h1 {
    margin: 12px 0 14px;
    font-size: clamp(2rem, 4vw, 2.9rem);
    line-height: 1.08;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    line-height: 1.82;
    color: var(--text-secondary);
  }
}

.document-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(79, 70, 229, 0.12);
  color: #4f46e5;
  font-size: 0.84rem;
  font-weight: 700;
}

.document-hero__meta {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-meta-card {
  min-width: 172px;
  min-height: 44px;
  padding: 8px 12px;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--border-color) 82%, rgba(99, 102, 241, 0.1));
  background: color-mix(in srgb, var(--card-bg) 92%, rgba(255, 255, 255, 0.08));
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
}

.hero-meta-card:nth-child(1) {
  border-color: rgba(14, 165, 233, 0.16);
}

.hero-meta-card:nth-child(2) {
  border-color: rgba(16, 185, 129, 0.16);
}

.hero-create-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 48px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #4f46e5, #0ea5e9);
  color: #fff;
  cursor: pointer;
  font-weight: 700;

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }

  &.is-secondary {
    background: linear-gradient(135deg, #0f172a, #334155);
  }
}

.meta-label {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 0;
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 0.76rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.meta-value {
  color: #0f172a;
  font-size: 0.92rem;
  line-height: 1.1;
  letter-spacing: 0.01em;
  font-weight: 700;
}

.hero-meta-card:nth-child(1) .meta-value {
  color: #0369a1;
}

.hero-meta-card:nth-child(2) .meta-value {
  color: #047857;
}

.document-section {
  padding: 24px;
  border-radius: 28px;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.05);
}

.document-upload-input {
  display: none;
}

.document-section__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  h2 {
    margin: 0 0 6px;
    color: var(--text-primary);
    font-size: 1.3rem;
  }

  p {
    margin: 0;
    color: var(--text-secondary);
  }
}

.back-link {
  display: inline-flex;
  align-items: center;
  height: fit-content;
  padding: 12px 16px;
  border-radius: 14px;
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  text-decoration: none;
}

.document-section__actions {
  display: flex;
  gap: 10px;
}

.back-link.as-button {
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.task-list {
  display: grid;
  gap: 14px;
}

.task-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: stretch;
}

.task-item__surface {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 252, 0.86)),
    color-mix(in srgb, var(--card-bg) 88%, rgba(255, 255, 255, 0.12));
  cursor: pointer;
  text-align: left;
  color: inherit;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;

  &:hover {
    transform: translateY(-3px);
    border-color: rgba(79, 70, 229, 0.26);
    box-shadow: 0 16px 30px rgba(79, 70, 229, 0.08);
  }
}

.task-item__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.task-item__headline {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  h3 {
    margin: 0;
    color: #0f172a;
    font-size: 1.04rem;
    line-height: 1.35;
  }
}

.task-item__main p,
.task-item__meta {
  color: #475569;
  margin: 0;
}

.task-item__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.82rem;
  text-align: right;
}

.task-item__pages {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 0.94rem;
  font-weight: 700;
}

.task-status {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 700;
}

.task-status.is-submitted {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.task-status.is-processing {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.task-status.is-parsed {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}

.task-status.is-failed {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.task-item__actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.task-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 108px;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(79, 70, 229, 0.22);
    background: rgba(255, 255, 255, 0.92);
  }
}

.task-action.is-danger {
  color: #b91c1c;

  &:hover {
    border-color: rgba(239, 68, 68, 0.24);
    background: rgba(254, 242, 242, 0.96);
  }
}

.task-empty {
  padding: 32px 18px;
  text-align: center;
  color: var(--text-secondary);
}

:global(:root[data-theme='dark'] .hero-meta-card) {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.42);
  box-shadow: 0 6px 16px rgba(2, 6, 23, 0.16);
}

:global(:root[data-theme='dark'] .meta-value) {
  color: rgba(241, 245, 249, 0.96);
}

:global(:root[data-theme='dark'] .hero-meta-card:nth-child(1)) {
  border-color: rgba(56, 189, 248, 0.2);
}

:global(:root[data-theme='dark'] .hero-meta-card:nth-child(1) .meta-value) {
  color: #7dd3fc;
}

:global(:root[data-theme='dark'] .hero-meta-card:nth-child(2)) {
  border-color: rgba(52, 211, 153, 0.2);
}

:global(:root[data-theme='dark'] .hero-meta-card:nth-child(2) .meta-value) {
  color: #6ee7b7;
}

:global(:root[data-theme='dark'] .meta-label) {
  color: rgba(226, 232, 240, 0.82);
}

:global(:root[data-theme='dark'] .task-item__surface) {
  border-color: rgba(71, 85, 105, 0.28);
  background:
    linear-gradient(135deg, rgba(30, 41, 59, 0.92), rgba(15, 23, 42, 0.9)),
    rgba(15, 23, 42, 0.72);
  box-shadow: 0 12px 26px rgba(2, 6, 23, 0.2);
}

:global(:root[data-theme='dark'] .task-item__headline h3) {
  color: rgba(241, 245, 249, 0.96);
}

:global(:root[data-theme='dark'] .task-item__main p),
:global(:root[data-theme='dark'] .task-item__meta),
:global(:root[data-theme='dark'] .task-item__pages) {
  color: rgba(148, 163, 184, 0.92);
}

:global(:root[data-theme='dark'] .task-action) {
  border-color: rgba(71, 85, 105, 0.34);
  background: rgba(15, 23, 42, 0.56);
  color: rgba(226, 232, 240, 0.92);
}

:global(:root[data-theme='dark'] .task-action:hover) {
  border-color: rgba(99, 102, 241, 0.3);
  background: rgba(30, 41, 59, 0.82);
}

:global(:root[data-theme='dark'] .task-action.is-danger) {
  color: #fca5a5;
}

:global(:root[data-theme='dark'] .task-action.is-danger:hover) {
  border-color: rgba(239, 68, 68, 0.32);
  background: rgba(69, 10, 10, 0.34);
}

@media (max-width: 900px) {
  .document-hero,
  .document-section__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .document-hero__meta {
    width: 100%;
  }

  .hero-meta-card,
  .hero-create-btn {
    width: 100%;
  }

  .task-item {
    grid-template-columns: 1fr;
  }

  .task-item__surface,
  .task-item__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .task-item__headline {
    flex-wrap: wrap;
  }

  .task-item__actions {
    flex-direction: row;
    width: 100%;
  }

  .task-action {
    flex: 1;
  }

  .task-item__meta {
    text-align: left;
  }
}
</style>
