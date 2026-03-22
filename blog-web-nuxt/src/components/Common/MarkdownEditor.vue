<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { marked } from 'marked'
import 'md-editor-v3/lib/style.css'
import { uploadFileApi } from '@/api/file'
import { unwrapResponseData } from '@/utils/response'

interface MarkdownEditorProps {
  modelValue?: string
  height?: string
  placeholder?: string
  uploadType?: string
}

const props = withDefaults(defineProps<MarkdownEditorProps>(), {
  modelValue: '',
  height: '500px',
  placeholder: '输入文章内容...',
  uploadType: 'article-content'
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
}>()

const MdEditorComponent = defineAsyncComponent(() => import('md-editor-v3').then((module) => module.MdEditor))

const innerValue = ref(props.modelValue)
const htmlValue = ref('')
const editorTheme = ref<'light' | 'dark'>('light')
let themeObserver: MutationObserver | null = null

const editorStyle = computed(() => ({
  height: props.height,
  width: '100%'
}))

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

defineExpose({
  getHtml
})
</script>

<template>
  <div class="markdown-editor">
    <ClientOnly>
      <MdEditorComponent
        v-model="innerValue"
        :theme="editorTheme"
        preview-theme="github"
        code-theme="github"
        :placeholder="placeholder"
        :style="editorStyle"
        @on-upload-img="handleUploadImg"
        @on-html-changed="handleHtmlChanged"
      />

      <template #fallback>
        <div class="markdown-fallback" :style="editorStyle">编辑器加载中...</div>
      </template>
    </ClientOnly>
  </div>
</template>

<style scoped lang="scss">
.markdown-editor {
  width: 100%;

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
