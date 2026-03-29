<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'md-editor-v3/lib/style.css'
import {
  createArticleConversationApi,
  createGlobalConversationApi,
  deleteConversationApi,
  getConversationDetailApi,
  getConversationMessagesApi,
  getConversationModelOptionsApi,
  getConversationPageApi,
  getConversationQuotaApi,
  renameConversationApi,
  streamConversationMessageApi
} from '@/api/ai'
import { useNoIndexSeo } from '@/composables/useSeo'
import { normalizeMarkdownContent } from '@/utils/ai-markdown'
import { removeToken } from '@/utils/cookie'
import { getThemeMode, initTheme, setThemeMode } from '@/utils/theme'
import { unwrapResponseData } from '@/utils/response'

type AnyRecord = Record<string, any>

const MdPreview = defineAsyncComponent(() => import('md-editor-v3').then((module) => module.MdPreview))

const AI_MODEL_STORAGE_KEY = 'BLOG_AI_SELECTED_MODEL_ID'

/**
 * 生成默认额度快照，保证额度面板在数据返回前也能稳定渲染。
 */
function createEmptyQuotaSnapshot() {
  return {
    enabled: true,
    minRequestTokens: 0,
    availableTokens: 0,
    totalEarnedTokens: 0,
    usedTokens: 0,
    manualBonusTokens: 0,
    signRewardTokens: 0,
    articleRewardTokens: 0,
    likeRewardTokens: 0,
    favoriteRewardTokens: 0,
    signRewardUnitTokens: 0,
    articleRewardUnitTokens: 0,
    likeRewardUnitTokens: 0,
    favoriteRewardUnitTokens: 0,
    cumulativeSignDays: 0,
    articleCount: 0,
    likedArticleCount: 0,
    favoriteArticleCount: 0,
    likeDailyLimit: 0,
    likeDailyPerArticleLimit: 0,
    todayLikeCount: 0,
    todayLikeRemainingCount: 0
  }
}

const route = useRoute()
const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const authStore = useAuthStore()

const messageListRef = ref<HTMLElement | null>(null)
const bootstrapping = ref(false)
const conversationListLoading = ref(false)
const messageLoading = ref(false)
const quotaLoading = ref(false)
const modelOptionsLoading = ref(false)
const sending = ref(false)
const isDarkMode = ref(false)
const quotaExpanded = ref(false)
const messageDraft = ref('')
const streamAbortController = ref<AbortController | null>(null)
const quotaSnapshot = ref(createEmptyQuotaSnapshot())
const chatModels = ref<AnyRecord[]>([])
const selectedModelId = ref('')
const conversations = ref<AnyRecord[]>([])
const currentConversation = ref<AnyRecord>({})
const messages = ref<AnyRecord[]>([])
let themeChangeHandler: (() => void) | null = null

const conversationId = computed(() => Number(route.query.conversationId || 0))
const articleId = computed(() => Number(route.query.articleId || 0))
const isArticleMode = computed(() => route.query.mode === 'article' || currentConversation.value.type === 'article')
const heroBadge = computed(() => (isArticleMode.value ? '文章问答任务' : '一般对话任务'))
const heroTitle = computed(() => (isArticleMode.value ? '这篇文章的一般问答任务' : '站内一般对话任务'))
const chatTitle = computed(() => (isArticleMode.value ? '文章上下文工作区' : '通用对话工作区'))
const chatSubtitle = computed(() => (isArticleMode.value
  ? '当前会话已绑定文章，会优先结合文章 Markdown 上下文回答'
  : '当前会话支持全站知识问答、工具查询与引用片段展示'))
const currentConversationTypeLabel = computed(() => getTypeLabel(currentConversation.value.type || (isArticleMode.value ? 'article' : 'global')))
const conversationCount = computed(() => {
  if (isArticleMode.value) {
    if (articleId.value) {
      return conversations.value.filter((item) => item.type === 'article' && Number(item.articleId) === articleId.value).length
    }
    return conversations.value.filter((item) => item.type === 'article').length
  }
  return conversations.value.filter((item) => item.type === 'global').length
})
const selectedModelOption = computed(() => chatModels.value.find((item) => item.id === selectedModelId.value) || null)
const composerHint = computed(() => (isArticleMode.value ? '支持文章上下文、工具查询与流式回复' : '支持全站问答、工具调用与流式回复'))
const canSend = computed(() => Boolean(conversationId.value && messageDraft.value.trim() && !sending.value && !bootstrapping.value))

useNoIndexSeo({
  title: () => `一般对话任务 - ${runtimeConfig.public.siteName}`,
  description: '站内一般对话任务工作区'
})

watch(
  () => route.fullPath,
  () => {
    if (import.meta.client) {
      bootstrap()
    }
  }
)

watch(
  () => authStore.userInfo,
  (value) => {
    if (!value && !authStore.isLoggedIn) {
      router.push('/login')
    }
  }
)

onMounted(() => {
  isDarkMode.value = initTheme()
  themeChangeHandler = () => {
    syncThemeState()
  }
  window.addEventListener('theme-change', themeChangeHandler)
  if (!authStore.isLoggedIn) {
    void router.push('/login')
    return
  }
  bootstrap()
})

onBeforeUnmount(() => {
  abortActiveStream()
  if (themeChangeHandler) {
    window.removeEventListener('theme-change', themeChangeHandler)
  }
})

/**
 * 统一弹出错误提示，避免服务端阶段误触发消息组件。
 */
function showError(message: string) {
  if (import.meta.client) {
    ElMessage.error(message)
  }
}

/**
 * 统一弹出成功提示，避免重复编写客户端判断。
 */
function showSuccess(message: string) {
  if (import.meta.client) {
    ElMessage.success(message)
  }
}

/**
 * 根据会话类型构造路由参数，保证全局和文章会话跳转一致。
 */
function buildConversationQuery(conversation: AnyRecord) {
  const query: Record<string, string> = {
    conversationId: String(conversation.id)
  }
  if (conversation.type === 'article' && conversation.articleId) {
    query.mode = 'article'
    query.articleId = String(conversation.articleId)
  }
  return query
}

/**
 * 在首屏和路由切换时重建 AI 页状态。
 */
async function bootstrap() {
  if (!authStore.isLoggedIn) {
    await router.push('/login')
    return
  }
  abortActiveStream()
  bootstrapping.value = true
  try {
    await Promise.all([loadConversationList(), loadQuotaSnapshot(), loadChatModels()])
    if (!conversationId.value) {
      await ensureConversation()
      return
    }
    await loadConversationDetail()
    await loadMessages()
    await loadConversationList()
  } finally {
    bootstrapping.value = false
  }
}

/**
 * 在没有指定会话时优先复用历史会话，否则自动创建新会话。
 */
async function ensureConversation() {
  const existingConversation = pickDefaultConversation()
  if (existingConversation) {
    await router.replace({ path: '/ai/chat', query: buildConversationQuery(existingConversation) })
    return
  }
  const response = isArticleMode.value && articleId.value
    ? await createArticleConversationApi(articleId.value, buildCreateConversationPayload())
    : await createGlobalConversationApi(buildCreateConversationPayload())
  const conversation = unwrapResponseData<AnyRecord | null>(response)
  if (conversation) {
    await router.replace({ path: '/ai/chat', query: buildConversationQuery(conversation) })
  }
}

/**
 * 拉取可用模型列表，并恢复本地缓存的默认选项。
 */
async function loadChatModels() {
  modelOptionsLoading.value = true
  try {
    const response = await getConversationModelOptionsApi()
    const models = unwrapResponseData<AnyRecord[] | null>(response)
    chatModels.value = Array.isArray(models) ? models : []
    ensureSelectedModel()
  } finally {
    modelOptionsLoading.value = false
  }
}

/**
 * 校验并恢复当前模型选择，避免本地缓存指向失效模型。
 */
function ensureSelectedModel() {
  if (!import.meta.client) {
    return
  }
  if (!chatModels.value.length) {
    selectedModelId.value = ''
    window.localStorage.removeItem(AI_MODEL_STORAGE_KEY)
    return
  }
  const availableIds = new Set(chatModels.value.map((item) => item.id))
  const storedModelId = window.localStorage.getItem(AI_MODEL_STORAGE_KEY) || ''
  if (storedModelId && !availableIds.has(storedModelId)) {
    window.localStorage.removeItem(AI_MODEL_STORAGE_KEY)
  }
  const defaultModel = chatModels.value.find((item) => item.defaultModel) ?? chatModels.value[0]
  if (!defaultModel) {
    selectedModelId.value = ''
    return
  }
  const nextModelId = availableIds.has(selectedModelId.value)
    ? selectedModelId.value
    : (availableIds.has(storedModelId) ? storedModelId : defaultModel.id)
  selectedModelId.value = nextModelId
  persistSelectedModel(nextModelId)
}

/**
 * 把当前模型选择写入本地缓存，供后续新建会话复用。
 */
function persistSelectedModel(modelId = selectedModelId.value) {
  if (!import.meta.client || !modelId) {
    return
  }
  window.localStorage.setItem(AI_MODEL_STORAGE_KEY, modelId)
}

/**
 * 生成新建会话请求体，只有显式选中模型时才带上模型参数。
 */
function buildCreateConversationPayload() {
  return selectedModelId.value ? { modelId: selectedModelId.value } : {}
}

/**
 * 按当前模式挑选默认会话，优先复用文章上下文会话。
 */
function pickDefaultConversation() {
  if (!conversations.value.length) {
    return null
  }
  if (isArticleMode.value && articleId.value) {
    return conversations.value.find((item) => item.type === 'article' && Number(item.articleId) === articleId.value) || null
  }
  return conversations.value.find((item) => item.type === 'global') || null
}

/**
 * 拉取左侧历史会话列表。
 */
async function loadConversationList() {
  conversationListLoading.value = true
  try {
    const response = await getConversationPageApi({ pageNum: 1, pageSize: 50 })
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    conversations.value = Array.isArray(page.records) ? page.records : []
  } finally {
    conversationListLoading.value = false
  }
}

/**
 * 拉取当前用户的额度概览。
 */
async function loadQuotaSnapshot() {
  quotaLoading.value = true
  try {
    const response = await getConversationQuotaApi()
    quotaSnapshot.value = {
      ...createEmptyQuotaSnapshot(),
      ...(unwrapResponseData<AnyRecord | null>(response) || {})
    }
  } finally {
    quotaLoading.value = false
  }
}

/**
 * 拉取当前会话详情，用于顶部标题和模型信息展示。
 */
async function loadConversationDetail() {
  if (!conversationId.value) {
    currentConversation.value = {}
    return
  }
  const response = await getConversationDetailApi(conversationId.value)
  currentConversation.value = unwrapResponseData<AnyRecord | null>(response) || {}
}

/**
 * 拉取当前会话消息，并在完成后滚动到底部。
 */
async function loadMessages() {
  if (!conversationId.value) {
    messages.value = []
    return
  }
  messageLoading.value = true
  try {
    const response = await getConversationMessagesApi(conversationId.value, { pageNum: 1, pageSize: 50 })
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    messages.value = (Array.isArray(page.records) ? page.records : []).map((item) => normalizeMessage(item))
    await nextTick()
    scrollMessagesToBottom()
  } finally {
    messageLoading.value = false
  }
}

/**
 * 切换到指定历史会话。
 */
function openConversation(conversation: AnyRecord) {
  router.push({ path: '/ai/chat', query: buildConversationQuery(conversation) })
}

/**
 * 创建新的全局会话，并切换到新会话。
 */
async function createGlobalConversation() {
  const response = await createGlobalConversationApi(buildCreateConversationPayload())
  const conversation = unwrapResponseData<AnyRecord | null>(response)
  if (conversation) {
    await router.push({ path: '/ai/chat', query: buildConversationQuery(conversation) })
  }
}

/**
 * 生成模型下拉项文案，展示名称和额度倍率。
 */
function buildModelOptionLabel(model: AnyRecord) {
  return `${model.displayName} · x${formatQuotaMultiplier(model.quotaMultiplier)}`
}

/**
 * 规范化额度倍率显示，去掉无意义的小数位。
 */
function formatQuotaMultiplier(value: unknown) {
  const normalized = Number(value || 1)
  if (Number.isNaN(normalized) || normalized <= 0) {
    return '1'
  }
  return Number.isInteger(normalized) ? String(normalized) : normalized.toFixed(1).replace(/\.0$/, '')
}

/**
 * 从全局主题状态同步当前页面的明暗模式。
 */
function syncThemeState() {
  isDarkMode.value = getThemeMode() === 'dark'
}

/**
 * 切换 AI 页面主题。
 */
function toggleTheme() {
  isDarkMode.value = !isDarkMode.value
  setThemeMode(isDarkMode.value ? 'dark' : 'light')
}

/**
 * 把消息列表滚动到最底部。
 */
function scrollMessagesToBottom() {
  const container = messageListRef.value
  if (!container) {
    return
  }
  container.scrollTop = container.scrollHeight
}

/**
 * 中断当前仍在进行中的流式回复。
 */
function abortActiveStream() {
  if (!streamAbortController.value) {
    return
  }
  streamAbortController.value.abort()
  streamAbortController.value = null
}

/**
 * 把服务端消息结构整理为页面渲染需要的统一格式。
 */
function normalizeMessage(message: AnyRecord) {
  const toolCalls = normalizeToolCalls(parseToolCalls(message.quotePayload, message.toolCalls))
  return {
    ...message,
    content: normalizeMarkdownContent(message.content || '', true),
    reasoningContent: normalizeMarkdownContent(message.reasoningContent || parseReasoningContent(message.quotePayload), true),
    citations: parseCitations(message.quotePayload),
    toolCalls,
    activities: buildMessageActivities(toolCalls),
    pending: false
  }
}

/**
 * 规范化工具调用记录，保留页面展示所需的最小字段。
 */
function normalizeToolCalls(toolCalls: AnyRecord[]) {
  if (!Array.isArray(toolCalls) || !toolCalls.length) {
    return []
  }
  const order: string[] = []
  const latestByKey = new Map<string, AnyRecord>()
  toolCalls.forEach((item, index) => {
    const key = item.id || item.name || `tool-${index}`
    if (!latestByKey.has(key)) {
      order.push(key)
    }
    latestByKey.set(key, {
      key,
      id: item.id || '',
      name: item.name || '未命名工具',
      displayName: item.displayName || '',
      status: item.status || 'requested'
    })
  })
  return order.map((key) => latestByKey.get(key)).filter(Boolean) as AnyRecord[]
}

/**
 * 把工具调用转换成消息中的活动标签。
 */
function buildMessageActivities(toolCalls: AnyRecord[]) {
  const activities: AnyRecord[] = []
  toolCalls.forEach((item) => {
    activities.push({
      key: `tool-${item.key}`,
      kind: 'tool',
      status: item.status || 'requested',
      icon: 'fa-wrench',
      label: `工具调用 - ${item.displayName || item.name || '未命名工具'}`
    })
  })
  return activities
}

/**
 * 从消息附带的 quotePayload 中提取思考过程。
 */
function parseReasoningContent(quotePayload: string) {
  if (!quotePayload) {
    return ''
  }
  try {
    const parsed = JSON.parse(quotePayload) as AnyRecord
    return parsed.reasoningContent || ''
  } catch {
    return ''
  }
}

/**
 * 从消息附带的 quotePayload 中提取引用片段。
 */
function parseCitations(quotePayload: string) {
  if (!quotePayload) {
    return []
  }
  try {
    const parsed = JSON.parse(quotePayload) as AnyRecord
    const citations = Array.isArray(parsed.citations) ? parsed.citations : []
    return citations.map((item: AnyRecord) => ({
      ...item,
      content: normalizeMarkdownContent(item.content || '', false),
      contentPreview: item.contentPreview || ''
    }))
  } catch {
    return []
  }
}

/**
 * 从显式字段或 quotePayload 中提取工具调用列表。
 */
function parseToolCalls(quotePayload: string, toolCalls: AnyRecord[]) {
  if (Array.isArray(toolCalls) && toolCalls.length) {
    return toolCalls
  }
  if (!quotePayload) {
    return []
  }
  try {
    const parsed = JSON.parse(quotePayload) as AnyRecord
    return Array.isArray(parsed.toolCalls) ? parsed.toolCalls : []
  } catch {
    return []
  }
}

/**
 * 使用服务端回写内容替换本地临时消息。
 */
function replaceMessage(tempId: string, nextMessage: AnyRecord) {
  messages.value = messages.value.map((item) => (item.id === tempId ? normalizeMessage(nextMessage) : item))
}

/**
 * 追加流式增量消息，并保持消息列表自动滚动。
 */
function appendStreamDelta(tempId: string, event: AnyRecord) {
  messages.value = messages.value.map((item) => {
    if (item.id !== tempId) {
      return item
    }
    const nextToolCalls = normalizeToolCalls(Array.isArray(event.toolCalls) && event.toolCalls.length ? event.toolCalls : (item.toolCalls || []))
    return {
      ...item,
      content: normalizeMarkdownContent(`${item.content || ''}${event.content || ''}`, true),
      reasoningContent: normalizeMarkdownContent(`${item.reasoningContent || ''}${event.reasoningContent || ''}`, true),
      toolCalls: nextToolCalls,
      activities: buildMessageActivities(nextToolCalls)
    }
  })
  nextTick(() => {
    scrollMessagesToBottom()
  })
}

/**
 * 发送一条消息，并通过流式接口持续接收模型输出。
 */
async function handleSend() {
  const content = messageDraft.value.trim()
  if (!content || !conversationId.value || sending.value) {
    return
  }

  sending.value = true
  const localUserId = `local-user-${Date.now()}`
  const localAssistantId = `local-assistant-${Date.now()}`
  messages.value = [
    ...messages.value,
    {
      id: localUserId,
      conversationId: conversationId.value,
      role: 'user',
      content,
      reasoningContent: '',
      citations: [],
      toolCalls: [],
      activities: [],
      pending: false
    },
    {
      id: localAssistantId,
      conversationId: conversationId.value,
      role: 'assistant',
      content: '',
      reasoningContent: '',
      citations: [],
      toolCalls: [],
      activities: [],
      pending: true
    }
  ]
  messageDraft.value = ''
  await nextTick()
  scrollMessagesToBottom()

  abortActiveStream()
  const abortController = new AbortController()
  streamAbortController.value = abortController

  try {
    await streamConversationMessageApi(
      conversationId.value,
      { content },
      {
        onUser: (event) => {
          if (event?.message) {
            replaceMessage(localUserId, event.message)
          }
        },
        onDelta: (event) => {
          appendStreamDelta(localAssistantId, event || {})
        },
        onDone: (event) => {
          if (event?.message) {
            replaceMessage(localAssistantId, event.message)
          }
        },
        onError: () => {}
      },
      abortController.signal
    )
    streamAbortController.value = null
    await Promise.all([loadMessages(), loadConversationDetail(), loadConversationList(), loadQuotaSnapshot()])
    await nextTick()
    scrollMessagesToBottom()
  } catch (error) {
    const currentError = error as Error & { name?: string; status?: number }
    if (currentError.name === 'AbortError') {
      return
    }
    if (currentError.status === 401) {
      removeToken()
      authStore.clearAuth()
      await router.push('/login')
      return
    }
    await loadMessages()
    showError(currentError.message || '发送失败')
  } finally {
    streamAbortController.value = null
    sending.value = false
  }
}

/**
 * 分发历史会话菜单命令。
 */
async function handleConversationCommand(command: string, conversation: AnyRecord) {
  if (command === 'rename') {
    await renameConversation(conversation)
    return
  }
  if (command === 'delete') {
    await deleteConversation(conversation)
  }
}

/**
 * 重命名指定会话，并同步更新当前页状态。
 */
async function renameConversation(conversation: AnyRecord) {
  try {
    const result = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: conversation.title,
      inputPlaceholder: '例如：深入聊聊 RAG 设计',
      inputPattern: /\S+/,
      inputErrorMessage: '会话标题不能为空'
    })
    const title = result.value.trim()
    await renameConversationApi(conversation.id, title)
    if (conversation.id === conversationId.value) {
      currentConversation.value = {
        ...currentConversation.value,
        title
      }
    }
    const target = conversations.value.find((item) => item.id === conversation.id)
    if (target) {
      target.title = title
    }
    showSuccess('会话已重命名')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    showError((error as Error)?.message || '重命名失败')
  }
}

/**
 * 删除指定会话，并在必要时切换到新的默认会话。
 */
async function deleteConversation(conversation: AnyRecord) {
  try {
    await ElMessageBox.confirm(`确认删除会话《${conversation.title}》吗？`, '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteConversationApi(conversation.id)
    showSuccess('会话已删除')
    await loadConversationList()
    if (conversation.id !== conversationId.value) {
      return
    }
    const nextConversation = conversations.value[0]
    if (nextConversation) {
      await router.replace({
        path: '/ai/chat',
        query: buildConversationQuery(nextConversation)
      })
      return
    }
    await router.replace({ path: '/ai/chat' })
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    showError((error as Error)?.message || '删除失败')
  }
}

/**
 * 打开引用片段对应的文章详情。
 */
function openCitation(citation: AnyRecord) {
  const targetArticleId = Number(citation?.articleId || 0)
  if (!targetArticleId) {
    return
  }
  const query: Record<string, string> = {}
  if (citation.sectionPath && citation.sectionPath !== '未分节') {
    query.sectionPath = citation.sectionPath
  }
  router.push({
    path: `/post/${targetArticleId}`,
    query
  })
}

/**
 * 统一格式化 token 数量展示。
 */
function formatTokenCount(value: unknown) {
  return Number(value || 0).toLocaleString('zh-CN')
}

/**
 * 把后端会话类型转换成页面展示文案。
 */
function getTypeLabel(type: string) {
  return type === 'article' ? '文章' : '全局'
}

/**
 * 把消息角色转换成页面展示文案。
 */
function getRoleLabel(role: string) {
  if (role === 'system') {
    return '系统'
  }
  if (role === 'assistant') {
    return '助手'
  }
  if (role === 'user') {
    return '用户'
  }
  return role || '消息'
}
</script>

<template>
  <div class="ai-page">
    <section class="workspace-shell">
      <aside class="sidebar-panel">
        <div class="sidebar-topbar">
          <div>
            <h1 class="sidebar-title">{{ heroTitle }}</h1>
          </div>
          <button class="primary-btn" type="button" @click="createGlobalConversation">
            新建
          </button>
        </div>

        <div class="sidebar-status">
          <span class="status-pill">{{ heroBadge }}</span>
          <span class="status-meta">{{ currentConversationTypeLabel }}</span>
          <span class="status-meta">#{{ conversationCount }}</span>
        </div>

        <div class="model-panel">
          <div class="panel-head compact">
            <span>新会话模型</span>
            <span class="panel-tip">仅对新建会话生效</span>
          </div>
          <ClientOnly>
            <ElSelect
              v-model="selectedModelId"
              class="model-select"
              popper-class="ai-model-select-dropdown"
              placeholder="请选择模型"
              :disabled="modelOptionsLoading || !chatModels.length"
              @change="persistSelectedModel"
            >
              <ElOption
                v-for="model in chatModels"
                :key="model.id"
                :label="buildModelOptionLabel(model)"
                :value="model.id"
              />
            </ElSelect>
            <template #fallback>
              <div class="model-select-fallback">
                {{ selectedModelOption ? buildModelOptionLabel(selectedModelOption) : '请选择模型' }}
              </div>
            </template>
          </ClientOnly>
          <div v-if="selectedModelOption" class="model-tip">
            当前倍率：x{{ formatQuotaMultiplier(selectedModelOption.quotaMultiplier) }}，会影响额度消耗
          </div>
          <div v-else class="empty-text">暂无可用模型，请先检查后端 provider 配置。</div>
        </div>

        <div class="quota-panel" :class="{ collapsed: !quotaExpanded }">
          <div class="panel-head compact quota-head">
            <div>
              <span>额度概览</span>
              <span class="panel-tip quota-mode">{{ quotaLoading ? '同步中...' : (quotaSnapshot.enabled ? 'Token 计费' : '已关闭限制') }}</span>
            </div>
            <button class="link-btn quota-toggle" type="button" @click="quotaExpanded = !quotaExpanded">
              {{ quotaExpanded ? '收起' : '展开' }}
            </button>
          </div>
          <template v-if="quotaSnapshot.enabled">
            <div class="quota-summary">
              <div class="quota-balance">
                <span>当前可用</span>
                <strong>{{ formatTokenCount(quotaSnapshot.availableTokens) }}</strong>
              </div>
              <div class="quota-brief">
                <span>门槛 {{ formatTokenCount(quotaSnapshot.minRequestTokens) }}</span>
                <span>消耗 {{ formatTokenCount(quotaSnapshot.usedTokens) }}</span>
                <span>签到 {{ quotaSnapshot.cumulativeSignDays || 0 }} 天</span>
              </div>
            </div>
            <div v-show="quotaExpanded" class="quota-details">
              <div class="quota-breakdown">
                <div class="quota-chip">
                  <span>签到</span>
                  <strong>{{ quotaSnapshot.cumulativeSignDays || 0 }} 天</strong>
                  <em>+{{ formatTokenCount(quotaSnapshot.signRewardTokens) }}</em>
                </div>
                <div class="quota-chip">
                  <span>发文</span>
                  <strong>{{ quotaSnapshot.articleCount || 0 }} 篇</strong>
                  <em>+{{ formatTokenCount(quotaSnapshot.articleRewardTokens) }}</em>
                </div>
                <div class="quota-chip">
                  <span>点赞</span>
                  <strong>{{ quotaSnapshot.likedArticleCount || 0 }} 次</strong>
                  <em>+{{ formatTokenCount(quotaSnapshot.likeRewardTokens) }}</em>
                </div>
                <div class="quota-chip">
                  <span>收藏</span>
                  <strong>{{ quotaSnapshot.favoriteArticleCount || 0 }} 篇</strong>
                  <em>+{{ formatTokenCount(quotaSnapshot.favoriteRewardTokens) }}</em>
                </div>
                <div v-if="quotaSnapshot.manualBonusTokens > 0" class="quota-chip">
                  <span>手动</span>
                  <strong>后台赠送</strong>
                  <em>+{{ formatTokenCount(quotaSnapshot.manualBonusTokens) }}</em>
                </div>
              </div>
              <div class="quota-rule-tip">
                规则：签到 +{{ formatTokenCount(quotaSnapshot.signRewardUnitTokens) }} / 发文 +{{ formatTokenCount(quotaSnapshot.articleRewardUnitTokens) }} / 点赞 +{{ formatTokenCount(quotaSnapshot.likeRewardUnitTokens) }} / 收藏 +{{ formatTokenCount(quotaSnapshot.favoriteRewardUnitTokens) }}
                <template v-if="quotaSnapshot.likeDailyLimit > 0">
                  / 今日已用 {{ quotaSnapshot.todayLikeCount || 0 }} / {{ quotaSnapshot.likeDailyLimit }} / 剩余 {{ quotaSnapshot.todayLikeRemainingCount || 0 }} / 单篇 {{ quotaSnapshot.likeDailyPerArticleLimit || 0 }}
                </template>
                <template v-else>
                  / 点赞奖励不限次
                </template>
              </div>
            </div>
          </template>
          <div v-else class="empty-text">管理员已关闭 AI 额度限制，当前按无限制模式运行。</div>
        </div>

        <div class="history-panel">
          <div class="panel-head">
            <span>历史会话</span>
            <span class="panel-tip">支持重命名 / 删除</span>
          </div>
          <div v-loading="conversationListLoading" class="history-list">
            <div
              v-for="conversation in conversations"
              :key="conversation.id"
              class="history-item"
              :class="{ active: conversation.id === conversationId }"
            >
              <button type="button" class="history-main" @click="openConversation(conversation)">
                <span class="history-row">
                  <span class="history-name">{{ conversation.title }}</span>
                  <span class="history-type">{{ getTypeLabel(conversation.type) }}</span>
                </span>
                <span class="history-summary">{{ conversation.summary || '暂无摘要' }}</span>
              </button>
              <ElDropdown
                trigger="click"
                placement="bottom-end"
                @command="(command: string | number | object) => handleConversationCommand(String(command), conversation)"
                @click.stop
              >
                <button type="button" class="history-menu-btn" @click.stop>
                  <i class="fas fa-ellipsis-h"></i>
                </button>
                <template #dropdown>
                  <ElDropdownMenu>
                    <ElDropdownItem command="rename">重命名</ElDropdownItem>
                    <ElDropdownItem command="delete" divided>删除</ElDropdownItem>
                  </ElDropdownMenu>
                </template>
              </ElDropdown>
            </div>
            <div v-if="!conversationListLoading && !conversations.length" class="empty-text">
              还没有会话，先创建一个吧。
            </div>
          </div>
        </div>
      </aside>

      <main class="main-panel">
        <div class="chat-topbar">
          <div class="chat-heading">
            <div class="chat-title">{{ currentConversation.title || chatTitle }}</div>
            <div class="chat-subtitle">{{ currentConversation.summary || chatSubtitle }}</div>
          </div>
          <div class="chat-meta">
            <NuxtLink to="/ai" class="chat-back-btn">
              <i class="fas fa-arrow-left"></i>
              <span>返回 AI</span>
            </NuxtLink>
            <button class="theme-toggle-btn" type="button" :title="isDarkMode ? '切到亮色' : '切到暗色'" @click="toggleTheme">
              <i :class="['fas', isDarkMode ? 'fa-sun' : 'fa-moon']"></i>
            </button>
            <span class="meta-chip">模型 {{ currentConversation.modelDisplayName || currentConversation.modelName || '加载中' }}</span>
            <span class="meta-chip warm">当前会话</span>
          </div>
        </div>

        <div ref="messageListRef" v-loading="messageLoading || bootstrapping" class="message-list">
          <div
            v-for="message in messages"
            :key="message.id"
            class="message-item"
            :class="[message.role, { pending: message.pending }]"
          >
            <div class="message-role">{{ getRoleLabel(message.role) }}</div>
            <details v-if="message.reasoningContent" class="message-reasoning">
              <summary>思考过程</summary>
              <div class="reasoning-content markdown-preview reasoning-preview">
                <MdPreview
                  :model-value="message.reasoningContent"
                  :theme="isDarkMode ? 'dark' : 'light'"
                  preview-theme="github"
                  code-theme="github"
                />
              </div>
            </details>
            <div v-if="message.content" class="message-content markdown-preview">
              <MdPreview
                :model-value="message.content"
                :theme="isDarkMode ? 'dark' : 'light'"
                preview-theme="github"
                code-theme="github"
              />
            </div>
            <div v-if="message.activities && message.activities.length" class="message-activities">
              <div
                v-for="activity in message.activities"
                :key="`${message.id}-activity-${activity.key}`"
                class="message-activity"
                :class="[activity.kind, activity.status]"
              >
                <i :class="['fas', activity.icon]"></i>
                <span class="message-activity-label">{{ activity.label }}</span>
              </div>
            </div>
            <div v-if="message.citations && message.citations.length" class="message-citations">
              <div class="citation-title">引用片段</div>
              <div
                v-for="(citation, citationIndex) in message.citations"
                :key="`${message.id}-citation-${citationIndex}`"
                class="citation-card"
                :class="{ clickable: !!citation.articleId }"
                @click="openCitation(citation)"
              >
                <div class="citation-meta">
                  <span class="citation-article">{{ citation.articleTitle || '未命名文章' }}</span>
                  <span v-if="citation.sectionPath" class="citation-section">{{ citation.sectionPath }}</span>
                </div>
                <div v-if="citation.content" class="citation-content markdown-preview">
                  <MdPreview
                    :model-value="citation.content"
                    :theme="isDarkMode ? 'dark' : 'light'"
                    preview-theme="github"
                    code-theme="github"
                  />
                </div>
                <div v-if="citation.articleId" class="citation-action">查看原文</div>
              </div>
            </div>
            <div v-if="message.pending" class="message-pending">生成中...</div>
          </div>
          <div v-if="!messageLoading && !messages.length" class="empty-text chat-empty">
            暂无消息，发一条试试。
          </div>
        </div>

        <div class="composer-shell">
          <textarea
            v-model="messageDraft"
            class="composer-input"
            :disabled="sending || bootstrapping"
            placeholder="输入你想问的问题，按 Ctrl + Enter 快速发送"
            @keydown.ctrl.enter.prevent="handleSend"
          />
          <div class="composer-footer">
            <span>{{ composerHint }}</span>
            <button class="composer-btn" type="button" :disabled="!canSend" @click="handleSend">
              {{ sending ? '发送中...' : '发送' }}
            </button>
          </div>
        </div>
      </main>
    </section>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.ai-page {
  height: calc(100vh - 176px);
  padding: 10px 24px 16px;
  box-sizing: border-box;
}

.workspace-shell {
  width: 100%;
  max-width: 1360px;
  height: 100%;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 288px minmax(0, 1fr);
  gap: 12px;
  overflow: hidden;
}

.sidebar-panel,
.main-panel {
  height: 100%;
  min-height: 0;
  background: rgba(var(--surface-rgb), 0.94);
  border: 1px solid rgba(var(--border-color-rgb), 0.14);
  border-radius: 18px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.sidebar-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-topbar,
.panel-head,
.history-row,
.chat-topbar,
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sidebar-title,
.chat-title {
  color: var(--text-primary);
  font-weight: 700;
}

.sidebar-title {
  margin-top: 4px;
  font-size: 22px;
  line-height: 1.2;
}

.primary-btn,
.link-btn,
.composer-btn,
.history-menu-btn {
  border: none;
  cursor: pointer;
}

.primary-btn {
  padding: 10px 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #14b8a6, #0ea5e9);
  color: #fff;
  font-weight: 600;
}

.sidebar-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-pill,
.status-meta,
.history-type,
.meta-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill,
.history-type {
  padding: 5px 10px;
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
}

.status-meta,
.meta-chip {
  padding: 5px 10px;
  background: rgba(var(--border-color-rgb), 0.1);
  color: var(--text-secondary);
}

.meta-chip.warm {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.history-panel,
.quota-panel,
.model-panel {
  min-height: 0;
  border-radius: 16px;
  background: rgba(var(--border-color-rgb), 0.05);
  border: 1px solid rgba(var(--border-color-rgb), 0.1);
  padding: 12px;
}

.model-select {
  width: 100%;
}

.model-select-fallback {
  width: 100%;
  min-height: 40px;
  padding: 10px 14px;
  border-radius: 16px;
  background: rgba(var(--border-color-rgb), 0.06);
  box-shadow: 0 0 0 1px rgba(var(--border-color-rgb), 0.16) inset;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 20px;
}

.model-select :deep(.el-input__wrapper),
.model-select :deep(.el-select__wrapper),
.model-select :deep(.el-input__inner) {
  background: rgba(var(--border-color-rgb), 0.06) !important;
  color: var(--text-primary) !important;
}

.model-select :deep(.el-input__wrapper),
.model-select :deep(.el-select__wrapper) {
  border-radius: 16px !important;
  box-shadow: 0 0 0 1px rgba(var(--border-color-rgb), 0.16) inset !important;
}

.model-select :deep(.el-select__selection),
.model-select :deep(.el-select__selected-item),
.model-select :deep(.el-select__placeholder) {
  border-radius: 16px !important;
}

.model-select :deep(.el-select__placeholder),
.model-select :deep(.el-select__selected-item),
.model-select :deep(.el-select__caret),
.model-select :deep(.el-input__icon) {
  color: var(--text-primary) !important;
}

.model-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.panel-head {
  align-items: flex-start;
}

.panel-head.compact {
  margin-bottom: 10px;
}

.quota-head > div {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quota-mode {
  font-size: 12px;
}

.quota-toggle {
  padding: 0;
  font-size: 12px;
  font-weight: 700;
}

.panel-tip,
.history-summary,
.chat-subtitle,
.message-content,
.composer-footer,
.empty-text {
  color: var(--text-secondary);
}

.panel-tip,
.chat-subtitle,
.composer-footer,
.history-summary,
.empty-text {
  font-size: 13px;
}

.history-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.history-list {
  flex: 1;
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.history-item {
  display: flex;
  align-items: stretch;
  gap: 8px;
  padding: 9px;
  border-radius: 14px;
  background: rgba(var(--surface-rgb), 0.42);
  border: 1px solid rgba(var(--border-color-rgb), 0.1);
  transition: all 0.2s ease;
}

.history-item.active,
.history-item:hover {
  border-color: rgba(14, 165, 233, 0.32);
  background: rgba(14, 165, 233, 0.08);
}

.history-main {
  display: block;
  flex: 1;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.history-row {
  align-items: flex-start;
}

.history-name {
  color: var(--text-primary);
  font-weight: 700;
  line-height: 1.4;
}

.history-summary {
  margin-top: 6px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  overflow: hidden;
}

.history-menu-btn {
  width: 34px;
  min-width: 34px;
  border-radius: 10px;
  background: rgba(var(--border-color-rgb), 0.12);
  color: var(--text-secondary);
}

.link-btn {
  background: transparent;
  color: #0ea5e9;
}

.quota-summary {
  display: grid;
  gap: 8px;
}

.quota-balance {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-secondary);
}

.quota-balance strong {
  color: var(--text-primary);
  font-size: 24px;
  line-height: 1;
}

.quota-brief {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

.quota-details {
  margin-top: 10px;
}

.quota-panel.collapsed {
  padding-bottom: 10px;
}

.quota-breakdown {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.quota-chip {
  min-width: 0;
  padding: 8px 9px;
  border-radius: 12px;
  background: rgba(var(--surface-rgb), 0.36);
  border: 1px solid rgba(var(--border-color-rgb), 0.08);
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 6px;
}

.quota-chip span,
.quota-chip em,
.quota-rule-tip {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
}

.quota-chip strong {
  min-width: 0;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}

.quota-rule-tip {
  margin-top: 12px;
  line-height: 1.6;
}

.main-panel {
  min-width: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-topbar {
  align-items: flex-start;
  padding: 0 0 8px;
  border-bottom: 1px solid rgba(var(--border-color-rgb), 0.12);
}

.chat-heading {
  min-width: 0;
}

.chat-title {
  font-size: 20px;
  line-height: 1.2;
}

.chat-subtitle {
  margin-top: 6px;
  line-height: 1.6;
}

.chat-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}

.chat-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(var(--border-color-rgb), 0.12);
  border-radius: 999px;
  background: rgba(var(--border-color-rgb), 0.1);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  transition: all 0.2s ease;
}

.chat-back-btn:hover {
  transform: translateY(-1px);
  background: rgba(var(--border-color-rgb), 0.14);
  color: var(--text-primary);
}

.theme-toggle-btn {
  width: 30px;
  height: 30px;
  border: 1px solid rgba(var(--border-color-rgb), 0.12);
  border-radius: 999px;
  background: rgba(var(--border-color-rgb), 0.1);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.theme-toggle-btn:hover {
  transform: translateY(-1px);
  background: rgba(var(--border-color-rgb), 0.14);
  color: var(--text-primary);
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.message-item {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(var(--border-color-rgb), 0.12);
}

.message-item.system {
  background: rgba(14, 165, 233, 0.08);
}

.message-item.assistant {
  background: rgba(20, 184, 166, 0.08);
}

.message-role {
  margin-bottom: 8px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
}

.message-content {
  line-height: 1.8;
}

.message-content + .message-activities,
.message-content + .message-citations {
  margin-top: 16px;
}

.markdown-preview {
  min-width: 0;
}

.markdown-preview :deep(.md-editor) {
  --md-color: var(--text-secondary);
  --md-hover-color: var(--text-primary);
  --md-bk-color: transparent;
  --md-bk-color-outstand: rgba(var(--border-color-rgb), 0.08);
  --md-bk-hover-color: rgba(var(--border-color-rgb), 0.08);
  --md-border-color: transparent;
  --md-border-hover-color: transparent;
  background: transparent;
  border: none;
  height: auto;
}

.markdown-preview :deep(.md-editor-content),
.markdown-preview :deep(.md-editor-preview-wrapper),
.markdown-preview :deep(.md-editor-preview) {
  background: transparent;
}

.markdown-preview :deep(.md-editor-preview) {
  --md-theme-color: var(--text-secondary);
  --md-theme-color-reverse: var(--card-bg);
  --md-theme-border-color: rgba(var(--border-color-rgb), 0.16);
  --md-theme-border-color-reverse: rgba(var(--border-color-rgb), 0.16);
  --md-theme-border-color-inset: rgba(var(--border-color-rgb), 0.16);
  --md-theme-bg-color: transparent;
  --md-theme-bg-color-inset: rgba(var(--border-color-rgb), 0.08);
  --md-theme-code-copy-tips-color: var(--text-primary);
  --md-theme-code-copy-tips-bg-color: var(--card-bg);
  color: var(--text-secondary);
  font-size: 14px;
}

.markdown-preview :deep(.md-editor-preview > *:first-child) {
  margin-top: 0;
}

.markdown-preview :deep(.md-editor-preview > *:last-child) {
  margin-bottom: 0;
}

.markdown-preview :deep(pre) {
  overflow-x: auto;
}

.markdown-preview :deep(table) {
  background: rgba(var(--surface-rgb), 0.24);
}

.markdown-preview :deep(thead th) {
  background: rgba(var(--border-color-rgb), 0.1);
  color: var(--text-primary);
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  border-color: rgba(var(--border-color-rgb), 0.16);
}

.markdown-preview :deep(tbody tr:nth-child(even)) {
  background: rgba(var(--border-color-rgb), 0.05);
}

.message-reasoning {
  margin-bottom: 10px;
  border: 1px solid rgba(var(--border-color-rgb), 0.12);
  border-radius: 12px;
  background: rgba(var(--border-color-rgb), 0.05);
  overflow: hidden;
}

.message-reasoning summary {
  padding: 10px 12px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
}

.reasoning-content {
  padding: 0 12px 12px;
}

.reasoning-preview :deep(.md-editor-preview) {
  font-size: 13px;
}

.message-citations {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citation-title {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.citation-card {
  width: 100%;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(var(--border-color-rgb), 0.12);
  background: rgba(var(--border-color-rgb), 0.05);
  text-align: left;
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.citation-card.clickable {
  cursor: pointer;
}

.citation-card.clickable:hover {
  border-color: rgba(14, 165, 233, 0.28);
  background: rgba(14, 165, 233, 0.08);
  transform: translateY(-1px);
}

.citation-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.citation-article {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
}

.citation-section {
  font-size: 11px;
  color: var(--text-secondary);
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(var(--border-color-rgb), 0.1);
}

.citation-action {
  margin-top: 10px;
  color: #0ea5e9;
  font-size: 12px;
  font-weight: 700;
}

.message-activities {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-activity {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(var(--border-color-rgb), 0.14);
  background: rgba(var(--border-color-rgb), 0.06);
}

.message-activity i {
  color: #14b8a6;
}

.message-activity.completed {
  background: rgba(20, 184, 166, 0.08);
  border-color: rgba(20, 184, 166, 0.18);
}

.message-activity.running,
.message-activity.requested {
  background: rgba(14, 165, 233, 0.08);
  border-color: rgba(14, 165, 233, 0.18);
}

.message-activity.failed {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.16);
}

.message-activity.failed i {
  color: #ef4444;
}

.message-activity.mcp i {
  color: #f59e0b;
}

.message-activity-label {
  color: var(--text-primary);
  font-weight: 700;
}

.message-item.pending {
  border-style: dashed;
}

.message-pending {
  margin-top: 8px;
  color: #0ea5e9;
  font-size: 12px;
  font-weight: 700;
}

.chat-empty {
  margin: auto 0;
}

.composer-shell {
  border-top: 1px solid rgba(var(--border-color-rgb), 0.12);
  padding-top: 12px;
}

.composer-input {
  width: 100%;
  min-height: 84px;
  resize: none;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(var(--border-color-rgb), 0.14);
  background: rgba(var(--border-color-rgb), 0.06);
  color: var(--text-primary);
  outline: none;
}

.composer-btn {
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(var(--border-color-rgb), 0.14);
  color: var(--text-primary);
}

.composer-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

:global(:root[data-theme='dark'] .status-pill),
:global(:root[data-theme='dark'] .history-type) {
  background: rgba(94, 234, 212, 0.14);
  color: #99f6e4;
}

:global(:root[data-theme='dark'] .meta-chip.warm) {
  background: rgba(251, 191, 36, 0.18);
  color: #fde68a;
}

:global(:root[data-theme='dark'] .model-select .el-input__wrapper),
:global(:root[data-theme='dark'] .model-select .el-select__wrapper),
:global(:root[data-theme='dark'] .model-select .el-input__inner) {
  background: rgba(15, 23, 42, 0.92) !important;
  color: #e2e8f0 !important;
}

:global(:root[data-theme='dark'] .model-select-fallback) {
  background: rgba(15, 23, 42, 0.92) !important;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.22) inset !important;
  color: #e2e8f0 !important;
}

:global(:root[data-theme='dark'] .model-select .el-input__wrapper),
:global(:root[data-theme='dark'] .model-select .el-select__wrapper) {
  border-radius: 16px !important;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.22) inset !important;
}

:global(:root[data-theme='dark'] .model-select .el-select__placeholder),
:global(:root[data-theme='dark'] .model-select .el-select__selected-item),
:global(:root[data-theme='dark'] .model-select .el-select__caret),
:global(:root[data-theme='dark'] .model-select .el-input__icon) {
  color: #e2e8f0 !important;
}

:global(.ai-model-select-dropdown.el-popper) {
  border-radius: 16px !important;
  overflow: hidden !important;
}

:global(.ai-model-select-dropdown .el-select-dropdown__wrap),
:global(.ai-model-select-dropdown .el-scrollbar),
:global(.ai-model-select-dropdown .el-scrollbar__view),
:global(.ai-model-select-dropdown .el-select-dropdown__list) {
  background: transparent !important;
}

:global(.ai-model-select-dropdown .el-select-dropdown__list) {
  padding: 6px !important;
}

:global(.ai-model-select-dropdown .el-select-dropdown__item) {
  margin: 0 !important;
  border-radius: 12px !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown) {
  background: #0f172a !important;
  border-color: rgba(148, 163, 184, 0.22) !important;
  box-shadow: 0 18px 44px rgba(2, 6, 23, 0.36) !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-popper__arrow::before) {
  background: #0f172a !important;
  border-color: rgba(148, 163, 184, 0.22) !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__wrap),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-scrollbar),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-scrollbar__view),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__list) {
  background: #0f172a !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item) {
  background: transparent !important;
  color: #e2e8f0 !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item.hover),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item:hover) {
  background: rgba(56, 189, 248, 0.14) !important;
  color: #f8fafc !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item.selected) {
  background: rgba(56, 189, 248, 0.12) !important;
  color: #67e8f9 !important;
}

@media (max-width: 1100px) {
  .ai-page {
    height: auto;
    min-height: calc(100vh - 160px);
    padding: 10px 16px 12px;
    overflow: visible;
  }

  .workspace-shell {
    height: auto;
    grid-template-columns: 1fr;
    grid-template-rows: auto auto;
    gap: 12px;
    overflow: visible;
  }

  .sidebar-panel,
  .main-panel {
    height: auto;
  }

  .history-panel {
    flex: none;
  }

  .history-list {
    max-height: 240px;
  }

  .message-list {
    min-height: 260px;
    max-height: 42vh;
  }
}

@media (max-width: 768px) {
  .ai-page {
    min-height: calc(100vh - 150px);
    padding: 8px 10px 8px;
  }

  .workspace-shell {
    grid-template-rows: auto auto;
    gap: 8px;
  }

  .sidebar-panel,
  .main-panel {
    padding: 12px;
    border-radius: 16px;
  }

  .sidebar-topbar,
  .panel-head,
  .history-row,
  .chat-topbar,
  .composer-footer,
  .quota-balance {
    flex-direction: column;
    align-items: flex-start;
  }

  .chat-meta {
    justify-content: flex-start;
  }

  .history-list {
    max-height: 200px;
  }

  .message-list {
    min-height: 220px;
    max-height: 46vh;
  }
}
</style>
