<template>
  <div class="app-container">
    <div class="search-wrapper">
      <el-form :inline="true" :model="queryParams" class="filter-form">
        <el-form-item label="用户">
          <el-input v-model="queryParams.userKeyword" placeholder="昵称 / 用户名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 140px">
            <el-option label="全局" value="global" />
            <el-option label="文章" value="article" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="标题 / 摘要" clearable @keyup.enter="handleQuery" />
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
        :data="conversationList"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="用户" min-width="170">
          <template #default="scope">
            <div class="user-cell">
              <el-avatar :src="scope.row.userAvatar" :size="34">
                {{ getUserInitial(scope.row.userNickname) }}
              </el-avatar>
              <div class="user-meta">
                <span class="user-name">{{ scope.row.userNickname || '-' }}</span>
                <span class="user-id">UID {{ scope.row.userId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="220" prop="title" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.type === 'article' ? 'success' : 'info'" effect="light">
              {{ scope.row.type === 'article' ? '文章' : '全局' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文章" min-width="180" prop="articleTitle" show-overflow-tooltip />
        <el-table-column label="消息数" width="90" align="center" prop="messageCount" />
        <el-table-column label="模型" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.modelDisplayName || scope.row.modelName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="最后消息" width="180" align="center" prop="lastMessageAt" />
        <el-table-column label="创建时间" width="180" align="center" prop="createTime" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" link icon="View" @click="handleViewMessages(scope.row)">
              查看消息
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

    <el-drawer v-model="messageDrawerVisible" :title="drawerTitle" size="42%">
      <div v-loading="messageLoading" class="message-drawer">
        <div v-if="messageList.length" class="message-list">
          <div v-for="message in messageList" :key="message.id" class="message-item" :class="message.role">
            <div class="message-head">
              <el-tag size="small" :type="getRoleTagType(message.role)" effect="light">
                {{ getRoleLabel(message.role) }}
              </el-tag>
              <span class="message-time">{{ message.createTime }}</span>
            </div>
            <div class="message-content">{{ message.content }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无消息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import {ElMessage, ElMessageBox} from 'element-plus'
import {deleteAiConversationApi, getAiConversationListApi, getAiConversationMessagesApi} from '@/api/ai/conversation'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  type: '',
  keyword: '',
  userKeyword: ''
})

const loading = ref(false)
const total = ref(0)
const conversationList = ref<any[]>([])
const selectedIds = ref<number[]>([])

const messageDrawerVisible = ref(false)
const messageLoading = ref(false)
const currentConversation = ref<any>(null)
const messageList = ref<any[]>([])

const drawerTitle = computed(() => {
  if (!currentConversation.value) {
    return '会话消息'
  }
  return `${currentConversation.value.title} · 消息记录`
})

/**
 * 拉取后台 AI 会话列表。
 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getAiConversationListApi(queryParams)
    conversationList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    conversationList.value = []
    total.value = 0
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 按筛选条件查询会话列表。
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
  queryParams.type = ''
  queryParams.keyword = ''
  queryParams.userKeyword = ''
  getList()
}

/**
 * 同步表格多选结果。
 */
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map((item) => item.id)
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
 * 打开指定会话的消息抽屉并加载消息记录。
 */
const handleViewMessages = async (row: any) => {
  currentConversation.value = row
  messageDrawerVisible.value = true
  messageLoading.value = true
  try {
    const { data } = await getAiConversationMessagesApi(row.id, { pageNum: 1, pageSize: 200 })
    messageList.value = data.records || []
  } catch (error) {
    messageList.value = []
  } finally {
    messageLoading.value = false
  }
}

/**
 * 执行会话删除，并同步清理当前抽屉状态。
 */
const doDelete = async (ids: number[]) => {
  await deleteAiConversationApi(ids)
  ElMessage.success('删除成功')
  if (currentConversation.value && ids.includes(currentConversation.value.id)) {
    messageDrawerVisible.value = false
    currentConversation.value = null
    messageList.value = []
  }
  selectedIds.value = []
  await getList()
}

/**
 * 删除单条会话记录。
 */
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除会话《${row.title}》?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await doDelete([row.id])
  } catch (error) {
  }
}

/**
 * 批量删除当前选中的会话记录。
 */
const handleBatchDelete = async () => {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`是否确认删除 ${selectedIds.value.length} 条会话?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await doDelete(selectedIds.value)
  } catch (error) {
  }
}

/**
 * 统一格式化消息角色文案。
 */
const getRoleLabel = (role: string) => {
  if (role === 'system') return '系统'
  if (role === 'assistant') return '助手'
  if (role === 'user') return '用户'
  return role || '消息'
}

/**
 * 统一格式化消息角色标签样式。
 */
const getRoleTagType = (role: string) => {
  if (role === 'assistant') return 'success'
  if (role === 'user') return 'primary'
  return 'info'
}

/**
 * 生成用户头像占位首字母。
 */
const getUserInitial = (nickname?: string) => {
  return nickname?.slice(0, 1) || 'A'
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.user-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-drawer {
  height: 100%;
  overflow: auto;
  padding-right: 4px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-light);
}

.message-item.assistant {
  background: rgba(16, 185, 129, 0.08);
}

.message-item.user {
  background: rgba(59, 130, 246, 0.08);
}

.message-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.message-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-content {
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--el-text-color-primary);
}
</style>
