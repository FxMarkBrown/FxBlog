<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createArticleApi, getArticleInfoApi, updateArticleApi } from '@/api/article'
import { getDictDataApi } from '@/api/dict'
import { uploadFileApi } from '@/api/file'
import { getCategoriesApi, getTagsApi } from '@/api/tags'
import { useNoIndexSeo } from '@/composables/useSeo'
import type { ArticleDetail, TagSummary } from '@/types/article'
import { unwrapResponseData } from '@/utils/response'

interface ArticleFormState {
  id: number | string | ''
  title: string
  summary: string
  content: string
  contentMd: string
  cover: string
  keywords: string
  isOriginal: number
  originalUrl: string
  categoryId: number | string | ''
  tagIds: Array<number | string>
  status: number | string | ''
}

interface CategoryItem {
  id: number | string
  name: string
}

interface DictItem {
  label?: string
  value?: number | string
  [key: string]: unknown
}

interface MarkdownEditorExpose {
  focus: () => void
  getHtml: () => string
  getSelectedText: () => string | undefined
  insert: (generator: (selectedText: string) => {
    targetValue: string
    select?: boolean
    deviationStart?: number
    deviationEnd?: number
  }) => void
}

const MarkdownEditor = defineAsyncComponent(() => import('@/components/Common/MarkdownEditor.vue'))

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const articleFormRef = ref<FormInstance | null>(null)
const coverInputRef = ref<HTMLInputElement | null>(null)
const mdRef = ref<MarkdownEditorExpose | null>(null)

const loading = ref(false)
const bootstrapping = ref(true)
const categories = ref<CategoryItem[]>([])
const tags = ref<TagSummary[]>([])
const statusList = ref<DictItem[]>([])

const articleForm = reactive<ArticleFormState>(createDefaultArticleForm())

const rules = reactive<FormRules>({
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { min: 5, max: 100, message: '标题长度应在5-100个字符之间', trigger: 'blur' }
  ],
  summary: [
    { required: true, message: '请输入文章描述', trigger: 'blur' },
    { min: 10, max: 500, message: '描述长度应在10-500个字符之间', trigger: 'blur' }
  ],
  contentMd: [
    { required: true, message: '请输入文章内容', trigger: 'blur' },
    { validator: validateContentMarkdown, trigger: ['blur', 'change'] }
  ],
  cover: [
    { required: true, message: '请上传封面图片', trigger: 'change' }
  ],
  categoryId: [
    { required: true, message: '请选择文章分类', trigger: 'change' }
  ],
  tagIds: [
    { required: true, message: '请选择文章标签', trigger: 'change' },
    { validator: validateTagIds, trigger: 'change' }
  ],
  originalUrl: [
    { validator: validateOriginalUrl, trigger: 'blur' }
  ],
  keywords: [
    { validator: validateKeywords, trigger: 'blur' }
  ]
})

useNoIndexSeo({
  title: () => (articleForm.id ? '编辑文章' : '写文章'),
  description: '博客文章编辑页'
})

watch(
  () => articleForm.isOriginal,
  (value) => {
    if (value === 1) {
      articleForm.originalUrl = ''
    }
  }
)

onMounted(() => {
  void initializePage()
})

/**
 * 创建文章表单默认值。
 */
function createDefaultArticleForm(): ArticleFormState {
  return {
    id: '',
    title: '',
    summary: '',
    content: '',
    contentMd: '',
    cover: '',
    keywords: '',
    isOriginal: 1,
    originalUrl: '',
    categoryId: '',
    tagIds: [],
    status: ''
  }
}

/**
 * 统一弹出错误提示。
 */
function showError(message: string) {
  if (import.meta.client) {
    ElMessage.error(message)
  }
}

/**
 * 统一弹出成功提示。
 */
function showSuccess(message: string) {
  if (import.meta.client) {
    ElMessage.success(message)
  }
}

/**
 * 校验文章正文内容。
 */
function validateContentMarkdown(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback()
    return
  }

  if (value.length < 50) {
    callback(new Error('文章内容至少需要50个字符'))
    return
  }

  if (/^[a-zA-Z]{10,}$/.test(value)) {
    callback(new Error('文章内容似乎没有实际意义，请认真编写'))
    return
  }

  callback()
}

/**
 * 校验标签数量限制。
 */
function validateTagIds(_rule: unknown, value: Array<number | string>, callback: (error?: Error) => void) {
  if (Array.isArray(value) && value.length > 3) {
    callback(new Error('最多只能选择3个标签'))
    return
  }
  callback()
}

/**
 * 校验转载文章的原文地址。
 */
function validateOriginalUrl(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (articleForm.isOriginal === 1) {
    callback()
    return
  }

  if (!value) {
    callback(new Error('请输入原文地址'))
    return
  }

  try {
    new URL(value)
    callback()
  } catch {
    callback(new Error('请输入有效的URL地址'))
  }
}

/**
 * 校验关键词数量限制。
 */
function validateKeywords(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value && value.split(',').map((item) => item.trim()).filter(Boolean).length > 5) {
    callback(new Error('关键词最多不超过5个'))
    return
  }
  callback()
}

/**
 * 读取路由中的文章 ID。
 */
function getRouteArticleId() {
  const rawId = route.query.id
  if (Array.isArray(rawId)) {
    return rawId[0] || ''
  }
  return rawId || ''
}

/**
 * 初始化编辑页依赖数据和文章详情。
 */
async function initializePage() {
  if (!authStore.isLoggedIn) {
    bootstrapping.value = false
    await router.push('/login')
    return
  }

  articleForm.id = getRouteArticleId()

  try {
    await Promise.all([
      loadEditorOptions(),
      loadArticleDetail()
    ])
  } finally {
    bootstrapping.value = false
  }
}

/**
 * 拉取编辑页所需的分类、标签和状态字典。
 */
async function loadEditorOptions() {
  const [categoriesResult, tagsResult, statusResult] = await Promise.allSettled([
    getCategoriesApi(),
    getTagsApi(),
    getDictDataApi('article_status')
  ])

  if (categoriesResult.status === 'fulfilled') {
    categories.value = unwrapResponseData<CategoryItem[] | null>(categoriesResult.value) || []
  } else {
    showError('获取分类列表失败')
  }

  if (tagsResult.status === 'fulfilled') {
    tags.value = unwrapResponseData<TagSummary[] | null>(tagsResult.value) || []
  } else {
    showError('获取标签列表失败')
  }

  if (statusResult.status === 'fulfilled') {
    statusList.value = unwrapResponseData<DictItem[] | null>(statusResult.value) || []
  } else {
    showError('获取文章状态失败')
  }
}

/**
 * 拉取待编辑文章详情。
 */
async function loadArticleDetail() {
  if (!articleForm.id) {
    return
  }

  try {
    const response = await getArticleInfoApi(articleForm.id)
    Object.assign(articleForm, normalizeArticleForm(unwrapResponseData<ArticleDetail | null>(response) || {}))
  } catch (error) {
    showError((error as Error)?.message || '获取文章详情失败')
  }
}

/**
 * 规范化文章编辑表单数据结构。
 */
function normalizeArticleForm(data: Partial<ArticleDetail & ArticleFormState>) {
  const normalizedTagIds = Array.isArray(data.tagIds)
    ? data.tagIds
    : Array.isArray(data.tags)
      ? data.tags.map((item) => item.id).filter((item): item is number | string => item !== undefined && item !== null)
      : []

  return {
    ...createDefaultArticleForm(),
    ...data,
    id: data.id ?? articleForm.id,
    categoryId: data.categoryId ?? '',
    tagIds: normalizedTagIds,
    cover: String(data.cover || ''),
    contentMd: String(data.contentMd || ''),
    content: String(data.content || '')
  }
}

/**
 * 根据字典标签查找文章状态值。
 */
function findStatusValue(labels: string[]) {
  const target = statusList.value.find((item) => labels.includes(String(item.label || '')))
  return target?.value ?? ''
}

/**
 * 保存草稿。
 */
async function saveDraft() {
  const status = findStatusValue(['草稿', 'draft'])
  if (status === '' || status === null || status === undefined) {
    showError('未找到草稿状态配置')
    return
  }

  articleForm.status = status
  await submitArticle()
}

/**
 * 提交审核。
 */
async function publishArticle() {
  const status = findStatusValue(['审核', '待审核', 'review', 'pending'])
  if (status === '' || status === null || status === undefined) {
    showError('未找到审核状态配置')
    return
  }

  articleForm.status = status
  await submitArticle()
}

/**
 * 提交文章创建或更新请求。
 */
async function submitArticle() {
  if (loading.value) {
    return
  }

  const valid = await articleFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  articleForm.content = mdRef.value?.getHtml() || articleForm.content

  try {
    const api = articleForm.id ? updateArticleApi : createArticleApi
    await api(articleForm)
    showSuccess('保存成功')
    await router.push('/user/profile')
  } catch (error) {
    showError((error as Error)?.message || '保存失败')
  } finally {
    loading.value = false
  }
}

/**
 * 触发封面文件选择。
 */
function triggerCoverUpload() {
  coverInputRef.value?.click()
}

/**
 * 上传文章封面图片。
 */
async function handleCoverUpload(event: Event) {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (!file) {
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await uploadFileApi(formData, 'article-cover')
    const coverUrl = unwrapResponseData<string | null>(response)
    if (!coverUrl) {
      throw new Error(String(response.message || response.msg || '上传失败'))
    }
    articleForm.cover = coverUrl
    showSuccess('上传成功')
  } catch (error) {
    showError((error as Error)?.message || '上传失败，请重试')
  } finally {
    if (input) {
      input.value = ''
    }
  }
}

/**
 * 移除当前封面图。
 */
function removeCover() {
  articleForm.cover = ''
}

</script>

<template>
  <div v-loading="bootstrapping" class="editor-container">
    <div class="editor-main">
      <ElForm ref="articleFormRef" :model="articleForm" :rules="rules" label-position="top" size="small">
        <div class="editor-content">
          <div class="content-card">
            <div class="title-cover-layout">
              <div class="title-section">
                <ElFormItem label="文章标题" prop="title">
                  <ElInput v-model="articleForm.title" placeholder="请输入文章标题..." />
                </ElFormItem>
                <ElFormItem label="文章描述" prop="summary">
                  <ElInput v-model="articleForm.summary" type="textarea" :rows="4" placeholder="请输入文章描述..." />
                </ElFormItem>
              </div>

              <div class="cover-section">
                <ElFormItem label="封面图片" prop="cover">
                  <div class="cover-area" @click="triggerCoverUpload">
                    <div v-if="!articleForm.cover" class="cover-placeholder">
                      <i class="fas fa-image"></i>
                      <span>点击上传封面图</span>
                    </div>
                    <div v-else class="cover-preview">
                      <img :src="articleForm.cover" alt="文章封面">
                      <div class="cover-actions">
                        <ElButton circle size="small" type="danger" @click.stop="removeCover">
                          <i class="fas fa-trash"></i>
                        </ElButton>
                      </div>
                    </div>
                  </div>
                  <input
                    ref="coverInputRef"
                    type="file"
                    accept="image/*"
                    style="display: none"
                    @change="handleCoverUpload"
                  >
                </ElFormItem>
              </div>
            </div>
          </div>

          <div class="content-card flex-card">
            <ElFormItem prop="contentMd" class="mb-20">
              <MarkdownEditor
                ref="mdRef"
                v-model="articleForm.contentMd"
                placeholder="输入文章内容..."
                height="500px"
                upload-type="article-content"
              />
            </ElFormItem>
          </div>
        </div>

        <div class="editor-sidebar">
          <div class="sidebar-section">
            <h3 class="section-title">
              <i class="fas fa-folder"></i>
              文章分类
            </h3>
            <ElFormItem prop="categoryId">
              <ElSelect v-model="articleForm.categoryId" placeholder="请选择分类">
                <ElOption v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
              </ElSelect>
            </ElFormItem>
          </div>

          <div class="sidebar-section">
            <h3 class="section-title">
              <i class="fas fa-tags"></i>
              文章标签
            </h3>
            <ElFormItem prop="tagIds">
              <ElSelect
                v-model="articleForm.tagIds"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="请选择标签"
              >
                <ElOption v-for="item in tags" :key="item.id" :label="item.name" :value="item.id" />
              </ElSelect>
            </ElFormItem>
          </div>

          <div class="sidebar-section">
            <h3 class="section-title">
              <i class="fas fa-cog"></i>
              文章设置
            </h3>
            <div class="setting-item">
              <ElSwitch v-model="articleForm.isOriginal" :active-value="1" :inactive-value="0" active-text="原创文章" />
            </div>
            <div v-if="!articleForm.isOriginal" class="setting-item">
              <div class="setting-label">转载地址：</div>
              <ElFormItem prop="originalUrl">
                <ElInput v-model="articleForm.originalUrl" placeholder="请输入原文地址" size="small" />
              </ElFormItem>
            </div>
            <div class="setting-item">
              <div class="setting-label">关键词：</div>
              <ElFormItem prop="keywords">
                <ElInput v-model="articleForm.keywords" placeholder="请输入关键词（多个用逗号隔开）" size="small" />
              </ElFormItem>
            </div>
          </div>

          <div class="sidebar-section">
            <div class="setting-item">
              <ElButton size="small" :loading="loading" @click="saveDraft">
                <i class="fas fa-save"></i>
                保存草稿
              </ElButton>
              <ElButton size="small" type="primary" :loading="loading" @click="publishArticle">
                <i class="fas fa-paper-plane"></i>
                提交审核
              </ElButton>
            </div>
          </div>
        </div>
      </ElForm>
    </div>
  </div>
</template>

<style scoped lang="scss">
.editor-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.editor-main {
  flex: 1;
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 40px;

  :deep(.el-form) {
    display: flex;
    gap: 24px;
  }
}

.editor-content {
  flex: 1;
  min-width: 0;

  .content-card {
    margin-bottom: 24px;
    padding: 24px;
    border-radius: 8px;
    background: var(--card-bg);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);

    &:last-child {
      margin-bottom: 0;
    }

    &.flex-card {
      display: flex;
      flex: 1;
      flex-direction: column;

      :deep(.el-form-item) {
        display: flex;
        flex: 1;
        flex-direction: column;
        margin-bottom: 0;
      }

      :deep(.el-form-item__content) {
        display: flex;
        flex: 1;
        flex-direction: column;
      }
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 12px;
    color: #999;
    font-size: 13px;
    font-weight: 400;
  }

  .title-cover-layout {
    display: flex;
    align-items: flex-start;
    gap: 24px;
  }

  .title-section {
    flex: 1;
  }

  .cover-section {
    width: 240px;
    flex-shrink: 0;
  }

  .cover-area {
    position: relative;
    width: 100%;
    height: 135px;
    overflow: hidden;
    border: 1px dashed var(--border-color);
    border-radius: 4px;
    background: var(--card-bg);
    cursor: pointer;
    transition: border-color 0.15s ease;

    &:hover {
      border-color: #409eff;
    }
  }

  .cover-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    height: 100%;
    color: #999;

    i {
      font-size: 24px;
    }

    span {
      font-size: 13px;
    }
  }

  .cover-preview {
    height: 100%;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .cover-actions {
    position: absolute;
    top: 12px;
    right: 12px;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .cover-preview:hover .cover-actions {
    opacity: 1;
  }

}

.editor-sidebar {
  display: flex;
  width: 320px;
  flex-shrink: 0;
  flex-direction: column;
  gap: 24px;

  .sidebar-section {
    padding: 24px;
    border-radius: 8px;
    background: var(--card-bg);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 12px;
    color: #999;
    font-size: 13px;
    font-weight: 500;

    i {
      color: #409eff;
      font-size: 14px;
    }
  }

  .setting-item {
    padding: 12px 0;
    border-bottom: 1px solid var(--border-color);

    &:first-child {
      padding-top: 0;
    }

    &:last-child {
      padding-bottom: 0;
      border-bottom: none;
    }
  }

  .setting-label {
    margin-bottom: 8px;
    color: #999;
    font-size: 13px;
  }

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

@media screen and (max-width: 1200px) {
  .editor-main {
    padding: 24px;

    :deep(.el-form) {
      flex-direction: column;
    }
  }

  .editor-sidebar {
    width: 100%;
  }
}

@media screen and (max-width: 768px) {
  .editor-main {
    padding: 16px;

    :deep(.el-form) {
      gap: 16px;
    }
  }

  .editor-content {
    .content-card {
      margin-bottom: 16px;
      padding: 20px;
    }

    .title-cover-layout {
      flex-direction: column;
      gap: 16px;
      width: 100%;
    }

    .title-section,
    .cover-section {
      width: 100%;
    }

    .cover-area {
      width: 100% !important;
      height: 120px;
    }
    :deep(.el-form-item),
    :deep(.el-form-item__content),
    :deep(.el-input),
    :deep(.el-textarea) {
      width: 100%;
    }
  }

  .editor-sidebar {
    gap: 16px;

    .sidebar-section {
      padding: 20px;
    }
  }

  :deep(.md-editor-toolbar) {
    overflow-x: auto;
    overflow-y: hidden;
    white-space: nowrap;
    scrollbar-width: none;
  }

  :deep(.md-editor-toolbar::-webkit-scrollbar) {
    display: none;
  }

  :deep(.md-editor-toolbar-item) {
    flex: 0 0 auto;
  }

  :deep(.md-editor) {
    min-height: 360px !important;
  }
}
</style>
