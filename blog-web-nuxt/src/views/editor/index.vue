<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import type { FormInst, FormItemRule, FormRules } from 'naive-ui'
import { createArticleApi, getArticleInfoApi, updateArticleApi } from '@/api/article'
import { getDictDataApi } from '@/api/dict'
import type { UploadedFileDetail } from '@/api/file'
import { uploadFileApi } from '@/api/file'
import { getCategoriesApi, getTagsApi } from '@/api/tags'
import { useNoIndexSeo } from '@/composables/useSeo'
import type { ArticleDetail, TagSummary } from '@/types/article'
import { message } from '@/utils/feedback'
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

type ArticleFormSource = Partial<ArticleDetail> & Partial<ArticleFormState>
type ArticleFormInput = ArticleDetail | Partial<ArticleFormState> | null | undefined

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
  insert: (
    generator: (selectedText: string) => {
      targetValue: string
      select?: boolean
      deviationStart?: number
      deviationEnd?: number
    }
  ) => void
}

const MarkdownEditor = defineAsyncComponent(() => import('@/components/Common/MarkdownEditor.vue'))

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const articleFormRef = ref<FormInst | null>(null)
const coverInputRef = ref<HTMLInputElement | null>(null)
const mdRef = ref<MarkdownEditorExpose | null>(null)

const loading = ref(false)
const bootstrapping = ref(true)
const categories = ref<CategoryItem[]>([])
const tags = ref<TagSummary[]>([])
const statusList = ref<DictItem[]>([])

const articleForm = reactive<ArticleFormState>(createDefaultArticleForm())

const categoryOptions = computed(() =>
  categories.value.map((item) => ({ label: item.name, value: item.id }))
)
const tagOptions = computed(() => tags.value.map((item) => ({ label: item.name, value: item.id })))

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
  cover: [{ required: true, message: '请上传封面图片', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择文章分类', trigger: 'change' }],
  tagIds: [
    { required: true, type: 'array', message: '请选择文章标签', trigger: 'change' },
    { validator: validateTagIds, trigger: 'change' }
  ],
  originalUrl: [{ validator: validateOriginalUrl, trigger: 'blur' }],
  keywords: [{ validator: validateKeywords, trigger: 'blur' }]
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
function showError(text: string) {
  if (import.meta.client) {
    message.error(text)
  }
}

/**
 * 统一弹出成功提示。
 */
function showSuccess(text: string) {
  if (import.meta.client) {
    message.success(text)
  }
}

/**
 * 校验文章正文内容。
 */
function validateContentMarkdown(_rule: FormItemRule, value: string) {
  if (!value) {
    return true
  }

  if (value.length < 50) {
    return new Error('文章内容至少需要50个字符')
  }

  if (/^[a-zA-Z]{10,}$/.test(value)) {
    return new Error('文章内容似乎没有实际意义，请认真编写')
  }

  return true
}

/**
 * 校验标签数量限制。
 */
function validateTagIds(_rule: FormItemRule, value: Array<number | string>) {
  if (Array.isArray(value) && value.length > 3) {
    return new Error('最多只能选择3个标签')
  }
  return true
}

/**
 * 校验转载文章的原文地址。
 */
function validateOriginalUrl(_rule: FormItemRule, value: string) {
  if (articleForm.isOriginal === 1) {
    return true
  }

  if (!value) {
    return new Error('请输入原文地址')
  }

  try {
    new URL(value)
    return true
  } catch {
    return new Error('请输入有效的URL地址')
  }
}

/**
 * 校验关键词数量限制。
 */
function validateKeywords(_rule: FormItemRule, value: string) {
  if (
    value &&
    value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean).length > 5
  ) {
    return new Error('关键词最多不超过5个')
  }
  return true
}

/**
 * 读取路由中的文章 ID。
 */
function getRouteArticleId() {
  const rawId = route.query.id
  const normalizedId = Array.isArray(rawId) ? rawId[0] || '' : rawId || ''
  return /^\d+$/.test(String(normalizedId).trim()) ? String(normalizedId).trim() : ''
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
    await Promise.all([loadEditorOptions(), loadArticleDetail()])
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
    const articleDetail = unwrapResponseData<ArticleDetail | null>(response)
    Object.assign(articleForm, normalizeArticleForm(articleDetail))
  } catch (error) {
    showError((error as Error)?.message || '获取文章详情失败')
  }
}

/**
 * 规范化文章编辑表单数据结构。
 */
function normalizeArticleForm(data: ArticleFormInput) {
  const source = (data ?? {}) as ArticleFormSource
  const articleTags = Array.isArray(source.tags) ? source.tags : []

  const normalizedTagIds = Array.isArray(source.tagIds)
    ? source.tagIds
    : articleTags
        .map((item) => item.id)
        .filter((item): item is number | string => item !== undefined && item !== null)

  return {
    ...createDefaultArticleForm(),
    ...source,
    id: source.id ?? articleForm.id,
    categoryId: source.categoryId ?? '',
    tagIds: normalizedTagIds,
    cover: String(source.cover || ''),
    contentMd: String(source.contentMd || ''),
    content: String(source.content || '')
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

  const valid = await articleFormRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
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
    const response = await uploadFileApi(formData, 'articleCover')
    const uploadedFile = unwrapResponseData<UploadedFileDetail | null>(response)
    const coverUrl = String(uploadedFile?.url || '')
    if (!coverUrl) {
      showError(String(response.message || response.msg || '上传失败'))
      return
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
      <NForm
        ref="articleFormRef"
        :model="articleForm"
        :rules="rules"
        label-placement="top"
        size="small"
      >
        <div class="editor-content">
          <div class="content-card">
            <div class="title-cover-layout">
              <div class="title-section">
                <NFormItem label="文章标题" path="title">
                  <NInput v-model:value="articleForm.title" placeholder="请输入文章标题..." />
                </NFormItem>
                <NFormItem label="文章描述" path="summary">
                  <NInput
                    v-model:value="articleForm.summary"
                    type="textarea"
                    :rows="4"
                    placeholder="请输入文章描述..."
                  />
                </NFormItem>
              </div>

              <div class="cover-section">
                <NFormItem label="封面图片" path="cover">
                  <div class="cover-area" @click="triggerCoverUpload">
                    <div v-if="!articleForm.cover" class="cover-placeholder">
                      <i class="fas fa-image"></i>
                      <span>点击上传封面图</span>
                    </div>
                    <div v-else class="cover-preview">
                      <img :src="articleForm.cover" alt="文章封面" />
                      <div class="cover-actions">
                        <NButton circle size="small" type="error" @click.stop="removeCover">
                          <i class="fas fa-trash"></i>
                        </NButton>
                      </div>
                    </div>
                  </div>
                  <input
                    ref="coverInputRef"
                    type="file"
                    accept="image/*"
                    style="display: none"
                    @change="handleCoverUpload"
                  />
                </NFormItem>
              </div>
            </div>
          </div>

          <div class="content-card flex-card">
            <NFormItem path="contentMd" class="mb-20">
              <MarkdownEditor
                ref="mdRef"
                v-model="articleForm.contentMd"
                placeholder="输入文章内容..."
                height="500px"
                upload-type="articlePicture"
              />
            </NFormItem>
          </div>
        </div>

        <div class="editor-sidebar">
          <div class="sidebar-section">
            <h3 class="section-title">
              <i class="fas fa-folder"></i>
              文章分类
            </h3>
            <NFormItem path="categoryId">
              <NSelect
                v-model:value="articleForm.categoryId"
                :options="categoryOptions"
                placeholder="请选择分类"
              />
            </NFormItem>
          </div>

          <div class="sidebar-section">
            <h3 class="section-title">
              <i class="fas fa-tags"></i>
              文章标签
            </h3>
            <NFormItem path="tagIds">
              <NSelect
                v-model:value="articleForm.tagIds"
                multiple
                filterable
                tag
                :options="tagOptions"
                placeholder="请选择标签"
              />
            </NFormItem>
          </div>

          <div class="sidebar-section">
            <h3 class="section-title">
              <i class="fas fa-cog"></i>
              文章设置
            </h3>
            <div class="setting-item">
              <NSwitch
                v-model:value="articleForm.isOriginal"
                :checked-value="1"
                :unchecked-value="0"
              />
              <span class="setting-switch-label">原创文章</span>
            </div>
            <div v-if="!articleForm.isOriginal" class="setting-item">
              <div class="setting-label">转载地址：</div>
              <NFormItem path="originalUrl">
                <NInput
                  v-model:value="articleForm.originalUrl"
                  placeholder="请输入原文地址"
                  size="small"
                />
              </NFormItem>
            </div>
            <div class="setting-item">
              <div class="setting-label">关键词：</div>
              <NFormItem path="keywords">
                <NInput
                  v-model:value="articleForm.keywords"
                  placeholder="请输入关键词（多个用逗号隔开）"
                  size="small"
                />
              </NFormItem>
            </div>
          </div>

          <div class="sidebar-section">
            <div class="setting-item">
              <NButton size="small" :loading="loading" @click="saveDraft">
                <i class="fas fa-save"></i>
                保存草稿
              </NButton>
              <NButton size="small" type="primary" :loading="loading" @click="publishArticle">
                <i class="fas fa-paper-plane"></i>
                提交审核
              </NButton>
            </div>
          </div>
        </div>
      </NForm>
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

  :deep(.n-form) {
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

      :deep(.n-form-item) {
        display: flex;
        flex: 1;
        flex-direction: column;
        margin-bottom: 0;
      }

      :deep(.n-form-item-blank) {
        display: flex;
        flex: 1;
        flex-direction: column;
      }
    }
  }

  :deep(.n-form-item) {
    margin-bottom: 0;
  }

  :deep(.n-form-item-label) {
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
      border-color: var(--primary-color);
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
      color: var(--primary-color);
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

  .setting-switch-label {
    margin-left: 8px;
    color: #999;
    font-size: 13px;
  }

  :deep(.n-form-item) {
    margin-bottom: 0;
  }
}

@media screen and (max-width: 1200px) {
  .editor-main {
    padding: 24px;

    :deep(.n-form) {
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

    :deep(.n-form) {
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
    :deep(.n-form-item),
    :deep(.n-form-item-blank),
    :deep(.n-input) {
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
