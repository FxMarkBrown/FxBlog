<template>
  <div ref="pageRef" v-loading="pageLoading" class="article-edit">
    <!-- 顶部操作区 -->
    <div class="edit-header">
      <div class="header-left">
        <el-button icon="Back" @click="handleBack">返回</el-button>
        <span class="page-title">{{ isEdit ? '修改文章' : '新增文章' }}</span>
      </div>
      <div class="header-right">
        <el-button type="primary" :loading="submitLoading" icon="Promotion" @click="submitForm">
          保存
        </el-button>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="edit-body">
      <!-- 左侧：标题 + 编辑器 -->
      <div class="edit-main">
        <el-form-item prop="title" class="title-item">
          <el-input
            v-model="form.title"
            placeholder="请输入文章标题"
            size="large"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item prop="contentMd" class="editor-item">
          <div ref="editorWrapRef" class="editor-wrap">
            <MarkdownEditor
              ref="mdRef"
              v-model="form.contentMd"
              placeholder="输入文章内容..."
              :height="`${editorHeight}px`"
              upload-type="articlePicture"
              :enable-video-insert="true"
            />
          </div>
        </el-form-item>
      </div>

      <!-- 右侧：元信息栏 -->
      <div class="edit-aside">
        <el-form-item label="文章封面" prop="cover">
          <UploadImage v-model="form.cover" :limit="1" :source="'articleCover'" />
        </el-form-item>

        <el-form-item label="文章简介" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="请输入文章简介" />
        </el-form-item>

        <el-form-item label="分类" prop="categoryName">
          <el-select
            v-model="form.categoryName"
            placeholder="请选择或输入分类"
            filterable
            allow-create
            default-first-option
            clearable
          >
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.name"
              :value="item.name"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标签" prop="tags">
          <el-select
            v-model="form.tags"
            placeholder="请选择或输入标签（最多3个）"
            multiple
            filterable
            allow-create
            default-first-option
            :multiple-limit="3"
          >
            <el-option
              v-for="item in tagOptions"
              :key="item.id"
              :label="item.name"
              :value="item.name"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="文章类型" prop="isOriginal">
          <el-select v-model="form.isOriginal" placeholder="请选择文章类型">
            <el-option label="原创" :value="1" />
            <el-option label="转载" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.isOriginal === 0" label="转载地址" prop="originalUrl">
          <el-input v-model="form.originalUrl" placeholder="请输入转载地址" />
        </el-form-item>

        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="请输入关键词" />
        </el-form-item>

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

        <div class="switch-group">
          <el-form-item label="是否置顶" prop="isStick">
            <el-switch
              v-model="form.isStick"
              style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              :active-value="1"
              :inactive-value="0"
            />
          </el-form-item>
          <el-form-item label="是否发布" prop="status">
            <el-switch
              v-model="form.status"
              style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              :active-value="1"
              :inactive-value="0"
            />
          </el-form-item>
          <el-form-item label="首页轮播" prop="isCarousel">
            <el-switch
              v-model="form.isCarousel"
              style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              :active-value="1"
              :inactive-value="0"
            />
          </el-form-item>
          <el-form-item label="是否推荐" prop="isRecommend">
            <el-switch
              v-model="form.isRecommend"
              style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              :active-value="1"
              :inactive-value="0"
            />
          </el-form-item>
        </div>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import UploadImage from '@/components/Upload/Image.vue'
import MarkdownEditor from '@/components/Common/MarkdownEditor.vue'
import { getCategoryListApi } from '@/api/article/category'
import { getTagListApi } from '@/api/article/tag'
import { addArticleApi, getDetailApi, updateArticleApi } from '@/api/article'
import { useArticleDraft } from '@/composables/useArticleDraft'

const route = useRoute()
const router = useRouter()

const articleId = computed(() => Number(route.params.id) || 0)
const isEdit = computed(() => articleId.value > 0)

const categoryOptions = ref<any>([])
const tagOptions = ref<any>([])

const pageLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const mdRef = ref<InstanceType<typeof MarkdownEditor> | null>(null)

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

// 表单校验规则（contentMd 由 MdEditor 驱动，blur 不会触发，统一在提交时 validate）
const rules = reactive<FormRules>({
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  categoryName: [{ required: true, message: '请选择文章分类', trigger: 'change' }],
  contentMd: [{ required: true, message: '请输入文章内容', trigger: 'change' }],
  summary: [
    { required: true, message: '请输入文章简介', trigger: 'blur' },
    { max: 500, message: '简介最多500个字符', trigger: 'blur' }
  ],
  isOriginal: [{ required: true, message: '请选择文章类型', trigger: 'change' }],
  tags: [{ required: true, message: '请选择文章标签', trigger: 'change' }],
  createTime: [{ required: true, message: '请选择发布时间', trigger: 'change' }],
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

// 草稿（新增页 article-draft:new，编辑页 article-draft:{id}）
const { clearDraft, checkDraft, startWatch } = useArticleDraft({
  key: isEdit.value ? `article-draft:${articleId.value}` : 'article-draft:new',
  form,
  onRestore: (draftForm) => Object.assign(form, draftForm)
})

// 标签最多 3 个，超出时提示并阻止
watch(
  () => form.tags,
  (tags) => {
    if (Array.isArray(tags) && tags.length > 3) {
      form.tags = tags.slice(0, 3)
      ElMessage.warning('最多选择 3 个标签')
    }
  }
)

// 编辑器高度自适应剩余可视区域，至少 600px
const pageRef = ref<HTMLElement | null>(null)
const editorWrapRef = ref<HTMLElement | null>(null)
const editorHeight = ref(600)

const updateEditorHeight = () => {
  if (!pageRef.value || !editorWrapRef.value) return
  const wrapTop = editorWrapRef.value.getBoundingClientRect().top
  const pageBottom = pageRef.value.getBoundingClientRect().bottom
  editorHeight.value = Math.max(600, Math.floor(pageBottom - wrapTop))
}

// 返回列表
const handleBack = () => {
  router.push('/article/index')
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  form.content = mdRef.value?.getHtml() || ''
  try {
    if (isEdit.value) {
      await updateArticleApi(form)
      ElMessage.success('修改成功')
    } else {
      await addArticleApi(form)
      ElMessage.success('新增成功')
    }
    clearDraft()
    router.push('/article/index')
  } catch (error) {
  } finally {
    submitLoading.value = false
  }
}

// 加载分类 / 标签选项
const loadOptions = () => {
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
}

// 初始化：回填数据 -> 检测草稿 -> 开启自动保存
const initPage = async () => {
  if (isEdit.value) {
    pageLoading.value = true
    try {
      const res = await getDetailApi(articleId.value)
      Object.assign(form, res.data)
    } catch (error) {
    } finally {
      pageLoading.value = false
    }
  } else {
    form.createTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }

  // 基线快照：新增页为空白表单，编辑页为服务器数据
  const baseline = JSON.parse(JSON.stringify(form))
  await checkDraft(baseline)
  startWatch()
}

onMounted(() => {
  loadOptions()
  initPage()
  nextTick(updateEditorHeight)
  window.addEventListener('resize', updateEditorHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateEditorHeight)
})
</script>

<style lang="scss" scoped>
.article-edit {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .header-left {
    display: flex;
    align-items: center;
  }

  .page-title {
    margin-left: 12px;
    font-size: 16px;
    font-weight: 600;
  }
}

.edit-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
}

.edit-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;

  .title-item {
    flex-shrink: 0;
  }

  .editor-item {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;

    :deep(.el-form-item__content) {
      flex: 1;
      min-height: 0;
    }
  }

  .editor-wrap {
    width: 100%;
    height: 100%;
  }
}

.edit-aside {
  width: 320px;
  flex-shrink: 0;
  overflow-y: auto;
  padding-right: 4px;

  .el-select {
    width: 100%;
  }

  .switch-group {
    display: flex;
    flex-wrap: wrap;
    column-gap: 24px;
  }
}

@media (max-width: 992px) {
  .article-edit {
    height: auto;
  }

  .edit-body {
    flex-direction: column;
  }

  .edit-aside {
    width: 100%;
    overflow-y: visible;
    padding-right: 0;
  }
}
</style>
