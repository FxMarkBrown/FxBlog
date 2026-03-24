<script setup lang="ts">
import { Link, VideoPlay } from '@element-plus/icons-vue'
import { ElLoading, ElMessage } from 'element-plus'
import { defineAsyncComponent } from 'vue'
import { marked } from 'marked'
import { allToolbar, DropdownToolbar } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { uploadFileApi } from '@/api/file'
import { unwrapResponseData } from '@/utils/response'

interface MarkdownEditorProps {
  modelValue?: string
  height?: string
  placeholder?: string
  uploadType?: string
  enableVideoInsert?: boolean
}

interface MarkdownInsertResult {
  targetValue: string
  select?: boolean
  deviationStart?: number
  deviationEnd?: number
}

interface MarkdownEditorInstance {
  insert: (generator: (selectedText: string) => MarkdownInsertResult) => void
  focus: () => void
  getSelectedText: () => string | undefined
}

const props = withDefaults(defineProps<MarkdownEditorProps>(), {
  modelValue: '',
  height: '500px',
  placeholder: '输入文章内容...',
  uploadType: 'articlePicture',
  enableVideoInsert: true
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
}>()

const MdEditorComponent = defineAsyncComponent(() => import('md-editor-v3').then((module) => module.MdEditor))

const editorRef = ref<MarkdownEditorInstance | null>(null)
const innerValue = ref(props.modelValue)
const htmlValue = ref('')
const editorTheme = ref<'light' | 'dark'>('light')
const alignDropdownVisible = ref(false)
const linkDropdownVisible = ref(false)
const externalLinkDialogVisible = ref(false)
const internalLinkDialogVisible = ref(false)
const videoDropdownVisible = ref(false)
const videoDialogVisible = ref(false)
const externalLinkUrlInput = ref('')
const externalLinkTextInput = ref('')
const internalLinkTargetInput = ref('')
const internalLinkTextInput = ref('')
const videoUrlInput = ref('')
const videoFileInputRef = ref<HTMLInputElement | null>(null)
let themeObserver: MutationObserver | null = null

const editorStyle = computed(() => ({
  height: props.height,
  width: '100%'
}))

const editorToolbars = computed<(string | number)[]>(() => {
  const toolbars = [...allToolbar] as (string | number)[]
  const titleIndex = toolbars.indexOf('title')
  const alignInsertIndex = titleIndex === -1 ? toolbars.length : titleIndex + 1
  toolbars.splice(alignInsertIndex, 0, 0)
  const linkIndex = toolbars.indexOf('link')
  if (linkIndex === -1) {
    toolbars.push(1)
  } else {
    toolbars.splice(linkIndex, 1, 1)
  }

  if (!props.enableVideoInsert) {
    return toolbars
  }

  const imageIndex = toolbars.indexOf('image')
  const videoInsertIndex = imageIndex === -1 ? toolbars.length : imageIndex + 1
  toolbars.splice(videoInsertIndex, 0, 2)
  return toolbars
})

watch(
  () => props.modelValue,
  (value) => {
    if (value !== innerValue.value) {
      innerValue.value = value
    }
  }
)

watch(innerValue, (value) => {
  emit('update:modelValue', value)
})

onMounted(() => {
  syncTheme()
  themeObserver = new MutationObserver(syncTheme)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  themeObserver = null
})

/**
 * 同步编辑器深浅色主题。
 */
function syncTheme() {
  editorTheme.value = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

/**
 * 规范化 Markdown 图片地址，避免特殊字符导致预览异常。
 */
function normalizeMarkdownUrl(url: string | null | undefined) {
  if (!url) {
    return ''
  }
  return encodeURI(url).replace(/#/g, '%23')
}

/**
 * 上传编辑器插入的图片并回填链接。
 */
async function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  const urls = await Promise.all(
    files.map(async (file) => {
      const formData = new FormData()
      formData.append('file', file)
      const response = await uploadFileApi(formData, props.uploadType)
      return normalizeMarkdownUrl(unwrapResponseData<string | null>(response))
    })
  )

  callback(urls.filter(Boolean))
}

/**
 * 缓存当前 Markdown 渲染后的 HTML 内容。
 */
function handleHtmlChanged(html: string) {
  htmlValue.value = html
}

/**
 * 对外暴露 HTML 内容，供编辑页提交时同步使用。
 */
function getHtml() {
  return htmlValue.value || (marked.parse(innerValue.value || '') as string)
}

/**
 * 对外暴露插入能力，供父级在当前光标位置插入自定义语法块。
 */
function insert(generator: (selectedText: string) => MarkdownInsertResult) {
  editorRef.value?.insert(generator)
}

/**
 * 对外暴露聚焦能力，便于插入内容后继续编辑。
 */
function focus() {
  editorRef.value?.focus()
}

/**
 * 读取当前选中的 Markdown 文本。
 */
function getSelectedText() {
  return editorRef.value?.getSelectedText()
}

/**
 * 在编辑器中插入对齐容器模板。
 */
function insertAlignBlock(direction: 'left' | 'center' | 'right') {
  const defaultText = '在此输入内容'
  const prefix = `::: align-${direction}\n`
  const suffix = '\n:::'

  editorRef.value?.insert((selectedText) => {
    const content = selectedText || defaultText
    return {
      targetValue: `${prefix}${content}${suffix}`,
      select: !selectedText,
      deviationStart: prefix.length,
      deviationEnd: prefix.length + content.length
    }
  })
  editorRef.value?.focus()
  alignDropdownVisible.value = false
}

/**
 * 处理链接下拉可见状态变化。
 */
function handleLinkDropdownChange(visible: boolean) {
  linkDropdownVisible.value = visible
}

/**
 * 处理链接菜单选择。
 */
function handleLinkMenuSelect(command: 'external' | 'internal') {
  linkDropdownVisible.value = false

  if (command === 'external') {
    externalLinkDialogVisible.value = true
    return
  }

  internalLinkDialogVisible.value = true
}

/**
 * 将站内目标规范化为新站相对路径。
 */
function normalizeInternalTarget(target: string) {
  const rawTarget = target.trim()
  if (!rawTarget) {
    return ''
  }

  if (/^\d+$/.test(rawTarget)) {
    return `/post/${rawTarget}`
  }

  const postMatch = rawTarget.match(/^\/?post\/(\d+)([?#].*)?$/i)
  if (postMatch) {
    return `/post/${postMatch[1]}${postMatch[2] || ''}`
  }

  if (rawTarget.startsWith('/') || rawTarget.startsWith('#')) {
    return rawTarget
  }

  return `/${rawTarget}`
}

/**
 * 生成 Markdown 链接片段。
 */
function buildMarkdownLink(url: string, explicitText: string) {
  return (selectedText: string): MarkdownInsertResult => {
    const label = explicitText.trim() || selectedText || '链接文字'
    return {
      targetValue: `[${label}](${url})`,
      select: !explicitText.trim() && !selectedText,
      deviationStart: 1,
      deviationEnd: 1 + label.length
    }
  }
}

/**
 * 插入站外链接。
 */
function handleInsertExternalLink() {
  const url = normalizeMarkdownUrl(externalLinkUrlInput.value.trim())
  if (!url) {
    ElMessage.warning('请输入外链地址')
    return
  }

  editorRef.value?.insert(buildMarkdownLink(url, externalLinkTextInput.value))
  externalLinkDialogVisible.value = false
  externalLinkUrlInput.value = ''
  externalLinkTextInput.value = ''
  editorRef.value?.focus()
}

/**
 * 插入站内跳转链接。
 */
function handleInsertInternalLink() {
  const normalizedTarget = normalizeInternalTarget(internalLinkTargetInput.value)
  if (!normalizedTarget) {
    ElMessage.warning('请输入站内路径或文章 ID')
    return
  }

  editorRef.value?.insert(buildMarkdownLink(normalizedTarget, internalLinkTextInput.value))
  internalLinkDialogVisible.value = false
  internalLinkTargetInput.value = ''
  internalLinkTextInput.value = ''
  editorRef.value?.focus()
}

/**
 * 生成视频标签模板。
 */
function buildVideoTag(src: string) {
  return `\n<video height="100%" width="100%" controls src="${src}"></video>\n`
}

/**
 * 上传单个资源文件并返回地址。
 */
async function uploadSingleFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await uploadFileApi(formData, props.uploadType)
  return normalizeMarkdownUrl(unwrapResponseData<string | null>(response))
}

/**
 * 处理对齐下拉可见状态变化。
 */
function handleAlignDropdownChange(visible: boolean) {
  alignDropdownVisible.value = visible
}

/**
 * 处理视频下拉可见状态变化。
 */
function handleVideoDropdownChange(visible: boolean) {
  videoDropdownVisible.value = visible
}

/**
 * 处理视频菜单选择。
 */
function handleVideoMenuSelect(command: 'upload' | 'link') {
  videoDropdownVisible.value = false

  if (command === 'upload') {
    videoFileInputRef.value?.click()
    return
  }

  if (command === 'link') {
    videoDialogVisible.value = true
  }
}

/**
 * 处理视频文件上传并插入编辑器。
 */
async function handleVideoFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const files = Array.from(input?.files || [])
  if (files.length === 0) {
    return
  }

  const loading = ElLoading.service({
    lock: true,
    text: '视频上传中...',
    background: 'rgba(0, 0, 0, 0.35)'
  })

  try {
    for (const file of files) {
      const url = await uploadSingleFile(file)
      if (!url) {
        continue
      }

      editorRef.value?.insert(() => ({
        targetValue: buildVideoTag(url)
      }))
    }
    ElMessage.success('视频已插入')
  } finally {
    loading.close()
    if (input) {
      input.value = ''
    }
  }
}

/**
 * 插入外链视频地址。
 */
function handleInsertVideoUrl() {
  const url = videoUrlInput.value.trim()
  if (!url) {
    ElMessage.warning('请输入视频地址')
    return
  }

  editorRef.value?.insert(() => ({
    targetValue: buildVideoTag(normalizeMarkdownUrl(url))
  }))
  videoDialogVisible.value = false
  videoUrlInput.value = ''
  editorRef.value?.focus()
}

defineExpose({
  focus,
  getHtml,
  getSelectedText,
  insert
})
</script>

<template>
  <div class="markdown-editor">
    <input
      ref="videoFileInputRef"
      type="file"
      accept="video/*"
      multiple
      class="video-file-input"
      @change="handleVideoFileChange"
    >

    <ClientOnly>
      <MdEditorComponent
        ref="editorRef"
        v-model="innerValue"
        :theme="editorTheme"
        preview-theme="github"
        code-theme="github"
        :placeholder="placeholder"
        :style="editorStyle"
        :toolbars="editorToolbars"
        :show-toolbar-name="false"
        @on-upload-img="handleUploadImg"
        @on-html-changed="handleHtmlChanged"
      >
        <template #defToolbars>
          <DropdownToolbar title="插入对齐块" :visible="alignDropdownVisible" @on-change="handleAlignDropdownChange">
            <template #default>
              <i class="fas fa-align-center custom-toolbar-icon"></i>
            </template>

            <template #overlay>
              <ul class="md-editor-menu" @click="alignDropdownVisible = false">
                <li class="md-editor-menu-item" @click="insertAlignBlock('left')">
                  <i class="fas fa-align-left"></i>
                  <span>左对齐</span>
                </li>
                <li class="md-editor-menu-item" @click="insertAlignBlock('center')">
                  <i class="fas fa-align-center"></i>
                  <span>居中</span>
                </li>
                <li class="md-editor-menu-item" @click="insertAlignBlock('right')">
                  <i class="fas fa-align-right"></i>
                  <span>右对齐</span>
                </li>
              </ul>
            </template>
          </DropdownToolbar>

          <DropdownToolbar title="插入链接" :visible="linkDropdownVisible" @on-change="handleLinkDropdownChange">
            <template #default>
              <Link class="custom-toolbar-icon custom-toolbar-icon--svg" />
            </template>

            <template #overlay>
              <ul class="md-editor-menu" @click="linkDropdownVisible = false">
                <li class="md-editor-menu-item" @click="handleLinkMenuSelect('external')">
                  <span>站外链接</span>
                </li>
                <li class="md-editor-menu-item" @click="handleLinkMenuSelect('internal')">
                  <span>站内跳转</span>
                </li>
              </ul>
            </template>
          </DropdownToolbar>

          <DropdownToolbar
            v-if="props.enableVideoInsert"
            title="插入视频"
            :visible="videoDropdownVisible"
            @on-change="handleVideoDropdownChange"
          >
            <template #default>
              <VideoPlay class="custom-toolbar-icon custom-toolbar-icon--svg" />
            </template>

            <template #overlay>
              <ul class="md-editor-menu" @click="videoDropdownVisible = false">
                <li class="md-editor-menu-item" @click="handleVideoMenuSelect('upload')">
                  <span>上传视频</span>
                </li>
                <li class="md-editor-menu-item" @click="handleVideoMenuSelect('link')">
                  <span>添加视频地址</span>
                </li>
              </ul>
            </template>
          </DropdownToolbar>
        </template>
      </MdEditorComponent>

      <template #fallback>
        <div class="markdown-fallback" :style="editorStyle">编辑器加载中...</div>
      </template>
    </ClientOnly>

    <ElDialog
      v-model="externalLinkDialogVisible"
      title="添加外链"
      width="min(92vw, 480px)"
      append-to-body
    >
      <div class="dialog-form-stack">
        <ElInput v-model="externalLinkUrlInput" placeholder="请输入完整外链地址" />
        <ElInput v-model="externalLinkTextInput" placeholder="请输入显示文字，可留空使用选中文本" />
      </div>

      <template #footer>
        <div class="video-dialog-footer">
          <ElButton @click="externalLinkDialogVisible = false">取 消</ElButton>
          <ElButton type="primary" @click="handleInsertExternalLink">确 定</ElButton>
        </div>
      </template>
    </ElDialog>

    <ElDialog
      v-model="internalLinkDialogVisible"
      title="添加站内跳转"
      width="min(92vw, 480px)"
      append-to-body
    >
      <div class="dialog-form-stack">
        <ElInput v-model="internalLinkTargetInput" placeholder="请输入文章 ID、/post/123 或其他站内路径" />
        <ElInput v-model="internalLinkTextInput" placeholder="请输入显示文字，可留空使用选中文本" />
      </div>

      <template #footer>
        <div class="video-dialog-footer">
          <ElButton @click="internalLinkDialogVisible = false">取 消</ElButton>
          <ElButton type="primary" @click="handleInsertInternalLink">确 定</ElButton>
        </div>
      </template>
    </ElDialog>

    <ElDialog
      v-model="videoDialogVisible"
      title="添加视频地址"
      width="min(92vw, 480px)"
      append-to-body
    >
      <ElInput v-model="videoUrlInput" placeholder="请输入可访问的视频地址" />

      <template #footer>
        <div class="video-dialog-footer">
          <ElButton @click="videoDialogVisible = false">取 消</ElButton>
          <ElButton type="primary" @click="handleInsertVideoUrl">确 定</ElButton>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.markdown-editor {
  width: 100%;

  .video-file-input {
    display: none;
  }

  :deep(.md-editor-menu-item) {
    display: flex;
    align-items: center;
    gap: 8px;

    i {
      width: 14px;
      color: var(--primary-color);
      text-align: center;
    }
  }

  :deep(.custom-toolbar-icon) {
    color: inherit;
    font-size: 16px;
  }

  :deep(.custom-toolbar-icon--svg) {
    width: 16px;
    height: 16px;
  }

  :deep(.md-editor) {
    background: var(--card-bg);
    color: var(--text-primary);
    border: 1px solid var(--border-color);
  }

  :deep(.md-editor-toolbar-wrapper),
  :deep(.md-editor-footer) {
    background: var(--card-bg);
    border-color: var(--border-color);
  }

  :deep(.md-editor-input-wrapper),
  :deep(.md-editor-preview-wrapper) {
    background: var(--card-bg);
  }

  :deep(.md-editor-input),
  :deep(.md-editor-preview) {
    color: var(--text-primary);
  }
}

.video-dialog-footer {
  text-align: right;
}

.dialog-form-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.markdown-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--card-bg);
  color: var(--text-secondary);
}
</style>
