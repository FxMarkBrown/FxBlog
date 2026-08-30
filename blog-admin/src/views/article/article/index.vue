<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="queryParams.title"
            placeholder="请输入文章标题"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="queryParams.categoryId" placeholder="请选择分类" clearable>
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" prop="tagId">
          <el-select v-model="queryParams.tagId" placeholder="请选择标签" clearable>
            <el-option
              v-for="item in tagOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option
              v-for="item in statusOptions"
              :key="item.id"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作按钮区域 -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <ButtonGroup>
            <el-button
              v-permission="['sys:article:add']"
              type="primary"
              icon="Plus"
              @click="handleAdd"
              >新增文章</el-button
            >
            <el-button
              v-permission="['sys:article:delete']"
              type="danger"
              icon="Delete"
              :disabled="selectedIds.length === 0"
              @click="handleBatchDelete"
              >批量删除</el-button
            >
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="封面" align="center" width="133">
          <template #default="scope">
            <el-image
              style="width: 120px; height: 80px; border-radius: 10px"
              :src="scope.row.cover"
            />
          </template>
        </el-table-column>
        <el-table-column label="标题" align="center" prop="title" width="200" show-overflow-tooltip>
          <template #default="scope">
            <span style="color: var(--el-color-primary)">{{ scope.row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="作者" align="center" prop="nickname" show-overflow-tooltip />
        <el-table-column label="分类" align="center" prop="categoryName" />
        <el-table-column label="标签" align="center" width="200">
          <template #default="scope">
            <el-tag v-for="tag in scope.row.tags" :key="tag.id" class="mx-1" size="small">
              {{ tag.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布状态" align="center" prop="status">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              :active-value="1"
              :inactive-value="0"
              @change="handleChangeStatus(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="是否推荐" align="center">
          <template #default="{ row }">
            <span v-for="item in yesNoOptions" :key="item.value">
              <el-tag v-if="row.isRecommend === Number(item.value)" :type="item.style">
                {{ item.label }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="是否置顶" align="center">
          <template #default="{ row }">
            <span v-for="item in yesNoOptions" :key="item.value">
              <el-tag v-if="row.isStick === Number(item.value)" :type="item.style">
                {{ item.label }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="阅读量" align="center" prop="quantity" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button
              v-permission="['sys:article:update']"
              type="primary"
              link
              icon="Edit"
              @click="handleUpdate(scope.row)"
              >修改</el-button
            >
            <el-button
              v-permission="['sys:article:delete']"
              type="danger"
              link
              icon="Delete"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
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
import type { FormInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getCategoryListApi } from '@/api/article/category'
import { getTagListApi } from '@/api/article/tag'
import { deleteArticleApi, getArticleListApi, updateStatusApi } from '@/api/article'
import { getDictDataByDictTypesApi } from '@/api/system/dict'

const router = useRouter()

// 分类/标签选项
const categoryOptions = ref<any>([])
const tagOptions = ref<any>([])

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  title: '',
  categoryId: undefined,
  tagId: undefined,
  status: undefined
})

const loading = ref(false)
const total = ref(0)
const tableData = ref([])
const queryFormRef = ref<FormInstance>()

// 选中项数组
const selectedIds = ref<string[]>([])

const statusOptions = ref<any>([])
const yesNoOptions = ref<any>([])

// 获取文章列表
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getArticleListApi(queryParams)
    tableData.value = data.records
    total.value = data.total
  } catch (error) {
    tableData.value = []
    total.value = 0
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

// 获取状态列表
const getStatusList = async () => {
  try {
    const { data } = await getDictDataByDictTypesApi(['article_status', 'sys_yes_no'])
    statusOptions.value = data.article_status.list
    yesNoOptions.value = data.sys_yes_no.list
  } catch (error) {
    statusOptions.value = []
    yesNoOptions.value = []
  }
}

// 表格选择项变化
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return

  try {
    await ElMessageBox.confirm(`是否确认删除 ${selectedIds.value.length} 篇文章?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteArticleApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    await getList()
  } catch (error) {}
}

// 删除
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除 ${row.title} 这篇文章?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteArticleApi(row.id)
    ElMessage.success('删除成功')
    selectedIds.value = selectedIds.value.filter((id) => id !== row.id)
    await getList()
  } catch (error) {}
}

// 发布/下线（带确认，取消则恢复原值）
const handleChangeStatus = async (row: any) => {
  const newStatus = row.status
  const previousStatus = newStatus === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '发布' : '下线'

  try {
    await ElMessageBox.confirm(`确认${actionText}《${row.title}》？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    row.status = previousStatus
    return
  }

  try {
    await updateStatusApi({ id: row.id, status: newStatus })
    ElMessage.success('修改成功')
    await getList()
  } catch (error) {
    row.status = previousStatus
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置查询
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

// 新增文章（独立编辑页）
const handleAdd = () => {
  router.push('/article/publish')
}

// 修改文章（独立编辑页）
const handleUpdate = (row: any) => {
  router.push(`/article/edit/${row.id}`)
}

// 分页大小改变
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

// 页码改变
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

// 初始化
onMounted(() => {
  getList()
  getCategoryListApi({ pageNum: 1, pageSize: 1000 })
    .then((res) => {
      categoryOptions.value = res.data.records
    })
    .catch(() => {
      categoryOptions.value = []
    })
  getTagListApi({ pageNum: 1, pageSize: 1000 })
    .then((res) => {
      tagOptions.value = res.data.records
    })
    .catch(() => {
      tagOptions.value = []
    })

  getStatusList()
})
</script>

<style lang="scss" scoped>
.app-container {
  .pagination-container {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }

  .el-tag {
    margin-right: 8px;
    margin-bottom: 8px;
  }
}
</style>
