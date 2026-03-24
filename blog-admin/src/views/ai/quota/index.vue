<template>
  <div class="app-container quota-page">
    <el-card class="rule-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">额度规则</div>
            <div class="card-tip">把门槛和奖励收口到后台，默认按“签到保底、点赞收藏做主增量、发文给明显跃迁”来平衡。</div>
          </div>
          <div class="header-actions">
            <el-button :loading="ruleLoading" @click="loadRule">刷新</el-button>
            <el-button type="primary" :loading="saveLoading" @click="handleSaveRule">保存规则</el-button>
          </div>
        </div>
      </template>

      <el-form :model="ruleForm" label-width="120px" class="rule-form">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="额度开关">
              <el-switch v-model="ruleForm.enabled" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="起聊门槛">
              <el-input-number v-model="ruleForm.minRequestTokens" :min="1" :step="100" :controls-position="'right'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="签到奖励">
              <el-input-number v-model="ruleForm.signRewardTokens" :min="0" :step="200" :controls-position="'right'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="发文奖励">
              <el-input-number v-model="ruleForm.articleRewardTokens" :min="0" :step="1000" :controls-position="'right'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="点赞奖励">
              <el-input-number v-model="ruleForm.likeRewardTokens" :min="0" :step="100" :controls-position="'right'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="收藏奖励">
              <el-input-number v-model="ruleForm.favoriteRewardTokens" :min="0" :step="100" :controls-position="'right'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="每日点赞上限">
              <el-input-number v-model="ruleForm.likeDailyLimit" :min="0" :step="1" :controls-position="'right'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12" :xl="8">
            <el-form-item label="单篇每日上限">
              <el-input-number v-model="ruleForm.likeDailyPerArticleLimit" :min="0" :step="1" :controls-position="'right'" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="list-toolbar">
          <el-form :inline="true" :model="queryParams" class="filter-form">
            <el-form-item label="用户">
              <el-input v-model="queryParams.userKeyword" placeholder="昵称 / 用户名" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="toolbar-tip">点赞/收藏统计的是“用户对他人已发布文章的互动”，今天的点赞用量也会一起显示，方便直接看限额是否合适。</div>
        </div>
      </template>

      <el-table v-loading="loading" :data="quotaList" style="width: 100%">
        <el-table-column label="用户" min-width="200">
          <template #default="scope">
            <div class="user-cell">
              <el-avatar :src="scope.row.userAvatar" :size="36">{{ getUserInitial(scope.row.userNickname) }}</el-avatar>
              <div class="user-meta">
                <span class="user-name">{{ scope.row.userNickname || '-' }}</span>
                <span class="user-id">@{{ scope.row.username || '-' }} / UID {{ scope.row.userId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可用额度" min-width="140" align="center">
          <template #default="scope">
            <span class="strong-number">{{ formatToken(scope.row.availableTokens) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计获得" min-width="140" align="center">
          <template #default="scope">{{ formatToken(scope.row.totalEarnedTokens) }}</template>
        </el-table-column>
        <el-table-column label="累计消耗" min-width="140" align="center">
          <template #default="scope">{{ formatToken(scope.row.usedTokens) }}</template>
        </el-table-column>
        <el-table-column label="签到" min-width="130" align="center">
          <template #default="scope">{{ scope.row.cumulativeSignDays || 0 }} 天 / {{ formatToken(scope.row.signRewardTokens) }}</template>
        </el-table-column>
        <el-table-column label="发文" min-width="130" align="center">
          <template #default="scope">{{ scope.row.articleCount || 0 }} 篇 / {{ formatToken(scope.row.articleRewardTokens) }}</template>
        </el-table-column>
        <el-table-column label="点赞" min-width="130" align="center">
          <template #default="scope">{{ scope.row.likedArticleCount || 0 }} 次 / {{ formatToken(scope.row.likeRewardTokens) }}</template>
        </el-table-column>
        <el-table-column label="收藏" min-width="130" align="center">
          <template #default="scope">{{ scope.row.favoriteArticleCount || 0 }} 篇 / {{ formatToken(scope.row.favoriteRewardTokens) }}</template>
        </el-table-column>
        <el-table-column label="今日点赞" min-width="170" align="center">
          <template #default="scope">
            <span>
              {{ scope.row.todayLikeCount || 0 }}
              <template v-if="scope.row.likeDailyLimit > 0">
                / {{ scope.row.likeDailyLimit }}
                ，剩 {{ scope.row.todayLikeRemainingCount || 0 }}
              </template>
              <template v-else>
                / 不限
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="手动额度" min-width="130" align="center">
          <template #default="scope">
            {{ scope.row.manualBonusTokens > 0 ? formatToken(scope.row.manualBonusTokens) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="最近消耗" width="180" align="center" prop="lastConsumeAt" />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" link icon="Edit" @click="handleAdjustManual(scope.row)">
              调整额度
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
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAiQuotaListApi,
  getAiQuotaRuleApi,
  updateAiQuotaManualApi,
  updateAiQuotaRuleApi
} from '@/api/ai/quota'

/**
 * 生成额度规则默认值，便于回填与重置表单。
 */
const createDefaultRuleForm = () => ({
  enabled: true,
  minRequestTokens: 800,
  signRewardTokens: 3000,
  articleRewardTokens: 12000,
  likeRewardTokens: 600,
  favoriteRewardTokens: 1800,
  likeDailyLimit: 30,
  likeDailyPerArticleLimit: 4
})

const loading = ref(false)
const ruleLoading = ref(false)
const saveLoading = ref(false)
const total = ref(0)
const quotaList = ref<any[]>([])
const ruleForm = reactive(createDefaultRuleForm())

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userKeyword: ''
})

/**
 * 拉取后台当前生效的额度规则。
 */
const loadRule = async () => {
  ruleLoading.value = true
  try {
    const { data } = await getAiQuotaRuleApi()
    Object.assign(ruleForm, createDefaultRuleForm(), data || {})
  } finally {
    ruleLoading.value = false
  }
}

/**
 * 拉取用户额度概览列表。
 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getAiQuotaListApi(queryParams)
    quotaList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    quotaList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 提交并保存额度规则。
 */
const handleSaveRule = async () => {
  saveLoading.value = true
  try {
    const { data } = await updateAiQuotaRuleApi(ruleForm)
    Object.assign(ruleForm, createDefaultRuleForm(), data || {})
    ElMessage.success('额度规则已保存')
    await getList()
  } finally {
    saveLoading.value = false
  }
}

/**
 * 调整指定用户的手动额度。
 */
const handleAdjustManual = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入用户 ${row.userNickname || row.username || row.userId} 的手动额度`,
      '调整手动额度',
      {
        confirmButtonText: '保存',
        cancelButtonText: '取消',
        inputValue: String(row.manualBonusTokens || 0),
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入 0 或正整数'
      }
    )
    await updateAiQuotaManualApi({
      userId: row.userId,
      manualBonusTokens: Number(value)
    })
    ElMessage.success('手动额度已更新')
    await getList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}

/**
 * 按筛选条件重新查询列表。
 */
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

/**
 * 重置筛选条件并恢复第一页。
 */
const handleReset = () => {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.userKeyword = ''
  getList()
}

/**
 * 更新分页大小并重新拉取列表。
 */
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

/**
 * 切换页码并重新拉取列表。
 */
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

/**
 * 格式化 token 数值展示。
 */
const formatToken = (value?: number) => Number(value || 0).toLocaleString('zh-CN')

/**
 * 生成用户头像占位首字母。
 */
const getUserInitial = (nickname?: string) => nickname?.slice(0, 1) || 'A'

onMounted(async () => {
  await Promise.all([loadRule(), getList()])
})
</script>

<style scoped lang="scss">
.quota-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header,
.list-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.card-tip,
.toolbar-tip,
.user-id {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.rule-card :deep(.el-card__body) {
  padding-bottom: 6px;
}

.rule-form :deep(.el-input-number) {
  width: 100%;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
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

.user-name,
.strong-number {
  font-weight: 700;
  color: var(--el-text-color-primary);
}

@media (max-width: 768px) {
  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }
}
</style>
