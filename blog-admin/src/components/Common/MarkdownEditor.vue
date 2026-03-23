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
import { allToolbar, config, DropdownToolbar, MdEditor } from 'md-editor-v3'
import type { ExposeParam, InsertParam } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { uploadApi } from '@/api/file'
import { installMarkdownAlignPlugin } from '@/utils/markdownAlign'

let markdownAlignConfigured = false

if (!markdownAlignConfigured) {
  config({
    markdownItConfig(md) {
      installMarkdownAlignPlugin(md)
    },
    markdownItPlugins(plugins) {
      return plugins.map((plugin) => {
        if (plugin.type !== 'xss') {
          return plugin
        }

        return {
          ...plugin,
          options: {
            ...(plugin.options || {}),
            extendedWhiteList: {
              div: ['class'],
              p: ['class'],
              span: ['class']
            }
          }
        }
      })
    }
  })
  markdownAlignConfigured = true
}

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
const alignDropdownVisible = ref(false)
const videoDropdownVisible = ref(false)
const videoDialogVisible = ref(false)
const videoUrlInput = ref('')
const videoFileInputRef = ref<HTMLInputElement | null>(null)
let themeObserver: MutationObserver | null = null

const editorStyle = computed(() => ({
  height: props.height,
  width: '100%'
}))

const editorToolbars = computed(() => {
  const toolbars = [...allToolbar] as (string | number)[]
  const titleIndex = toolbars.indexOf('title')
  const alignInsertIndex = titleIndex === -1 ? toolbars.length : titleIndex + 1
  toolbars.splice(alignInsertIndex, 0, 0)

  if (!props.enableVideoInsert) {
    return toolbars
  }

  const imageIndex = toolbars.indexOf('image')
  const videoInsertIndex = imageIndex === -1 ? toolbars.length : imageIndex + 1
  toolbars.splice(videoInsertIndex, 0, 1)
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

/**
 * 同步编辑器当前主题。
 */
const syncTheme = () => {
  editorTheme.value = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

/**
 * 处理对齐下拉可见状态变化。
 */
const handleAlignDropdownChange = (visible: boolean) => {
  alignDropdownVisible.value = visible
}

/**
 * 处理视频下拉可见状态变化。
 */
const handleVideoDropdownChange = (visible: boolean) => {
  videoDropdownVisible.value = visible
}

/**
 * 上传 Markdown 内插入的图片并回填地址。
 */
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

/**
 * 缓存当前 Markdown 渲染后的 HTML。
 */
const handleHtmlChanged = (html: string) => {
  htmlValue.value = html
}

/**
 * 读取编辑器当前 HTML 内容。
 */
const getHtml = () => htmlValue.value || marked.parse(innerValue.value || '')

/**
 * 生成视频标签模板。
 */
const buildVideoTag = (src: string) =>
  `\n<video height="100%" width="100%" controls src="${src}"></video>\n`

/**
 * 规范化 Markdown 资源地址。
 */
const normalizeMarkdownUrl = (url: string) => {
  if (!url) {
    return url
  }
  return encodeURI(url).replace(/#/g, '%23')
}

/**
 * 上传单个资源文件并返回地址。
 */
const uploadSingleFile = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await uploadApi(formData, props.uploadType)
  return normalizeMarkdownUrl(res.data)
}

/**
 * 处理视频菜单选择。
 */
const handleVideoMenuSelect = (command: 'upload' | 'link') => {
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

/**
 * 插入外链视频地址。
 */
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

/**
 * 在编辑器中插入文本或根据选区生成内容。
 */
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

/**
 * 在编辑器中插入对齐容器模板。
 */
const insertAlignBlock = (direction: 'left' | 'center' | 'right') => {
  const defaultText = '在此输入内容'
  const prefix = `::: align-${direction}\n`
  const suffix = '\n:::'

  insert((selectedText) => {
    const content = selectedText || defaultText
    return {
      targetValue: `${prefix}${content}${suffix}`,
      select: !selectedText,
      deviationStart: prefix.length,
      deviationEnd: prefix.length + content.length
    }
  })
  alignDropdownVisible.value = false
  focus()
}

/**
 * 使编辑器重新获得焦点。
 */
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

  .video-dialog-footer {
    text-align: right;
  }
}

.markdown-editor {
  :deep(.md-editor-menu-item) {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  :deep(.md-editor-menu-item i) {
    width: 14px;
    color: var(--el-color-primary);
    text-align: center;
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
