<script setup lang="ts">
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { ElMessage } from 'element-plus'
import type { Connection, Edge, Node, OnConnectStartParams } from '@vue-flow/core'
import { ConnectionMode, MarkerType, VueFlow } from '@vue-flow/core'
import DocumentCanvasNode from '@/components/ai-document/DocumentCanvasNode.vue'
import { getConversationModelOptionsApi } from '@/api/ai'
import {
  getDocumentNodeMessagesApi,
  getDocumentNodeThreadApi,
  getDocumentTaskDetailApi,
  getDocumentTaskResultApi,
  streamDocumentNodeApi
} from '@/api/ai-document'
import type {
  DocumentContextNode,
  DocumentKnowledgeFlowEdge,
  DocumentNodeAnswer,
  DocumentNodeMessage,
  DocumentNodeThread,
  DocumentParseResult,
  DocumentTaskDetail,
  DocumentTreeNode
} from '@/types/ai-document'
import type { PageResult } from '@/types/common'
import { normalizeMarkdownContent } from '@/utils/ai-markdown'
import { unwrapResponseData } from '@/utils/response'
import { getThemeMode, initTheme, setThemeMode } from '@/utils/theme'

type QueryModePreset = 'strict' | 'balanced' | 'explore'
type AnyRecord = Record<string, any>
const AI_DOCUMENT_MODEL_STORAGE_KEY = 'BLOG_AI_DOCUMENT_SELECTED_MODEL_ID'

type ChatState = {
  mode: QueryModePreset
  question: string
  sending: boolean
  threadId?: number
  historyLoaded?: boolean
  selectedNodeIds?: string[]
  modelId?: string
  answer?: string
  error?: string
  citations?: DocumentNodeAnswer['citations']
  usedNodes?: DocumentNodeAnswer['usedNodes']
  candidateNodes?: DocumentNodeAnswer['candidateNodes']
  knowledgeFlowEdges?: DocumentNodeAnswer['knowledgeFlowEdges']
  contextPlan?: DocumentNodeAnswer['contextPlan']
  budgetReport?: DocumentNodeAnswer['budgetReport']
}

type CanvasNodeData = {
  kind: 'outline' | 'source-preview' | 'chat-thread'
  nodeId: string
  highlightRole?: 'current' | 'used' | 'candidate' | 'citation' | 'selected'
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
  queryMode?: QueryModePreset
  contextSummary?: string
  usedNodes?: DocumentContextNode[]
  candidateNodes?: DocumentContextNode[]
  selectedContextNodes?: DocumentContextNode[]
  showContextSocket?: boolean
  acceptContextDrop?: boolean
  sending?: boolean
  error?: string
  onToggleExpand?: (nodeId: string) => void
  onTogglePreview?: (nodeId: string) => void
  onToggleChat?: (nodeId: string) => void
  onQuestionChange?: (nodeId: string, value: string) => void
  onSubmitQuestion?: (nodeId: string) => void
  onSelectCitation?: (nodeId: string) => void
  onQueryModeChange?: (nodeId: string, mode: QueryModePreset) => void
  onToggleSelectedContextNode?: (nodeId: string, targetNodeId: string) => void
}

type OutlineDescriptor = {
  id: string
  parentId: string | null
  node: DocumentTreeNode
}

type CanvasFlowNode = Node<CanvasNodeData> & {
  selected?: boolean
}

type OutlinePanelItem = {
  id: string
  title: string
  depth: number
  expanded: boolean
  hasChildren: boolean
  isMatched: boolean
}

type ViewportAction = 'none' | 'fit' | 'center' | 'focus-node'

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
const outlineDrawerOpen = ref(false)
const outlineSearch = ref('')
const flowRef = ref<InstanceType<typeof VueFlow> | null>(null)
const isDarkMode = ref(false)
const isMobileViewport = ref(false)
const modelOptionsLoading = ref(false)
const chatModels = ref<AnyRecord[]>([])
const selectedModelId = ref('')
const activeAnswerNodeId = ref('')
const connectingSocketChatNodeId = ref('')
const elkEngine = shallowRef<any | null>(null)
const streamAbortControllerMap = new Map<string, AbortController>()
let layoutRequestId = 0
let pendingViewportAction: ViewportAction = 'none'
let pendingViewportNodeId = ''

const visibleNodes = ref<CanvasFlowNode[]>([])
const visibleEdges = ref<Edge[]>([])
const outlineSearchKeyword = computed(() => outlineSearch.value.trim().toLowerCase())
const outlinePanelItems = computed(() => buildOutlinePanelItems(parseResult.value?.root, outlineSearchKeyword.value))
const documentNodeLookup = computed<Record<string, DocumentTreeNode>>(() => {
  const lookup: Record<string, DocumentTreeNode> = {}
  for (const node of flattenTree(parseResult.value?.root)) {
    lookup[node.id] = node
  }
  return lookup
})
const activeAnswerState = computed(() => (activeAnswerNodeId.value ? chatStateMap.value[activeAnswerNodeId.value] : undefined))
const selectedModelOption = computed(() => chatModels.value.find((item) => item.id === selectedModelId.value) || null)
const contextControlState = computed(() => {
  if (connectingSocketChatNodeId.value) {
    return chatStateMap.value[connectingSocketChatNodeId.value]
  }
  return activeAnswerState.value
})
const activeUsedNodeIds = computed(() => new Set((activeAnswerState.value?.usedNodes || []).map((node) => String(node.nodeId || '')).filter(Boolean)))
const activeCandidateNodeIds = computed(() => new Set((activeAnswerState.value?.candidateNodes || []).map((node) => String(node.nodeId || '')).filter(Boolean)))
const activeCitationNodeIds = computed(() => new Set((activeAnswerState.value?.citations || []).map((citation) => String(citation.nodeId || '')).filter(Boolean)))
const activeSelectedContextNodeIds = computed(() => new Set((contextControlState.value?.selectedNodeIds || []).map((nodeId) => String(nodeId || '')).filter(Boolean)))
const activeKnowledgeFlowEdges = computed(() => activeAnswerState.value?.knowledgeFlowEdges || [])

const nodeTypes = markRaw({
  documentNode: markRaw(DocumentCanvasNode)
})

watch(
  [parseResult, expandedNodeIds, previewOpenIds, chatOpenIds, chatStateMap, isDarkMode, isMobileViewport, activeAnswerNodeId, connectingSocketChatNodeId],
  () => {
    void rebuildCanvas()
  },
  { deep: true }
)

watch(isMobileViewport, () => {
  if (isMobileViewport.value) {
    outlineDrawerOpen.value = false
  }
  requestViewportAction('fit')
})

watch(selectedOutlineNodeId, () => {
  syncSelectedOutlineNodes()
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

function ensureSelectedModel() {
  if (!import.meta.client) {
    return
  }
  if (!chatModels.value.length) {
    selectedModelId.value = ''
    window.localStorage.removeItem(AI_DOCUMENT_MODEL_STORAGE_KEY)
    return
  }
  const availableIds = new Set(chatModels.value.map((item) => item.id))
  const storedModelId = window.localStorage.getItem(AI_DOCUMENT_MODEL_STORAGE_KEY) || ''
  if (storedModelId && !availableIds.has(storedModelId)) {
    window.localStorage.removeItem(AI_DOCUMENT_MODEL_STORAGE_KEY)
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

function persistSelectedModel(modelId = selectedModelId.value) {
  if (!import.meta.client || !modelId) {
    return
  }
  window.localStorage.setItem(AI_DOCUMENT_MODEL_STORAGE_KEY, modelId)
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

function matchesOutlineKeyword(node: DocumentTreeNode, keyword: string) {
  if (!keyword) {
    return false
  }

  return [node.title, node.summary, node.markdown]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(keyword))
}

function buildOutlinePanelItems(root?: DocumentTreeNode | null, keyword = '') {
  if (!root) {
    return [] as OutlinePanelItem[]
  }

  const items: OutlinePanelItem[] = []
  const searching = Boolean(keyword)

  function traverse(node: DocumentTreeNode, depth: number): boolean {
    const children = getVisibleTreeChildren(node)
    const isMatched = matchesOutlineKeyword(node, keyword)
    items.push({
      id: node.id,
      title: String(node.title || '未命名节点'),
      depth,
      expanded: expandedNodeIds.value.has(node.id),
      hasChildren: Boolean(children.length),
      isMatched
    })

    if (!searching) {
      if (expandedNodeIds.value.has(node.id)) {
        for (const child of children) {
          traverse(child, depth + 1)
        }
      }
      return true
    }

    let hasMatchedDescendant = false
    const startIndex = items.length
    for (const child of children) {
      const childStart = items.length
      const include = traverse(child, depth + 1)
      if (!include) {
        items.splice(childStart)
      } else {
        hasMatchedDescendant = true
      }
    }

    if (!isMatched && !hasMatchedDescendant) {
      items.splice(startIndex - 1)
      return false
    }

    return true
  }

  traverse(root, 0)
  return items
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
      mobileAttachmentHeight: 392,
      desktopAttachmentHeight: 0
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
    attachmentStackGap: 28,
    mobileAttachmentHeight: 0,
    desktopAttachmentHeight: 456
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
    nodes: [] as CanvasFlowNode[],
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
          highlightRole: resolveNodeHighlightRole(descriptor.id),
          themeMode: isDarkMode.value ? 'dark' : 'light',
        title: String(node.title || '未命名节点'),
        subtitle: buildSubtitle(node),
        body: String(node.summary || ''),
          badge: buildBadge(node),
          expandable: Boolean(getVisibleTreeChildren(node).length),
          expanded: expandedNodeIds.value.has(descriptor.id),
          acceptContextDrop: Boolean(connectingSocketChatNodeId.value),
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
        type: 'bezier',
        animated: false,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 18,
          height: 18,
          color: isDarkMode.value ? '#7c8cff' : '#6366f1'
        },
        style: {
          stroke: isDarkMode.value ? '#7c8cff' : '#6366f1',
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
  appendKnowledgeFlowEdges(result)
  appendUserContextControlEdges(result)

  visibleNodes.value = result.nodes
  visibleEdges.value = result.edges

  if (pendingViewportAction !== 'none') {
    const action = pendingViewportAction
    const nodeId = pendingViewportNodeId
    pendingViewportAction = 'none'
    pendingViewportNodeId = ''
    await nextTick()
    scheduleViewportAction(action, nodeId)
  }
}

function appendAttachmentNodes(
  root: DocumentTreeNode,
  result: { nodes: CanvasFlowNode[]; edges: Edge[]; centers: Map<string, { x: number; y: number }> }
) {
  const metrics = getLayoutMetrics()
  const outlineNodes = flattenTree(root)
  for (const node of outlineNodes) {
    const anchor = result.centers.get(node.id)
    if (!anchor) {
      continue
    }

    const desktopStacked = !isMobileViewport.value && previewOpenIds.value.has(node.id) && chatOpenIds.value.has(node.id)
    const desktopStackBaseY = anchor.y - 96
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
            y: desktopStacked
              ? desktopStackBaseY + attachmentIndex * (metrics.desktopAttachmentHeight + metrics.attachmentStackGap)
              : anchor.y + metrics.previewOffsetY
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
          markdown: normalizeMarkdownContent(String(node.markdown || node.summary || '暂无原文预览内容')),
          pageLabel: sourceAnchor?.page ? `第 ${sourceAnchor.page} 页` : '未提供页码',
          anchorBox: normalizeAnchorBox(sourceAnchor?.bbox),
          sourceUrl: taskDetail.value?.sourceUrl || '',
          onTogglePreview: handleTogglePreview
        }
      })
      attachmentIndex += 1
      result.edges.push({
        id: `${node.id}-->${previewId}`,
        source: node.id,
        target: previewId,
        type: 'bezier',
        animated: true,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 18,
          height: 18,
          color: '#10b981'
        },
        style: {
          stroke: '#10b981',
          strokeWidth: 1.5,
          strokeDasharray: '6 4'
        }
      })
    }

    if (chatOpenIds.value.has(node.id)) {
      const chatId = `${node.id}__chat`
      const chatState = getChatState(node.id)
      const chatPosition = isMobileViewport.value
        ? {
            x: anchor.x,
            y: anchor.y + metrics.chatOffsetY + attachmentIndex * (metrics.mobileAttachmentHeight + metrics.attachmentStackGap)
          }
        : {
            x: anchor.x + metrics.attachmentOffsetX,
            y: desktopStacked
              ? desktopStackBaseY + attachmentIndex * (metrics.desktopAttachmentHeight + metrics.attachmentStackGap)
              : anchor.y + metrics.chatOffsetY
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
          subtitle: buildChatSubtitle(chatState),
          markdown: chatState.answer ? '' : normalizeMarkdownContent(buildChatHint(node)),
          question: chatState.question,
          answer: chatState.answer,
          citations: chatState.citations,
          queryMode: chatState.mode,
          contextSummary: buildContextSummary(chatState),
          usedNodes: chatState.usedNodes,
          candidateNodes: chatState.candidateNodes,
          selectedContextNodes: resolveSelectedContextNodes(node.id),
          showContextSocket: true,
          sending: chatState.sending,
          error: chatState.error,
          onToggleChat: handleToggleChat,
          onQuestionChange: handleQuestionChange,
          onSubmitQuestion: handleSubmitQuestion,
          onSelectCitation: handleSelectCitation,
          onQueryModeChange: handleQueryModeChange,
          onToggleSelectedContextNode: handleToggleSelectedContextNode
        }
      })
      result.edges.push({
        id: `${node.id}-->${chatId}`,
        source: node.id,
        target: chatId,
        targetHandle: 'context-socket-target',
        type: 'bezier',
        animated: true,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 18,
          height: 18,
          color: '#0ea5e9'
        },
        style: {
          stroke: '#0ea5e9',
          strokeWidth: 1.8
        }
      })
    }
  }
}

function appendKnowledgeFlowEdges(result: { nodes: CanvasFlowNode[]; edges: Edge[]; centers: Map<string, { x: number; y: number }> }) {
  const visibleNodeIds = new Set(result.nodes.map((node) => node.id))
  activeKnowledgeFlowEdges.value.forEach((edge: DocumentKnowledgeFlowEdge, index: number) => {
    const fromNodeId = String(edge.fromNodeId || '')
    const toNodeId = String(edge.toNodeId || '')
    if (!fromNodeId || !toNodeId || !visibleNodeIds.has(fromNodeId) || !visibleNodeIds.has(toNodeId)) {
      return
    }
    result.edges.push({
      id: `knowledge:${fromNodeId}:${toNodeId}:${index}`,
      source: fromNodeId,
      target: toNodeId,
      type: 'bezier',
      animated: true,
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 20,
        height: 20,
        color: '#38bdf8'
      },
      label: buildKnowledgeEdgeLabel(edge),
      style: {
        stroke: '#38bdf8',
        strokeWidth: 2.1,
        strokeDasharray: '7 5'
      }
    })
  })
}

function appendUserContextControlEdges(result: { nodes: CanvasFlowNode[]; edges: Edge[]; centers: Map<string, { x: number; y: number }> }) {
  const visibleNodeIds = new Set(result.nodes.map((node) => node.id))
  Object.entries(chatStateMap.value).forEach(([chatNodeId, chatState]) => {
    if (!chatOpenIds.value.has(chatNodeId)) {
      return
    }

    const sourceId = `${chatNodeId}__chat`
    if (!visibleNodeIds.has(sourceId)) {
      return
    }

    ;(chatState.selectedNodeIds || []).forEach((targetNodeId, index) => {
      if (!targetNodeId || !visibleNodeIds.has(targetNodeId)) {
        return
      }

      result.edges.push({
        id: `context-control:${chatNodeId}:${targetNodeId}:${index}`,
        source: sourceId,
        target: targetNodeId,
        type: 'bezier',
        sourceHandle: 'context-socket-source',
        targetHandle: 'context-target',
        animated: connectingSocketChatNodeId.value === chatNodeId,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 18,
          height: 18,
          color: '#f59e0b'
        },
        style: {
          stroke: '#f59e0b',
          strokeWidth: 2.2
        }
      })
    })
  })
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

function syncSelectedOutlineNodes() {
  const nextNodes: CanvasFlowNode[] = []
  for (const node of visibleNodes.value as CanvasFlowNode[]) {
    if (node.data?.kind !== 'outline') {
      nextNodes.push(node)
      continue
    }

    nextNodes.push({
      ...node,
      selected: node.id === selectedOutlineNodeId.value
    })
  }
  visibleNodes.value = nextNodes
}

function findOutlineTrail(root: DocumentTreeNode | null | undefined, nodeId: string) {
  if (!root || !nodeId) {
    return [] as DocumentTreeNode[]
  }

  const trail: DocumentTreeNode[] = []

  function visit(node: DocumentTreeNode): boolean {
    trail.push(node)
    if (node.id === nodeId) {
      return true
    }

    for (const child of getVisibleTreeChildren(node)) {
      if (visit(child)) {
        return true
      }
    }

    trail.pop()
    return false
  }

  return visit(root) ? trail : []
}

function collectOutlineSubtreeIds(node: DocumentTreeNode | null | undefined) {
  if (!node) {
    return [] as string[]
  }

  const ids = [node.id]
  for (const child of getVisibleTreeChildren(node)) {
    ids.push(...collectOutlineSubtreeIds(child))
  }
  return ids
}

function findOutlineNode(root: DocumentTreeNode | null | undefined, nodeId: string): DocumentTreeNode | null {
  if (!root || !nodeId) {
    return null
  }
  if (root.id === nodeId) {
    return root
  }

  for (const child of getVisibleTreeChildren(root)) {
    const matched = findOutlineNode(child, nodeId)
    if (matched) {
      return matched
    }
  }

  return null
}

function expandOutlineAncestors(nodeId: string) {
  const trail = findOutlineTrail(parseResult.value?.root, nodeId)
  if (!trail.length) {
    return false
  }

  const next = new Set(expandedNodeIds.value)
  let changed = false
  for (const ancestor of trail.slice(0, -1)) {
    if (!next.has(ancestor.id)) {
      next.add(ancestor.id)
      changed = true
    }
  }

  if (changed) {
    expandedNodeIds.value = next
  }
  return changed
}

async function focusOutlineNode(nodeId: string) {
  if (!nodeId) {
    return
  }

  const expandedChanged = expandOutlineAncestors(nodeId)
  selectedOutlineNodeId.value = nodeId

  if (expandedChanged) {
    requestViewportAction('focus-node', nodeId)
    await rebuildCanvas()
    return
  }

  await nextTick()
  scheduleViewportAction('focus-node', nodeId)
}

function toggleOutlineDrawer() {
  if (isMobileViewport.value) {
    return
  }
  outlineDrawerOpen.value = !outlineDrawerOpen.value
}

function handleOutlineItemToggle(nodeId: string) {
  handleToggleExpand(nodeId)
}

function handleOutlineItemSelect(nodeId: string) {
  void focusOutlineNode(nodeId)
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
    '你也可以从顶部插口拖出箭头，把别的节点纳入本轮显式上下文。',
    '提问后会显示：',
    '1. 实际使用的上下文节点',
    '2. 候选但未使用的节点',
    '3. 可回跳的引用与知识流边'
  ].join('\n')
}

function buildKnowledgeEdgeLabel(edge?: DocumentKnowledgeFlowEdge) {
  const edgeType = String(edge?.edgeType || '').toLowerCase()
  switch (edgeType) {
    case 'bridges':
      return '桥接'
    case 'supports':
      return '补充'
    case 'explains':
      return '上文'
    case 'extends':
      return '下文'
    case 'compares':
      return '对照'
    default:
      return ''
  }
}

function buildChatSubtitle(chatState?: ChatState) {
  const modelLabel = resolveModelDisplayName(chatState?.modelId) || selectedModelOption.value?.displayName || '默认模型'
  if (chatState?.sending) {
    return `正在生成回答 · ${modelLabel}`
  }
  if (!chatState?.answer) {
    return `当前问题将绑定到所选节点，并可叠加显式上下文 · ${modelLabel}`
  }
  return `已完成一次节点问答 · ${describeMode(chatState.mode)}模式 · ${modelLabel}`
}

function buildContextSummary(chatState?: ChatState) {
  const parts: string[] = []
  const truncatedNodeCount = chatState?.budgetReport?.truncatedNodeCount
  if (chatState?.selectedNodeIds?.length) {
    parts.push(`${chatState.selectedNodeIds.length} 个显式节点`)
  }
  if (chatState?.answer) {
    parts.push(`${chatState.usedNodes?.length || 0} 个已使用节点`)
    parts.push(`${chatState.candidateNodes?.length || 0} 个候选节点`)
  }
  if (truncatedNodeCount) {
    parts.push(`截断 ${truncatedNodeCount} 个`)
  }
  return parts.join(' · ')
}

function describeMode(mode?: QueryModePreset) {
  switch (mode) {
    case 'strict':
      return '严格'
    case 'explore':
      return '探索'
    default:
      return '平衡'
  }
}

function buildModelOptionLabel(model: AnyRecord) {
  return `${model.displayName} · x${formatQuotaMultiplier(model.quotaMultiplier)}`
}

function resolveModelDisplayName(modelId?: string) {
  if (!modelId) {
    return ''
  }
  return String(chatModels.value.find((item) => item.id === modelId)?.displayName || '')
}

function formatQuotaMultiplier(value: unknown) {
  const normalized = Number(value || 1)
  if (Number.isNaN(normalized) || normalized <= 0) {
    return '1'
  }
  return Number.isInteger(normalized) ? String(normalized) : normalized.toFixed(1).replace(/\.0$/, '')
}

function resolveNodeHighlightRole(nodeId: string) {
  if (!nodeId) {
    return undefined
  }
  if (nodeId === activeAnswerNodeId.value) {
    return 'current' as const
  }
  if (activeSelectedContextNodeIds.value.has(nodeId)) {
    return 'selected' as const
  }
  if (activeCitationNodeIds.value.has(nodeId)) {
    return 'citation' as const
  }
  if (activeUsedNodeIds.value.has(nodeId)) {
    return 'used' as const
  }
  if (activeCandidateNodeIds.value.has(nodeId)) {
    return 'candidate' as const
  }
  return undefined
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
  requestViewportAction('focus-node', nodeId)
}

function handleTogglePreview(nodeId: string) {
  toggleSetValue(previewOpenIds, nodeId)
}

async function handleToggleChat(nodeId: string) {
  ensureChatState(nodeId)
  const shouldLoadHistory = !chatOpenIds.value.has(nodeId)
  activeAnswerNodeId.value = nodeId
  toggleSetValue(chatOpenIds, nodeId)
  if (!chatOpenIds.value.has(nodeId) && activeAnswerNodeId.value === nodeId) {
    activeAnswerNodeId.value = ''
  }
  if (!chatOpenIds.value.has(nodeId) && connectingSocketChatNodeId.value === nodeId) {
    connectingSocketChatNodeId.value = ''
  }
  if (shouldLoadHistory && chatOpenIds.value.has(nodeId)) {
    await loadNodeThreadState(nodeId)
  }
}

function handleQuestionChange(nodeId: string, value: string) {
  const currentState = getChatState(nodeId)
  chatStateMap.value = {
    ...chatStateMap.value,
    [nodeId]: {
      ...currentState,
      question: value
    }
  }
}

function handleQueryModeChange(nodeId: string, mode: QueryModePreset) {
  const currentState = getChatState(nodeId)
  chatStateMap.value = {
    ...chatStateMap.value,
    [nodeId]: {
      ...currentState,
      mode
    }
  }
}

async function handleSubmitQuestion(nodeId: string) {
  const currentState = getChatState(nodeId)
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
      historyLoaded: true,
      modelId: selectedModelId.value || currentState.modelId || '',
      error: '',
      answer: '',
      citations: [],
      usedNodes: [],
      candidateNodes: [],
      knowledgeFlowEdges: []
    }
  }
  activeAnswerNodeId.value = nodeId
  abortNodeStream(nodeId)
  const abortController = new AbortController()
  streamAbortControllerMap.set(nodeId, abortController)

  try {
    const selectedNodeIds = Array.from(new Set([
      ...(currentState.selectedNodeIds || []),
      selectedOutlineNodeId.value
    ].filter((id) => id && id !== nodeId)))
    await streamDocumentNodeApi(taskId.value, nodeId, {
      question,
      modelId: selectedModelId.value || undefined,
      selectedNodeIds,
      ...buildAskPayloadByMode(currentState.mode)
    }, {
      onMeta: (event) => {
        const metaAnswer = event?.answer as DocumentNodeAnswer | undefined
        chatStateMap.value = {
          ...chatStateMap.value,
          [nodeId]: {
            ...getChatState(nodeId),
            question,
            sending: true,
            threadId: metaAnswer?.threadId,
            historyLoaded: true,
            error: '',
            selectedNodeIds,
            modelId: String(metaAnswer?.modelId || selectedModelId.value || ''),
            citations: metaAnswer?.citations || [],
            usedNodes: metaAnswer?.usedNodes || [],
            candidateNodes: metaAnswer?.candidateNodes || [],
            knowledgeFlowEdges: metaAnswer?.knowledgeFlowEdges || [],
            contextPlan: metaAnswer?.contextPlan,
            budgetReport: metaAnswer?.budgetReport
          }
        }
      },
      onDelta: (event) => {
        const current = getChatState(nodeId)
        chatStateMap.value = {
          ...chatStateMap.value,
          [nodeId]: {
            ...current,
            sending: true,
            answer: normalizeMarkdownContent(`${current.answer || ''}${String(event?.content || '')}`, true)
          }
        }
      },
      onDone: (event) => {
        const answer = event?.answer as DocumentNodeAnswer | undefined
        chatStateMap.value = {
          ...chatStateMap.value,
          [nodeId]: {
            ...getChatState(nodeId),
            question,
            sending: false,
            threadId: answer?.threadId,
            historyLoaded: true,
            error: '',
            selectedNodeIds,
            modelId: String(answer?.modelId || selectedModelId.value || ''),
            answer: normalizeMarkdownContent(answer?.answer || '', true),
            citations: answer?.citations || [],
            usedNodes: answer?.usedNodes || [],
            candidateNodes: answer?.candidateNodes || [],
            knowledgeFlowEdges: answer?.knowledgeFlowEdges || [],
            contextPlan: answer?.contextPlan,
            budgetReport: answer?.budgetReport
          }
        }
      },
      onError: (error) => {
        chatStateMap.value = {
          ...chatStateMap.value,
          [nodeId]: {
            ...getChatState(nodeId),
            question,
            sending: false,
            historyLoaded: true,
            error: error.message || '节点问答失败'
          }
        }
      }
    }, abortController.signal)
    streamAbortControllerMap.delete(nodeId)
  } catch (error) {
    if ((error as Error)?.name === 'AbortError') {
      return
    }
    streamAbortControllerMap.delete(nodeId)
    chatStateMap.value = {
      ...chatStateMap.value,
      [nodeId]: {
        ...getChatState(nodeId),
        question,
        sending: false,
        historyLoaded: true,
        error: (error as Error)?.message || '节点问答失败'
      }
    }
  }
}

async function loadNodeThreadState(nodeId: string) {
  if (!taskId.value) {
    return
  }
  const currentState = getChatState(nodeId)
  if (currentState.historyLoaded) {
    return
  }

  try {
    const threadResponse = await getDocumentNodeThreadApi(taskId.value, nodeId)
    const thread = unwrapResponseData<DocumentNodeThread | null>(threadResponse)
    if (!thread?.threadId) {
      chatStateMap.value = {
        ...chatStateMap.value,
        [nodeId]: {
          ...currentState,
          historyLoaded: true,
          threadId: undefined
        }
      }
      return
    }

    const messageResponse = await getDocumentNodeMessagesApi(taskId.value, nodeId, { pageNum: 1, pageSize: 50 })
    const page = unwrapResponseData<PageResult<DocumentNodeMessage> | null>(messageResponse) || {}
    const messages = Array.isArray(page.records) ? page.records : []
    const assistantIndex = findLastMessageIndex(messages, 'assistant')
    const assistantMessage = assistantIndex >= 0 ? messages[assistantIndex] : null
    const userMessage = assistantIndex >= 0
      ? findPreviousMessage(messages, assistantIndex - 1, 'user')
      : findPreviousMessage(messages, messages.length - 1, 'user')

    chatStateMap.value = {
      ...chatStateMap.value,
      [nodeId]: {
        ...currentState,
        threadId: Number(thread.threadId),
        historyLoaded: true,
        question: String(userMessage?.content || currentState.question || ''),
        answer: assistantMessage?.content ? normalizeMarkdownContent(String(assistantMessage.content), true) : '',
        modelId: String(assistantMessage?.modelId || thread.modelId || currentState.modelId || ''),
        error: '',
        citations: assistantMessage?.citations || [],
        usedNodes: assistantMessage?.usedNodes || [],
        candidateNodes: assistantMessage?.candidateNodes || [],
        knowledgeFlowEdges: assistantMessage?.knowledgeFlowEdges || [],
        contextPlan: assistantMessage?.contextPlan,
        budgetReport: assistantMessage?.budgetReport,
        selectedNodeIds: assistantMessage?.selectedNodeIds || currentState.selectedNodeIds || []
      }
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '节点对话历史加载失败')
  }
}

function findLastMessageIndex(messages: DocumentNodeMessage[], role: string) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (String(messages[index]?.role || '') === role) {
      return index
    }
  }
  return -1
}

function findPreviousMessage(messages: DocumentNodeMessage[], startIndex: number, role: string) {
  for (let index = startIndex; index >= 0; index -= 1) {
    const message = messages[index]
    if (String(message?.role || '') === role) {
      return message
    }
  }
  return null
}

function buildAskPayloadByMode(mode: QueryModePreset = 'balanced') {
  switch (mode) {
    case 'strict':
      return {
        descendantDepth: 1,
        includeAncestorSiblings: false,
        includeSemanticBridges: false,
        maxBridgeNodes: 0
      }
    case 'explore':
      return {
        descendantDepth: 3,
        includeAncestorSiblings: true,
        includeSemanticBridges: true,
        maxBridgeNodes: 4
      }
    default:
      return {
        descendantDepth: 2,
        includeAncestorSiblings: true,
        includeSemanticBridges: true,
        maxBridgeNodes: 2
      }
  }
}

function handleSelectCitation(nodeId: string) {
  if (!nodeId) {
    return
  }
  void focusOutlineNode(nodeId)
}

function handleToggleSelectedContextNode(nodeId: string, targetNodeId: string) {
  if (!nodeId || !targetNodeId || nodeId === targetNodeId) {
    return
  }

  const currentState = getChatState(nodeId)
  const nextSelectedNodeIds = new Set((currentState.selectedNodeIds || []).filter(Boolean))
  if (nextSelectedNodeIds.has(targetNodeId)) {
    nextSelectedNodeIds.delete(targetNodeId)
  } else {
    nextSelectedNodeIds.add(targetNodeId)
  }

  chatStateMap.value = {
    ...chatStateMap.value,
    [nodeId]: {
      ...currentState,
      selectedNodeIds: Array.from(nextSelectedNodeIds)
    }
  }
  activeAnswerNodeId.value = nodeId
}

function handleNodeClick(event: any) {
  const clickedNode = event.node
  if (!clickedNode || clickedNode.data.kind !== 'outline') {
    return
  }

  selectedOutlineNodeId.value = clickedNode.id
  if (chatOpenIds.value.has(clickedNode.id)) {
    activeAnswerNodeId.value = clickedNode.id
  }
}

function extractChatOwnerNodeId(nodeId?: string | null) {
  if (!nodeId || !nodeId.endsWith('__chat')) {
    return ''
  }
  return nodeId.slice(0, -'__chat'.length)
}

function handleConnectStart(connectionEvent: { event?: MouseEvent } & OnConnectStartParams) {
  if (connectionEvent.handleId !== 'context-socket-source') {
    connectingSocketChatNodeId.value = ''
    return
  }

  const ownerNodeId = extractChatOwnerNodeId(connectionEvent.nodeId)
  if (!ownerNodeId) {
    connectingSocketChatNodeId.value = ''
    return
  }

  connectingSocketChatNodeId.value = ownerNodeId
  activeAnswerNodeId.value = ownerNodeId
}

function handleConnect(connection: Connection) {
  const ownerNodeId = extractChatOwnerNodeId(connection.source)
  const targetNodeId = String(connection.target || '')
  if (!ownerNodeId || !targetNodeId || connection.sourceHandle !== 'context-socket-source') {
    return
  }

  handleToggleSelectedContextNode(ownerNodeId, targetNodeId)
}

function handleConnectEnd() {
  connectingSocketChatNodeId.value = ''
}

function handleNodeDoubleClick(event: any) {
  event?.event?.stopPropagation?.()
  const clickedNode = event?.node
  if (!clickedNode || clickedNode.data?.kind !== 'outline') {
    return
  }

  const currentNode = findOutlineNode(parseResult.value?.root, clickedNode.id)
  if (!currentNode) {
    return
  }

  selectedOutlineNodeId.value = clickedNode.id

  if (!clickedNode.data.expandable) {
    return
  }

  if (!expandedNodeIds.value.has(clickedNode.id)) {
    handleToggleExpand(clickedNode.id)
    return
  }

  const subtreeIds = new Set(collectOutlineSubtreeIds(currentNode))
  const nextExpandedIds = new Set([...expandedNodeIds.value].filter((id) => !subtreeIds.has(id)))
  if (nextExpandedIds.size === expandedNodeIds.value.size) {
    return
  }

  expandedNodeIds.value = nextExpandedIds
  requestViewportAction('focus-node', clickedNode.id)
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

function requestViewportAction(action: ViewportAction, nodeId = '') {
  const priorityMap: Record<ViewportAction, number> = {
    none: 0,
    center: 1,
    'focus-node': 2,
    fit: 3
  }

  if (priorityMap[action] >= priorityMap[pendingViewportAction]) {
    pendingViewportAction = action
    pendingViewportNodeId = action === 'focus-node' ? nodeId : ''
  }
}

function getViewportDimensions() {
  if (!import.meta.client) {
    return { width: 1440, height: 900 }
  }

  return {
    width: window.innerWidth,
    height: window.innerHeight
  }
}

function getNodeFrameSize(kind?: CanvasNodeData['kind']) {
  const metrics = getLayoutMetrics()
  if (kind === 'outline') {
    return {
      width: metrics.outlineWidth,
      height: metrics.outlineHeight
    }
  }

  const viewport = getViewportDimensions()
  if (isMobileViewport.value) {
    const width = viewport.width <= 420
      ? Math.min(Math.max(viewport.width - 24, 252), 332)
      : Math.min(Math.max(viewport.width - 40, 272), 360)
    return {
      width,
      height: Math.max(360, Math.min(viewport.height * 0.62, 560))
    }
  }

  const preferredWidth = Math.min(Math.max(viewport.width * 0.34, 420), 620)
  const maxWidth = Math.min(viewport.width * 0.78, 760)
  return {
    width: Math.min(preferredWidth, maxWidth),
    height: Math.max(420, Math.min(viewport.height * 0.68, 760))
  }
}

function getNodeBoundsByIds(nodeIds: string[]) {
  if (!nodeIds.length) {
    return null
  }

  const lookup = new Set(nodeIds)
  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  let maxY = Number.NEGATIVE_INFINITY
  let foundNode = false

  for (const node of visibleNodes.value as Array<{ id: string; position: { x: number; y: number }; data?: CanvasNodeData }>) {
    if (!lookup.has(node.id)) {
      continue
    }

    const frame = getNodeFrameSize(node.data?.kind)
    const x = Number(node.position.x || 0)
    const y = Number(node.position.y || 0)
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    maxX = Math.max(maxX, x + frame.width)
    maxY = Math.max(maxY, y + frame.height)
    foundNode = true
  }

  if (!foundNode) {
    return null
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY
  }
}

function getFocusedNodeBounds(nodeId: string) {
  if (!nodeId) {
    return null
  }

  const nodeIds = [nodeId]
  if (previewOpenIds.value.has(nodeId)) {
    nodeIds.push(`${nodeId}__preview`)
  }
  if (chatOpenIds.value.has(nodeId)) {
    nodeIds.push(`${nodeId}__chat`)
  }

  return getNodeBoundsByIds(nodeIds)
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

function focusNodeBounds(nodeId: string) {
  const bounds = getFocusedNodeBounds(nodeId)
  if (!bounds) {
    centerOutlineBounds()
    return
  }

  flowRef.value?.fitBounds?.(bounds, {
    padding: isMobileViewport.value ? 0.12 : 0.18,
    duration: 280
  })
}

function scheduleViewportAction(action: ViewportAction, nodeId = '') {
  if (!import.meta.client) {
    if (action === 'fit') {
      fitOutlineBounds()
    } else if (action === 'focus-node') {
      focusNodeBounds(nodeId)
    } else if (action === 'center') {
      centerOutlineBounds()
    }
    return
  }

  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      if (action === 'fit') {
        fitOutlineBounds()
      } else if (action === 'focus-node') {
        focusNodeBounds(nodeId)
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

function createDefaultChatState(): ChatState {
  return {
    question: '',
    mode: 'balanced',
    sending: false,
    historyLoaded: false,
    modelId: '',
    answer: '',
    error: '',
    citations: [],
    usedNodes: [],
    candidateNodes: [],
    knowledgeFlowEdges: [],
    selectedNodeIds: []
  }
}

function getChatState(nodeId: string): ChatState {
  return chatStateMap.value[nodeId] || createDefaultChatState()
}

function abortNodeStream(nodeId: string) {
  const controller = streamAbortControllerMap.get(nodeId)
  if (!controller) {
    return
  }
  controller.abort()
  streamAbortControllerMap.delete(nodeId)
}

function abortAllNodeStreams() {
  streamAbortControllerMap.forEach((controller) => controller.abort())
  streamAbortControllerMap.clear()
}

function ensureChatState(nodeId: string) {
  if (chatStateMap.value[nodeId]) {
    return
  }

  chatStateMap.value = {
    ...chatStateMap.value,
    [nodeId]: createDefaultChatState()
  }
}

function resolveSelectedContextNodes(nodeId: string): DocumentContextNode[] {
  return (getChatState(nodeId).selectedNodeIds || [])
    .map((selectedNodeId) => {
      const treeNode = documentNodeLookup.value[selectedNodeId]
      if (!treeNode) {
        return null
      }

      return {
        nodeId: selectedNodeId,
        title: treeNode.title,
        level: treeNode.level,
        type: treeNode.type,
        summary: treeNode.summary,
        page: treeNode.sourceAnchors?.[0]?.page,
        relation: 'selected'
      } as DocumentContextNode
    })
    .filter(Boolean) as DocumentContextNode[]
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

  void loadChatModels()
  void loadTask()
})

onBeforeUnmount(() => {
  abortAllNodeStreams()
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
        <div class="toolbar-model">
          <span class="toolbar-model__label">当前模型</span>
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
          <small v-if="selectedModelOption">倍率 x{{ formatQuotaMultiplier(selectedModelOption.quotaMultiplier) }}</small>
        </div>
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
      <div
        v-if="!isMobileViewport"
        class="outline-drawer"
        :class="{ 'is-open': outlineDrawerOpen, 'is-dark': isDarkMode }"
      >
        <button type="button" class="outline-drawer__toggle" :title="outlineDrawerOpen ? '收起目录' : '展开目录'" @click="toggleOutlineDrawer">
          <i :class="['fas', outlineDrawerOpen ? 'fa-chevron-left' : 'fa-chevron-right']"></i>
        </button>
        <aside v-if="outlineDrawerOpen" class="outline-drawer__panel">
          <div class="outline-drawer__header">
            <strong>文档结构</strong>
            <div class="outline-drawer__header-actions">
              <span>{{ outlinePanelItems.length }} 项</span>
              <button type="button" class="outline-drawer__close" title="关闭目录" @click="toggleOutlineDrawer">
                <i class="fas fa-xmark"></i>
              </button>
            </div>
          </div>
          <label class="outline-search">
            <i class="fas fa-magnifying-glass"></i>
            <input v-model.trim="outlineSearch" type="text" placeholder="搜索节点标题、摘要或内容" />
          </label>
          <div class="outline-drawer__list">
            <div
              v-for="item in outlinePanelItems"
              :key="item.id"
              class="outline-item"
              :class="{
                'is-selected': item.id === selectedOutlineNodeId,
                'is-matched': item.isMatched
              }"
              :style="{ '--outline-depth': item.depth }"
            >
              <button
                type="button"
                class="outline-item__expand"
                :class="{ 'is-placeholder': !item.hasChildren }"
                :title="item.expanded ? '收起下一级' : '展开下一级'"
                :disabled="!item.hasChildren"
                @click.stop="item.hasChildren && handleOutlineItemToggle(item.id)"
              >
                <i v-if="item.hasChildren" :class="['fas', item.expanded ? 'fa-chevron-down' : 'fa-chevron-right']"></i>
              </button>
              <button type="button" class="outline-item__button" @click="handleOutlineItemSelect(item.id)">
                <span class="outline-item__title">{{ item.title }}</span>
              </button>
            </div>
            <div v-if="!outlinePanelItems.length" class="outline-empty">
              未找到匹配节点
            </div>
          </div>
        </aside>
      </div>

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
          :connection-mode="ConnectionMode.Strict"
          :connection-line-options="{ markerEnd: { type: MarkerType.ArrowClosed, color: '#f59e0b', width: 18, height: 18 } }"
          :connection-line-style="{ stroke: '#f59e0b', strokeWidth: 2.2 }"
          :nodes-connectable="false"
          :elements-selectable="true"
          :fit-view-on-init="true"
          :auto-connect="false"
          :zoom-on-double-click="true"
          @connect="handleConnect"
          @connect-start="handleConnectStart"
          @connect-end="handleConnectEnd"
          @node-click="handleNodeClick"
          @node-double-click="handleNodeDoubleClick"
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

    <aside v-if="!isMobileViewport" class="document-tip" :class="{ 'is-dark': isDarkMode }">
      <p>左侧抽屉: 查看结构与搜索节点</p>
      <p>单击节点: 选中</p>
      <p>双击节点: 展开 / 收起子树</p>
      <p>拖动问答卡片顶部插口: 把节点纳入上下文</p>
      <p>双击空白区域: 缩放</p>
      <div class="document-tip__legend">
        <span class="document-tip__legend-item is-current">当前</span>
        <span class="document-tip__legend-item is-selected">显式</span>
        <span class="document-tip__legend-item is-used">已用</span>
        <span class="document-tip__legend-item is-candidate">候选</span>
        <span class="document-tip__legend-item is-citation">引用</span>
      </div>
    </aside>
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

.task-toolbar__right {
  margin-left: auto;
  justify-content: flex-end;
  flex-wrap: wrap;
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

.toolbar-model {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 220px;
  max-width: 280px;

  small {
    color: #64748b;
    font-size: 0.78rem;
  }
}

.toolbar-model__label {
  color: #475569;
  font-size: 0.78rem;
  font-weight: 700;
}

.model-select {
  width: 100%;
}

.model-select-fallback {
  width: 100%;
  min-height: 40px;
  padding: 10px 14px;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.08);
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.16) inset;
  color: #0f172a;
  font-size: 14px;
  line-height: 20px;
}

.model-select :deep(.el-input__wrapper),
.model-select :deep(.el-select__wrapper),
.model-select :deep(.el-input__inner) {
  background: rgba(148, 163, 184, 0.08) !important;
  color: #0f172a !important;
}

.model-select :deep(.el-input__wrapper),
.model-select :deep(.el-select__wrapper) {
  border-radius: 16px !important;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.16) inset !important;
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
  color: #0f172a !important;
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

.outline-drawer {
  position: absolute;
  left: 0;
  top: 50%;
  z-index: 24;
  display: flex;
  align-items: center;
  gap: 12px;
  pointer-events: none;
  transform: translateY(-50%);
}

.outline-drawer__toggle,
.outline-drawer__panel {
  pointer-events: auto;
}

.outline-drawer__toggle {
  width: 42px;
  height: 72px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-left: none;
  border-radius: 0 18px 18px 0;
  background: rgba(255, 255, 255, 0.88);
  color: #334155;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(14px);
  cursor: pointer;
  transform: translateX(-10px);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    background 0.22s ease;
}

.outline-drawer:not(.is-open):hover .outline-drawer__toggle {
  transform: translateX(4px) scale(1.02);
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.12);
}

.outline-drawer.is-open .outline-drawer__toggle {
  transform: translateX(0);
}

.outline-drawer__panel {
  width: min(320px, calc(100vw - 120px));
  height: min(calc(100dvh - 180px), 760px);
  display: flex;
  flex-direction: column;
  padding: 14px;
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 22px 48px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.outline-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  strong {
    color: #0f172a;
    font-size: 0.96rem;
  }

  span {
    color: #64748b;
    font-size: 0.8rem;
  }
}

.outline-drawer__header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.outline-drawer__close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.12);
  color: #475569;
  cursor: pointer;
}

.outline-search {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 11px 12px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(248, 250, 252, 0.92);
  color: #64748b;

  input {
    flex: 1 1 auto;
    min-width: 0;
    border: none;
    outline: none;
    background: transparent;
    color: #0f172a;
    font: inherit;
  }
}

.outline-drawer__list {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.outline-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  padding-left: calc(var(--outline-depth, 0) * 14px);
}

.outline-item__expand {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
  cursor: pointer;

  &.is-placeholder {
    opacity: 0;
    pointer-events: none;
  }
}

.outline-item__button {
  min-width: 0;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.64);
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    background: rgba(99, 102, 241, 0.1);
    transform: translateX(2px);
  }
}

.outline-item__title {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 0.86rem;
  line-height: 1.4;
}

.outline-item.is-selected .outline-item__button {
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.18), rgba(14, 165, 233, 0.16));
  color: #1e1b4b;
  box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.24);
}

.outline-item.is-matched:not(.is-selected) .outline-item__button {
  background: rgba(14, 165, 233, 0.1);
  color: #0c4a6e;
}

.outline-empty {
  padding: 14px 12px;
  color: #64748b;
  font-size: 0.84rem;
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

.document-tip {
  position: fixed;
  left: 24px;
  bottom: 24px;
  z-index: 22;
  width: min(240px, calc(100vw - 140px));
  pointer-events: none;

  p {
    margin: 4px 0 0;
    color: rgba(100, 116, 139, 0.92);
    font-size: 0.76rem;
    line-height: 1.45;
  }

  p:first-child {
    margin-top: 0;
  }
}

.document-tip__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.document-tip__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(100, 116, 139, 0.94);
  font-size: 0.72rem;
  line-height: 1.3;

  &::before {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 999px;
    background: rgba(148, 163, 184, 0.7);
  }

  &.is-current::before {
    background: #0ea5e9;
  }

  &.is-used::before {
    background: #38bdf8;
  }

  &.is-selected::before {
    background: #f59e0b;
  }

  &.is-candidate::before {
    background: #94a3b8;
  }

  &.is-citation::before {
    background: #a855f7;
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

:deep(.vue-flow__edge-text) {
  fill: #0f172a;
  font-size: 11px;
  font-weight: 700;
}

:deep(.vue-flow__edge-textbg) {
  fill: rgba(255, 255, 255, 0.92);
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

.document-task-page.is-dark .toolbar-model__label,
.document-task-page.is-dark .toolbar-model small {
  color: #94a3b8;
}

.document-task-page.is-dark .model-select-fallback {
  background: rgba(15, 23, 42, 0.9);
  box-shadow: 0 0 0 1px rgba(100, 116, 139, 0.28) inset;
  color: #e2e8f0;
}

.document-task-page.is-dark .model-select :deep(.el-input__wrapper),
.document-task-page.is-dark .model-select :deep(.el-select__wrapper),
.document-task-page.is-dark .model-select :deep(.el-input__inner) {
  background: rgba(15, 23, 42, 0.9) !important;
  color: #e2e8f0 !important;
}

.document-task-page.is-dark .model-select :deep(.el-input__wrapper),
.document-task-page.is-dark .model-select :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px rgba(100, 116, 139, 0.28) inset !important;
}

.document-task-page.is-dark .model-select :deep(.el-select__placeholder),
.document-task-page.is-dark .model-select :deep(.el-select__selected-item),
.document-task-page.is-dark .model-select :deep(.el-select__caret),
.document-task-page.is-dark .model-select :deep(.el-input__icon) {
  color: #e2e8f0 !important;
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

.document-task-page.is-dark .outline-drawer__toggle,
.document-task-page.is-dark .outline-drawer__panel {
  background: rgba(15, 23, 42, 0.86);
  border-color: rgba(100, 116, 139, 0.28);
  box-shadow: 0 20px 44px rgba(2, 6, 23, 0.28);
}

.document-task-page.is-dark .outline-drawer__toggle {
  color: #cbd5e1;
}

.document-task-page.is-dark .outline-drawer__header strong,
.document-task-page.is-dark .outline-search input {
  color: #e2e8f0;
}

.document-task-page.is-dark .outline-drawer__header span,
.document-task-page.is-dark .outline-search,
.document-task-page.is-dark .outline-empty {
  color: #94a3b8;
}

.document-task-page.is-dark .outline-drawer__close {
  background: rgba(51, 65, 85, 0.56);
  color: #cbd5e1;
}

.document-task-page.is-dark .outline-search {
  background: rgba(15, 23, 42, 0.8);
  border-color: rgba(100, 116, 139, 0.24);
}

.document-task-page.is-dark .outline-item__expand {
  background: rgba(99, 102, 241, 0.16);
  color: #a5b4fc;
}

.document-task-page.is-dark .outline-item__button {
  background: rgba(30, 41, 59, 0.72);
  color: #cbd5e1;
}

.document-task-page.is-dark .outline-item.is-selected .outline-item__button {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.22), rgba(14, 165, 233, 0.18));
  color: #e0e7ff;
  box-shadow: inset 0 0 0 1px rgba(129, 140, 248, 0.26);
}

.document-task-page.is-dark .outline-item.is-matched:not(.is-selected) .outline-item__button {
  background: rgba(14, 165, 233, 0.14);
  color: #bae6fd;
}

.document-task-page.is-dark .document-theme-toggle {
  box-shadow: 0 16px 36px rgba(14, 165, 233, 0.24);
}

.document-task-page.is-dark .document-tip {
  p {
    color: rgba(148, 163, 184, 0.88);
  }
}

.document-task-page.is-dark .document-tip__legend-item {
  color: rgba(148, 163, 184, 0.9);
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

.document-task-page.is-dark :deep(.vue-flow__edge-text) {
  fill: #e2e8f0;
}

.document-task-page.is-dark :deep(.vue-flow__edge-textbg) {
  fill: rgba(15, 23, 42, 0.9);
}

:deep(.document-theme-tooltip.el-popper) {
  background: var(--card-bg) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14) !important;
}

:global(.ai-model-select-dropdown.el-popper) {
  max-width: min(420px, calc(100vw - 24px));
}

:global(.ai-model-select-dropdown .el-select-dropdown__wrap),
:global(.ai-model-select-dropdown .el-scrollbar),
:global(.ai-model-select-dropdown .el-scrollbar__view),
:global(.ai-model-select-dropdown .el-select-dropdown__list) {
  width: 100%;
}

:global(.ai-model-select-dropdown .el-select-dropdown__list) {
  max-width: 100%;
}

:global(.ai-model-select-dropdown .el-select-dropdown__item) {
  white-space: normal;
  line-height: 1.4;
  height: auto;
  padding-top: 10px;
  padding-bottom: 10px;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown) {
  background: rgba(15, 23, 42, 0.96) !important;
  border-color: rgba(100, 116, 139, 0.32) !important;
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.4) !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-popper__arrow::before) {
  background: rgba(15, 23, 42, 0.96) !important;
  border-color: rgba(100, 116, 139, 0.32) !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__wrap),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-scrollbar),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-scrollbar__view),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__list) {
  background: transparent !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item) {
  color: #e2e8f0 !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item.hover),
:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item:hover) {
  background: rgba(59, 130, 246, 0.12) !important;
}

:global(:root[data-theme='dark'] .ai-model-select-dropdown .el-select-dropdown__item.selected) {
  color: #93c5fd !important;
  font-weight: 700;
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
    justify-content: flex-start;
  }

  .toolbar-model {
    min-width: 0;
    width: 100%;
    max-width: none;
  }

  .toolbar-btn {
    justify-content: center;
  }

  .document-theme-toggle {
    right: 16px;
    bottom: 16px;
  }

  .outline-drawer {
    display: none;
  }

  .document-tip {
    display: none;
  }
}
</style>
