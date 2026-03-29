export interface DocumentSourceAnchor {
  page?: number
  bbox?: number[]
  textSnippet?: string
}

export interface DocumentTreeNode {
  id: string
  parentId?: string | null
  type?: string
  title?: string
  level?: number
  summary?: string
  markdown?: string
  expandable?: boolean
  children?: DocumentTreeNode[]
  sourceAnchors?: DocumentSourceAnchor[]
}

export interface DocumentTaskListItem {
  taskId: number
  title?: string
  sourceFileId?: string
  status?: string
  fileName?: string
  pageCount?: number
  parsed?: boolean
  expireAt?: string
  createTime?: string
  updateTime?: string
}

export interface DocumentTaskDetail {
  taskId: number
  title?: string
  sourceFileId?: string
  status?: string
  remoteTaskId?: string
  fileName?: string
  sourceUrl?: string
  markdownUrl?: string
  pageCount?: number
  rootNodeId?: string
  expireAt?: string
  createTime?: string
  updateTime?: string
}

export interface DocumentParseResult {
  taskId: number
  title?: string
  markdown?: string
  root?: DocumentTreeNode | null
}

export interface DocumentNodeCitation {
  displayLabel?: string
  nodeId: string
  title?: string
  level?: number
  type?: string
  relation?: string
}

export interface DocumentContextNode {
  nodeId: string
  title?: string
  level?: number
  type?: string
  relation?: string
  weight?: number
  reason?: string
  summary?: string
  page?: number
}

export interface DocumentKnowledgeFlowEdge {
  displayLabel?: string
  fromNodeId: string
  toNodeId: string
  edgeType?: string
  weight?: number
  reason?: string
}

export interface DocumentContextPlan {
  queryMode?: string
  currentNodeId?: string
  descendantDepth?: number
  maxRetrievedNodes?: number
  ancestorCount?: number
  descendantCount?: number
  peerContextCount?: number
  selectedCount?: number
  retrievedCount?: number
  totalCandidateCount?: number
  totalUsedCount?: number
}

export interface DocumentContextBudget {
  maxChars?: number
  candidateChars?: number
  usedChars?: number
  remainingChars?: number
  truncatedNodeCount?: number
}

export interface DocumentNodeAnswer {
  taskId: number
  threadId?: number
  nodeId: string
  question: string
  answer?: string
  modelId?: string
  contextNodeIds?: string[]
  citations?: DocumentNodeCitation[]
  contextPlan?: DocumentContextPlan
  budgetReport?: DocumentContextBudget
  usedNodes?: DocumentContextNode[]
  candidateNodes?: DocumentContextNode[]
  knowledgeFlowEdges?: DocumentKnowledgeFlowEdge[]
}

export interface DocumentNodeThread {
  threadId: number
  taskId: number
  nodeId: string
  title?: string
  summary?: string
  modelProvider?: string
  modelName?: string
  modelId?: string
  modelDisplayName?: string
  lastMessageAt?: string
  createTime?: string
  updateTime?: string
}

export interface DocumentNodeMessage {
  id: number
  threadId: number
  role: string
  content: string
  modelId?: string
  tokensIn?: number
  tokensOut?: number
  quotePayload?: string
  selectedNodeIds?: string[]
  citations?: DocumentNodeCitation[]
  contextPlan?: DocumentContextPlan
  budgetReport?: DocumentContextBudget
  usedNodes?: DocumentContextNode[]
  candidateNodes?: DocumentContextNode[]
  knowledgeFlowEdges?: DocumentKnowledgeFlowEdge[]
  createTime?: string
}
