<script setup lang="ts">
import 'md-editor-v3/lib/style.css'
import { Handle, Position, type NodeProps } from '@vue-flow/core'

const MdPreview = defineAsyncComponent(() => import('md-editor-v3').then((module) => module.MdPreview))

type CanvasNodeData = {
  kind?: 'outline' | 'source-preview' | 'chat-thread'
  highlightRole?: 'current' | 'used' | 'candidate' | 'citation' | 'selected'
  themeMode?: 'light' | 'dark'
  title?: string
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
  queryMode?: 'strict' | 'balanced' | 'explore'
  contextSummary?: string
  usedNodes?: Array<{ nodeId: string; title?: string; relation?: string }>
  candidateNodes?: Array<{ nodeId: string; title?: string; relation?: string }>
  citations?: Array<{ nodeId: string; title?: string; relation?: string }>
  selectedContextNodes?: Array<{ nodeId: string; title?: string; relation?: string }>
  showContextSocket?: boolean
  acceptContextDrop?: boolean
  sending?: boolean
  error?: string
  nodeId?: string
  onToggleExpand?: (nodeId: string) => void
  onTogglePreview?: (nodeId: string) => void
  onToggleChat?: (nodeId: string) => void
  onQuestionChange?: (nodeId: string, value: string) => void
  onSubmitQuestion?: (nodeId: string) => void
  onSelectCitation?: (nodeId: string) => void
  onQueryModeChange?: (nodeId: string, mode: 'strict' | 'balanced' | 'explore') => void
  onToggleSelectedContextNode?: (nodeId: string, targetNodeId: string) => void
}

const props = defineProps<NodeProps<CanvasNodeData>>()

const isOutline = computed(() => props.data.kind === 'outline')
const isPreview = computed(() => props.data.kind === 'source-preview')
const isChat = computed(() => props.data.kind === 'chat-thread')
const showClose = computed(() => isPreview.value || isChat.value)
const markdownTheme = computed(() => props.data.themeMode === 'dark' ? 'dark' : 'light')
const selectedContextSet = computed(() => new Set((props.data.selectedContextNodes || []).map((node) => String(node.nodeId || '')).filter(Boolean)))
const showContextSocket = computed(() => isChat.value && Boolean(props.data.showContextSocket))

function handleToggleExpand() {
  if (!props.data.nodeId || !props.data.onToggleExpand) {
    return
  }

  props.data.onToggleExpand(props.data.nodeId)
}

function handleTogglePreview() {
  if (!props.data.nodeId || !props.data.onTogglePreview) {
    return
  }

  props.data.onTogglePreview(props.data.nodeId)
}

function handleToggleChat() {
  if (!props.data.nodeId || !props.data.onToggleChat) {
    return
  }

  props.data.onToggleChat(props.data.nodeId)
}

function handleClosePanel() {
  if (isPreview.value) {
    handleTogglePreview()
    return
  }
  if (isChat.value) {
    handleToggleChat()
  }
}

function handleQuestionInput(event: Event) {
  if (!props.data.nodeId || !props.data.onQuestionChange) {
    return
  }

  const target = event.target as HTMLTextAreaElement | null
  props.data.onQuestionChange(props.data.nodeId, target?.value || '')
}

function handleSubmitQuestion() {
  if (!props.data.nodeId || !props.data.onSubmitQuestion || props.data.sending) {
    return
  }

  props.data.onSubmitQuestion(props.data.nodeId)
}

function handleCitationSelect(nodeId?: string) {
  if (!nodeId || !props.data.onSelectCitation) {
    return
  }
  props.data.onSelectCitation(nodeId)
}

function handleQueryModeChange(mode: 'strict' | 'balanced' | 'explore') {
  if (!props.data.nodeId || !props.data.onQueryModeChange) {
    return
  }
  props.data.onQueryModeChange(props.data.nodeId, mode)
}

function handleToggleSelectedContextNode(targetNodeId?: string) {
  if (!props.data.nodeId || !targetNodeId || !props.data.onToggleSelectedContextNode) {
    return
  }
  props.data.onToggleSelectedContextNode(props.data.nodeId, targetNodeId)
}

function isSelectedContextNode(nodeId?: string) {
  if (!nodeId) {
    return false
  }
  return selectedContextSet.value.has(nodeId)
}
</script>

<template>
  <div
    class="document-canvas-node"
    :class="{
      'is-selected': selected,
      'is-outline': isOutline,
      'is-preview': isPreview,
      'is-chat': isChat,
      'is-dark': data.themeMode === 'dark',
      'is-current': data.highlightRole === 'current',
      'is-used': data.highlightRole === 'used',
      'is-candidate': data.highlightRole === 'candidate',
      'is-citation': data.highlightRole === 'citation',
      'is-selected-context': data.highlightRole === 'selected'
    }"
  >
    <Handle
      v-if="isOutline && data.acceptContextDrop"
      id="context-target"
      type="target"
      :position="Position.Top"
      class="outline-drop-handle"
      :connectable="true"
      :connectable-start="false"
      :connectable-end="true"
    />

    <div class="node-shell" :class="{ 'has-context-socket': showContextSocket }">
      <div v-if="showContextSocket" class="chat-node-socket">
        <Handle
          id="context-socket-target"
          type="target"
          :position="Position.Top"
          class="chat-node-socket__handle"
          :connectable="true"
          :connectable-start="false"
          :connectable-end="true"
        />
        <Handle
          id="context-socket-source"
          type="source"
          :position="Position.Top"
          class="chat-node-socket__handle"
          :connectable="true"
          :connectable-start="true"
          :connectable-end="false"
        />
        <div class="chat-node-socket__cap" title="从这里拖出上下文箭头">
          <span class="chat-node-socket__glyph"></span>
        </div>
      </div>

      <div class="node-card">
      <div class="node-card__header">
        <div class="node-card__heading">
          <span v-if="data.badge" class="node-badge">{{ data.badge }}</span>
          <strong>{{ data.title || '未命名节点' }}</strong>
        </div>
        <button
          v-if="isOutline && data.expandable"
          type="button"
          class="node-toggle"
          :title="data.expanded ? '收起下一级' : '展开下一级'"
          @click.stop="handleToggleExpand"
        >
          <i :class="['fas', data.expanded ? 'fa-minus' : 'fa-plus']"></i>
        </button>
        <button
          v-else-if="showClose"
          type="button"
          class="node-close"
          title="关闭"
          @click.stop="handleClosePanel"
        >
          <i class="fas fa-xmark"></i>
        </button>
      </div>

      <div v-if="data.subtitle" class="node-card__subtitle">{{ data.subtitle }}</div>
      <div v-if="data.body" class="node-card__body">{{ data.body }}</div>

      <div v-if="isPreview && data.pageLabel" class="node-card__meta">{{ data.pageLabel }}</div>
      <div v-if="isPreview && data.markdown" class="node-preview markdown-preview">
        <MdPreview
          :model-value="data.markdown"
          :theme="markdownTheme"
          preview-theme="github"
          code-theme="github"
        />
      </div>
      <a v-if="isPreview && data.sourceUrl" :href="data.sourceUrl" target="_blank" rel="noopener noreferrer" class="source-link">
        <i class="fas fa-up-right-from-square"></i>
        <span>打开原文</span>
      </a>

      <div v-if="isChat && data.markdown" class="node-chat markdown-preview">
        <MdPreview
          :model-value="data.markdown"
          :theme="markdownTheme"
          preview-theme="github"
          code-theme="github"
        />
      </div>

      <div v-if="isChat" class="node-ask">
        <div class="node-query-modes">
          <button
            type="button"
            class="node-query-mode"
            :class="{ 'is-active': data.queryMode === 'strict' }"
            @click.stop="handleQueryModeChange('strict')"
          >
            严格
          </button>
          <button
            type="button"
            class="node-query-mode"
            :class="{ 'is-active': data.queryMode === 'balanced' || !data.queryMode }"
            @click.stop="handleQueryModeChange('balanced')"
          >
            平衡
          </button>
          <button
            type="button"
            class="node-query-mode"
            :class="{ 'is-active': data.queryMode === 'explore' }"
            @click.stop="handleQueryModeChange('explore')"
          >
            探索
          </button>
        </div>

        <div v-if="data.contextSummary" class="node-context-summary">{{ data.contextSummary }}</div>

        <div v-if="data.selectedContextNodes?.length" class="node-context-group">
          <div class="node-context-group__label">显式上下文</div>
          <div class="node-context-chips">
            <div
              v-for="node in data.selectedContextNodes"
              :key="`${node.nodeId}-selected`"
              class="node-context-chip"
            >
              <button
                type="button"
                class="node-citation is-selected"
                @click.stop="handleCitationSelect(node.nodeId)"
              >
                {{ node.title || node.nodeId }}
              </button>
              <button
                type="button"
                class="node-context-chip__toggle is-active"
                title="移出显式上下文"
                @click.stop="handleToggleSelectedContextNode(node.nodeId)"
              >
                <i class="fas fa-xmark"></i>
              </button>
            </div>
          </div>
        </div>

        <textarea
          class="node-ask__input"
          rows="4"
          :value="data.question || ''"
          placeholder="围绕当前节点提问，例如：这一节的核心论点是什么？"
          @input="handleQuestionInput"
        ></textarea>
        <div class="node-ask__actions">
          <button type="button" class="node-action primary" :disabled="data.sending" @click.stop="handleSubmitQuestion">
            <i :class="['fas', data.sending ? 'fa-spinner fa-spin' : 'fa-paper-plane']"></i>
            <span>{{ data.sending ? '提问中' : '发送问题' }}</span>
          </button>
        </div>
        <div v-if="data.error" class="node-ask__error">{{ data.error }}</div>
        <div v-if="data.answer" class="node-answer markdown-preview">
          <MdPreview
            :model-value="data.answer"
            :theme="markdownTheme"
            preview-theme="github"
            code-theme="github"
          />
        </div>
        <div v-if="data.citations?.length" class="node-citations">
          <div
            v-for="citation in data.citations"
            :key="`${citation.nodeId}-${citation.relation || 'citation'}`"
            class="node-context-chip"
          >
            <button
              type="button"
              class="node-citation"
              @click.stop="handleCitationSelect(citation.nodeId)"
            >
              {{ citation.title || citation.nodeId }}
            </button>
            <button
              type="button"
              class="node-context-chip__toggle"
              :class="{ 'is-active': isSelectedContextNode(citation.nodeId) }"
              :title="isSelectedContextNode(citation.nodeId) ? '移出显式上下文' : '纳入显式上下文'"
              @click.stop="handleToggleSelectedContextNode(citation.nodeId)"
            >
              <i :class="['fas', isSelectedContextNode(citation.nodeId) ? 'fa-check' : 'fa-plus']"></i>
            </button>
          </div>
        </div>

        <div v-if="data.usedNodes?.length" class="node-context-group">
          <div class="node-context-group__label">已使用上下文</div>
          <div class="node-context-chips">
            <div
              v-for="node in data.usedNodes"
              :key="`${node.nodeId}-used`"
              class="node-context-chip"
            >
              <button
                type="button"
                class="node-citation is-used"
                @click.stop="handleCitationSelect(node.nodeId)"
              >
                {{ node.title || node.nodeId }}
              </button>
              <button
                type="button"
                class="node-context-chip__toggle"
                :class="{ 'is-active': isSelectedContextNode(node.nodeId) }"
                :title="isSelectedContextNode(node.nodeId) ? '移出显式上下文' : '纳入显式上下文'"
                @click.stop="handleToggleSelectedContextNode(node.nodeId)"
              >
                <i :class="['fas', isSelectedContextNode(node.nodeId) ? 'fa-check' : 'fa-plus']"></i>
              </button>
            </div>
          </div>
        </div>

        <div v-if="data.candidateNodes?.length" class="node-context-group">
          <div class="node-context-group__label">候选节点</div>
          <div class="node-context-chips">
            <div
              v-for="node in data.candidateNodes.slice(0, 8)"
              :key="`${node.nodeId}-candidate`"
              class="node-context-chip"
            >
              <button
                type="button"
                class="node-citation is-candidate"
                @click.stop="handleCitationSelect(node.nodeId)"
              >
                {{ node.title || node.nodeId }}
              </button>
              <button
                type="button"
                class="node-context-chip__toggle"
                :class="{ 'is-active': isSelectedContextNode(node.nodeId) }"
                :title="isSelectedContextNode(node.nodeId) ? '移出显式上下文' : '纳入显式上下文'"
                @click.stop="handleToggleSelectedContextNode(node.nodeId)"
              >
                <i :class="['fas', isSelectedContextNode(node.nodeId) ? 'fa-check' : 'fa-plus']"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="isOutline && selected" class="node-actions">
        <button type="button" class="node-action ghost" @click.stop="handleTogglePreview">
          <i class="far fa-file-lines"></i>
          <span>查看原文</span>
        </button>
        <button type="button" class="node-action primary" @click.stop="handleToggleChat">
          <i class="fas fa-comment-dots"></i>
          <span>对话</span>
        </button>
      </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.document-canvas-node {
  min-width: 320px;
  max-width: 340px;
  position: relative;
}

.document-canvas-node.is-preview,
.document-canvas-node.is-chat {
  width: clamp(420px, 34vw, 620px);
  max-width: min(78vw, 760px);
}

.node-shell {
  position: relative;
  --context-socket-bg: rgba(255, 255, 255, 0.92);
  --context-socket-border: rgba(148, 163, 184, 0.22);
  --context-socket-glyph: rgba(71, 85, 105, 0.72);
  --context-socket-shadow: 0 4px 10px rgba(15, 23, 42, 0.06);
}

.node-shell.has-context-socket {
  padding-top: 9px;
}

.node-shell.has-context-socket::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 8px;
  z-index: 3;
  width: 30px;
  height: 10px;
  transform: translateX(-50%);
  background: var(--context-socket-bg);
  border-left: 1px solid var(--context-socket-border);
  border-right: 1px solid var(--context-socket-border);
}

.chat-node-socket {
  position: absolute;
  left: 50%;
  top: 0;
  z-index: 4;
  width: 48px;
  height: 15px;
  transform: translate(-50%, 1px);
}

.chat-node-socket__handle,
.outline-drop-handle {
  opacity: 0;
  border: none;
  background: transparent;
}

.chat-node-socket__handle {
  width: 100% !important;
  height: 100% !important;
  top: 0 !important;
  left: 0 !important;
  transform: none !important;
}

.outline-drop-handle {
  width: 100% !important;
  height: 100% !important;
  top: 0 !important;
  left: 0 !important;
  transform: none !important;
  z-index: 1;
}

.chat-node-socket__cap {
  position: absolute;
  inset: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--context-socket-border);
  border-bottom: none;
  border-radius: 12px 12px 0 0;
  background: var(--context-socket-bg);
  box-shadow: var(--context-socket-shadow);
  pointer-events: none;
}

.chat-node-socket__cap::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -1px;
  width: 26px;
  height: 2px;
  transform: translateX(-50%);
  background: var(--context-socket-bg);
}

.chat-node-socket__glyph {
  width: 14px;
  height: 4px;
  border-radius: 999px;
  background: var(--context-socket-glyph);
}

.document-canvas-node.is-chat .node-shell {
  --context-socket-bg: rgba(240, 249, 255, 0.96);
  --context-socket-border: rgba(14, 165, 233, 0.16);
  --context-socket-glyph: rgba(14, 165, 233, 0.6);
  --context-socket-shadow: 0 4px 10px rgba(14, 165, 233, 0.08);
}

.node-card {
  padding: 14px 14px 12px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(12px);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.document-canvas-node.is-preview .node-card,
.document-canvas-node.is-chat .node-card {
  display: flex;
  flex-direction: column;
  min-height: 420px;
  height: min(68vh, 760px);
  resize: both;
  overflow: hidden;
}

.is-selected .node-card {
  border-style: dashed;
  border-width: 2px;
  border-color: #4f46e5;
  box-shadow: 0 18px 34px rgba(79, 70, 229, 0.16);
}

.is-current:not(.is-selected) .node-card {
  border-color: rgba(14, 165, 233, 0.55);
  box-shadow: 0 18px 34px rgba(14, 165, 233, 0.14);
}

.is-used:not(.is-selected):not(.is-current) .node-card {
  border-color: rgba(56, 189, 248, 0.44);
  box-shadow: 0 16px 30px rgba(56, 189, 248, 0.1);
}

.is-candidate:not(.is-selected):not(.is-current):not(.is-used) .node-card {
  border-color: rgba(148, 163, 184, 0.36);
  box-shadow: 0 14px 28px rgba(148, 163, 184, 0.08);
}

.is-citation:not(.is-selected):not(.is-current) .node-card {
  border-color: rgba(168, 85, 247, 0.44);
  box-shadow: 0 16px 30px rgba(168, 85, 247, 0.1);
}

.is-selected-context:not(.is-selected):not(.is-current) .node-card {
  border-color: rgba(245, 158, 11, 0.52);
  box-shadow: 0 18px 32px rgba(245, 158, 11, 0.14);
}

.is-preview .node-card {
  background: rgba(248, 250, 252, 0.96);
}

.is-chat .node-card {
  background: rgba(240, 249, 255, 0.96);
}

.node-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.node-card__heading {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  min-width: 0;

  strong {
    min-width: 0;
    color: #0f172a;
    font-size: 1rem;
    line-height: 1.35;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.node-badge {
  display: inline-flex;
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
  font-size: 0.74rem;
  font-weight: 700;
}

.node-toggle {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
  cursor: pointer;
}

.node-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.12);
  color: #475569;
  cursor: pointer;
}

.node-card__subtitle {
  margin-top: 8px;
  color: #475569;
  font-size: 0.84rem;
}

.node-card__body {
  margin-top: 10px;
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
  font-size: 0.9rem;
}

.node-card__meta {
  margin-top: 10px;
  color: #6366f1;
  font-size: 0.78rem;
  font-weight: 600;
}

.node-preview,
.node-chat {
  margin-top: 10px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.2);
  color: #334155;
  line-height: 1.75;
  font-size: 0.88rem;
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  white-space: normal;
  scrollbar-gutter: stable;
}

.node-ask {
  margin-top: 12px;
}

.node-query-modes {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.node-query-mode {
  border: none;
  border-radius: 999px;
  padding: 7px 11px;
  background: rgba(148, 163, 184, 0.12);
  color: #475569;
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;

  &.is-active {
    background: rgba(79, 70, 229, 0.12);
    color: #4338ca;
  }
}

.node-context-summary {
  margin-bottom: 10px;
  color: #64748b;
  font-size: 0.78rem;
  line-height: 1.5;
}

.node-ask__input {
  width: 100%;
  min-height: 112px;
  margin-top: 12px;
  padding: 12px 13px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.26);
  background: rgba(255, 255, 255, 0.88);
  color: #0f172a;
  resize: vertical;
  outline: none;
  font: inherit;
  line-height: 1.65;

  &:focus {
    border-color: rgba(14, 165, 233, 0.55);
    box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.12);
  }
}

.node-ask__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.node-ask__error {
  margin-top: 10px;
  color: #dc2626;
  font-size: 0.82rem;
}

.node-answer {
  margin-top: 12px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(14, 165, 233, 0.16);
  color: #1e293b;
  line-height: 1.75;
  max-height: 260px;
  overflow: auto;
  white-space: normal;
}

.node-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.node-context-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.node-context-group {
  margin-top: 10px;
}

.node-context-group__label {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 700;
  line-height: 1.35;
}

.node-citation {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border: none;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.1);
  color: #0369a1;
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;

  &.is-used {
    background: rgba(56, 189, 248, 0.12);
    color: #0f766e;
  }

  &.is-candidate {
    background: rgba(148, 163, 184, 0.12);
    color: #475569;
  }

  &.is-selected {
    background: rgba(245, 158, 11, 0.14);
    color: #b45309;
  }
}

.node-context-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-width: 0;
}

.node-context-chip__toggle {
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  border: none;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  color: #64748b;
  cursor: pointer;

  &.is-active {
    background: rgba(245, 158, 11, 0.16);
    color: #b45309;
  }
}

.node-context-group + .node-context-group {
  margin-top: 12px;
}

.source-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
  color: #4f46e5;
  text-decoration: none;
  font-size: 0.84rem;
  font-weight: 600;
}

.node-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
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

.markdown-preview :deep(.md-editor-toolbar),
.markdown-preview :deep(.md-editor-toolbar-wrapper),
.markdown-preview :deep(.md-editor-footer) {
  display: none !important;
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
  color: inherit;
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
  display: block;
  max-width: 100%;
  overflow-x: auto;
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

.node-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 12px;
  border-radius: 12px;
  border: none;
  cursor: pointer;
  font-size: 0.84rem;
  font-weight: 600;

  &.ghost {
    background: rgba(148, 163, 184, 0.12);
    color: #334155;
  }

  &.primary {
    background: linear-gradient(135deg, #4f46e5, #0ea5e9);
    color: #fff;
  }
}

.document-canvas-node.is-dark .node-card {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(100, 116, 139, 0.34);
}

.document-canvas-node.is-dark.is-preview .node-card {
  background: rgba(15, 23, 42, 0.94);
}

.document-canvas-node.is-dark.is-chat .node-card {
  background: rgba(8, 47, 73, 0.92);
}

.document-canvas-node.is-dark .node-card__heading strong {
  color: #e2e8f0;
}

.document-canvas-node.is-dark .node-badge {
  background: rgba(16, 185, 129, 0.18);
  color: #6ee7b7;
}

.document-canvas-node.is-dark .node-toggle {
  background: rgba(99, 102, 241, 0.18);
  color: #a5b4fc;
}

.document-canvas-node.is-dark .node-close {
  background: rgba(51, 65, 85, 0.56);
  color: #cbd5e1;
}

.document-canvas-node.is-dark .node-card__subtitle,
.document-canvas-node.is-dark .node-card__body,
.document-canvas-node.is-dark .node-preview,
.document-canvas-node.is-dark .node-chat {
  color: #cbd5e1;
}

.document-canvas-node.is-dark .node-card__meta {
  color: #818cf8;
}

.document-canvas-node.is-dark .node-preview,
.document-canvas-node.is-dark .node-chat {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(100, 116, 139, 0.28);
}

.document-canvas-node.is-dark .node-ask__input,
.document-canvas-node.is-dark .node-answer {
  background: rgba(15, 23, 42, 0.82);
  border-color: rgba(100, 116, 139, 0.3);
  color: #e2e8f0;
}

.document-canvas-node.is-dark .node-ask__input::placeholder {
  color: #64748b;
}

.document-canvas-node.is-dark .node-query-mode {
  background: rgba(51, 65, 85, 0.56);
  color: #cbd5e1;

  &.is-active {
    background: rgba(99, 102, 241, 0.24);
    color: #c7d2fe;
  }
}

.document-canvas-node.is-dark .node-context-summary,
.document-canvas-node.is-dark .node-context-group__label {
  color: #94a3b8;
}

.document-canvas-node.is-dark .chat-node-socket__cap {
  box-shadow: var(--context-socket-shadow);
}

.document-canvas-node.is-dark.is-chat .node-shell {
  --context-socket-bg: rgba(8, 47, 73, 0.92);
  --context-socket-border: rgba(100, 116, 139, 0.28);
  --context-socket-glyph: rgba(125, 211, 252, 0.8);
  --context-socket-shadow: 0 6px 14px rgba(2, 6, 23, 0.18);
}

.document-canvas-node.is-dark .node-citation {
  background: rgba(59, 130, 246, 0.16);
  color: #93c5fd;
}

.document-canvas-node.is-dark .node-citation.is-used {
  background: rgba(34, 197, 94, 0.18);
  color: #86efac;
}

.document-canvas-node.is-dark .node-citation.is-candidate {
  background: rgba(71, 85, 105, 0.52);
  color: #cbd5e1;
}

.document-canvas-node.is-dark .node-citation.is-selected {
  background: rgba(180, 83, 9, 0.32);
  color: #fcd34d;
}

.document-canvas-node.is-dark .node-context-chip__toggle {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;

  &.is-active {
    background: rgba(180, 83, 9, 0.3);
    color: #fcd34d;
  }
}

.document-canvas-node.is-dark .source-link {
  color: #818cf8;
}

.document-canvas-node.is-dark .node-action.ghost {
  background: rgba(51, 65, 85, 0.48);
  color: #cbd5e1;
}

.document-canvas-node.is-dark.is-selected .node-card {
  border-color: #818cf8;
  box-shadow: 0 18px 34px rgba(99, 102, 241, 0.22);
}

@media (max-width: 900px) {
  .document-canvas-node {
    min-width: min(272px, calc(100vw - 56px));
    max-width: min(300px, calc(100vw - 56px));
  }

  .document-canvas-node.is-preview,
  .document-canvas-node.is-chat {
    width: min(calc(100vw - 40px), 360px);
    max-width: min(calc(100vw - 40px), 360px);
  }

  .document-canvas-node.is-preview .node-card,
  .document-canvas-node.is-chat .node-card {
    min-height: 360px;
    height: min(62vh, 560px);
  }

  .node-card {
    padding: 12px 12px 10px;
    border-radius: 16px;
  }

  .node-card__heading {
    gap: 6px;
  }

  .node-card__heading strong {
    font-size: 0.94rem;
  }

  .node-preview,
  .node-chat,
  .node-answer {
    padding: 10px;
    border-radius: 12px;
  }

  .node-action {
    padding: 9px 11px;
  }
}

@media (max-width: 420px) {
  .document-canvas-node {
    min-width: min(252px, calc(100vw - 36px));
    max-width: min(280px, calc(100vw - 36px));
  }

  .document-canvas-node.is-preview,
  .document-canvas-node.is-chat {
    width: min(calc(100vw - 24px), 332px);
    max-width: min(calc(100vw - 24px), 332px);
  }

  .node-actions {
    flex-wrap: wrap;
  }
}
</style>
