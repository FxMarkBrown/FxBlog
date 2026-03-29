<template>
  <div class="app-container rag-page">
    <el-card class="status-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">RAG 控制面板</div>
            <div class="card-tip">这里专门管理文章向量索引重建。启动自动全量已关闭，平时靠文章增量同步，只有需要时再手动触发。</div>
          </div>
          <div class="header-actions">
            <el-button :loading="statusLoading" @click="loadStatus">刷新状态</el-button>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmitRebuild">提交重建</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16" class="status-grid">
        <el-col :xs="24" :md="12" :xl="6">
          <div class="status-item">
            <span class="status-label">RAG 总开关</span>
            <el-tag :type="statusForm.enabled ? 'success' : 'danger'" effect="light">
              {{ statusForm.enabled ? '已开启' : '未开启' }}
            </el-tag>
          </div>
        </el-col>
        <el-col :xs="24" :md="12" :xl="6">
          <div class="status-item">
            <span class="status-label">启动全量重建</span>
            <el-tag :type="statusForm.syncOnStartup ? 'warning' : 'success'" effect="light">
              {{ statusForm.syncOnStartup ? '已开启' : '已关闭' }}
            </el-tag>
          </div>
        </el-col>
        <el-col :xs="24" :md="12" :xl="6">
          <div class="status-item">
            <span class="status-label">当前任务</span>
            <el-tag :type="statusForm.running ? 'warning' : 'info'" effect="light">
              {{ statusForm.running ? '重建中' : '空闲' }}
            </el-tag>
          </div>
        </el-col>
        <el-col :xs="24" :md="12" :xl="6">
          <div class="status-item">
            <span class="status-label">默认索引范围</span>
            <el-tag :type="statusForm.indexPublishedOnly ? 'success' : 'warning'" effect="light">
              {{ statusForm.indexPublishedOnly ? '仅已发布文章' : '全部文章' }}
            </el-tag>
          </div>
        </el-col>
      </el-row>

      <el-divider />

      <el-form label-width="140px" class="action-form">
        <el-form-item label="本次重建范围">
          <el-switch
            v-model="rebuildForm.publishedOnly"
            inline-prompt
            active-text="仅已发布"
            inactive-text="全部文章"
          />
        </el-form-item>
        <el-form-item label="执行策略说明">
          <div class="helper-text">
            这会在后台异步逐篇重建文章向量索引；如果当前已有任务在跑，新的提交会被拒绝，避免重复堆积。
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {ElMessage, ElMessageBox} from 'element-plus'
import {onBeforeUnmount, onMounted} from 'vue'
import {getAiRagStatusApi, submitAiRagRebuildApi} from '@/api/ai/rag'

interface AiRagStatusForm {
  enabled: boolean
  syncOnStartup: boolean
  indexPublishedOnly: boolean
  running: boolean
}

interface AiRagSubmitResult {
  submitted: boolean
  running: boolean
  articleCount: number
  publishedOnly: boolean
  trigger: string
}

const statusLoading = ref(false)
const submitLoading = ref(false)
const pollTimer = ref<number | null>(null)
const initialized = ref(false)

const statusForm = reactive<AiRagStatusForm>({
  enabled: false,
  syncOnStartup: false,
  indexPublishedOnly: true,
  running: false
})

const rebuildForm = reactive({
  publishedOnly: true
})

/**
 * 拉取后台当前的 RAG 状态，并同步默认重建范围。
 */
const loadStatus = async () => {
  statusLoading.value = true
  try {
    const { data } = await getAiRagStatusApi()
    Object.assign(statusForm, {
      enabled: Boolean(data?.enabled),
      syncOnStartup: Boolean(data?.syncOnStartup),
      indexPublishedOnly: Boolean(data?.indexPublishedOnly),
      running: Boolean(data?.running)
    })
    if (!initialized.value) {
      rebuildForm.publishedOnly = statusForm.indexPublishedOnly
      initialized.value = true
    }
    syncPollingState()
  } finally {
    statusLoading.value = false
  }
}

/**
 * 根据运行状态决定是否开启轮询，避免页面空闲时无意义请求。
 */
const syncPollingState = () => {
  if (statusForm.running) {
    startPolling()
    return
  }
  stopPolling()
}

/**
 * 开启轻量轮询，用于在重建期间自动刷新状态。
 */
const startPolling = () => {
  if (pollTimer.value !== null) {
    return
  }
  pollTimer.value = window.setInterval(() => {
    loadStatus()
  }, 5000)
}

/**
 * 停止轮询，防止重复定时器残留。
 */
const stopPolling = () => {
  if (pollTimer.value === null) {
    return
  }
  window.clearInterval(pollTimer.value)
  pollTimer.value = null
}

/**
 * 提交一次后台异步全量重建任务。
 */
const handleSubmitRebuild = async () => {
  await ElMessageBox.confirm(
    `确认提交一次${rebuildForm.publishedOnly ? '仅已发布文章' : '全部文章'}的 RAG 全量重建吗？`,
    '提交重建',
    {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '取消'
    }
  )
  submitLoading.value = true
  try {
    const { data } = await submitAiRagRebuildApi({
      publishedOnly: rebuildForm.publishedOnly
    })
    const result = (data || {}) as Partial<AiRagSubmitResult>
    if (result.submitted) {
      ElMessage.success(`重建任务已提交，本次预计处理 ${result.articleCount || 0} 篇文章`)
    } else if (result.running) {
      ElMessage.warning('已有重建任务在运行，请等待当前任务结束')
    } else {
      ElMessage.info('当前没有可重建的文章')
    }
    await loadStatus()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 页面初始化时拉取一次状态。
 */
onMounted(() => {
  loadStatus()
})

/**
 * 页面销毁前清理轮询定时器。
 */
onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.rag-page {
  display: grid;
  gap: 16px;
}

.status-card {
  border-radius: 18px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.card-tip {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.status-grid {
  row-gap: 16px;
}

.status-item {
  min-height: 92px;
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: linear-gradient(180deg, var(--el-fill-color-blank), var(--el-fill-color-light));
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
}

.status-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.action-form {
  max-width: 720px;
}

.helper-text {
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }
}
</style>
