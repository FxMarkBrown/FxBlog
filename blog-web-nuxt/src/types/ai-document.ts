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
  maxBridgeNodes?: number
  ancestorCount?: number
  descendantCount?: number
  ancestorSiblingCount?: number
  selectedCount?: number
  semanticBridgeCount?: number
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
