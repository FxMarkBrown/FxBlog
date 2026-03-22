<template>
  <div class="app-container">
    <el-card class="box-card">
      <template #header>
        <div class="toolbar">
          <el-form :inline="true" :model="queryParams" class="filter-form">
            <el-form-item label="用户">
              <el-input v-model="queryParams.userKeyword" placeholder="昵称 / 用户名" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="queryParams.bizType" placeholder="全部" clearable style="width: 140px">
                <el-option label="签到" value="sign" />
                <el-option label="发文" value="article" />
                <el-option label="点赞" value="like" />
                <el-option label="收藏" value="favorite" />
                <el-option label="AI消耗" value="consume" />
                <el-option label="后台调整" value="manual" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="toolbar-tip">这里看的是 AI 额度变动明细，正数为获得，负数为扣减。</div>
        </div>
      </template>

      <el-table v-loading="loading" :data="logList" style="width: 100%">
        <el-table-column label="用户" min-width="190">
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
        <el-table-column label="类型" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getBizTagType(scope.row.bizType)" effect="light">
              {{ getBizLabel(scope.row.bizType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="额度变化" width="140" align="center">
          <template #default="scope">
            <span class="delta" :class="{ positive: Number(scope.row.tokenDelta) > 0, negative: Number(scope.row.tokenDelta) < 0 }">
              {{ formatDelta(scope.row.tokenDelta) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="220" prop="sourceTitle" show-overflow-tooltip />
        <el-table-column label="备注" min-width="240" prop="remark" show-overflow-tooltip />
        <el-table-column label="时间" width="180" align="center" prop="createTime" />
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
  </div>
</template>

<script setup lang="ts">
import { getAiQuotaLogListApi } from '@/api/ai/quota'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  bizType: '',
  userKeyword: ''
})

const loading = ref(false)
const total = ref(0)
const logList = ref<any[]>([])

/**
 * 拉取额度流水列表。
 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getAiQuotaLogListApi(queryParams)
    logList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    logList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 按筛选条件查询额度流水。
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
  queryParams.bizType = ''
  queryParams.userKeyword = ''
  getList()
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
 * 生成用户头像占位首字母。
 */
const getUserInitial = (nickname?: string) => nickname?.slice(0, 1) || 'A'

/**
 * 统一格式化额度流水业务类型文案。
 */
const getBizLabel = (bizType: string) => {
  if (bizType === 'sign') return '签到'
  if (bizType === 'article') return '发文'
  if (bizType === 'like') return '点赞'
  if (bizType === 'favorite') return '收藏'
  if (bizType === 'consume') return 'AI消耗'
  if (bizType === 'manual') return '后台调整'
  return bizType || '未知'
}

/**
 * 统一格式化额度流水业务类型标签样式。
 */
const getBizTagType = (bizType: string) => {
  if (bizType === 'sign') return 'success'
  if (bizType === 'article') return 'warning'
  if (bizType === 'like') return 'danger'
  if (bizType === 'favorite') return 'primary'
  if (bizType === 'consume') return 'info'
  return ''
}

/**
 * 格式化额度增减值，正数补上前导加号。
 */
const formatDelta = (value?: number) => {
  const amount = Number(value || 0)
  return `${amount > 0 ? '+' : ''}${amount.toLocaleString('zh-CN')}`
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-form {
  margin-bottom: 0;
}

.toolbar-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

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
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.user-id {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.delta {
  font-weight: 700;
}

.delta.positive {
  color: #16a34a;
}

.delta.negative {
  color: #dc2626;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
