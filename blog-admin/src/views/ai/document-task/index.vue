<template>
  <div class="app-container">
    <div class="search-wrapper">
      <el-form :inline="true" :model="queryParams" class="filter-form">
        <el-form-item label="用户">
          <el-input v-model="queryParams.userKeyword" placeholder="昵称 / 用户名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="解析中" value="PROCESSING" />
            <el-option label="已解析" value="PARSED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应方">
          <el-select v-model="queryParams.provider" placeholder="全部" clearable style="width: 160px">
            <el-option label="MinerU" value="mineru" />
            <el-option label="Local Mock" value="local-mock" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="标题 / 文件名 / 远端任务号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <ButtonGroup>
            <el-button
              type="danger"
              icon="Delete"
              :disabled="selectedIds.length === 0"
              @click="handleBatchDelete"
            >
              批量删除
            </el-button>
          </ButtonGroup>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="taskList"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="用户" min-width="180">
          <template #default="scope">
            <div class="user-cell">
              <el-avatar :src="scope.row.userAvatar" :size="34">{{ getUserInitial(scope.row.userNickname) }}</el-avatar>
              <div class="user-meta">
                <span class="user-name">{{ scope.row.userNickname || '-' }}</span>
                <span class="user-id">@{{ scope.row.username || '-' }} / UID {{ scope.row.userId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="220" prop="title" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" effect="light">
              {{ getStatusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="供应方" width="110" align="center">
          <template #default="scope">
            {{ scope.row.provider || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="文件名" min-width="180" prop="fileName" show-overflow-tooltip />
        <el-table-column label="解析" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.parsed ? 'success' : 'info'" effect="light">
              {{ scope.row.parsed ? '已生成' : '未生成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="线程数" width="90" align="center" prop="threadCount" />
        <el-table-column label="消息数" width="90" align="center" prop="messageCount" />
        <el-table-column label="页数" width="80" align="center" prop="pageCount" />
        <el-table-column label="最近消息" width="180" align="center" prop="lastMessageAt" />
        <el-table-column label="创建时间" width="180" align="center" prop="createTime" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" link icon="View" @click="handleViewThreads(scope.row)">
              查看线程
            </el-button>
            <el-button type="danger" link icon="Delete" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]"
          :total="total"
          :background="true"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="threadDrawerVisible" :title="threadDrawerTitle" size="42%">
      <div v-loading="threadLoading" class="drawer-body">
        <div class="drawer-toolbar">
          <el-input
            v-model="threadQueryParams.keyword"
            placeholder="搜索节点标题 / 线程标题 / 节点 ID"
            clearable
            @keyup.enter="loadThreads"
          >
            <template #append>
              <el-button icon="Search" @click="loadThreads" />
            </template>
          </el-input>
        </div>

        <el-table v-if="threadList.length" :data="threadList" style="width: 100%">
          <el-table-column label="节点" min-width="220">
            <template #default="scope">
              <div class="thread-node">
                <span class="thread-node__title">{{ scope.row.nodeTitle || scope.row.nodeId }}</span>
                <span class="thread-node__id">{{ scope.row.nodeId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="线程标题" min-width="200" prop="title" show-overflow-tooltip />
          <el-table-column label="模型" min-width="160" show-overflow-tooltip>
            <template #default="scope">
              {{ scope.row.modelDisplayName || scope.row.modelName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="消息数" width="90" align="center" prop="messageCount" />
          <el-table-column label="最后消息" width="180" align="center" prop="lastMessageAt" />
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" link icon="View" @click="handleViewMessages(scope.row)">
                查看消息
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无节点线程" />
      </div>
    </el-drawer>

    <el-drawer v-model="messageDrawerVisible" :title="messageDrawerTitle" size="42%">
      <div v-loading="messageLoading" class="drawer-body">
        <div v-if="messageList.length" class="message-list">
          <div v-for="message in messageList" :key="message.id" class="message-item" :class="message.role">
            <div class="message-head">
              <el-tag size="small" :type="getRoleTagType(message.role)" effect="light">
                {{ getRoleLabel(message.role) }}
              </el-tag>
              <span class="message-time">{{ message.createTime }}</span>
            </div>
            <div class="message-content">{{ message.content }}</div>
            <div class="message-meta">
              <span v-if="message.modelId">模型 {{ message.modelId }}</span>
              <span v-if="message.tokensIn || message.tokensOut">Token {{ (message.tokensIn || 0) + (message.tokensOut || 0) }}</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无消息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAiDocumentTaskApi,
  getAiDocumentTaskListApi,
  getAiDocumentTaskMessagesApi,
  getAiDocumentTaskThreadsApi
} from '@/api/ai/document-task'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  status: '',
  provider: '',
  keyword: '',
  userKeyword: ''
})

const loading = ref(false)
const total = ref(0)
const taskList = ref<any[]>([])
const selectedIds = ref<number[]>([])

const threadDrawerVisible = ref(false)
const threadLoading = ref(false)
const currentTask = ref<any>(null)
const threadList = ref<any[]>([])
const threadQueryParams = reactive({
  pageNum: 1,
  pageSize: 100,
  keyword: ''
})

const messageDrawerVisible = ref(false)
const messageLoading = ref(false)
const currentThread = ref<any>(null)
const messageList = ref<any[]>([])

const threadDrawerTitle = computed(() => {
  if (!currentTask.value) {
    return '节点线程'
  }
  return `${currentTask.value.title || `文档任务 #${currentTask.value.taskId}`} · 节点线程`
})

const messageDrawerTitle = computed(() => {
  if (!currentThread.value) {
    return '线程消息'
  }
  return `${currentThread.value.nodeTitle || currentThread.value.nodeId} · 消息记录`
})

/**
 * 拉取后台文档任务列表。
 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getAiDocumentTaskListApi(queryParams)
    taskList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    taskList.value = []
    total.value = 0
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 拉取指定任务下的节点线程列表。
 */
const loadThreads = async () => {
  if (!currentTask.value?.taskId) {
    return
  }
  threadLoading.value = true
  try {
    const { data } = await getAiDocumentTaskThreadsApi(currentTask.value.taskId, threadQueryParams)
    threadList.value = data.records || []
  } catch (error) {
    threadList.value = []
  } finally {
    threadLoading.value = false
  }
}

/**
 * 拉取指定线程下的消息记录。
 */
const loadMessages = async () => {
  if (!currentThread.value?.threadId) {
    return
  }
  messageLoading.value = true
  try {
    const { data } = await getAiDocumentTaskMessagesApi(currentThread.value.threadId, { pageNum: 1, pageSize: 200 })
    messageList.value = data.records || []
  } catch (error) {
    messageList.value = []
  } finally {
    messageLoading.value = false
  }
}

/**
 * 按筛选条件查询文档任务。
 */
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

/**
 * 重置筛选条件并恢复默认分页。
 */
const handleReset = () => {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.status = ''
  queryParams.provider = ''
  queryParams.keyword = ''
  queryParams.userKeyword = ''
  getList()
}

/**
 * 同步表格多选结果。
 */
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map((item) => item.taskId)
}

/**
 * 更新分页大小并刷新列表。
 */
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

/**
 * 更新当前页码并刷新列表。
 */
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

/**
 * 打开指定任务的线程抽屉。
 */
const handleViewThreads = async (row: any) => {
  currentTask.value = row
  threadQueryParams.pageNum = 1
  threadQueryParams.keyword = ''
  threadDrawerVisible.value = true
  messageDrawerVisible.value = false
  currentThread.value = null
  messageList.value = []
  await loadThreads()
}

/**
 * 打开指定线程的消息抽屉。
 */
const handleViewMessages = async (row: any) => {
  currentThread.value = row
  messageDrawerVisible.value = true
  await loadMessages()
}

/**
 * 执行文档任务删除，并同步清理抽屉状态。
 */
const doDelete = async (ids: number[]) => {
  await deleteAiDocumentTaskApi(ids)
  ElMessage.success('删除成功')
  if (currentTask.value && ids.includes(currentTask.value.taskId)) {
    threadDrawerVisible.value = false
    messageDrawerVisible.value = false
    currentTask.value = null
    currentThread.value = null
    threadList.value = []
    messageList.value = []
  }
  selectedIds.value = []
  await getList()
}

/**
 * 删除单条文档任务。
 */
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除文档任务《${row.title}》?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await doDelete([row.taskId])
  } catch (error) {
  }
}

/**
 * 批量删除当前选中的文档任务。
 */
const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除选中的 ${selectedIds.value.length} 个文档任务?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await doDelete(selectedIds.value)
  } catch (error) {
  }
}

/**
 * 生成用户头像占位首字母。
 */
const getUserInitial = (nickname?: string) => nickname?.slice(0, 1) || 'A'

/**
 * 格式化任务状态文案。
 */
const getStatusLabel = (status?: string) => {
  if (status === 'SUBMITTED') return '已提交'
  if (status === 'PROCESSING') return '解析中'
  if (status === 'PARSED') return '已解析'
  if (status === 'FAILED') return '失败'
  return status || '未知'
}

/**
 * 根据任务状态返回标签样式。
 */
const getStatusTagType = (status?: string) => {
  if (status === 'PARSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

/**
 * 格式化消息角色文案。
 */
const getRoleLabel = (role?: string) => {
  if (role === 'assistant') return '助手'
  if (role === 'user') return '用户'
  if (role === 'system') return '系统'
  return role || '未知'
}

/**
 * 根据消息角色返回标签样式。
 */
const getRoleTagType = (role?: string) => {
  if (role === 'assistant') return 'success'
  if (role === 'user') return 'primary'
  if (role === 'system') return 'info'
  return undefined
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta,
.thread-node {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name,
.thread-node__title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.user-id,
.thread-node__id {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-blank);
}

.message-item.user {
  background: rgba(59, 130, 246, 0.06);
}

.message-item.assistant {
  background: rgba(16, 185, 129, 0.06);
}

.message-head,
.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.message-head {
  margin-bottom: 8px;
}

.message-content {
  color: var(--el-text-color-primary);
  line-height: 1.7;
  white-space: pre-wrap;
}

.message-meta {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  justify-content: flex-start;
}

.message-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
