<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { addCommentApi, getCommentsApi } from '@/api/article'
import EmojiPicker from '@/components/Common/EmojiPicker.vue'
import { getBrowserInfo } from '@/utils/browser'
import { formatTime } from '@/utils/time'
import type { ArticleComment, ArticleCommentPayload } from '@/types/article'
import type { PageResult } from '@/types/common'
import { unwrapResponseData } from '@/utils/response'

const props = withDefaults(defineProps<{
  articleId: string | number
  commentCount?: string | number
  articleAuthorId?: string | number
}>(), {
  commentCount: 0,
  articleAuthorId: ''
})

const emit = defineEmits<{
  commentAdded: []
  commentDeleted: []
}>()

const authStore = useAuthStore()
const siteStore = useSiteStore()

const commentInputRef = ref<HTMLElement | null>(null)
const replyInputRef = ref<HTMLElement | null>(null)
const childReplyInputRef = ref<HTMLElement | null>(null)

const commentContent = ref('')
const replyContent = ref('')
const replyingTo = ref<number | string | null>(null)
const showReplyBox = ref(false)
const activeReplyId = ref<number | string | null>(null)
const comments = ref<ArticleComment[]>([])
const total = ref(0)
const sortBy = ref<'newest' | 'hottest'>('newest')
const countdown = ref(0)
const canComment = ref(true)
const cooldownTimer = ref<ReturnType<typeof setInterval> | null>(null)
const browserInfo = ref({ name: 'Unknown', version: 'Unknown' })
const params = reactive({
  pageNum: 1,
  pageSize: 10,
  articleId: props.articleId,
  sortType: 'newest'
})

const userAvatar = computed(() => String(authStore.userInfo?.avatar || siteStore.websiteInfo.touristAvatar || siteStore.websiteInfo.authorAvatar || ''))
const userName = computed(() => String(authStore.userInfo?.nickname || '游客'))
const sortedComments = computed(() => comments.value)

/**
 * 拉取评论分页列表。
 */
async function fetchComments() {
  try {
    const response = await getCommentsApi({ ...params })
    const page = unwrapResponseData<PageResult<ArticleComment> | null>(response)
    comments.value = page?.records || []
    total.value = Number(page?.total || 0)
  } catch {
    comments.value = []
    total.value = 0
    ElMessage.error('获取评论失败')
  }
}

/**
 * 获取指定编辑器实例。
 */
function getEditorRef(refName: 'comment' | 'reply' | 'childReply') {
  if (refName === 'comment') {
    return commentInputRef.value
  }

  if (refName === 'reply') {
    return replyInputRef.value
  }

  return childReplyInputRef.value
}

/**
 * 清空编辑器内容。
 */
function clearEditor(refName: 'comment' | 'reply' | 'childReply') {
  const editor = getEditorRef(refName)
  if (editor) {
    editor.innerHTML = ''
  }
}

/**
 * 向编辑器插入 HTML 内容。
 */
function insertEditorHtml(refName: 'comment' | 'reply' | 'childReply', html: string) {
  const editor = getEditorRef(refName)
  if (!editor || !import.meta.client) {
    return
  }

  editor.focus()
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) {
    editor.insertAdjacentHTML('beforeend', html)
    return
  }

  const range = selection.getRangeAt(0)
  if (!editor.contains(range.commonAncestorContainer)) {
    editor.insertAdjacentHTML('beforeend', html)
    return
  }

  range.deleteContents()
  const temp = document.createElement('div')
  temp.innerHTML = html
  const fragment = document.createDocumentFragment()
  let node = temp.firstChild
  let lastNode: ChildNode | null = null

  while (node) {
    lastNode = fragment.appendChild(node)
    node = temp.firstChild
  }

  range.insertNode(fragment)
  if (lastNode) {
    range.setStartAfter(lastNode)
    range.collapse(true)
    selection.removeAllRanges()
    selection.addRange(range)
  }
}

/**
 * 重置评论编辑状态。
 */
function resetCommentState() {
  commentContent.value = ''
  replyContent.value = ''
  replyingTo.value = null
  showReplyBox.value = false
  activeReplyId.value = null
  clearEditor('comment')
  clearEditor('reply')
  clearEditor('childReply')
}

/**
 * 判断富文本内容是否为空。
 */
function isContentEmpty(content: string) {
  if (!content) {
    return true
  }

  if (!import.meta.client) {
    return content.trim().length === 0
  }

  const container = document.createElement('div')
  container.innerHTML = content
  const hasMedia = !!container.querySelector('img, video, audio, iframe, embed')
  const text = (container.textContent || '').replace(/\u200B/g, '').replace(/\u00A0/g, ' ').trim()

  return !hasMedia && text === ''
}

/**
 * 处理主评论输入。
 */
function handleCommentInput(event: Event) {
  commentContent.value = (event.target as HTMLElement).innerHTML
}

/**
 * 处理一级回复输入。
 */
function handleReplyInput(event: Event) {
  replyContent.value = (event.target as HTMLElement).innerHTML
}

/**
 * 处理子回复输入。
 */
function handleChildReplyInput(event: Event) {
  replyContent.value = (event.target as HTMLElement).innerHTML
}

/**
 * 启动评论冷却倒计时。
 */
function startCooldown() {
  if (cooldownTimer.value) {
    clearInterval(cooldownTimer.value)
  }

  canComment.value = false
  countdown.value = 5
  cooldownTimer.value = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      if (cooldownTimer.value) {
        clearInterval(cooldownTimer.value)
        cooldownTimer.value = null
      }
      canComment.value = true
    }
  }, 1000)
}

/**
 * 构造评论提交载荷。
 */
function buildCommentPayload(extra?: Partial<ArticleCommentPayload>) {
  return {
    articleId: props.articleId,
    content: extra?.content || '',
    browser: `${browserInfo.value.name} ${browserInfo.value.version}`,
    parentId: extra?.parentId,
    replyUserId: extra?.replyUserId
  } satisfies ArticleCommentPayload
}

/**
 * 提交主评论。
 */
async function submitComment() {
  if (isContentEmpty(commentContent.value) || !canComment.value) {
    return
  }

  if (!authStore.userInfo) {
    ElMessage.error('请先登录')
    return
  }

  try {
    await addCommentApi(buildCommentPayload({ content: commentContent.value }))
    await fetchComments()
    ElMessage.success('评论成功')
    emit('commentAdded')
    commentContent.value = ''
    clearEditor('comment')
    startCooldown()

    await nextTick()
    document.querySelector('.comment-item')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  } catch (error) {
    ElMessage.error((error as Error)?.message || '评论失败')
  }
}

/**
 * 提交一级回复。
 */
async function submitReply(comment: ArticleComment) {
  if (isContentEmpty(replyContent.value) || !canComment.value) {
    return
  }

  if (!authStore.userInfo) {
    ElMessage.error('请先登录')
    return
  }

  try {
    await addCommentApi(buildCommentPayload({
      content: replyContent.value,
      parentId: comment.id,
      replyUserId: comment.userId
    }))
    await fetchComments()
    ElMessage.success('回复成功')
    emit('commentAdded')
    replyContent.value = ''
    clearEditor('reply')
    replyingTo.value = null
    startCooldown()
  } catch (error) {
    ElMessage.error((error as Error)?.message || '回复失败')
  }
}

/**
 * 提交子评论回复。
 */
async function submitChildReply(reply: ArticleComment) {
  if (isContentEmpty(replyContent.value) || !canComment.value) {
    return
  }

  if (!authStore.userInfo) {
    ElMessage.error('请先登录')
    return
  }

  try {
    await addCommentApi(buildCommentPayload({
      content: replyContent.value,
      parentId: reply.parentId || reply.id,
      replyUserId: reply.userId
    }))
    await fetchComments()
    ElMessage.success('回复成功')
    emit('commentAdded')
    replyContent.value = ''
    clearEditor('childReply')
    cancelReply()
    startCooldown()
  } catch (error) {
    ElMessage.error((error as Error)?.message || '回复失败，请重试')
  }
}

/**
 * 打开一级评论回复框。
 */
function replyTo(comment: ArticleComment) {
  showReplyBox.value = false
  activeReplyId.value = null
  replyingTo.value = comment.id || null
  replyContent.value = ''
}

/**
 * 打开子评论回复框。
 */
function handleReplyChild(reply: ArticleComment) {
  if (!authStore.userInfo) {
    ElMessage.error('请先登录')
    return
  }

  replyingTo.value = null
  showReplyBox.value = true
  activeReplyId.value = reply.id || null
  replyContent.value = ''
}

/**
 * 取消当前回复状态。
 */
function cancelReply() {
  replyingTo.value = null
  showReplyBox.value = false
  activeReplyId.value = null
  replyContent.value = ''
  clearEditor('reply')
  clearEditor('childReply')
}

/**
 * 插入主评论表情。
 */
function insertEmoji(emojiUrl: string) {
  const imageHtml = `<img src="${emojiUrl}" class="emoji" style="width: 30px; height: 30px; vertical-align: middle;">`
  insertEditorHtml('comment', imageHtml)
  commentContent.value = commentInputRef.value?.innerHTML || ''
}

/**
 * 插入一级回复表情。
 */
function insertReplyEmoji(emojiUrl: string) {
  const imageHtml = `<img src="${emojiUrl}" class="emoji" style="width: 22px; height: 22px; vertical-align: middle;">`
  insertEditorHtml('reply', imageHtml)
  replyContent.value = replyInputRef.value?.innerHTML || ''
}

/**
 * 插入子回复表情。
 */
function insertChildReplyEmoji(emojiUrl: string) {
  const imageHtml = `<img src="${emojiUrl}" class="emoji" style="width: 22px; height: 22px; vertical-align: middle;">`
  insertEditorHtml('childReply', imageHtml)
  replyContent.value = childReplyInputRef.value?.innerHTML || ''
}

/**
 * 格式化 IP 属地文案。
 */
function formatIpSource(ipSource?: string) {
  return ipSource ? String(ipSource).split('|')[1] || '' : ''
}

/**
 * 切换评论分页。
 */
async function handlePageChange(page: number) {
  params.pageNum = page
  await fetchComments()
}

watch(
  () => props.articleId,
  async (value) => {
    params.articleId = value
    params.pageNum = 1
    resetCommentState()
    await fetchComments()
  }
)

watch(sortBy, async (value) => {
  params.sortType = value
  params.pageNum = 1
  await fetchComments()
})

onMounted(async () => {
  browserInfo.value = getBrowserInfo()
  await fetchComments()
})

onBeforeUnmount(() => {
  if (cooldownTimer.value) {
    clearInterval(cooldownTimer.value)
    cooldownTimer.value = null
  }
})
</script>

<template>
  <div class="comment-section">
    <div class="comment-editor">
      <div class="editor-content">
        <div class="avatar-container">
          <img :src="userAvatar" :alt="userName">
        </div>
        <div class="input-container">
          <div
            ref="commentInputRef"
            class="comment-input"
            contenteditable="true"
            :placeholder="`写下你的评论...`"
            @input="handleCommentInput"
          ></div>
          <div class="editor-footer">
            <div class="editor-tools">
              <EmojiPicker @select="insertEmoji" />
            </div>
            <button
              class="submit-btn"
              :disabled="isContentEmpty(commentContent) || !canComment"
              @click="submitComment"
            >
              {{ canComment ? '发表评论' : `${countdown}秒后可评论` }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="comments-list">
      <div class="list-header">
        <h3>全部评论 <span>({{ commentCount }})</span></h3>
        <div class="sort-options">
          <button class="sort-btn" :class="{ active: sortBy === 'newest' }" @click="sortBy = 'newest'">
            最新
          </button>
          <button class="sort-btn" :class="{ active: sortBy === 'hottest' }" @click="sortBy = 'hottest'">
            最热
          </button>
        </div>
      </div>

      <template v-if="sortedComments.length">
        <div
          v-for="comment in sortedComments"
          :id="`comment-${comment.id}`"
          :key="comment.id"
          class="comment-item"
        >
          <div class="comment-avatar">
            <img :src="comment.avatar" :alt="comment.nickname">
          </div>
          <div class="comment-content">
            <div class="comment-header">
              <div class="comment-info">
                <span class="nickname">{{ comment.nickname }}</span>
                <span v-if="String(comment.userId || '') === String(articleAuthorId || '')" class="author-tag">作者</span>
                <span v-if="formatIpSource(comment.ipSource)" class="ipSource">IP属地:{{ formatIpSource(comment.ipSource) }}</span>
                <span class="time">{{ formatTime(comment.createTime) }}</span>
              </div>
              <div class="comment-actions">
                <button class="action-btn" @click="replyTo(comment)">
                  <i class="far fa-comment"></i>
                  <span>回复</span>
                </button>
              </div>
            </div>
            <div class="comment-text markdown-body" v-html="comment.content"></div>

            <div v-if="comment.children?.length" class="replies-list">
              <div v-for="reply in comment.children" :key="reply.id" class="reply-item">
                <div class="reply-avatar">
                  <img :src="reply.avatar" :alt="reply.nickname">
                </div>
                <div class="reply-content">
                  <div class="reply-header">
                    <div class="reply-info">
                      <span class="nickname">{{ reply.nickname }}</span>
                      <span v-if="String(reply.userId || '') === String(articleAuthorId || '')" class="author-tag">作者</span>
                      <span class="reply-to">回复 <span class="target">@{{ reply.replyNickname }}</span></span>
                      <span v-if="formatIpSource(reply.ipSource)" class="ipSource">IP属地:{{ formatIpSource(reply.ipSource) }}</span>
                      <span class="time">{{ formatTime(reply.createTime) }}</span>
                    </div>
                    <div class="comment-actions">
                      <button class="action-btn" @click="handleReplyChild(reply)">
                        <i class="far fa-comment"></i>
                        <span>回复</span>
                      </button>
                    </div>
                  </div>
                  <div class="reply-text markdown-body" v-html="reply.content"></div>

                  <div v-if="showReplyBox && activeReplyId === reply.id" class="reply-box">
                    <div
                      ref="childReplyInputRef"
                      class="reply-input"
                      contenteditable="true"
                      :placeholder="`回复 @${reply.nickname}`"
                      @input="handleChildReplyInput"
                    ></div>
                    <div class="editor-footer">
                      <div class="editor-tools">
                        <EmojiPicker @select="insertChildReplyEmoji" />
                      </div>
                      <div class="reply-actions">
                        <button class="cancel-btn" @click="cancelReply">取消</button>
                        <button class="submit-btn" :disabled="isContentEmpty(replyContent) || !canComment" @click="submitChildReply(reply)">
                          回复
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="replyingTo === comment.id" class="reply-editor">
              <div
                ref="replyInputRef"
                class="reply-input"
                contenteditable="true"
                :placeholder="`回复 @${comment.nickname}`"
                @input="handleReplyInput"
              ></div>
              <div class="editor-footer">
                <div class="editor-tools">
                  <EmojiPicker @select="insertReplyEmoji" />
                </div>
                <div class="reply-actions">
                  <button class="cancel-btn" @click="cancelReply">取消</button>
                  <button class="submit-btn" :disabled="isContentEmpty(replyContent) || !canComment" @click="submitReply(comment)">
                    {{ canComment ? '回复' : `${countdown}秒后可评论` }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="pagination-box">
          <ElPagination
            v-if="total"
            background
            :current-page="params.pageNum"
            :page-size="params.pageSize"
            layout="prev, pager, next"
            :total="total"
            @current-change="handlePageChange"
          />
        </div>
      </template>

      <div v-else class="empty-state">
        <i class="far fa-comments"></i>
        <p>暂无评论，快来抢沙发吧~</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.comment-section {
  margin-top: $spacing-xl;
}

.comment-editor {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: $border-radius-lg;

  .editor-content {
    display: flex;
    gap: $spacing-lg;
    padding: $spacing-lg;

    .avatar-container {
      flex-shrink: 0;

      @include responsive(sm) {
        display: none;
      }

      img {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        object-fit: cover;
      }
    }
  }
}

.input-container {
  flex: 1;
  min-width: 0;
}

.comment-input,
.reply-input {
  width: 100%;
  min-height: 110px;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
  color: var(--text-primary);
  padding: $spacing-md;
  border-radius: $border-radius-md;
  font-size: 1em;
  line-height: 1.6;
  transition: all 0.3s ease;
  overflow-y: auto;

  &:focus {
    outline: none;
    border-color: $primary;
    box-shadow: 0 0 0 3px rgba($primary, 0.1);
  }

  &:empty::before {
    content: attr(placeholder);
    color: var(--text-secondary);
  }
}

.reply-input {
  min-height: 90px;
  font-size: 0.95em;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: $spacing-md;
  gap: $spacing-md;
}

.editor-tools {
  display: flex;
  gap: $spacing-xs;
}

.submit-btn,
.cancel-btn {
  padding: $spacing-sm $spacing-xl;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.submit-btn {
  background: $primary;
  color: #fff;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &:not(:disabled):hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba($primary, 0.2);
  }
}

.cancel-btn {
  background: var(--hover-bg);
  color: var(--text-secondary);
}

.comments-list {
  margin-top: $spacing-xl;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
  padding: 0 $spacing-md;

  h3 {
    color: var(--text-primary);
    font-size: 1.2em;
    font-weight: 500;

    span {
      color: var(--text-secondary);
      font-size: 0.9em;
    }
  }
}

.sort-options {
  display: flex;
  gap: $spacing-sm;
}

.sort-btn {
  padding: $spacing-xs $spacing-md;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: $border-radius-sm;

  &.active {
    color: $primary;
    background: var(--hover-bg);
  }

  &:hover:not(.active) {
    color: $primary;
  }
}

.comment-item {
  display: flex;
  gap: $spacing-lg;
  padding: $spacing-md;
  border-bottom: 1px dashed var(--border-color);

  &:last-child {
    border-bottom: none;
  }
}

.comment-avatar,
.reply-avatar {
  flex-shrink: 0;

  img {
    border-radius: 50%;
    object-fit: cover;
  }
}

.comment-avatar img {
  width: 48px;
  height: 48px;
}

.reply-avatar img {
  width: 32px;
  height: 32px;
}

.comment-content,
.reply-content {
  flex: 1;
  min-width: 0;
}

.comment-header,
.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-sm;
  gap: $spacing-sm;
}

.comment-info,
.reply-info {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.nickname {
  color: var(--text-primary);
  font-weight: 500;
}

.author-tag {
  display: inline-block;
  padding: 2px 6px;
  font-size: 12px;
  line-height: 1.2;
  color: $primary;
  background: rgba($primary, 0.1);
  border: 1px solid rgba($primary, 0.2);
  border-radius: 4px;
  margin: 0 $spacing-xs;
}

.ipSource,
.time,
.reply-to {
  color: var(--text-secondary);
  font-size: 0.9em;
}

.reply-to .target {
  color: $primary;
}

.comment-actions {
  display: flex;
  gap: $spacing-md;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-sm;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 0.9em;

  &:hover {
    color: $primary;
  }
}

.comment-text,
.reply-text {
  color: var(--text-primary);
  line-height: 1.7;

  :deep(img) {
    max-width: 200px;
    max-height: 150px;
    object-fit: cover;
    border-radius: $border-radius-sm;
    vertical-align: middle;
  }
}

.replies-list {
  margin-left: $spacing-xl;
  margin-top: $spacing-md;
  padding-left: $spacing-lg;
  border-left: 2px solid var(--border-color);
}

.reply-item {
  display: flex;
  gap: $spacing-md;
  padding: $spacing-md 0;
}

.reply-editor,
.reply-box {
  margin-top: $spacing-md;
  background: var(--hover-bg);
  border-radius: $border-radius-lg;
  padding: $spacing-md;
}

.reply-actions {
  display: flex;
  gap: $spacing-sm;
}

.reply-actions .cancel-btn {
  background: none;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);

  &:hover {
    color: $primary;
    border-color: $primary;
  }
}

.pagination-box {
  display: flex;
  justify-content: center;
  padding-top: $spacing-lg;
}

.empty-state {
  padding: $spacing-xl * 2;
  text-align: center;
  color: var(--text-secondary);

  i {
    font-size: 3em;
    margin-bottom: $spacing-md;
    color: $primary;
    opacity: 0.8;
  }

  p {
    font-size: 1.1em;
  }
}

@include responsive(md) {
  .comment-item,
  .reply-item,
  .comment-header,
  .reply-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .editor-footer {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .submit-btn,
  .cancel-btn {
    min-width: 112px;
  }

  .replies-list {
    margin-left: 0;
    padding-left: $spacing-md;
  }
}

@include responsive(sm) {
  .comment-editor .editor-content {
    padding: $spacing-md;
  }

  .editor-footer {
    gap: $spacing-sm;
  }

  .submit-btn,
  .cancel-btn {
    padding: $spacing-sm $spacing-lg;
  }

  .comments-list .comment-item {
    padding: $spacing-md;
    gap: $spacing-md;
  }

  .comments-list .comment-avatar img {
    width: 40px;
    height: 40px;
  }

  .comments-list .replies-list {
    margin-left: $spacing-lg;
    padding-left: $spacing-md;
  }
}
</style>
