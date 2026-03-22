<template>
  <div class="markdown-editor-shell">
    <input
      ref="videoFileInputRef"
      type="file"
      accept="video/*"
      multiple
      class="video-file-input"
      @change="handleVideoFileChange"
    />

    <MdEditor
      ref="editorRef"
      v-model="innerValue"
      class="markdown-editor"
      :theme="editorTheme"
      preview-theme="github"
      code-theme="github"
      :placeholder="placeholder"
      :style="editorStyle"
      :toolbars="editorToolbars"
      @onUploadImg="handleUploadImg"
      @onHtmlChanged="handleHtmlChanged"
    >
      <template v-if="enableVideoInsert" #defToolbars>
        <el-dropdown
          trigger="hover"
          placement="bottom-start"
          :popper-class="videoDropdownPopperClass"
          @command="handleVideoMenuSelect"
        >
          <button type="button" class="md-editor-toolbar-item video-toolbar-button" title="插入视频" aria-label="插入视频">
            <VideoPlay class="video-toolbar-button__icon" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="upload">上传视频</el-dropdown-item>
              <el-dropdown-item command="link">添加视频地址</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </MdEditor>

    <el-dialog
      v-model="videoDialogVisible"
      title="添加视频地址"
      width="min(92vw, 480px)"
      append-to-body
    >
      <el-input v-model="videoUrlInput" placeholder="请输入可访问的视频地址" />
      <template #footer>
        <div class="video-dialog-footer">
          <el-button @click="videoDialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="handleInsertVideoUrl">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { VideoPlay } from '@element-plus/icons-vue'
import { ElLoading, ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import { allToolbar, MdEditor } from 'md-editor-v3'
import type { ExposeParam, InsertParam } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { uploadApi } from '@/api/file'

const props = withDefaults(defineProps<{
  modelValue?: string
  height?: string
  placeholder?: string
  uploadType?: string
  enableVideoInsert?: boolean
}>(), {
  modelValue: '',
  height: '500px',
  placeholder: '输入文章内容...',
  uploadType: 'article-content',
  enableVideoInsert: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editorRef = ref<ExposeParam | null>(null)
const innerValue = ref(props.modelValue)
const htmlValue = ref('')
const editorTheme = ref<'light' | 'dark'>('light')
const videoDialogVisible = ref(false)
const videoUrlInput = ref('')
const videoFileInputRef = ref<HTMLInputElement | null>(null)
let themeObserver: MutationObserver | null = null

const editorStyle = computed(() => ({
  height: props.height,
  width: '100%'
}))

const videoDropdownPopperClass = computed(() =>
  `video-toolbar-dropdown video-toolbar-dropdown--${editorTheme.value}`
)

const editorToolbars = computed(() => {
  if (!props.enableVideoInsert) {
    return allToolbar
  }

  const toolbars = [...allToolbar] as string[]
  const imageIndex = toolbars.indexOf('image')
  const insertIndex = imageIndex === -1 ? toolbars.length : imageIndex + 1
  toolbars.splice(insertIndex, 0, '0')
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

const syncTheme = () => {
  editorTheme.value = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

const handleUploadImg = async (files: File[], callback: (urls: string[]) => void) => {
  const urls = await Promise.all(
    files.map(async (file) => {
      const formData = new FormData()
      formData.append('file', file)
      const res = await uploadApi(formData, props.uploadType)
      return normalizeMarkdownUrl(res.data)
    })
  )

  callback(urls)
}

const handleHtmlChanged = (html: string) => {
  htmlValue.value = html
}

const getHtml = () => htmlValue.value || marked.parse(innerValue.value || '')

const buildVideoTag = (src: string) =>
  `\n<video height="100%" width="100%" controls src="${src}"></video>\n`

const normalizeMarkdownUrl = (url: string) => {
  if (!url) {
    return url
  }
  return encodeURI(url).replace(/#/g, '%23')
}

const uploadSingleFile = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await uploadApi(formData, props.uploadType)
  return normalizeMarkdownUrl(res.data)
}

const handleVideoMenuSelect = (command: 'upload' | 'link') => {
  if (command === 'upload') {
    videoFileInputRef.value?.click()
    return
  }

  if (command === 'link') {
    videoDialogVisible.value = true
  }
}

const handleVideoFileChange = async (event: Event) => {
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
      insert(buildVideoTag(url))
    }
    ElMessage.success('视频已插入')
  } finally {
    loading.close()
    if (input) {
      input.value = ''
    }
  }
}

const handleInsertVideoUrl = () => {
  const url = videoUrlInput.value.trim()
  if (!url) {
    ElMessage.warning('请输入视频地址')
    return
  }
  insert(buildVideoTag(normalizeMarkdownUrl(url)))
  videoDialogVisible.value = false
  videoUrlInput.value = ''
}

const insert = (content: string | ((selectedText: string) => InsertParam)) => {
  if (!editorRef.value) {
    return
  }

  if (typeof content === 'function') {
    editorRef.value.insert(content)
    return
  }

  editorRef.value.insert(() => ({
    targetValue: content
  }))
}

const focus = () => {
  editorRef.value?.focus()
}

defineExpose({
  getHtml,
  insert,
  focus
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
</script>

<style lang="scss" scoped>
.markdown-editor-shell {
  .video-file-input {
    display: none;
  }

  .video-toolbar-button {
    display: inline-flex;
    align-items: center;
    padding-inline: 8px;
  }

  .video-toolbar-button__icon {
    width: 16px;
    height: 16px;
  }

  .video-dialog-footer {
    text-align: right;
  }
}

:deep(.video-toolbar-dropdown) {
  min-width: 148px;
  padding: 0;
  border: 1px solid #e6e6e6;
  border-radius: 3px;
  background: #fff;
  box-shadow: 0 6px 24px 2px rgb(0 0 0 / 10%);
}

:deep(.video-toolbar-dropdown .el-popper__arrow::before) {
  background: #fff;
  border-color: #e6e6e6;
}

:deep(.video-toolbar-dropdown .el-dropdown-menu) {
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

:deep(.video-toolbar-dropdown .el-dropdown-menu__item) {
  padding: 4px 10px;
  font-size: 12px;
  line-height: 16px;
  color: #3f4a54;
  background: transparent;
}

:deep(.video-toolbar-dropdown .el-dropdown-menu__item:first-of-type) {
  padding-top: 8px;
}

:deep(.video-toolbar-dropdown .el-dropdown-menu__item:last-of-type) {
  padding-bottom: 8px;
}

:deep(.video-toolbar-dropdown .el-dropdown-menu__item:focus),
:deep(.video-toolbar-dropdown .el-dropdown-menu__item.is-focus),
:deep(.video-toolbar-dropdown .el-dropdown-menu__item:not(.is-disabled):hover),
:deep(.video-toolbar-dropdown .el-dropdown-menu__item:not(.is-disabled):active) {
  background: #f5f7fa;
  color: #3f4a54;
}

:deep(.video-toolbar-dropdown--dark) {
  border-color: #2d2d2d;
  background: #000;
  box-shadow: 0 6px 24px 2px rgb(0 0 0 / 40%);
}

:deep(.video-toolbar-dropdown--dark .el-popper__arrow::before) {
  background: #000;
  border-color: #2d2d2d;
}

:deep(.video-toolbar-dropdown--dark .el-dropdown-menu__item) {
  color: #999;
}

:deep(.video-toolbar-dropdown--dark .el-dropdown-menu__item:focus),
:deep(.video-toolbar-dropdown--dark .el-dropdown-menu__item.is-focus),
:deep(.video-toolbar-dropdown--dark .el-dropdown-menu__item:not(.is-disabled):hover),
:deep(.video-toolbar-dropdown--dark .el-dropdown-menu__item:not(.is-disabled):active) {
  background: #1b1a1a;
  color: #999;
}

.markdown-editor {
  :deep(.md-editor) {
    background: var(--el-bg-color);
    color: var(--el-text-color-primary);
    border: 1px solid var(--el-border-color);
  }

  :deep(.md-editor-toolbar-wrapper),
  :deep(.md-editor-footer),
  :deep(.md-editor-input-wrapper),
  :deep(.md-editor-preview-wrapper) {
    background: var(--el-bg-color);
    border-color: var(--el-border-color);
  }

  :deep(.md-editor-input),
  :deep(.md-editor-preview) {
    color: var(--el-text-color-primary);
  }
}
</style>
