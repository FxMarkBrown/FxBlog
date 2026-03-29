<script setup lang="ts">
import type { NodeProps } from '@vue-flow/core'

type CanvasNodeData = {
  kind?: 'outline' | 'source-preview' | 'chat-thread'
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
  citations?: Array<{ nodeId: string; title?: string; relation?: string }>
  sending?: boolean
  error?: string
  nodeId?: string
  onToggleExpand?: (nodeId: string) => void
  onTogglePreview?: (nodeId: string) => void
  onToggleChat?: (nodeId: string) => void
  onQuestionChange?: (nodeId: string, value: string) => void
  onSubmitQuestion?: (nodeId: string) => void
}

const props = defineProps<NodeProps<CanvasNodeData>>()

const isOutline = computed(() => props.data.kind === 'outline')
const isPreview = computed(() => props.data.kind === 'source-preview')
const isChat = computed(() => props.data.kind === 'chat-thread')

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
</script>

<template>
  <div
    class="document-canvas-node"
    :class="{
      'is-selected': selected,
      'is-outline': isOutline,
      'is-preview': isPreview,
      'is-chat': isChat
    }"
  >
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
      </div>

      <div v-if="data.subtitle" class="node-card__subtitle">{{ data.subtitle }}</div>
      <div v-if="data.body" class="node-card__body">{{ data.body }}</div>

      <div v-if="isPreview && data.pageLabel" class="node-card__meta">{{ data.pageLabel }}</div>
      <div v-if="isPreview" class="source-preview-sheet">
        <div class="source-preview-sheet__paper">
          <div
            v-if="data.anchorBox && data.anchorBox.length >= 4"
            class="source-preview-sheet__bbox"
            :style="{
              left: `${Math.max(0, Math.min(100, Number(data.anchorBox[0]) * 100))}%`,
              top: `${Math.max(0, Math.min(100, Number(data.anchorBox[1]) * 100))}%`,
              width: `${Math.max(4, Math.min(100, (Number(data.anchorBox[2]) - Number(data.anchorBox[0])) * 100))}%`,
              height: `${Math.max(6, Math.min(100, (Number(data.anchorBox[3]) - Number(data.anchorBox[1])) * 100))}%`
            }"
          ></div>
        </div>
      </div>
      <div v-if="isPreview && data.markdown" class="node-preview">
        {{ data.markdown }}
      </div>
      <a v-if="isPreview && data.sourceUrl" :href="data.sourceUrl" target="_blank" rel="noopener noreferrer" class="source-link">
        <i class="fas fa-up-right-from-square"></i>
        <span>打开原文</span>
      </a>

      <div v-if="isChat && data.markdown" class="node-chat">
        {{ data.markdown }}
      </div>

      <div v-if="isChat" class="node-ask">
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
        <div v-if="data.answer" class="node-answer">
          {{ data.answer }}
        </div>
        <div v-if="data.citations?.length" class="node-citations">
          <span
            v-for="citation in data.citations"
            :key="`${citation.nodeId}-${citation.relation || 'citation'}`"
            class="node-citation"
          >
            {{ citation.title || citation.nodeId }}
          </span>
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
</template>

<style scoped lang="scss">
.document-canvas-node {
  min-width: 320px;
  max-width: 340px;
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

.is-selected .node-card {
  border-style: dashed;
  border-width: 2px;
  border-color: #4f46e5;
  box-shadow: 0 18px 34px rgba(79, 70, 229, 0.16);
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
  flex-direction: column;
  gap: 8px;

  strong {
    color: #0f172a;
    font-size: 1rem;
    line-height: 1.35;
  }
}

.node-badge {
  display: inline-flex;
  width: fit-content;
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
  white-space: pre-wrap;
  font-size: 0.88rem;
}

.node-ask {
  margin-top: 12px;
}

.node-ask__input {
  width: 100%;
  min-height: 112px;
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
  white-space: pre-wrap;
}

.node-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.node-citation {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.1);
  color: #0369a1;
  font-size: 0.76rem;
  font-weight: 600;
}

.source-preview-sheet {
  margin-top: 10px;
}

.source-preview-sheet__paper {
  position: relative;
  height: 170px;
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.98));
  border: 1px solid rgba(148, 163, 184, 0.22);
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 10px;
    border-radius: 10px;
    background:
      repeating-linear-gradient(
        180deg,
        rgba(148, 163, 184, 0.08) 0 8px,
        transparent 8px 18px
      );
  }
}

.source-preview-sheet__bbox {
  position: absolute;
  border: 2px dashed #4f46e5;
  border-radius: 8px;
  background: rgba(79, 70, 229, 0.12);
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.14);
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

:root[data-theme='dark'] .node-card {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(100, 116, 139, 0.34);
}

:root[data-theme='dark'] .node-card__heading strong {
  color: #e2e8f0;
}

:root[data-theme='dark'] .node-card__subtitle,
:root[data-theme='dark'] .node-card__body,
:root[data-theme='dark'] .node-preview,
:root[data-theme='dark'] .node-chat {
  color: #cbd5e1;
}

:root[data-theme='dark'] .node-preview,
:root[data-theme='dark'] .node-chat {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(100, 116, 139, 0.28);
}

:root[data-theme='dark'] .node-ask__input,
:root[data-theme='dark'] .node-answer {
  background: rgba(15, 23, 42, 0.82);
  border-color: rgba(100, 116, 139, 0.3);
  color: #e2e8f0;
}

:root[data-theme='dark'] .node-citation {
  background: rgba(59, 130, 246, 0.16);
  color: #93c5fd;
}

:root[data-theme='dark'] .source-preview-sheet__paper {
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.98), rgba(15, 23, 42, 0.98));
  border-color: rgba(100, 116, 139, 0.26);
}

:root[data-theme='dark'] .source-link {
  color: #818cf8;
}

:root[data-theme='dark'] .is-selected .node-card {
  border-color: #818cf8;
  box-shadow: 0 18px 34px rgba(99, 102, 241, 0.22);
}
</style>
