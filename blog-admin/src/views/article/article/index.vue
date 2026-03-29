<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="标题" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入文章标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="queryParams.categoryId" placeholder="请选择分类" clearable>
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" prop="tagId">
          <el-select v-model="queryParams.tagId" placeholder="请选择标签" clearable>
            <el-option v-for="item in tagOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option v-for="item in statusOptions" :key="item.id" :value="item.value" :label="item.label" />
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
            <el-button type="primary" icon="Plus" @click="handleAdd" v-permission="['sys:article:add']">新增文章</el-button>
            <el-button type="danger" icon="Delete" :disabled="selectedIds.length === 0"
              v-permission="['sys:article:delete']" @click="handleBatchDelete">批量删除</el-button>
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="tableData" style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="封面" align="center" width="133">
          <template #default="scope">
            <el-image style="width: 120px; height: 80px;border-radius: 10px" :src="scope.row.cover" />
          </template>
        </el-table-column>
        <el-table-column label="标题" align="center" prop="title" width="200" show-overflow-tooltip>
          <template #default="scope">
            <span style="color: var(--el-color-primary);">{{ scope.row.title }}</span>
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
            <el-switch @change="handleChangeStatus(scope.row)"
              style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949" v-model="scope.row.status"
              :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="是否推荐" align="center">
          <template #default="{ row }">
            <span v-for="item in yesNoOptions">
              <el-tag :type="item.style" v-if="row.isRecommend === Number(item.value)">
                {{ item.label }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="是否置顶" align="center">
          <template #default="{ row }">
            <span v-for="item in yesNoOptions">
              <el-tag :type="item.style" v-if="row.isStick === Number(item.value)">
                {{ item.label }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="阅读量" align="center" prop="quantity" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button type="primary" link icon="Edit" @click="handleUpdate(scope.row)"
              v-permission="['sys:article:update']">修改</el-button>
            <el-button type="danger" link icon="Delete" @click="handleDelete(scope.row)"
              v-permission="['sys:article:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" :background="true"
          layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
          @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 添加或修改对话框 -->
    <el-dialog
      :title="dialog.title"
      v-model="dialog.visible"
      class="article-editor-dialog"
      width="min(96vw, 1400px)"
      top="2vh"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="文章标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入文章标题" />
        </el-form-item>

        <el-form-item label="文章封面" prop="cover">
          <UploadImage v-model="form.cover" :limit="1" :source="'articleCover'" />
        </el-form-item>

        <el-form-item label="文章简介" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="请输入文章简介" />
        </el-form-item>

        <el-row :gutter="20" class="mb-20">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryName">
              <el-tag
                type="success"
                v-show="form.categoryName"
                style="margin: 0 1rem 0 0"
                :closable="true"
                @close="removeCategory()"
              >
                {{ form.categoryName }}
              </el-tag>
              <!-- 分类选项 -->
              <el-popover
                placement="bottom-start"
                width="460"
                trigger="click"
                v-if="!form.categoryName"
              >
                <div class="popover-title">分类</div>
                <!-- 输入框 -->
                <el-input
                  style="width: 100%"
                  v-model="categoryName"
                  placeholder="请输入分类名,enter添加自定义分类"
                  @keyup.enter="saveCategory"
                />
                <!-- 分类 -->
                <div class="popover-container">
                  <div>添加分类</div>
                  <el-tag
                    v-for="(item, index) of categoryOptions"
                    :key="index"
                    style="margin-left: 3px; margin-top: 2px"
                    class="category-item"
                    @click="addCategory(item.name)"
                  >
                    {{ item.name }}
                  </el-tag>
                </div>
                <template #reference>
                  <el-button type="success" plain> 添加分类 </el-button>
                </template>
              </el-popover>
            </el-form-item>

          </el-col>
          <el-col :span="12">
            <el-form-item label="标签" prop="tags">
              <el-tag
                v-for="(item, index) of form.tags"
                :key="index"
                style="margin: 0 1rem 0 0"
                :closable="true"
                @close="removeTag(item)"
              >
                {{ item }}
              </el-tag>
              <!-- 标签选项 -->
              <el-popover
                placement="bottom-start"
                width="460"
                trigger="click"
                v-if="form.tags && form.tags.length < 3"
              >
                <div class="popover-title">标签</div>
                <!-- 搜索框 -->
                <el-input
                  style="width: 100%"
                  v-model="tagName"
                  placeholder="请输入标签名,enter添加自定义标签"
                  @keyup.enter="saveTag"
                />
                <!-- 标签 -->
                <div class="popover-container">
                  <div>添加标签</div>
                  <el-tag
                    v-for="(item, index) of tagOptions"
                    :key="index"
                    style="margin-left: 3px; margin-top: 2px"
                    @click="addTag(item.name)"
                  >
                    {{ item.name }}
                  </el-tag>
                </div>
                <template #reference>
                  <el-button type="primary" plain> 添加标签 </el-button>
                </template>
              </el-popover>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" class="mb-20">
          <el-col :span="12">
            <el-form-item label="文章类型" prop="isOriginal">
              <el-select v-model="form.isOriginal" placeholder="请选择文章类型">
                <el-option label="原创" :value="1" />
                <el-option label="转载" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关键词" prop="keywords">
              <el-input v-model="form.keywords" placeholder="请输入关键词" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" class="mb-20">
          <el-col :span="12">
            <el-form-item label="发布时间" prop="createTime">
              <el-date-picker
                v-model="form.createTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择发布时间"
                style="width: 100%"
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="转载地址" prop="originalUrl" v-if="form.isOriginal === 0" class="mb-20">
          <el-input v-model="form.originalUrl" placeholder="请输入转载地址" />
        </el-form-item>

        <el-row :gutter="20" class="mb-20">
          <el-col :span="6">
            <el-form-item label="是否置顶" prop="isStick">
              <el-switch style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949" v-model="form.isStick"
                :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="是否发布" prop="status">
              <el-select v-model="form.status" placeholder="请选择文章状态">
                <el-option v-for="item in statusOptions" :key="item.id" :value="Number(item.value)"
                  :label="item.label" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="首页轮播" prop="isCarousel">
              <el-switch style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949" v-model="form.isCarousel"
                :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="是否推荐" prop="isRecommend">
              <el-switch style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
                v-model="form.isRecommend" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="文章内容" prop="contentMd" class="mb-20">
          <MarkdownEditor
            ref="mdRef"
            v-model="form.contentMd"
            placeholder="输入文章内容..."
            height="500px"
            upload-type="articlePicture"
            :enable-video-insert="true"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'
import dayjs from 'dayjs'
import UploadImage from '@/components/Upload/Image.vue'
import MarkdownEditor from '@/components/Common/MarkdownEditor.vue'
import {getCategoryListApi} from '@/api/article/category'
import {getTagListApi} from '@/api/article/tag'
import {
  addArticleApi,
  deleteArticleApi,
  getArticleListApi,
  getDetailApi,
  updateArticleApi,
  updateStatusApi
} from '@/api/article'
import {getDictDataByDictTypesApi} from '@/api/system/dict'

// 模拟数据
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
const formRef = ref<FormInstance>()
const mdRef = ref<InstanceType<typeof MarkdownEditor> | null>(null)
const submitLoading = ref(false)

// 选中项数组
const selectedIds = ref<string[]>([])

// 弹窗控制
const dialog = reactive({
  title: '',
  visible: false,
  type: 'add'
})

// 表单数据
const form = reactive<any>({
  id: undefined,
  title: '',
  cover: '',
  summary: '',
  categoryName: '',
  tags: [],
  content: '',
  contentMd: '',
  isOriginal: 1,
  originalUrl: '',
  isStick: 0,
  status: 1,
  isCarousel: 0,
  isRecommend: 0,
  keywords: '',
  createTime: ''
})

const statusOptions = ref<any>([])
const yesNoOptions = ref<any>([])

const tagName = ref('')
const categoryName = ref('')





// 表单校验规则
const rules = reactive<FormRules>({
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  categoryName: [
    { required: true, message: '请选择文章分类', trigger: 'change' }
  ],
  contentMd: [
    { required: true, message: '请输入文章内容', trigger: 'blur' }
  ],
  summary: [
    { required: true, message: '请输入文章简介', trigger: 'blur' },
    { max: 500, message: '简介最多500个字符', trigger: 'blur' }
  ],
  isOriginal: [
    { required: true, message: '请选择文章类型', trigger: 'change' }
  ],
  tags: [
    { required: true, message: '请选择文章标签', trigger: 'change' }
  ],
  createTime: [
    { required: true, message: '请选择发布时间', trigger: 'change' }
  ],
  originalUrl: [
    {
      required: true,
      message: '请输入转载地址',
      trigger: 'blur',
      validator: (_rule: any, value: string, callback: any) => {
        if (form.isOriginal === 0 && !value) {
          callback(new Error('转载文章必须填写转载地址'))
        } else {
          callback()
        }
      }
    }
  ]
})

const removeTag = (tag: string) => {
  form.tags = form.tags.filter((item: string) => item !== tag)
}

const addTag = (tag: any) => {
  if (form.tags.includes(tag)) {
    ElMessage.warning('标签已存在')
    return
  }
  form.tags.push(tag)
}

const saveTag = () => {
  if (tagName.value.trim() !== "") {
    addTag(tagName.value);
    tagName.value = "";
  }
}

const removeCategory = () => {
  form.categoryName = ''
}

const addCategory = (category: any) => {
  if (form.categoryName.includes(category)) {
    ElMessage.warning('分类已存在')
    return
  }
  form.categoryName = category
}

const saveCategory = () => {
  if (categoryName.value.trim() !== "") {
    addCategory(categoryName.value);
    categoryName.value = "";
  }
}

// 获取分类列表
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
  selectedIds.value = selection.map(item => item.id)
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
  } catch (error) {
  }
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
    selectedIds.value = selectedIds.value.filter(id => id !== row.id)
    await getList()
  } catch (error) {
  }
}

// 发布文章
const handleChangeStatus = async (row: any) => {
  const previousStatus = row.status === 1 ? 0 : 1
  try {
    await updateStatusApi({ id: row.id, status: row.status })
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

//清空表单
const clearForm = () => {
  form.id = undefined
  form.title = ''
  form.cover = undefined
  form.summary = ''
  form.categoryName = ''
  form.tags = []
  form.content = ''
  form.contentMd = ''
  form.originalUrl = ''
  form.isStick = 0
  form.status = 1
  form.isCarousel = 0
  form.isRecommend = 0
  form.keywords = ''
  form.createTime = ''
}

// 新增用户
const handleAdd = () => {
  dialog.type = 'add'
  dialog.title = '新增文章'
  dialog.visible = true
  clearForm()
  form.createTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

// 修改分类
const handleUpdate = async (row: any) => {
  clearForm()
  dialog.type = 'edit'
  dialog.title = '修改文章'
  try {
    const res = await getDetailApi(row.id)
    dialog.visible = true
    Object.assign(form, res.data)
    await nextTick(() => {
      formRef.value?.clearValidate()
    })
  } catch (error) {
  }
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  form.content = mdRef.value?.getHtml() || ''
  try {
    if (dialog.type === 'add') {
      await addArticleApi(form)
      ElMessage.success('新增成功')
    } else {
      await updateArticleApi(form)
      ElMessage.success('修改成功')
    }
    await getList()
    dialog.visible = false
    clearForm()
  } catch (error) {
  } finally {
    submitLoading.value = false
  }
}

// 取消按钮
const cancel = () => {
  dialog.visible = false
  formRef.value?.resetFields()
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

watch(() => dialog.visible, (visible) => {
  if (!visible) {
    submitLoading.value = false
    clearForm()
    nextTick(() => {
      formRef.value?.clearValidate()
    })
  }
})
</script>

<style lang="scss" scoped>
.avatar-uploader {
  :deep(.el-upload) {
    border: 2px dashed var(--el-border-color-lighter);
    border-radius: 8px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: var(--el-transition-duration-fast);

    &:hover {
      border-color: var(--el-color-primary);
      background-color: var(--el-color-primary-light-9);
    }
  }

  .avatar-uploader-icon {
    font-size: 28px;
    color: var(--el-text-color-secondary);
    width: 120px;
    height: 120px;
    text-align: center;
    line-height: 120px;
  }

  .avatar {
    width: 120px !important;
    height: 120px !important;
  }
}

.app-container {

  .pagination-container {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }

  :deep(.el-dialog) {
    .el-dialog__body {
      padding: 20px 40px;
    }

    .el-form {
      .el-form-item {
        margin-bottom: 22px;

        &:last-child {
          margin-bottom: 0;
        }

        .el-form-item__label {
          font-weight: 500;
          padding-right: 20px;
        }
      }

      .el-select {
        width: 100%;
      }
    }

  }

  :deep(.article-editor-dialog) {
    display: flex;
    flex-direction: column;
    max-height: 96vh;

    .el-dialog__body {
      flex: 1;
      overflow-y: auto;
      max-height: calc(96vh - 140px);
      padding: 18px 28px;
    }

    .el-dialog__footer {
      padding: 16px 28px 20px;
      background: var(--el-bg-color);
      border-top: 1px solid var(--el-border-color-lighter);
    }
  }

  .mb-20 {
    margin-bottom: 20px;
  }



  .dialog-footer {
    text-align: right;

    .el-button {
      padding: 12px 25px;

      &+.el-button {
        margin-left: 12px;
      }
    }
  }

  .el-tag {
    margin-right: 8px;
    margin-bottom: 8px;
  }
}

@media (max-width: 992px) {
  .app-container {
    :deep(.article-editor-dialog) {
      width: min(98vw, 1400px) !important;

      .el-dialog__body {
        padding: 16px 16px 12px;
        max-height: calc(96vh - 128px);
      }

      .el-dialog__footer {
        padding: 12px 16px 16px;
      }
    }

    .dialog-footer {
      display: flex;
      justify-content: flex-end;
      gap: 12px;

      .el-button {
        min-width: 96px;
        padding: 10px 18px;
        margin-left: 0;
      }
    }
  }
}

:root[data-theme='dark'] {
  .app-container {
    :deep(.el-dialog) {
      .el-form {
        .el-input__wrapper {
          box-shadow: 0 0 0 1px var(--el-border-color) inset;
        }
      }
    }
  }
}
</style>
