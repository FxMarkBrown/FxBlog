<script setup lang="ts">
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { ElMessage } from 'element-plus'
import type { Edge, Node } from '@vue-flow/core'
import { VueFlow } from '@vue-flow/core'
import DocumentCanvasNode from '@/components/ai-document/DocumentCanvasNode.vue'
import { askDocumentNodeApi, getDocumentTaskDetailApi, getDocumentTaskResultApi } from '@/api/ai-document'
import type { DocumentNodeAnswer, DocumentParseResult, DocumentTaskDetail, DocumentTreeNode } from '@/types/ai-document'
import { unwrapResponseData } from '@/utils/response'
import { getThemeMode, initTheme, setThemeMode } from '@/utils/theme'

type ChatState = {
  question: string
  sending: boolean
  answer?: string
  error?: string
  citations?: DocumentNodeAnswer['citations']
}

type CanvasNodeData = {
  kind: 'outline' | 'source-preview' | 'chat-thread'
  nodeId: string
  themeMode?: 'light' | 'dark'
  title: string
  subtitle?: string
  body?: string
  markdown?: string
  badge?: string
  expandable?: boolean
  expanded?: boolean
  pageLabel?: string
  anchorBox?: number[]
  sourceUrl?: string
  question?: string
  answer?: string
  citations?: DocumentNodeAnswer['citations']
  sending?: boolean
  error?: string
  onToggleExpand?: (nodeId: string) => void
  onTogglePreview?: (nodeId: string) => void
  onToggleChat?: (nodeId: string) => void
  onQuestionChange?: (nodeId: string, value: string) => void
  onSubmitQuestion?: (nodeId: string) => void
}

type OutlineDescriptor = {
  id: string
  parentId: string | null
  node: DocumentTreeNode
}

type ViewportAction = 'none' | 'fit' | 'center'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const taskId = computed(() => Number(route.params.taskId || 0))
const loading = ref(false)
const taskDetail = ref<DocumentTaskDetail | null>(null)
const parseResult = ref<DocumentParseResult | null>(null)
const selectedOutlineNodeId = ref('')
const expandedNodeIds = ref<Set<string>>(new Set())
const previewOpenIds = ref<Set<string>>(new Set())
const chatOpenIds = ref<Set<string>>(new Set())
const chatStateMap = ref<Record<string, ChatState>>({})
const nodePositionOverrides = ref<Record<string, { x: number; y: number }>>({})
const flowRef = ref<InstanceType<typeof VueFlow> | null>(null)
const isDarkMode = ref(false)
const isMobileViewport = ref(false)
const elkEngine = shallowRef<any | null>(null)
let layoutRequestId = 0
let pendingViewportAction: ViewportAction = 'none'

const visibleNodes = ref<Node<CanvasNodeData>[]>([])
const visibleEdges = ref<Edge[]>([])

const nodeTypes = markRaw({
  documentNode: markRaw(DocumentCanvasNode)
})

watch(
  [parseResult, expandedNodeIds, previewOpenIds, chatOpenIds, chatStateMap, isDarkMode, isMobileViewport],
  () => {
    void rebuildCanvas()
  },
  { deep: true }
)

watch(isMobileViewport, () => {
  requestViewportAction('fit')
})

async function loadTask(silent = false) {
  if (!taskId.value) {
    return
  }

  if (!silent) {
    loading.value = true
  }
  try {
    const previousRootId = parseResult.value?.root?.id
    const [detailResponse, resultResponse] = await Promise.all([
      getDocumentTaskDetailApi(taskId.value),
      getDocumentTaskResultApi(taskId.value)
    ])
    taskDetail.value = unwrapResponseData<DocumentTaskDetail | null>(detailResponse)
    parseResult.value = unwrapResponseData<DocumentParseResult | null>(resultResponse)

    const rootNodeId = parseResult.value?.root?.id
    if (rootNodeId && previousRootId && previousRootId !== rootNodeId) {
      expandedNodeIds.value = new Set()
    }
    if (rootNodeId && !selectedOutlineNodeId.value) {
      selectedOutlineNodeId.value = rootNodeId
    }

    if (!silent) {
      requestViewportAction('fit')
      await rebuildCanvas()
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '文档任务加载失败')
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

function isStructureNode(node?: DocumentTreeNode | null) {
  if (!node) {
    return false
  }

  const normalizedType = String(node.type || '').toLowerCase()
  return normalizedType === 'document' || normalizedType === 'section' || normalizedType === 'subsection'
}

function getVisibleTreeChildren(node?: DocumentTreeNode | null) {
  const children = (node?.children || []).filter(Boolean)
  if (!children.length) {
    return []
  }

  const structuralChildren = children.filter((child) => isStructureNode(child))
  return structuralChildren.length ? structuralChildren : children
}

function getLayoutMetrics() {
  if (isMobileViewport.value) {
    return {
      baseX: 36,
      baseY: 72,
      outlineWidth: 280,
      outlineHeight: 116,
      nodeGap: 22,
      layerGap: 116,
      attachmentOffsetX: 0,
      previewOffsetY: 228,
      chatOffsetY: 228,
      attachmentStackGap: 28,
      mobileAttachmentHeight: 392
    }
  }

  return {
    baseX: 120,
    baseY: 100,
    outlineWidth: 308,
    outlineHeight: 118,
    nodeGap: 42,
    layerGap: 188,
    attachmentOffsetX: 372,
    previewOffsetY: 92,
    chatOffsetY: -128,
    attachmentStackGap: 0,
    mobileAttachmentHeight: 0
  }
}

function collectVisibleOutlineDescriptors(root?: DocumentTreeNode | null) {
  if (!root) {
    return []
  }

  const descriptors: OutlineDescriptor[] = []

  function visit(node: DocumentTreeNode, parentId: string | null) {
    descriptors.push({
      id: node.id,
      parentId,
      node
    })

    if (!expandedNodeIds.value.has(node.id)) {
      return
    }

    for (const child of getVisibleTreeChildren(node)) {
      visit(child, node.id)
    }
  }

  visit(root, null)
  return descriptors
}

async function computeOutlineLayout(descriptors: OutlineDescriptor[]) {
  const metrics = getLayoutMetrics()

  if (!descriptors.length) {
    return new Map<string, { x: number; y: number }>()
  }

  if (!elkEngine.value) {
    return new Map(
      descriptors.map((descriptor, index) => [
        descriptor.id,
        {
          x: metrics.baseX + index * 28,
          y: metrics.baseY + index * 18
        }
      ])
    )
  }

  const graph = {
    id: 'document-outline',
    layoutOptions: {
      'elk.algorithm': 'mrtree',
      'elk.direction': isMobileViewport.value ? 'RIGHT' : 'DOWN',
      'elk.spacing.nodeNode': String(metrics.nodeGap),
      'elk.layered.spacing.nodeNodeBetweenLayers': String(metrics.layerGap),
      'elk.edgeRouting': 'POLYLINE'
    },
    children: descriptors.map((descriptor) => ({
      id: descriptor.id,
      width: metrics.outlineWidth,
      height: metrics.outlineHeight
    })),
    edges: descriptors
      .filter((descriptor) => descriptor.parentId)
      .map((descriptor) => ({
        id: `${descriptor.parentId}-->${descriptor.id}`,
        sources: [descriptor.parentId as string],
        targets: [descriptor.id]
      }))
  }

  const layout = await elkEngine.value.layout(graph)
  const positions = new Map<string, { x: number; y: number }>()
  const layoutChildren = layout?.children || []
  if (!layoutChildren.length) {
    return positions
  }

  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY

  for (const child of layoutChildren) {
    const childX = Number(child.x || 0)
    const childY = Number(child.y || 0)
    const childWidth = Number(child.width || metrics.outlineWidth)
    minX = Math.min(minX, childX)
    minY = Math.min(minY, childY)
    maxX = Math.max(maxX, childX + childWidth)
  }

  const graphCenterX = minX + Math.max(metrics.outlineWidth, maxX - minX) / 2

  for (const child of layoutChildren) {
    positions.set(String(child.id), {
      x: Number(child.x || 0) - graphCenterX + metrics.baseX,
      y: Number(child.y || 0) - minY + metrics.baseY
    })
  }

  return positions
}

async function rebuildCanvas() {
  const root = parseResult.value?.root
  if (!root) {
    visibleNodes.value = []
    visibleEdges.value = []
    return
  }

  const requestId = ++layoutRequestId
  const descriptors = collectVisibleOutlineDescriptors(root)
  const positions = await computeOutlineLayout(descriptors)
  if (requestId !== layoutRequestId) {
    return
  }

  const result = {
    nodes: [] as Node<CanvasNodeData>[],
    edges: [] as Edge[],
    centers: new Map<string, { x: number; y: number }>()
  }

  for (const descriptor of descriptors) {
    const node = descriptor.node
    const autoPosition = positions.get(descriptor.id) || { x: 120, y: 120 }
    const position = nodePositionOverrides.value[descriptor.id] || autoPosition

    result.centers.set(descriptor.id, position)
    result.nodes.push({
      id: descriptor.id,
      type: 'documentNode',
      position,
      data: {
        kind: 'outline',
        nodeId: descriptor.id,
        themeMode: isDarkMode.value ? 'dark' : 'light',
        title: String(node.title || '未命名节点'),
        subtitle: buildSubtitle(node),
        body: String(node.summary || ''),
        badge: buildBadge(node),
        expandable: Boolean(getVisibleTreeChildren(node).length),
        expanded: expandedNodeIds.value.has(descriptor.id),
        onToggleExpand: handleToggleExpand,
        onTogglePreview: handleTogglePreview,
        onToggleChat: handleToggleChat
      }
    })

    if (descriptor.parentId) {
      result.edges.push({
        id: `${descriptor.parentId}-->${descriptor.id}`,
        source: descriptor.parentId,
        target: descriptor.id,
        type: 'smoothstep',
        animated: false,
        style: {
          stroke: '#7c83fd',
          strokeWidth: 1.6
        }
      })
    }
  }

  result.nodes = result.nodes.map((node) => {
    if (node.data?.kind === 'outline') {
      return {
        ...node,
        selected: node.id === selectedOutlineNodeId.value
      }
    }
    return node
  })

  appendAttachmentNodes(root, result)

  visibleNodes.value = result.nodes
  visibleEdges.value = result.edges

  if (pendingViewportAction !== 'none') {
    const action = pendingViewportAction
    pendingViewportAction = 'none'
    await nextTick()
    scheduleViewportAction(action)
  }
}

function appendAttachmentNodes(
  root: DocumentTreeNode,
  result: { nodes: Node<CanvasNodeData>[]; edges: Edge[]; centers: Map<string, { x: number; y: number }> }
) {
  const metrics = getLayoutMetrics()
  const outlineNodes = flattenTree(root)
  for (const node of outlineNodes) {
    const anchor = result.centers.get(node.id)
    if (!anchor) {
      continue
    }

    let attachmentIndex = 0

    if (previewOpenIds.value.has(node.id)) {
      const previewId = `${node.id}__preview`
      const sourceAnchor = node.sourceAnchors?.[0]
      const previewPosition = isMobileViewport.value
        ? {
            x: anchor.x,
            y: anchor.y + metrics.previewOffsetY + attachmentIndex * (metrics.mobileAttachmentHeight + metrics.attachmentStackGap)
          }
        : {
            x: anchor.x + metrics.attachmentOffsetX,
            y: anchor.y + metrics.previewOffsetY
          }
      result.nodes.push({
        id: previewId,
        type: 'documentNode',
        position: nodePositionOverrides.value[previewId] || previewPosition,
        draggable: true,
        selectable: true,
        data: {
          kind: 'source-preview',
          nodeId: node.id,
          themeMode: isDarkMode.value ? 'dark' : 'light',
          title: `${node.title || '节点'} · 原文预览`,
          subtitle: sourceAnchor?.textSnippet || '已定位到当前节点对应的原文片段。',
          markdown: String(node.markdown || node.summary || '暂无原文预览内容'),
          pageLabel: sourceAnchor?.page ? `第 ${sourceAnchor.page} 页` : '未提供页码',
          anchorBox: normalizeAnchorBox(sourceAnchor?.bbox),
          sourceUrl: taskDetail.value?.sourceUrl || ''
        }
      })
      attachmentIndex += 1
      result.edges.push({
        id: `${node.id}-->${previewId}`,
        source: node.id,
        target: previewId,
        type: 'smoothstep',
        animated: true,
        style: {
          stroke: '#10b981',
          strokeWidth: 1.5,
          strokeDasharray: '6 4'
        }
      })
    }

    if (chatOpenIds.value.has(node.id)) {
      const chatId = `${node.id}__chat`
      const chatState = chatStateMap.value[node.id] || {
        question: '',
        sending: false,
        answer: '',
        error: '',
        citations: []
      }
      const chatPosition = isMobileViewport.value
        ? {
            x: anchor.x,
            y: anchor.y + metrics.chatOffsetY + attachmentIndex * (metrics.mobileAttachmentHeight + metrics.attachmentStackGap)
          }
        : {
            x: anchor.x + metrics.attachmentOffsetX,
            y: anchor.y + metrics.chatOffsetY
          }
      result.nodes.push({
        id: chatId,
        type: 'documentNode',
        position: nodePositionOverrides.value[chatId] || chatPosition,
        draggable: true,
        selectable: true,
        data: {
          kind: 'chat-thread',
          nodeId: node.id,
          themeMode: isDarkMode.value ? 'dark' : 'light',
          title: `${node.title || '节点'} · 节点对话`,
          subtitle: '当前为节点上下文对话挂件，后续会接入运行时 RAG 与流式回答。',
          markdown: buildChatHint(node),
          question: chatState.question,
          answer: chatState.answer,
          citations: chatState.citations,
          sending: chatState.sending,
          error: chatState.error,
          onQuestionChange: handleQuestionChange,
          onSubmitQuestion: handleSubmitQuestion
        }
      })
      result.edges.push({
        id: `${node.id}-->${chatId}`,
        source: node.id,
        target: chatId,
        type: 'smoothstep',
        animated: true,
        style: {
          stroke: '#0ea5e9',
          strokeWidth: 1.5,
          strokeDasharray: '6 4'
        }
      })
    }
  }
}

function flattenTree(root?: DocumentTreeNode | null): DocumentTreeNode[] {
  if (!root) {
    return []
  }

  const items: DocumentTreeNode[] = [root]
  for (const child of root.children || []) {
    items.push(...flattenTree(child))
  }
  return items
}

function buildSubtitle(node: DocumentTreeNode) {
  if (node.type === 'document') {
    return '根节点 · 点击展开目录'
  }

  return `L${node.level || 1} · ${String(node.type || 'section')}`
}

function buildBadge(node: DocumentTreeNode) {
  if (node.type === 'document') {
    return '文档'
  }

  const visibleChildren = getVisibleTreeChildren(node)
  if (visibleChildren.length) {
    return `${visibleChildren.length}项`
  }

  return '节点'
}

function buildChatHint(node: DocumentTreeNode) {
  return [
    `当前对话默认绑定节点《${node.title || '未命名节点'}》。`,
    '后续这里会显示：',
    '1. 当前节点摘要',
    '2. 当前节点子树上下文',
    '3. 引用与回跳结果'
  ].join('\n')
}

function normalizeAnchorBox(rawBbox?: number[]) {
  if (!Array.isArray(rawBbox) || rawBbox.length < 4) {
    return undefined
  }

  const normalized = rawBbox.map((value) => {
    const nextValue = Number(value || 0)
    return nextValue > 1 ? nextValue / 1000 : nextValue
  })

  return [
    Math.max(0, Math.min(1, normalized[0] || 0)),
    Math.max(0, Math.min(1, normalized[1] || 0)),
    Math.max(0, Math.min(1, normalized[2] || 0)),
    Math.max(0, Math.min(1, normalized[3] || 0))
  ]
}

function handleToggleExpand(nodeId: string) {
  const wasExpanded = expandedNodeIds.value.has(nodeId)
  const next = new Set(expandedNodeIds.value)
  if (wasExpanded) {
    next.delete(nodeId)
  } else {
    next.add(nodeId)
  }
  expandedNodeIds.value = next
  selectedOutlineNodeId.value = nodeId
  const isRootNode = nodeId === parseResult.value?.root?.id
  if (isMobileViewport.value || isRootNode) {
    requestViewportAction('fit')
  } else {
    requestViewportAction('center')
  }
}

function toggleSetValue(target: { value: Set<string> }, nodeId: string) {
  const next = new Set(target.value)
  if (next.has(nodeId)) {
    next.delete(nodeId)
  } else {
    next.add(nodeId)
  }
  target.value = next
  selectedOutlineNodeId.value = nodeId
  if (isMobileViewport.value) {
    requestViewportAction('fit')
  } else {
    requestViewportAction('center')
  }
}

function handleTogglePreview(nodeId: string) {
  toggleSetValue(previewOpenIds, nodeId)
}

function handleToggleChat(nodeId: string) {
  if (!chatStateMap.value[nodeId]) {
    chatStateMap.value = {
      ...chatStateMap.value,
      [nodeId]: {
        question: '',
        sending: false,
        answer: '',
        error: '',
        citations: []
      }
    }
  }
  toggleSetValue(chatOpenIds, nodeId)
}

function handleQuestionChange(nodeId: string, value: string) {
  chatStateMap.value = {
    ...chatStateMap.value,
    [nodeId]: {
      ...(chatStateMap.value[nodeId] || { question: '', sending: false }),
      question: value
    }
  }
}

async function handleSubmitQuestion(nodeId: string) {
  const currentState = chatStateMap.value[nodeId] || { question: '', sending: false }
  const question = String(currentState.question || '').trim()
  if (!question) {
    ElMessage.warning('先输入问题')
    return
  }
  if (!taskId.value) {
    return
  }

  chatStateMap.value = {
    ...chatStateMap.value,
    [nodeId]: {
      ...currentState,
      sending: true,
      error: ''
    }
  }

  try {
    const selectedNodeIds = [selectedOutlineNodeId.value].filter((id) => id && id !== nodeId)
    const response = await askDocumentNodeApi(taskId.value, nodeId, {
      question,
      selectedNodeIds
    })
    const answer = unwrapResponseData<DocumentNodeAnswer | null>(response)
    chatStateMap.value = {
      ...chatStateMap.value,
      [nodeId]: {
        ...currentState,
        question,
        sending: false,
        error: '',
        answer: answer?.answer || '',
        citations: answer?.citations || []
      }
    }
  } catch (error) {
    chatStateMap.value = {
      ...chatStateMap.value,
      [nodeId]: {
        ...currentState,
        question,
        sending: false,
        error: (error as Error)?.message || '节点问答失败'
      }
    }
  }
}

function handleNodeClick(event: any) {
  const clickedNode = event.node
  if (!clickedNode || clickedNode.data.kind !== 'outline') {
    return
  }

  selectedOutlineNodeId.value = clickedNode.id
  if (clickedNode.data.expandable && !expandedNodeIds.value.has(clickedNode.id)) {
    handleToggleExpand(clickedNode.id)
  }
}

function handleNodeDragStop(event: any) {
  const draggedNode = event?.node
  if (!draggedNode?.id || !draggedNode?.position) {
    return
  }

  nodePositionOverrides.value = {
    ...nodePositionOverrides.value,
    [draggedNode.id]: {
      x: Number(draggedNode.position.x || 0),
      y: Number(draggedNode.position.y || 0)
    }
  }
}

function fitCanvas() {
  flowRef.value?.fitView?.({
    padding: isMobileViewport.value ? 0.1 : 0.16,
    includeHiddenNodes: false,
    duration: 280
  })
}

function requestViewportAction(action: ViewportAction) {
  const priorityMap: Record<ViewportAction, number> = {
    none: 0,
    center: 1,
    fit: 2
  }

  if (priorityMap[action] >= priorityMap[pendingViewportAction]) {
    pendingViewportAction = action
  }
}

function getOutlineBounds() {
  const metrics = getLayoutMetrics()
  const width = metrics.outlineWidth
  const height = metrics.outlineHeight

  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  let maxY = Number.NEGATIVE_INFINITY
  let foundOutlineNode = false

  for (const node of visibleNodes.value as Array<{ position: { x: number; y: number }; data?: CanvasNodeData }>) {
    if (node.data?.kind !== 'outline') {
      continue
    }

    foundOutlineNode = true
    const x = Number(node.position.x || 0)
    const y = Number(node.position.y || 0)
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    maxX = Math.max(maxX, x + width)
    maxY = Math.max(maxY, y + height)
  }

  if (!foundOutlineNode) {
    return null
  }

  return {
    x: minX,
    y: minY,
    width: Math.max(width, maxX - minX),
    height: Math.max(height, maxY - minY)
  }
}

function fitOutlineBounds() {
  const bounds = getOutlineBounds()
  if (!bounds) {
    fitCanvas()
    return
  }

  flowRef.value?.fitBounds?.(bounds, {
    padding: isMobileViewport.value ? 0.1 : 0.16,
    duration: 280
  })
}

function centerOutlineBounds() {
  const bounds = getOutlineBounds()
  if (!bounds) {
    fitCanvas()
    return
  }

  const viewport = flowRef.value?.getViewport?.()
  flowRef.value?.setCenter?.(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, {
    zoom: viewport?.zoom,
    duration: 260
  })
}

function scheduleViewportAction(action: ViewportAction) {
  if (!import.meta.client) {
    if (action === 'fit') {
      fitOutlineBounds()
    } else if (action === 'center') {
      centerOutlineBounds()
    }
    return
  }

  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      if (action === 'fit') {
        fitOutlineBounds()
      } else if (action === 'center') {
        centerOutlineBounds()
      }
    })
  })
}

function syncViewportState() {
  if (!import.meta.client) {
    return
  }

  isMobileViewport.value = window.innerWidth <= 768
}

function syncThemeState() {
  isDarkMode.value = getThemeMode() === 'dark'
}

function toggleTheme() {
  isDarkMode.value = !isDarkMode.value
  setThemeMode(isDarkMode.value ? 'dark' : 'light')
}

onMounted(() => {
  isDarkMode.value = initTheme()
  syncViewportState()
  window.addEventListener('theme-change', syncThemeState)
  window.addEventListener('resize', syncViewportState, { passive: true })

  if (import.meta.client) {
    void import('elkjs/lib/elk.bundled.js').then((module) => {
      const Elk = module.default
      elkEngine.value = markRaw(new Elk())
      requestViewportAction('fit')
      void rebuildCanvas()
    })
  }

  if (!authStore.isLoggedIn) {
    void router.push('/login')
    return
  }

  void loadTask()
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', syncThemeState)
  window.removeEventListener('resize', syncViewportState)
})
</script>

<template>
  <section class="document-task-page" :class="{ 'is-dark': isDarkMode }">
    <header class="task-toolbar">
      <div class="task-toolbar__left">
        <button type="button" class="toolbar-btn secondary" @click="router.push('/ai/document')">
          <i class="fas fa-arrow-left"></i>
          <span>返回任务列表</span>
        </button>
        <div class="task-headline">
          <div class="task-title-row">
            <span class="task-chip">文档画布</span>
            <h1>{{ taskDetail?.title || `文档任务 #${taskId}` }}</h1>
          </div>
          <p>{{ taskDetail?.fileName || '未命名文档' }}</p>
        </div>
      </div>
      <div class="task-toolbar__right">
        <div class="toolbar-meta">
          <span>状态：{{ taskDetail?.status || 'LOADING' }}</span>
          <span>页数：{{ taskDetail?.pageCount || 0 }}</span>
        </div>
        <button type="button" class="toolbar-btn secondary" @click="loadTask()">
          <i class="fas fa-rotate-right"></i>
          <span>刷新结果</span>
        </button>
        <button type="button" class="toolbar-btn" @click="fitCanvas">
          <i class="fas fa-expand"></i>
          <span>重新聚焦</span>
        </button>
      </div>
    </header>

    <div v-loading="loading" class="document-canvas-shell">
      <ClientOnly>
        <VueFlow
          ref="flowRef"
          v-model:nodes="visibleNodes"
          v-model:edges="visibleEdges"
          :node-types="nodeTypes"
          class="document-flow"
          :default-viewport="{ zoom: 0.92 }"
          :min-zoom="0.3"
          :max-zoom="1.5"
          :nodes-connectable="false"
          :elements-selectable="true"
          :fit-view-on-init="true"
          @node-click="handleNodeClick"
          @node-drag-stop="handleNodeDragStop"
        >
          <div class="canvas-bg"></div>
        </VueFlow>
      </ClientOnly>
    </div>

    <ElTooltip content="切换主题" placement="left" effect="light" popper-class="document-theme-tooltip" :teleported="false">
      <button type="button" class="document-theme-toggle" :title="isDarkMode ? '切换为亮色' : '切换为暗色'" @click="toggleTheme">
        <i :class="['fas', isDarkMode ? 'fa-sun' : 'fa-moon']"></i>
      </button>
    </ElTooltip>
  </section>
</template>

<style scoped lang="scss">
.document-task-page {
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(79, 70, 229, 0.08), transparent 22%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.98));
}

.task-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  flex: 0 0 auto;
  padding: 18px 24px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14px);
}

.task-toolbar__left,
.task-toolbar__right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.task-headline {
  min-width: 0;
}

.task-title-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.task-headline {
  h1 {
    margin: 0;
    color: #0f172a;
    font-size: clamp(1.55rem, 2.2vw, 1.9rem);
    line-height: 1.08;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  p {
    margin: 8px 0 0;
    color: #64748b;
  }
}

.task-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(79, 70, 229, 0.12);
  color: #4f46e5;
  font-size: 0.8rem;
  font-weight: 700;
}

.toolbar-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #475569;
  font-size: 0.84rem;
  min-width: 78px;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #4f46e5, #0ea5e9);
  color: #fff;
  cursor: pointer;
  font-weight: 600;

  &.secondary {
    background: rgba(255, 255, 255, 0.88);
    color: #0f172a;
    border: 1px solid rgba(148, 163, 184, 0.24);
  }
}

.document-canvas-shell {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
}

.document-theme-toggle {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 30;
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4f46e5, #0ea5e9);
  color: #fff;
  box-shadow: 0 16px 36px rgba(37, 99, 235, 0.28);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    opacity 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 20px 42px rgba(37, 99, 235, 0.32);
  }

  &:active {
    transform: translateY(0);
  }

  i {
    font-size: 1rem;
  }
}

.document-flow {
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.06), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.24), rgba(248, 250, 252, 0.1));
}

.canvas-bg {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(99, 102, 241, 0.12) 1px, transparent 1px),
    linear-gradient(180deg, rgba(255, 255, 255, 0.26), rgba(255, 255, 255, 0.04));
  background-size: 24px 24px, 100% 100%;
  pointer-events: none;
}

:deep(.vue-flow__pane) {
  cursor: grab;
}

:deep(.vue-flow__controls),
:deep(.vue-flow__minimap) {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(16px);
}

:deep(.vue-flow__controls-button) {
  background: transparent;
  color: #0f172a;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

:deep(.vue-flow__controls-button:last-child) {
  border-bottom: none;
}

:deep(.vue-flow__node-documentNode) {
  width: auto;
  border: none;
  background: transparent;
  box-shadow: none;
}

:deep(.vue-flow__edge-path) {
  stroke-linecap: round;
}

.document-task-page.is-dark {
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.14), transparent 26%),
    linear-gradient(180deg, rgba(2, 6, 23, 0.98), rgba(15, 23, 42, 0.98));
}

.document-task-page.is-dark .task-toolbar {
  background: rgba(2, 6, 23, 0.74);
  border-bottom-color: rgba(71, 85, 105, 0.28);
}

.document-task-page.is-dark .task-headline h1 {
  color: #e2e8f0;
}

.document-task-page.is-dark .task-headline p,
.document-task-page.is-dark .toolbar-meta {
  color: #94a3b8;
}

.document-task-page.is-dark .toolbar-btn.secondary {
  background: rgba(15, 23, 42, 0.9);
  color: #e2e8f0;
  border-color: rgba(100, 116, 139, 0.32);
}

.document-task-page.is-dark .document-flow {
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.52), rgba(2, 6, 23, 0.3));
}

.document-task-page.is-dark .document-theme-toggle {
  box-shadow: 0 16px 36px rgba(14, 165, 233, 0.24);
}

.document-task-page.is-dark .canvas-bg {
  background-image:
    radial-gradient(circle, rgba(148, 163, 184, 0.16) 1px, transparent 1px),
    linear-gradient(180deg, rgba(15, 23, 42, 0.18), rgba(2, 6, 23, 0.02));
}

.document-task-page.is-dark :deep(.vue-flow__controls),
.document-task-page.is-dark :deep(.vue-flow__minimap) {
  background: rgba(15, 23, 42, 0.84);
  border-color: rgba(100, 116, 139, 0.28);
  box-shadow: 0 12px 30px rgba(2, 6, 23, 0.32);
}

.document-task-page.is-dark :deep(.vue-flow__controls-button) {
  color: #e2e8f0;
  border-bottom-color: rgba(100, 116, 139, 0.2);
}

:deep(.document-theme-tooltip.el-popper) {
  background: var(--card-bg) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14) !important;
}

:deep(.document-theme-tooltip.el-popper .el-popper__arrow::before) {
  background: var(--card-bg) !important;
  border-color: var(--border-color) !important;
}

@media (max-width: 900px) {
  .task-toolbar,
  .task-toolbar__left,
  .task-toolbar__right {
    flex-direction: column;
    align-items: flex-start;
  }

  .task-toolbar {
    padding: 16px 16px 14px;
  }

  .task-title-row {
    flex-wrap: wrap;
    gap: 10px;
  }

  .task-headline h1 {
    white-space: normal;
  }

  .task-toolbar__right {
    width: 100%;
    align-items: stretch;
  }

  .toolbar-btn {
    justify-content: center;
  }

  .document-theme-toggle {
    right: 16px;
    bottom: 16px;
  }
}
</style>
