<script setup lang="ts">
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  deleteNotificationApi,
  getNotificationsApi,
  getUnreadNotificationsCountApi,
  markAllNotificationsAsReadApi,
  markNotificationAsReadApi
} from '@/api/message'
import {useNoIndexSeo} from '@/composables/useSeo'
import type {NotificationItem} from '@/types/article'
import {formatTime} from '@/utils/time'
import {unwrapResponseData} from '@/utils/response'

interface NotificationCategory {
  type: string
  name: string
  icon: string
  unread: number
}

interface NotificationCountMap {
  [key: string]: {
    num?: number
  }
}

const authStore = useAuthStore()
const siteStore = useSiteStore()
const router = useRouter()

const loading = ref(false)
const total = ref(0)
const notifications = ref<NotificationItem[]>([])
const currentCategory = ref('all')
const params = reactive({
  pageNum: 1,
  pageSize: 10,
  type: null as string | null
})
const categories = ref<NotificationCategory[]>(createDefaultCategories())

const currentCategoryName = computed(() => categories.value.find((item) => item.type === currentCategory.value)?.name || '全部消息')
const hasUnread = computed(() => notifications.value.some((item) => !item.isRead))

useNoIndexSeo({
  title: '通知',
  description: '通知中心'
})

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    await router.push('/login')
    return
  }

  await Promise.all([fetchNotifications(), getUnreadNotificationsCount()])
})

/**
 * 创建默认消息分类列表。
 */
function createDefaultCategories(): NotificationCategory[] {
  return [
    { type: 'all', name: '全部消息', icon: 'fas fa-bell', unread: 0 },
    { type: 'system', name: '系统消息', icon: 'fas fa-cog', unread: 0 },
    { type: 'comment', name: '评论消息', icon: 'fas fa-comment', unread: 0 },
    { type: 'like', name: '点赞消息', icon: 'fas fa-heart', unread: 0 },
    { type: 'favorite', name: '收藏消息', icon: 'fas fa-star', unread: 0 },
    { type: 'follow', name: '关注消息', icon: 'fas fa-user-plus', unread: 0 }
  ]
}

/**
 * 统一弹出错误提示。
 */
function showError(message: string) {
  if (import.meta.client) {
    ElMessage.error(message)
  }
}

/**
 * 统一弹出成功提示。
 */
function showSuccess(message: string) {
  if (import.meta.client) {
    ElMessage.success(message)
  }
}

/**
 * 切换消息分类并重新拉取列表。
 */
async function switchCategory(type: string) {
  currentCategory.value = type
  params.type = type === 'all' ? null : type
  params.pageNum = 1
  await fetchNotifications()
}

/**
 * 获取消息通知图标。
 */
function getNotificationIcon(type: string) {
  return categories.value.find((item) => item.type === type)?.icon || 'fas fa-bell'
}

/**
 * 跳转到通知关联的文章详情。
 */
function handleArticleClick(id?: number | string) {
  if (!id) {
    return
  }

  router.push(`/post/${id}`)
}

/**
 * 拉取通知列表。
 */
async function fetchNotifications() {
  loading.value = true
  try {
    const response = await getNotificationsApi(params)
    const page = unwrapResponseData<Record<string, unknown> | null>(response) || {}
    notifications.value = Array.isArray(page.records) ? page.records as NotificationItem[] : []
    total.value = Number(page.total || 0)
  } catch {
    notifications.value = []
    total.value = 0
    showError('获取消息通知失败')
  } finally {
    loading.value = false
  }
}

/**
 * 处理分页切换。
 */
async function handlePageChange(page: number) {
  params.pageNum = page
  await fetchNotifications()
}

/**
 * 标记当前分类下的全部通知为已读。
 */
async function markAllAsRead() {
  try {
    await markAllNotificationsAsReadApi()
    await Promise.all([fetchNotifications(), getUnreadNotificationsCount(), siteStore.fetchUnreadStatus().catch(() => null)])
    showSuccess('已将所有消息标记为已读')
  } catch {
    showError('操作失败')
  }
}

/**
 * 处理消息点击，并在需要时标记为已读。
 */
async function handleNotificationClick(notification: NotificationItem) {
  if (notification.isRead || !notification.id) {
    return
  }

  try {
    await markNotificationAsReadApi(notification.id)
    notification.isRead = true
    await Promise.all([getUnreadNotificationsCount(), siteStore.fetchUnreadStatus().catch(() => null)])
  } catch {
    showError('标记已读失败')
  }
}

/**
 * 删除指定通知。
 */
async function deleteNotification(id?: number | string) {
  if (!id) {
    return
  }

  try {
    await ElMessageBox.confirm('确定要删除这条消息吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteNotificationApi(id)
    notifications.value = notifications.value.filter((item) => item.id !== id)
    total.value = Math.max(total.value - 1, 0)

    if (!notifications.value.length && params.pageNum > 1) {
      params.pageNum -= 1
      await fetchNotifications()
    }

    await Promise.all([getUnreadNotificationsCount(), siteStore.fetchUnreadStatus().catch(() => null)])
    showSuccess('删除成功')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    showError('删除失败')
  }
}

/**
 * 拉取各分类未读数，并同步到左侧分类面板。
 */
async function getUnreadNotificationsCount() {
  try {
    const response = await getUnreadNotificationsCountApi()
    const data = unwrapResponseData<NotificationCountMap | null>(response) || {}
    let totalUnread = 0

    categories.value.forEach((category) => {
      if (category.type === 'all') {
        return
      }
      category.unread = Number(data[category.type]?.num || 0)
      totalUnread += category.unread
    })

    const allCategory = categories.value.find((item) => item.type === 'all')
    if (allCategory) {
      allCategory.unread = totalUnread
    }
  } catch {
    showError('获取未读消息数量失败')
  }
}

/**
 * 渲染通知时间。
 */
function renderTime(time?: string | number) {
  return formatTime(time)
}
</script>

<template>
  <div class="notifications-page">
    <aside class="notifications-sidebar">
      <div class="sidebar-header">
        <h2>消息分类</h2>
      </div>

      <div class="category-list">
        <button
          v-for="category in categories"
          :key="category.type"
          type="button"
          class="category-item"
          :class="{ active: currentCategory === category.type }"
          @click="switchCategory(category.type)"
        >
          <i :class="category.icon"></i>
          <span>{{ category.name }}</span>
          <ElBadge v-if="category.unread" :value="category.unread" class="category-badge" />
        </button>
      </div>
    </aside>

    <section class="notifications-main">
      <div class="notifications-header">
        <h1>{{ currentCategoryName }}</h1>
        <div class="header-actions">
          <ElButton v-if="hasUnread" text @click="markAllAsRead">
            <i class="fas fa-check-double"></i>
            全部已读
          </ElButton>
        </div>
      </div>

      <div v-loading="loading" class="notifications-content">
        <template v-if="notifications.length">
          <article
            v-for="notification in notifications"
            :key="notification.id"
            class="notification-item"
            :class="{ unread: !notification.isRead }"
            @click="handleNotificationClick(notification)"
          >
            <div class="notification-icon">
              <i :class="getNotificationIcon(String(notification.type || ''))"></i>
            </div>

            <div class="notification-body">
              <div class="notification-title">{{ notification.title }}</div>

              <div class="notification-message">
                <template v-if="notification.type === 'comment'">
                  <span v-if="notification.fromUserId">
                    {{ notification.fromNickname }} 回复了你在
                    <span class="article-title" @click.stop="handleArticleClick(notification.articleId)">{{ notification.articleTitle }}</span>
                    中的评论
                  </span>
                  <span v-else>
                    {{ notification.fromNickname }} 评论了你的文章
                  </span>
                  <div v-html="String(notification.message || '')"></div>
                </template>

                <template v-else-if="notification.type === 'like'">
                  {{ notification.fromNickname }} 点赞了你的
                  <span class="article-title" @click.stop="handleArticleClick(notification.articleId)">{{ notification.articleTitle }}</span>
                  文章
                </template>

                <template v-else-if="notification.type === 'favorite'">
                  {{ notification.fromNickname }} 收藏了你的
                  <span class="article-title" @click.stop="handleArticleClick(notification.articleId)">{{ notification.articleTitle }}</span>
                  文章
                </template>

                <template v-else-if="notification.type === 'follow'">
                  {{ notification.fromNickname }} 关注了你
                </template>

                <template v-else>
                  {{ notification.message }}
                </template>
              </div>

              <div class="notification-time">{{ renderTime(notification.createTime as string | number | undefined) }}</div>
            </div>

            <div class="notification-actions">
              <ElButton text size="small" @click.stop="deleteNotification(notification.id)">
                <i class="fas fa-trash-alt"></i>
              </ElButton>
            </div>
          </article>

          <div class="pagination-box">
            <ElPagination
              v-if="total > 0"
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
          <i class="fas fa-bell-slash"></i>
          <p>暂无{{ currentCategoryName }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.notifications-page {
  display: flex;
  gap: 20px;
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  min-height: calc(100vh - 64px);
}

.notifications-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: var(--card-bg);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.sidebar-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.1em;
  font-weight: 500;
}

.category-list {
  display: flex;
  flex-direction: column;
}

.category-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 12px 20px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background-color 0.2s ease;
  text-align: left;
}

.category-item i {
  width: 24px;
  margin-right: 12px;
  font-size: 1.1em;
}

.category-item i.fa-bell {
  color: #1677ff;
}

.category-item i.fa-cog {
  color: #52c41a;
}

.category-item i.fa-comment {
  color: #722ed1;
}

.category-item i.fa-heart {
  color: #eb2f96;
}

.category-item i.fa-star {
  color: #f59e0b;
}

.category-item i.fa-user-plus {
  color: #13c2c2;
}

.category-item span {
  flex: 1;
  font-size: 0.9em;
}

.category-item:hover {
  background: rgba($primary, 0.04);
}

.category-item.active {
  background: rgba($primary, 0.08);
  color: $primary;
}

.category-item.active i {
  color: $primary;
}

.category-badge {
  margin-left: auto;
}

.category-badge :deep(.el-badge__content) {
  height: 16px;
  padding: 0 6px;
  border: none;
  border-radius: 8px;
  background-color: $primary;
  font-size: 12px;
  font-weight: normal;
  line-height: 16px;
}

.notifications-main {
  flex: 1;
  min-width: 0;
}

.notifications-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.notifications-header h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.2em;
  font-weight: 500;
}

.header-actions :deep(.el-button) {
  color: $primary;
}

.header-actions i {
  margin-right: 4px;
}

.notifications-content {
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
}

.notification-item {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--card-bg);
  transition: background-color 0.2s ease;
}

.notification-item:hover {
  background: rgba($primary, 0.02);
}

.notification-item.unread {
  background: rgba($primary, 0.04);
}

.notification-item.unread:hover {
  background: rgba($primary, 0.06);
}

.notification-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: 16px;
  border-radius: 4px;
  background: rgba($primary, 0.1);
}

.notification-icon i {
  font-size: 1.1em;
}

.notification-icon .fa-bell {
  color: #1677ff;
}

.notification-icon .fa-cog {
  color: #52c41a;
}

.notification-icon .fa-comment {
  color: #722ed1;
}

.notification-icon .fa-heart {
  color: #eb2f96;
}

.notification-icon .fa-star {
  color: #f59e0b;
}

.notification-icon .fa-user-plus {
  color: #13c2c2;
}

.notification-body {
  flex: 1;
  min-width: 0;
}

.notification-title {
  margin-bottom: 6px;
  color: var(--text-primary);
  font-size: 1em;
  font-weight: 500;
}

.notification-message {
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 0.95em;
  line-height: 1.5;
}

.notification-message :deep(p) {
  margin: 8px 0 0;
}

.article-title {
  color: $primary;
  cursor: pointer;
}

.article-title:hover {
  text-decoration: underline;
}

.notification-time {
  margin-top: $spacing-sm;
  color: var(--text-secondary);
  font-size: 0.85em;
}

.notification-actions {
  margin-left: 12px;
  visibility: hidden;
}

.notification-actions :deep(.el-button) {
  padding: 4px;
}

.notification-actions i {
  color: var(--text-secondary);
  font-size: 0.9em;
}

.notification-actions :deep(.el-button:hover) i {
  color: #ff4d4f;
}

.notification-item:hover .notification-actions {
  visibility: visible;
}

.pagination-box {
  display: flex;
  justify-content: center;
  padding: 20px;
}

.empty-state {
  padding: 40px;
  border-radius: 8px;
  background: var(--card-bg);
  color: var(--text-secondary);
  text-align: center;
}

.empty-state i {
  margin-bottom: 12px;
  font-size: 2em;
}

.empty-state p {
  margin: 0;
  font-size: 0.95em;
}

@media screen and (max-width: 768px) {
  .notifications-page {
    flex-direction: column;
    padding: 16px;
  }

  .notifications-sidebar {
    width: 100%;
  }

  .category-item {
    padding: 10px 16px;
  }

  .category-item i {
    margin-right: 8px;
  }
}
</style>
