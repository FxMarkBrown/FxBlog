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

const visibleNodes = ref<Node<CanvasNodeData>[]>([])
const visibleEdges = ref<Edge[]>([])

const nodeTypes = {
  documentNode: DocumentCanvasNode
}

watch(
  [parseResult, expandedNodeIds, previewOpenIds, chatOpenIds, chatStateMap],
  () => {
    rebuildCanvas()
  },
  { deep: true }
)

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
    if (rootNodeId && (!previousRootId || previousRootId !== rootNodeId || !expandedNodeIds.value.size)) {
      expandedNodeIds.value = new Set([rootNodeId])
    }
    if (rootNodeId && !selectedOutlineNodeId.value) {
      selectedOutlineNodeId.value = rootNodeId
    }

    if (!silent) {
      await nextTick()
      fitCanvas()
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '文档任务加载失败')
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

function subtreeWeight(node?: DocumentTreeNode | null): number {
  if (!node) {
    return 1
  }

  const children = expandedNodeIds.value.has(node.id)
    ? (node.children || []).filter(Boolean)
    : []

  if (!children.length) {
    return 1
  }

  return children.reduce((sum, child) => sum + subtreeWeight(child), 0)
}

function layoutTreeNode(
  node: DocumentTreeNode,
  depth: number,
  startUnit: number,
  result: { nodes: Node<CanvasNodeData>[]; edges: Edge[]; centers: Map<string, { x: number; y: number }> }
) {
  const weight = subtreeWeight(node)
  const children = expandedNodeIds.value.has(node.id) ? (node.children || []) : []
  const hasChildren = children.length > 0
  const centerUnit = hasChildren
    ? startUnit + (weight - 1) / 2
    : startUnit

  const x = 120 + depth * 360
  const y = 120 + centerUnit * 220
  const position = nodePositionOverrides.value[node.id] || { x, y }

  result.centers.set(node.id, position)
  result.nodes.push({
    id: node.id,
    type: 'documentNode',
    position,
    data: {
      kind: 'outline',
      nodeId: node.id,
      title: String(node.title || '未命名节点'),
      subtitle: buildSubtitle(node),
      body: String(node.summary || ''),
      badge: buildBadge(node),
      expandable: Boolean(node.children?.length),
      expanded: expandedNodeIds.value.has(node.id),
      onToggleExpand: handleToggleExpand,
      onTogglePreview: handleTogglePreview,
      onToggleChat: handleToggleChat
    }
  })

  let childUnit = startUnit
  for (const child of children) {
    const childWeight = subtreeWeight(child)
    layoutTreeNode(child, depth + 1, childUnit, result)
    result.edges.push({
      id: `${node.id}-->${child.id}`,
      source: node.id,
      target: child.id,
      type: 'smoothstep',
      animated: false,
      style: {
        stroke: '#7c83fd',
        strokeWidth: 1.6
      }
    })
    childUnit += childWeight
  }
}

function rebuildCanvas() {
  const root = parseResult.value?.root
  if (!root) {
    visibleNodes.value = []
    visibleEdges.value = []
    return
  }

  const result = {
    nodes: [] as Node<CanvasNodeData>[],
    edges: [] as Edge[],
    centers: new Map<string, { x: number; y: number }>()
  }

  layoutTreeNode(root, 0, 0, result)

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
}

function appendAttachmentNodes(
  root: DocumentTreeNode,
  result: { nodes: Node<CanvasNodeData>[]; edges: Edge[]; centers: Map<string, { x: number; y: number }> }
) {
  const outlineNodes = flattenTree(root)
  for (const node of outlineNodes) {
    const anchor = result.centers.get(node.id)
    if (!anchor) {
      continue
    }

    if (previewOpenIds.value.has(node.id)) {
      const previewId = `${node.id}__preview`
      const sourceAnchor = node.sourceAnchors?.[0]
      result.nodes.push({
        id: previewId,
        type: 'documentNode',
        position: nodePositionOverrides.value[previewId] || { x: anchor.x + 360, y: anchor.y + 92 },
        draggable: true,
        selectable: true,
        data: {
          kind: 'source-preview',
          nodeId: node.id,
          title: `${node.title || '节点'} · 原文预览`,
          subtitle: sourceAnchor?.textSnippet || '已定位到当前节点对应的原文片段。',
          markdown: String(node.markdown || node.summary || '暂无原文预览内容'),
          pageLabel: sourceAnchor?.page ? `第 ${sourceAnchor.page} 页` : '未提供页码',
          anchorBox: normalizeAnchorBox(sourceAnchor?.bbox),
          sourceUrl: taskDetail.value?.sourceUrl || ''
        }
      })
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
      result.nodes.push({
        id: chatId,
        type: 'documentNode',
        position: nodePositionOverrides.value[chatId] || { x: anchor.x + 360, y: anchor.y - 128 },
        draggable: true,
        selectable: true,
        data: {
          kind: 'chat-thread',
          nodeId: node.id,
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
    return '根节点'
  }

  return `L${node.level || 1} · ${String(node.type || 'section')}`
}

function buildBadge(node: DocumentTreeNode) {
  if (node.type === 'document') {
    return '文档'
  }
  if (node.children?.length) {
    return '可展开'
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
  if (!wasExpanded) {
    nextTick(() => {
      fitCanvas()
    })
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
    padding: 0.16,
    includeHiddenNodes: false,
    duration: 280
  })
}

onMounted(() => {
  if (!authStore.isLoggedIn) {
    void router.push('/login')
    return
  }

  void loadTask()
})
</script>

<template>
  <section class="document-task-page">
    <header class="task-toolbar">
      <div class="task-toolbar__left">
        <button type="button" class="toolbar-btn secondary" @click="router.push('/ai/document')">
          <i class="fas fa-arrow-left"></i>
          <span>返回任务列表</span>
        </button>
        <div class="task-headline">
          <span class="task-chip">文档画布</span>
          <h1>{{ taskDetail?.title || `文档任务 #${taskId}` }}</h1>
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
  </section>
</template>

<style scoped lang="scss">
.document-task-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(79, 70, 229, 0.08), transparent 22%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.98));
}

.task-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 26px 18px;
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
  h1 {
    margin: 8px 0 6px;
    color: #0f172a;
    font-size: 1.7rem;
  }

  p {
    margin: 0;
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
  height: calc(100vh - 97px);
}

.document-flow {
  width: 100%;
  height: 100%;
  background: transparent;
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

:deep(.vue-flow__node-documentNode) {
  width: auto;
  border: none;
  background: transparent;
  box-shadow: none;
}

:deep(.vue-flow__edge-path) {
  stroke-linecap: round;
}

:root[data-theme='dark'] .document-task-page {
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.14), transparent 26%),
    linear-gradient(180deg, rgba(2, 6, 23, 0.98), rgba(15, 23, 42, 0.98));
}

:root[data-theme='dark'] .task-toolbar {
  background: rgba(2, 6, 23, 0.74);
  border-bottom-color: rgba(71, 85, 105, 0.28);
}

:root[data-theme='dark'] .task-headline h1 {
  color: #e2e8f0;
}

:root[data-theme='dark'] .task-headline p,
:root[data-theme='dark'] .toolbar-meta {
  color: #94a3b8;
}

:root[data-theme='dark'] .toolbar-btn.secondary {
  background: rgba(15, 23, 42, 0.9);
  color: #e2e8f0;
  border-color: rgba(100, 116, 139, 0.32);
}

@media (max-width: 900px) {
  .task-toolbar,
  .task-toolbar__left,
  .task-toolbar__right {
    flex-direction: column;
    align-items: flex-start;
  }

  .document-canvas-shell {
    height: calc(100vh - 188px);
  }
}
</style>
