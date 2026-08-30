<template>
  <div ref="pageRef" v-loading="pageLoading" class="article-edit">
    <!-- 顶部操作区 -->
    <div class="edit-header">
      <div class="header-left">
        <el-button icon="Back" @click="handleBack">返回</el-button>
        <span class="page-title">{{ isEdit ? '修改文章' : '新增文章' }}</span>
      </div>
      <div class="header-right">
        <span class="draft-status">
          <template v-if="dirty">正在编辑…</template>
          <template v-else-if="savedAt">
            草稿已自动保存 {{ dayjs(savedAt).format('HH:mm:ss') }}
          </template>
        </span>
        <el-button type="primary" :loading="submitLoading" icon="Promotion" @click="submitForm">
          保存
        </el-button>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="edit-body"
      @submit.prevent
    >
      <!-- 主行：标题 + 分类 + 标签（必填项常显，一行搞定） -->
      <div class="primary-row">
        <el-form-item prop="title" class="primary-title">
          <el-input
            v-model="form.title"
            placeholder="请输入文章标题"
            size="large"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item prop="categoryName" class="primary-category">
          <el-select
            v-model="form.categoryName"
            placeholder="分类（必选）"
            size="large"
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
        <el-form-item prop="tags" class="primary-tags">
          <el-select
            v-model="form.tags"
            placeholder="标签（必选，最多3个）"
            size="large"
            multiple
            filterable
            allow-create
            default-first-option
            collapse-tags
            collapse-tags-tooltip
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
      </div>

      <!-- 更多设置折叠条 -->
      <div class="settings-toggle" @click="settingsOpen = !settingsOpen">
        <span class="toggle-line"></span>
        <span class="toggle-text">
          更多设置
          <i class="fas fa-chevron-down toggle-arrow" :class="{ 'is-open': settingsOpen }"></i>
        </span>
        <span class="toggle-line"></span>
      </div>

      <!-- 设置面板：默认收起，写作时界面保持干净 -->
      <el-collapse-transition>
        <div v-show="settingsOpen" class="edit-meta">
          <el-form-item label="文章封面" prop="cover" class="meta-cover">
            <UploadImage v-model="form.cover" :limit="1" :source="'articleCover'" />
          </el-form-item>

          <div class="meta-grid">
            <el-form-item label="文章简介" prop="summary" class="meta-summary">
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="请输入文章简介"
              />
            </el-form-item>

            <el-form-item label="文章类型" prop="isOriginal">
              <el-select v-model="form.isOriginal" placeholder="请选择文章类型">
                <el-option label="原创" :value="1" />
                <el-option label="转载" :value="0" />
              </el-select>
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

            <el-form-item
              v-if="form.isOriginal === 0"
              label="转载地址"
              prop="originalUrl"
              class="meta-original-url"
            >
              <el-input v-model="form.originalUrl" placeholder="请输入转载地址" />
            </el-form-item>

            <div class="switch-group">
              <el-form-item label="置顶" prop="isStick">
                <el-switch
                  v-model="form.isStick"
                  style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
                  :active-value="1"
                  :inactive-value="0"
                />
              </el-form-item>
              <el-form-item label="发布" prop="status">
                <el-switch
                  v-model="form.status"
                  style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
                  :active-value="1"
                  :inactive-value="0"
                />
              </el-form-item>
              <el-form-item label="轮播" prop="isCarousel">
                <el-switch
                  v-model="form.isCarousel"
                  style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
                  :active-value="1"
                  :inactive-value="0"
                />
              </el-form-item>
              <el-form-item label="推荐" prop="isRecommend">
                <el-switch
                  v-model="form.isRecommend"
                  style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
                  :active-value="1"
                  :inactive-value="0"
                />
              </el-form-item>
            </div>
          </div>
        </div>
      </el-collapse-transition>

      <!-- 编辑器：撑满剩余高度 -->
      <div class="edit-main">
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
const { clearDraft, checkDraft, startWatch, dirty, savedAt } = useArticleDraft({
  key: isEdit.value ? `article-draft:${articleId.value}` : 'article-draft:new',
  form,
  onRestore: (draftForm) => Object.assign(form, draftForm)
})

// 「更多设置」面板默认收起，保持写作界面干净
const settingsOpen = ref(false)

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

// 编辑器高度自适应剩余可视区域；元信息区已移到上方，高度不足时页面可滚动，下限放宽到 400px
const pageRef = ref<HTMLElement | null>(null)
const editorWrapRef = ref<HTMLElement | null>(null)
const editorHeight = ref(600)

const updateEditorHeight = () => {
  if (!pageRef.value || !editorWrapRef.value) return
  const wrapTop = editorWrapRef.value.getBoundingClientRect().top
  const pageBottom = pageRef.value.getBoundingClientRect().bottom
  editorHeight.value = Math.max(400, Math.floor(pageBottom - wrapTop))
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

  .header-right {
    display: flex;
    align-items: center;
  }

  .draft-status {
    margin-right: 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
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
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

// 主行：标题 + 分类 + 标签，必填项一屏可见
.primary-row {
  flex-shrink: 0;
  display: flex;
  gap: 12px;

  .el-form-item {
    margin-bottom: 0;
  }

  .primary-title {
    flex: 1;
    min-width: 0;
  }

  .primary-category {
    width: 220px;
  }

  .primary-tags {
    width: 320px;
  }

  .el-select {
    width: 100%;
  }
}

// 「更多设置」折叠条：两侧细线 + 居中文字
.settings-toggle {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: var(--el-text-color-secondary);

  .toggle-line {
    flex: 1;
    height: 1px;
    background: var(--el-border-color-lighter);
  }

  .toggle-text {
    display: flex;
    align-items: center;
    gap: 6px;
    transition: color 0.2s ease;
  }

  .toggle-arrow {
    font-size: 11px;
    transition: transform 0.3s ease;

    &.is-open {
      transform: rotate(180deg);
    }
  }

  &:hover .toggle-text {
    color: var(--el-color-primary);
  }
}

.edit-meta {
  flex-shrink: 0;
  display: flex;
  gap: 20px;
  padding: 16px 16px 2px;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  .meta-cover {
    flex-shrink: 0;
    width: 150px;

    // 封面是横图，把上传框压成 3:2 小卡片；已有封面时隐藏「+」占位框
    :deep(.el-upload--picture-card),
    :deep(.el-upload-list--picture-card .el-upload-list__item) {
      width: 150px;
      height: 100px;
    }

    :deep(.el-upload-list--picture-card:has(.el-upload-list__item) .el-upload--picture-card) {
      display: none;
    }
  }

  .meta-grid {
    flex: 1;
    min-width: 0;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    column-gap: 16px;

    .meta-summary {
      grid-column: span 3;
    }

    .meta-original-url {
      grid-column: span 2;
    }

    .el-select {
      width: 100%;
    }

    .switch-group {
      grid-column: span 3;
      display: flex;
      flex-wrap: wrap;
      column-gap: 32px;

      .el-form-item {
        margin-bottom: 12px;
      }
    }
  }
}

.edit-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  .editor-item {
    flex: 1;
    min-height: 0;
    margin-bottom: 0;
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

@media (max-width: 992px) {
  .article-edit {
    height: auto;
  }

  .primary-row {
    flex-wrap: wrap;

    .primary-title {
      flex-basis: 100%;
    }

    .primary-category,
    .primary-tags {
      flex: 1;
      width: auto;
      min-width: 0;
    }
  }

  .edit-meta {
    flex-direction: column;

    .meta-cover {
      width: 100%;
    }

    .meta-grid {
      grid-template-columns: 1fr;

      .meta-summary,
      .meta-original-url,
      .switch-group {
        grid-column: auto;
      }
    }
  }
}
</style>
