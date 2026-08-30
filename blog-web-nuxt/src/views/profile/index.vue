<script setup lang="ts">
import type { FormInst, FormRules, MenuOption } from 'naive-ui'
import { marked } from 'marked'
import AvatarCropper from '@/components/Common/AvatarCropper.vue'
import { getConversationQuotaApi } from '@/api/ai'
import { delArticleApi, favoriteArticleApi, getMyArticleApi, unlikeArticleApi } from '@/api/article'
import { getDictDataApi } from '@/api/dict'
import {
  addFeedbackApi,
  delMyCommentApi,
  getMyAiQuotaLogApi,
  getMyCommentApi,
  getMyFavoriteApi,
  getMyFeedbackApi,
  getMyLikeApi,
  getMyReplyApi,
  getSignInStatsApi,
  getSignInStatusApi,
  getUserProfileApi,
  signInApi,
  updatePasswordApi,
  updateProfileApi
} from '@/api/user'
import { useNoIndexSeo } from '@/composables/useSeo'
import { dialog, message } from '@/utils/feedback'
import { unwrapResponseData } from '@/utils/response'

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- 用户信息/列表等后端 payload 字段动态，刻意保留宽松别名（全文件 25+ 处复用）
type AnyRecord = Record<string, any>

interface ProfileTabItem {
  key: string
  label: string
  icon: string
}

const authStore = useAuthStore()
const router = useRouter()

const profileFormRef = ref<FormInst | null>(null)
const passwordFormRef = ref<FormInst | null>(null)
const feedbackFormRef = ref<FormInst | null>(null)

const loading = ref(false)
const signInLoading = ref(false)
const quotaLoading = ref(false)
const showCropper = ref(false)
const currentTab = ref('profile')
const total = ref(0)
const userInfo = ref<AnyRecord>({})
const feedbackTypes = ref<AnyRecord[]>([])
const feedbackStatus = ref<AnyRecord[]>([])
const posts = ref<AnyRecord[]>([])
const myComments = ref<AnyRecord[]>([])
const myReplies = ref<AnyRecord[]>([])
const myLikes = ref<AnyRecord[]>([])
const myFavorites = ref<AnyRecord[]>([])
const quotaLogs = ref<AnyRecord[]>([])
const myFeedbacks = ref<AnyRecord[]>([])

const statistics = reactive({
  posts: 0,
  comments: 0,
  likes: 0
})

const params = reactive({
  pageNum: 1,
  pageSize: 10,
  title: ''
})

const profileForm = reactive({
  nickname: '',
  email: '',
  sex: 2,
  signature: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const feedbackForm = reactive({
  type: '',
  content: '',
  email: ''
})

const signInStatus = reactive({
  hasSignedIn: false
})

const signInStats = reactive({
  continuousDays: 0,
  totalDays: 0
})

const quotaSnapshot = ref(createEmptyQuotaSnapshot())

const tabs: ProfileTabItem[] = [
  { key: 'profile', label: '个人资料', icon: 'fas fa-user' },
  { key: 'posts', label: '我的文章', icon: 'fas fa-file-alt' },
  { key: 'comments', label: '我的评论', icon: 'fas fa-comments' },
  { key: 'replies', label: '我的回复', icon: 'fas fa-reply' },
  { key: 'likes', label: '我的点赞', icon: 'fas fa-heart' },
  { key: 'favorites', label: '我的收藏', icon: 'fas fa-star' },
  { key: 'quotaLogs', label: 'AI额度流水', icon: 'fas fa-coins' },
  { key: 'security', label: '修改密码', icon: 'fas fa-lock' },
  { key: 'feedback', label: '反馈', icon: 'fas fa-comment-dots' }
]

const menuOptions: MenuOption[] = tabs.map((tab) => ({
  key: tab.key,
  label: tab.label,
  icon: () => h('i', { class: tab.icon })
}))

/**
 * 校验确认密码与新密码是否一致。
 */
const validateConfirmPassword = (_rule: unknown, value: string) => {
  if (value !== passwordForm.newPassword) {
    return new Error('两次输入的密码不一致')
  }
  return true
}

const passwordRules = reactive<FormRules>({
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
})

const profileRules = reactive<FormRules>({
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
})

const feedbackRules = reactive<FormRules>({
  type: [{ required: true, message: '请选择反馈类型', trigger: 'blur' }],
  content: [{ required: true, message: '请输入反馈内容', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }]
})

useNoIndexSeo({
  title: '个人中心',
  description: '个人中心'
})

watch(currentTab, async (value) => {
  params.pageNum = 1
  switch (value) {
    case 'posts':
      await getMyArticle()
      break
    case 'comments':
      await getMyComment()
      break
    case 'replies':
      await getMyReplies()
      break
    case 'likes':
      await getMyLikes()
      break
    case 'favorites':
      await getMyFavorites()
      break
    case 'quotaLogs':
      await getQuotaLogs()
      break
    case 'feedback':
      await getMyFeedbacks()
      break
    default:
      break
  }
})

watch(
  () => authStore.userInfo,
  (value) => {
    if (!value && !authStore.isLoggedIn) {
      router.push('/login')
    }
  }
)

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    await router.push('/login')
    return
  }

  await Promise.all([
    loadProfileSummary(),
    getFeedbackDict(),
    getSignInStatus(),
    getSignInStats(),
    getQuotaSnapshot()
  ])
})

/**
 * 生成默认额度快照，保证侧边额度卡片稳定渲染。
 */
function createEmptyQuotaSnapshot() {
  return {
    enabled: true,
    minRequestTokens: 0,
    availableTokens: 0,
    usedTokens: 0,
    likedArticleCount: 0,
    favoriteArticleCount: 0,
    likeDailyLimit: 0,
    likeDailyPerArticleLimit: 0,
    todayLikeCount: 0,
    todayLikeRemainingCount: 0
  }
}

/**
 * 统一弹出错误提示。
 */
function showError(text: string) {
  if (import.meta.client) {
    message.error(text)
  }
}

/**
 * 统一弹出成功提示。
 */
function showSuccess(text: string) {
  if (import.meta.client) {
    message.success(text)
  }
}

/**
 * 格式化 AI 额度增减值。
 */
function formatQuotaDelta(value: unknown) {
  const amount = Number(value || 0)
  return `${amount > 0 ? '+' : ''}${formatToken(amount)}`
}

/**
 * 获取额度流水类型文案。
 */
function getQuotaLogTypeLabel(type: string) {
  const typeMap: Record<string, string> = {
    sign: '签到',
    article: '发文',
    like: '点赞',
    favorite: '收藏',
    consume: 'AI消耗',
    manual: '后台调整'
  }
  return typeMap[type] || '额度变动'
}

type TagType = 'default' | 'error' | 'info' | 'primary' | 'success' | 'warning'

/**
 * 获取额度流水标签样式。
 */
function getQuotaLogTagType(type: string): TagType {
  const typeMap: Record<string, TagType> = {
    sign: 'success',
    article: 'warning',
    like: 'error',
    favorite: 'primary',
    consume: 'info',
    manual: 'default'
  }
  return typeMap[type] || 'default'
}

/**
 * 将字典中的 EP 风格标签类型映射为 Naive UI 标签类型。
 */
function normalizeTagType(type: unknown): TagType {
  const value = String(type || '')
  if (value === 'danger') {
    return 'error'
  }
  if (value === 'primary' || value === 'success' || value === 'warning' || value === 'info') {
    return value
  }
  return 'default'
}

/**
 * 拉取个人中心摘要信息。
 */
async function loadProfileSummary() {
  try {
    const response = await getUserProfileApi()
    const payload = unwrapResponseData<AnyRecord | null>(response) || {}
    const profile =
      payload.sysUser && typeof payload.sysUser === 'object' ? payload.sysUser : payload
    userInfo.value = profile
    Object.assign(profileForm, {
      nickname: profile.nickname || '',
      email: profile.email || '',
      sex: profile.sex ?? 2,
      signature: profile.signature || ''
    })
    statistics.posts = Number(payload.articleCount || 0)
    statistics.comments = Number(payload.commentCount || 0)
    statistics.likes = Number(payload.receivedLikeCount || 0)

    if (authStore.userInfo) {
      authStore.setUserInfo({
        ...authStore.userInfo,
        nickname: String(profile.nickname || authStore.userInfo.nickname || ''),
        avatar: String(profile.avatar || authStore.userInfo.avatar || ''),
        signature: String(profile.signature || authStore.userInfo.signature || '')
      })
    }

    feedbackForm.email = profile.email || feedbackForm.email
  } catch (error) {
    showError((error as Error)?.message || '获取用户信息失败')
    await router.push('/')
  }
}

/**
 * 统一格式化 token 数量。
 */
function formatToken(value: unknown) {
  return Number(value || 0).toLocaleString('zh-CN')
}

/**
 * 拉取 AI 额度概览。
 */
async function getQuotaSnapshot() {
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
 * 重置反馈表单默认值。
 */
function getDefaultFeedbackForm() {
  return {
    type: '',
    content: '',
    email: String(userInfo.value.email || '')
  }
}

/**
 * 根据反馈类型值获取字典项。
 */
function getFeedbackType(type: string) {
  return feedbackTypes.value.find((item) => String(item.value) === String(type))
}

/**
 * 根据反馈状态值获取字典项。
 */
function getFeedbackStatus(status: string | number) {
  return feedbackStatus.value.find((item) => String(item.value) === String(status))
}

/**
 * 反馈类型标签文案。
 */
function getFeedbackTypeLabel(type: string) {
  return getFeedbackType(type)?.label || ''
}

/**
 * 反馈类型标签样式。
 */
function getFeedbackTypeStyle(type: string) {
  return getFeedbackType(type)?.style
}

/**
 * 反馈状态标签文案。
 */
function getFeedbackStatusLabel(status: string | number) {
  return getFeedbackStatus(status)?.label || ''
}

/**
 * 反馈状态标签样式。
 */
function getFeedbackStatusStyle(status: string | number) {
  return getFeedbackStatus(status)?.style
}

/**
 * 拉取反馈相关字典数据。
 */
async function getFeedbackDict() {
  const [typeResponse, statusResponse] = await Promise.all([
    getDictDataApi(['feedback_type']).catch(() => null),
    getDictDataApi(['feedback_status']).catch(() => null)
  ])

  feedbackTypes.value = Array.isArray(unwrapResponseData<AnyRecord[] | null>(typeResponse || null))
    ? unwrapResponseData<AnyRecord[] | null>(typeResponse || null) || []
    : []
  feedbackStatus.value = Array.isArray(
    unwrapResponseData<AnyRecord[] | null>(statusResponse || null)
  )
    ? unwrapResponseData<AnyRecord[] | null>(statusResponse || null) || []
    : []
}

/**
 * 拉取我的评论列表。
 */
async function getMyComment() {
  loading.value = true
  try {
    const response = await getMyCommentApi(params)
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    myComments.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 将评论 Markdown 内容转换为 HTML。
 */
function parseContent(content: string) {
  return marked.parse(content || '') as string
}

/**
 * 评论分页切换。
 */
async function handlePageChange(page: number) {
  params.pageNum = page
  await getMyComment()
}

/**
 * 拉取我的文章列表。
 */
async function getMyArticle() {
  loading.value = true
  try {
    const response = await getMyArticleApi(params)
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    posts.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 跳转文章详情页。
 */
function viewPost(id: number | string) {
  router.push(`/post/${id}`)
}

/**
 * 跳转文章编辑页。
 */
function editPost(id: number | string) {
  router.push(`/editor?id=${id}`)
}

/**
 * 删除指定文章。
 */
async function deletePost(row: AnyRecord) {
  try {
    await new Promise<void>((resolve, reject) => {
      dialog.warning({
        title: '提示',
        content: `确定要删除标题为 '${row.title}' 的文章吗？`,
        positiveText: '确定',
        negativeText: '取消',
        onPositiveClick: () => resolve(),
        onNegativeClick: () => reject(new Error('cancel')),
        onClose: () => reject(new Error('close'))
      })
    })
    await delArticleApi(row.id)
    showSuccess('删除成功')
    await Promise.all([getMyArticle(), loadProfileSummary(), getQuotaSnapshot()])
    if (currentTab.value === 'quotaLogs') {
      await getQuotaLogs()
    }
  } catch (error) {
    if (error instanceof Error && (error.message === 'cancel' || error.message === 'close')) {
      return
    }
    showError((error as Error)?.message || '删除失败')
  }
}

/**
 * 按标题搜索我的文章。
 */
async function handleSearch() {
  params.pageNum = 1
  await getMyArticle()
}

/**
 * 文章分页切换。
 */
async function handlePostChange(page: number) {
  params.pageNum = page
  await getMyArticle()
}

/**
 * 删除评论。
 */
async function deleteComment(id: number | string) {
  try {
    await new Promise<void>((resolve, reject) => {
      dialog.warning({
        title: '提示',
        content: '此操作会把该评论下的子评论也一并删除，是否继续？',
        positiveText: '确定',
        negativeText: '取消',
        onPositiveClick: () => resolve(),
        onNegativeClick: () => reject(new Error('cancel')),
        onClose: () => reject(new Error('close'))
      })
    })
    await delMyCommentApi(id)
    showSuccess('删除成功')
    await getMyComment()
  } catch (error) {
    if (error instanceof Error && (error.message === 'cancel' || error.message === 'close')) {
      return
    }
    showError((error as Error)?.message || '删除失败')
  }
}

/**
 * 删除回复。
 */
async function deleteReply(id: number | string) {
  try {
    await new Promise<void>((resolve, reject) => {
      dialog.warning({
        title: '提示',
        content: '确定要删除这条回复吗？',
        positiveText: '确定',
        negativeText: '取消',
        onPositiveClick: () => resolve(),
        onNegativeClick: () => reject(new Error('cancel')),
        onClose: () => reject(new Error('close'))
      })
    })
    await delMyCommentApi(id)
    showSuccess('删除成功')
    await getMyReplies()
  } catch (error) {
    if (error instanceof Error && (error.message === 'cancel' || error.message === 'close')) {
      return
    }
    showError((error as Error)?.message || '删除失败')
  }
}

/**
 * 取消点赞。
 */
async function cancelLike(id: number | string) {
  try {
    await new Promise<void>((resolve, reject) => {
      dialog.warning({
        title: '提示',
        content: '确定要取消点赞吗？',
        positiveText: '确定',
        negativeText: '取消',
        onPositiveClick: () => resolve(),
        onNegativeClick: () => reject(new Error('cancel')),
        onClose: () => reject(new Error('close'))
      })
    })
    await unlikeArticleApi(id)
    showSuccess('已取消点赞')
    await Promise.all([getMyLikes(), getQuotaSnapshot()])
    if (currentTab.value === 'quotaLogs') {
      await getQuotaLogs()
    }
  } catch (error) {
    if (error instanceof Error && (error.message === 'cancel' || error.message === 'close')) {
      return
    }
    showError((error as Error)?.message || '取消点赞失败')
  }
}

/**
 * 取消收藏。
 */
async function cancelFavorite(id: number | string) {
  try {
    await new Promise<void>((resolve, reject) => {
      dialog.warning({
        title: '提示',
        content: '确定要取消收藏吗？',
        positiveText: '确定',
        negativeText: '取消',
        onPositiveClick: () => resolve(),
        onNegativeClick: () => reject(new Error('cancel')),
        onClose: () => reject(new Error('close'))
      })
    })
    await favoriteArticleApi(id)
    showSuccess('已取消收藏')
    await Promise.all([getMyFavorites(), getQuotaSnapshot()])
    if (currentTab.value === 'quotaLogs') {
      await getQuotaLogs()
    }
  } catch (error) {
    if (error instanceof Error && (error.message === 'cancel' || error.message === 'close')) {
      return
    }
    showError((error as Error)?.message || '取消收藏失败')
  }
}

/**
 * 提交反馈。
 */
async function submitFeedback() {
  const valid = await feedbackFormRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await addFeedbackApi(feedbackForm)
    showSuccess('感谢您的反馈！')
    Object.assign(feedbackForm, getDefaultFeedbackForm())
    feedbackFormRef.value?.restoreValidation()
  } finally {
    loading.value = false
  }
}

/**
 * 提交密码修改。
 */
async function submitPasswordChange() {
  const valid = await passwordFormRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await updatePasswordApi(passwordForm)
    showSuccess('密码修改成功！')
    Object.assign(passwordForm, {
      oldPassword: '',
      newPassword: '',
      confirmPassword: ''
    })
    passwordFormRef.value?.restoreValidation()
  } catch (error) {
    showError((error as Error)?.message || '密码修改失败')
  } finally {
    loading.value = false
  }
}

/**
 * 提交个人资料修改。
 */
async function submitProfile() {
  const valid = await profileFormRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await updateProfileApi(profileForm)
    userInfo.value = {
      ...userInfo.value,
      nickname: profileForm.nickname,
      email: profileForm.email,
      sex: profileForm.sex,
      signature: profileForm.signature
    }
    if (authStore.userInfo) {
      authStore.setUserInfo({
        ...authStore.userInfo,
        nickname: profileForm.nickname,
        signature: profileForm.signature
      })
    }
    showSuccess('个人资料更新成功！')
  } catch (error) {
    showError((error as Error)?.message || '个人资料更新失败')
  } finally {
    loading.value = false
  }
}

/**
 * 重置个人资料表单。
 */
function resetProfile() {
  Object.assign(profileForm, {
    nickname: userInfo.value.nickname || '',
    email: userInfo.value.email || '',
    sex: userInfo.value.sex ?? 0,
    signature: userInfo.value.signature || ''
  })
  profileFormRef.value?.restoreValidation()
}

/**
 * 拉取我的回复列表。
 */
async function getMyReplies() {
  loading.value = true
  try {
    const response = await getMyReplyApi(params)
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    myReplies.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 回复分页切换。
 */
async function handleReplyPageChange(page: number) {
  params.pageNum = page
  await getMyReplies()
}

/**
 * 拉取我的点赞列表。
 */
async function getMyLikes() {
  loading.value = true
  try {
    const response = await getMyLikeApi(params)
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    myLikes.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 点赞分页切换。
 */
async function handleLikePageChange(page: number) {
  params.pageNum = page
  await getMyLikes()
}

/**
 * 拉取我的收藏列表。
 */
async function getMyFavorites() {
  loading.value = true
  try {
    const response = await getMyFavoriteApi(params)
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    myFavorites.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 拉取 AI 额度流水。
 */
async function getQuotaLogs() {
  loading.value = true
  try {
    const response = await getMyAiQuotaLogApi(params)
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    quotaLogs.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 收藏分页切换。
 */
async function handleFavoritePageChange(page: number) {
  params.pageNum = page
  await getMyFavorites()
}

/**
 * 额度流水分页切换。
 */
async function handleQuotaLogPageChange(page: number) {
  params.pageNum = page
  await getQuotaLogs()
}

/**
 * 拉取我的反馈列表。
 */
async function getMyFeedbacks() {
  loading.value = true
  try {
    const response = await getMyFeedbackApi({
      ...params,
      source: 'PC'
    })
    const page = unwrapResponseData<AnyRecord | null>(response) || {}
    myFeedbacks.value = Array.isArray(page.records) ? page.records : []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 反馈分页切换。
 */
async function handleFeedbackPageChange(page: number) {
  params.pageNum = page
  await getMyFeedbacks()
}

/**
 * 拉取今日签到状态。
 */
async function getSignInStatus() {
  const response = await getSignInStatusApi()
  Object.assign(signInStatus, unwrapResponseData<AnyRecord | null>(response) || {})
}

/**
 * 拉取签到统计。
 */
async function getSignInStats() {
  const response = await getSignInStatsApi()
  const stats = unwrapResponseData<AnyRecord | null>(response) || {}
  signInStats.continuousDays = Number(stats.continuousDays || 0)
  signInStats.totalDays = Number(stats.totalDays || 0)
}

/**
 * 执行签到并刷新相关数据。
 */
async function handleSignIn() {
  if (signInStatus.hasSignedIn) {
    return
  }

  signInLoading.value = true
  try {
    await signInApi()
    showSuccess('签到成功！')
    await Promise.all([getSignInStatus(), getSignInStats(), getQuotaSnapshot()])
    if (currentTab.value === 'quotaLogs') {
      await getQuotaLogs()
    }
  } catch (error) {
    showError((error as Error)?.message || '签到失败')
  } finally {
    signInLoading.value = false
  }
}

/**
 * 在头像裁剪完成后同步更新本地资料和全局登录态。
 */
function handleAvatarUpdate(newAvatarUrl: string) {
  userInfo.value.avatar = newAvatarUrl
  if (authStore.userInfo) {
    authStore.setUserInfo({
      ...authStore.userInfo,
      avatar: newAvatarUrl
    })
  }
}
</script>

<template>
  <div class="profile-container">
    <div class="profile-sidebar" role="complementary">
      <div class="poetize-card user-card">
        <div class="avatar-section">
          <div
            class="avatar-wrapper"
            role="button"
            tabindex="0"
            aria-label="更换头像"
            @click="showCropper = true"
          >
            <NAvatar :size="100" round :src="String(userInfo.avatar || '')" alt="用户头像" />
            <div class="upload-overlay" inert>
              <i class="fas fa-camera"></i>
            </div>
          </div>
        </div>

        <h3 class="username">{{ userInfo.nickname }}</h3>
        <p class="signature">{{ userInfo.signature || '这个人很懒，还没有写简介...' }}</p>

        <div class="sign-in-section">
          <NButton
            type="primary"
            size="small"
            :disabled="signInStatus.hasSignedIn"
            :loading="signInLoading"
            @click="handleSignIn"
          >
            <i class="fas fa-check"></i>
            {{ signInStatus.hasSignedIn ? '今日已签到' : '立即签到' }}
          </NButton>

          <div class="sign-in-stats">
            <div class="stat-item">
              <span class="label">连续签到</span>
              <span class="value">{{ signInStats.continuousDays }}天</span>
            </div>
            <div class="stat-item">
              <span class="label">累计签到</span>
              <span class="value">{{ signInStats.totalDays }}天</span>
            </div>
          </div>
        </div>

        <div class="user-stats" role="list">
          <div class="stat-item" role="listitem">
            <span class="number">{{ statistics.posts }}</span>
            <span class="label">文章</span>
          </div>
          <div class="stat-item" role="listitem">
            <span class="number">{{ statistics.comments }}</span>
            <span class="label">评论</span>
          </div>
          <div class="stat-item" role="listitem">
            <span class="number">{{ statistics.likes }}</span>
            <span class="label">获赞</span>
          </div>
        </div>

        <div v-loading="quotaLoading" class="ai-quota-card">
          <div class="ai-quota-head">
            <span>AI 额度</span>
            <span class="ai-quota-mode">{{ quotaSnapshot.enabled ? 'Token' : '关闭' }}</span>
          </div>
          <div class="ai-quota-balance">{{ formatToken(quotaSnapshot.availableTokens) }}</div>
          <div class="ai-quota-meta">
            <span>门槛 {{ formatToken(quotaSnapshot.minRequestTokens) }}</span>
            <span>已用 {{ formatToken(quotaSnapshot.usedTokens) }}</span>
          </div>
          <div class="ai-quota-grid">
            <div class="ai-quota-item">
              <strong>{{ signInStats.totalDays }}</strong>
              <span>签到</span>
            </div>
            <div class="ai-quota-item">
              <strong>{{ quotaSnapshot.likedArticleCount || 0 }}</strong>
              <span>点赞次数</span>
            </div>
            <div class="ai-quota-item">
              <strong>{{ quotaSnapshot.favoriteArticleCount || 0 }}</strong>
              <span>收藏篇数</span>
            </div>
          </div>
          <div class="ai-quota-tip">
            今日点赞 {{ quotaSnapshot.todayLikeCount || 0 }}
            <template v-if="quotaSnapshot.likeDailyLimit > 0">
              / {{ quotaSnapshot.likeDailyLimit }}，还可获得
              {{ quotaSnapshot.todayLikeRemainingCount || 0 }} 次，单篇上限
              {{ quotaSnapshot.likeDailyPerArticleLimit || 0 }}
            </template>
            <template v-else> / 不限次 </template>
          </div>
        </div>
      </div>

      <NMenu v-model:value="currentTab" class="nav-menu" :options="menuOptions" />
    </div>

    <main class="content-area" role="main">
      <div v-if="currentTab === 'profile'" class="content-section">
        <h2 class="section-title">个人资料</h2>
        <NForm
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-placement="left"
          label-align="right"
          label-width="80px"
          class="profile-form"
        >
          <NFormItem label="昵称" path="nickname">
            <NInput
              v-model:value="profileForm.nickname"
              placeholder="请输入昵称"
              aria-label="昵称输入框"
            />
          </NFormItem>
          <NFormItem label="邮箱" path="email">
            <NInput
              v-model:value="profileForm.email"
              placeholder="请输入邮箱"
              aria-label="邮箱输入框"
            />
          </NFormItem>
          <NFormItem label="个人简介">
            <NInput
              v-model:value="profileForm.signature"
              type="textarea"
              :rows="4"
              placeholder="介绍一下自己吧..."
            />
          </NFormItem>
          <NFormItem label="性别">
            <NRadioGroup v-model:value="profileForm.sex">
              <NRadio :value="1">男</NRadio>
              <NRadio :value="2">女</NRadio>
              <NRadio :value="0">保密</NRadio>
            </NRadioGroup>
          </NFormItem>
          <NFormItem>
            <NButton type="primary" size="small" :loading="loading" @click="submitProfile">
              <i class="fas fa-save"></i>
              保存修改
            </NButton>
            <NButton size="small" @click="resetProfile">
              <i class="fas fa-undo"></i>
              重置
            </NButton>
          </NFormItem>
        </NForm>
      </div>

      <div v-if="currentTab === 'posts'" class="content-section">
        <h2 class="section-title">我的文章</h2>
        <div class="action-bar">
          <div class="search-group">
            <NInput
              v-model:value="params.title"
              class="search-input"
              size="small"
              placeholder="输入文字标题搜索文章..."
            >
              <template #prefix>
                <i class="fas fa-search"></i>
              </template>
            </NInput>
            <NButton class="search-btn" type="primary" size="small" @click="handleSearch">
              <i class="fas fa-search"></i>
              搜索
            </NButton>
          </div>

          <NButton
            class="create-post-btn"
            type="primary"
            size="small"
            @click="router.push('/editor')"
          >
            <i class="fas fa-pen"></i>
            写文章
          </NButton>
        </div>

        <div v-if="posts.length" v-loading="loading">
          <div v-for="post in posts" :key="post.id" class="poetize-card post-item">
            <div class="post-content">
              <h3 class="post-title" @click="viewPost(post.id)">{{ post.title }}</h3>
              <p class="post-excerpt">{{ post.summary }}</p>
              <div class="post-meta">
                <NTag :bordered="false" size="small"
                  ><i class="far fa-calendar-alt"></i>{{ post.createTime }}</NTag
                >
                <NTag :bordered="false" size="small" type="info"
                  ><i class="far fa-eye"></i>{{ post.quantity }} 阅读</NTag
                >
                <NTag :bordered="false" size="small" type="success"
                  ><i class="far fa-comments"></i>{{ post.commentNum || 0 }} 评论</NTag
                >
                <NTag :bordered="false" size="small" type="warning"
                  ><i class="far fa-star"></i>{{ post.likeNum || 0 }} 点赞</NTag
                >
              </div>
            </div>
            <div class="post-actions">
              <NButton text @click="viewPost(post.id)"><i class="far fa-eye"></i>查看</NButton>
              <NButton text @click="editPost(post.id)"><i class="fas fa-pen"></i>编辑</NButton>
              <NButton text type="error" @click="deletePost(post)"
                ><i class="far fa-trash-alt"></i>删除</NButton
              >
            </div>
          </div>

          <div class="pagination-box">
            <NPagination
              class="pagination"
              :page="params.pageNum"
              :page-size="params.pageSize"
              :item-count="total"
              @update:page="handlePostChange"
            />
          </div>
        </div>
        <NEmpty v-else description="暂无文章，快去发布你的文章吧~~" />
      </div>

      <div v-if="currentTab === 'comments'" class="content-section">
        <h2 class="section-title">我的评论</h2>
        <div v-if="myComments.length" v-loading="loading">
          <div v-for="comment in myComments" :key="comment.id" class="poetize-card comment-item">
            <div class="comment-actions">
              <p class="comment-text" v-html="parseContent(comment.content)"></p>
              <NButton text type="error" @click="deleteComment(comment.id)"
                ><i class="far fa-trash-alt"></i>删除</NButton
              >
            </div>
            <div class="comment-meta">
              <NButton text type="primary" @click="viewPost(comment.articleId)"
                >文章：{{ comment.articleTitle }}</NButton
              >
              <NTag :bordered="false" size="small"
                ><i class="far fa-clock"></i>{{ comment.createTime }}</NTag
              >
              <NTag :bordered="false" size="small" type="success"
                ><i class="far fa-star"></i>{{ comment.likeCount || 0 }} 赞</NTag
              >
            </div>
          </div>
          <div class="pagination-box">
            <NPagination
              :page="params.pageNum"
              :page-size="params.pageSize"
              :item-count="total"
              @update:page="handlePageChange"
            />
          </div>
        </div>
        <NEmpty v-else description="暂无评论数据" />
      </div>

      <div v-if="currentTab === 'replies'" class="content-section">
        <h2 class="section-title">我的回复</h2>
        <div v-if="myReplies.length" v-loading="loading">
          <div v-for="reply in myReplies" :key="reply.id" class="poetize-card reply-item">
            <div class="reply-content">
              <div class="comment-actions">
                <div class="reply-text">
                  <NTag :bordered="false" size="small" type="info"
                    >回复 @{{ reply.replyNickname }}</NTag
                  >
                  <div v-html="parseContent(reply.content)"></div>
                </div>
                <NButton text type="error" @click="deleteReply(reply.id)"
                  ><i class="far fa-trash-alt"></i>删除</NButton
                >
              </div>
              <div class="reply-meta">
                <NButton text type="primary" @click="viewPost(reply.articleId)"
                  >文章：{{ reply.articleTitle }}</NButton
                >
                <NTag :bordered="false" size="small"
                  ><i class="far fa-clock"></i>{{ reply.createTime }}</NTag
                >
              </div>
            </div>
          </div>
          <div class="pagination-box">
            <NPagination
              :page="params.pageNum"
              :page-size="params.pageSize"
              :item-count="total"
              @update:page="handleReplyPageChange"
            />
          </div>
        </div>
        <NEmpty v-else description="暂无回复评论数据" />
      </div>

      <div v-if="currentTab === 'likes'" class="content-section">
        <h2 class="section-title">我的点赞</h2>
        <div v-if="myLikes.length" v-loading="loading">
          <div v-for="like in myLikes" :key="like.id" class="poetize-card like-item">
            <div class="like-content">
              <div class="comment-actions">
                <a class="article-title" @click="viewPost(like.id)">{{ like.title }}</a>
                <NButton text type="error" @click="cancelLike(like.id)"
                  ><i class="far fa-star"></i>取消点赞</NButton
                >
              </div>
              <div class="like-meta">
                <NTag :bordered="false" size="small"
                  ><i class="far fa-clock"></i>{{ like.createTime }}</NTag
                >
              </div>
            </div>
          </div>
          <div class="pagination-box">
            <NPagination
              :page="params.pageNum"
              :page-size="params.pageSize"
              :item-count="total"
              @update:page="handleLikePageChange"
            />
          </div>
        </div>
        <NEmpty v-else description="暂无点赞数据" />
      </div>

      <div v-if="currentTab === 'favorites'" class="content-section">
        <h2 class="section-title">我的收藏</h2>
        <div v-if="myFavorites.length" v-loading="loading">
          <div v-for="favorite in myFavorites" :key="favorite.id" class="poetize-card like-item">
            <div class="like-content">
              <div class="comment-actions">
                <a class="article-title" @click="viewPost(favorite.id)">{{ favorite.title }}</a>
                <NButton text type="error" @click="cancelFavorite(favorite.id)"
                  ><i class="far fa-star"></i>取消收藏</NButton
                >
              </div>
              <div class="like-meta">
                <NTag :bordered="false" size="small"
                  ><i class="far fa-clock"></i>{{ favorite.createTime }}</NTag
                >
              </div>
            </div>
          </div>
          <div class="pagination-box">
            <NPagination
              :page="params.pageNum"
              :page-size="params.pageSize"
              :item-count="total"
              @update:page="handleFavoritePageChange"
            />
          </div>
        </div>
        <NEmpty v-else description="暂无收藏数据" />
      </div>

      <div v-if="currentTab === 'quotaLogs'" class="content-section">
        <h2 class="section-title">AI额度流水</h2>
        <div v-if="quotaLogs.length" v-loading="loading">
          <div v-for="log in quotaLogs" :key="log.id" class="poetize-card quota-log-item">
            <div class="quota-log-main">
              <div class="quota-log-head">
                <div class="quota-log-title">
                  <NTag :bordered="false" size="small" :type="getQuotaLogTagType(log.bizType)">{{
                    getQuotaLogTypeLabel(log.bizType)
                  }}</NTag>
                  <span class="quota-log-source">{{ log.sourceTitle || 'AI 额度变动' }}</span>
                </div>
                <span
                  class="quota-log-delta"
                  :class="{
                    positive: Number(log.tokenDelta) > 0,
                    negative: Number(log.tokenDelta) < 0
                  }"
                >
                  {{ formatQuotaDelta(log.tokenDelta) }}
                </span>
              </div>
              <p class="quota-log-remark">{{ log.remark || '额度发生变动' }}</p>
              <div class="quota-log-meta">
                <NTag :bordered="false" size="small"
                  ><i class="far fa-clock"></i>{{ log.createTime }}</NTag
                >
              </div>
            </div>
          </div>
          <div class="pagination-box">
            <NPagination
              :page="params.pageNum"
              :page-size="params.pageSize"
              :item-count="total"
              @update:page="handleQuotaLogPageChange"
            />
          </div>
        </div>
        <NEmpty v-else description="暂无额度流水" />
      </div>

      <div v-if="currentTab === 'security'" class="content-section">
        <h2 class="section-title">修改密码</h2>
        <div class="binding-tips">
          <NAlert class="password-tip" title="修改密码提示" type="info">
            只有邮箱登录的才可修改密码，其他第三方登录不存在修改密码功能。
          </NAlert>
        </div>
        <NForm
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-placement="left"
          label-align="right"
          label-width="100px"
          class="security-form"
        >
          <NFormItem label="当前密码" path="oldPassword">
            <NInput
              v-model:value="passwordForm.oldPassword"
              type="password"
              show-password-on="click"
              placeholder="请输入当前密码"
            />
          </NFormItem>
          <NFormItem label="新密码" path="newPassword">
            <NInput
              v-model:value="passwordForm.newPassword"
              type="password"
              show-password-on="click"
              placeholder="请输入新密码"
            />
          </NFormItem>
          <NFormItem label="确认新密码" path="confirmPassword">
            <NInput
              v-model:value="passwordForm.confirmPassword"
              type="password"
              show-password-on="click"
              placeholder="请再次输入新密码"
            />
          </NFormItem>
          <NFormItem>
            <NButton size="small" type="primary" :loading="loading" @click="submitPasswordChange">
              <i class="fas fa-key"></i>
              确认修改
            </NButton>
          </NFormItem>
        </NForm>
      </div>

      <div v-if="currentTab === 'feedback'" class="content-section">
        <h2 class="section-title">意见反馈</h2>
        <NTabs type="line">
          <NTabPane tab="提交反馈" name="submit">
            <NForm
              ref="feedbackFormRef"
              :model="feedbackForm"
              :rules="feedbackRules"
              label-placement="left"
              label-align="right"
              label-width="100px"
              class="feedback-form"
            >
              <NFormItem label="反馈类型" path="type">
                <NSelect
                  v-model:value="feedbackForm.type"
                  :options="feedbackTypes"
                  placeholder="请选择反馈类型"
                />
              </NFormItem>
              <NFormItem label="反馈内容" path="content">
                <NInput
                  v-model:value="feedbackForm.content"
                  type="textarea"
                  :rows="5"
                  placeholder="请详细描述您的问题或建议..."
                />
              </NFormItem>
              <NFormItem label="联系邮箱" path="email">
                <NInput
                  v-model:value="feedbackForm.email"
                  placeholder="请留下您的联系邮箱，方便我们回复您"
                />
              </NFormItem>
              <NFormItem>
                <NButton type="primary" :loading="loading" @click="submitFeedback">
                  <i class="fas fa-paper-plane"></i>
                  提交反馈
                </NButton>
              </NFormItem>
            </NForm>
          </NTabPane>

          <NTabPane tab="我的反馈" name="list">
            <div class="feedback-list">
              <div v-if="myFeedbacks.length" v-loading="loading">
                <div
                  v-for="feedback in myFeedbacks"
                  :key="feedback.id"
                  class="poetize-card feedback-item"
                >
                  <div class="feedback-header">
                    <div class="feedback-info">
                      <NTag
                        v-if="getFeedbackTypeLabel(feedback.type)"
                        :bordered="false"
                        :type="normalizeTagType(getFeedbackTypeStyle(feedback.type))"
                      >
                        {{ getFeedbackTypeLabel(feedback.type) }}
                      </NTag>
                      <span class="feedback-time">
                        <i class="far fa-clock"></i>
                        {{ feedback.createTime }}
                      </span>
                    </div>
                    <NTag
                      v-if="getFeedbackStatusLabel(feedback.status)"
                      :bordered="false"
                      :type="normalizeTagType(getFeedbackStatusStyle(feedback.status))"
                    >
                      {{ getFeedbackStatusLabel(feedback.status) }}
                    </NTag>
                  </div>
                  <div class="feedback-content">
                    <p>{{ feedback.content }}</p>
                  </div>
                  <div v-if="feedback.replyContent" class="feedback-reply">
                    <div class="reply-title">
                      <i class="far fa-comment-dots"></i>
                      管理员回复：
                    </div>
                    <p class="reply-content">{{ feedback.replyContent }}</p>
                  </div>
                </div>
                <div class="pagination-box">
                  <NPagination
                    :page="params.pageNum"
                    :page-size="params.pageSize"
                    :item-count="total"
                    @update:page="handleFeedbackPageChange"
                  />
                </div>
              </div>
              <NEmpty v-else description="暂无反馈记录" />
            </div>
          </NTabPane>
        </NTabs>
      </div>
    </main>

    <AvatarCropper
      v-model:visible="showCropper"
      :user="userInfo"
      @update-avatar="handleAvatarUpdate"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.profile-container {
  display: flex;
  gap: 20px;
  min-height: 100vh;
  padding: 20px;
}

.profile-sidebar {
  position: sticky;
  top: 80px;
  width: 300px;
  height: fit-content;
  flex-shrink: 0;

  @include responsive(sm) {
    position: unset;
  }
}

.poetize-card {
  padding: 20px;
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--shadow-card);
}

.user-card {
  border: 1px solid var(--border-color);
  text-align: center;
}

.user-card .avatar-section {
  margin-bottom: 16px;
}

.user-card .avatar-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 50%;
  cursor: pointer;
}

.user-card .upload-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  opacity: 0;
  transition: opacity 0.3s;
}

.user-card .upload-overlay i {
  color: white;
  font-size: 24px;
}

.user-card .avatar-wrapper:hover .upload-overlay {
  opacity: 1;
}

.user-card .username {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.user-card .signature {
  margin: 0 0 16px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.user-card .user-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.user-card .stat-item .number {
  display: block;
  color: var(--primary-color);
  font-size: 18px;
  font-weight: 600;
}

.user-card .stat-item .label {
  color: var(--text-secondary);
  font-size: 12px;
}

.user-card .ai-quota-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid rgba($primary, 0.14);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba($primary, 0.1) 0%, rgba($primary, 0.04) 100%);
}

.user-card .ai-quota-head,
.user-card .ai-quota-meta,
.user-card .ai-quota-tip {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}

.user-card .ai-quota-balance {
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.user-card .ai-quota-mode {
  color: var(--primary-color);
}

.user-card .ai-quota-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.user-card .ai-quota-item {
  padding: 10px 8px;
  border-radius: 12px;
  background: var(--card-bg);
  text-align: center;
}

.user-card .ai-quota-item strong {
  display: block;
  color: var(--text-primary);
  font-size: 16px;
}

.user-card .ai-quota-item span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 12px;
}

.nav-menu {
  margin-top: $spacing-md;
  border-radius: 8px;
  background: var(--card-bg);
}

.nav-menu :deep(.n-menu-item-content) {
  height: 48px;
  color: var(--text-secondary);
}

.nav-menu :deep(.n-menu-item-content:hover) {
  background: var(--hover-bg);
  color: $primary;
}

.nav-menu :deep(.n-menu-item-content--selected) {
  background: var(--hover-bg);
  color: $primary;
}

.nav-menu :deep(.n-menu-item-content__icon) {
  margin-right: 12px;
}

.content-area {
  flex: 1;
  min-width: 0;
  padding: 24px;
  border-radius: 12px;
  background: var(--card-bg);
}

.section-title {
  margin: 0 0 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 600;
}

.profile-form,
.security-form,
.feedback-form {
  max-width: 600px;

  :deep(.n-button + .n-button) {
    margin-left: 12px;
  }
}

.binding-tips {
  margin-bottom: 20px;
}

.binding-tips :deep(.password-tip) {
  border: 1px solid rgba($primary, 0.2);
  border-radius: 12px;
  background: rgba($primary, 0.08);
}

.binding-tips :deep(.password-tip .n-alert-icon) {
  color: $primary;
}

.binding-tips :deep(.password-tip .n-alert-body__title) {
  color: var(--text-primary);
  font-weight: 600;
}

.binding-tips :deep(.password-tip .n-alert-body__content) {
  color: var(--text-secondary);
  line-height: 1.7;
}

.post-item {
  margin-bottom: 16px;
}

.post-item .post-content {
  margin-bottom: 16px;
}

.post-item .post-title {
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-size: 18px;
  cursor: pointer;
}

.post-item .post-title:hover {
  color: $primary;
}

.post-item .post-excerpt {
  margin: 0 0 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.post-item .post-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.post-item .post-meta .n-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.post-item .post-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.comment-item,
.reply-item,
.like-item {
  margin-bottom: 16px;
}

.comment-item .comment-actions,
.reply-item .comment-actions,
.like-item .comment-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.comment-item .article-title,
.reply-item .article-title,
.like-item .article-title {
  color: var(--text-secondary);
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    color: $primary;
  }
}

.comment-item .comment-text,
.reply-item .reply-text {
  margin: 0 0 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.comment-item .comment-text :deep(img),
.reply-item .reply-text :deep(img) {
  max-width: 200px !important;
  max-height: 200px !important;
}

.comment-item .comment-meta,
.reply-item .reply-meta,
.like-item .like-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.action-bar .search-group {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  max-width: 640px;
  min-width: 0;
}

.action-bar .search-input {
  flex: 1;
  min-width: 0;
}

.action-bar .search-btn,
.action-bar .create-post-btn {
  min-height: 32px;
}

.action-bar .create-post-btn {
  flex-shrink: 0;
}

@media (max-width: 1024px) {
  .profile-container {
    flex-direction: column;
    padding: 16px;
  }

  .profile-sidebar {
    position: static;
    width: 100%;
  }

  .content-area {
    padding: 16px;
  }

  .action-bar {
    gap: 12px;
  }

  .action-bar .search-group {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    max-width: none;
    flex: 1;
    align-items: center;
  }

  .action-bar .search-input {
    width: 100% !important;
  }

  .action-bar .search-btn {
    justify-self: start;
  }

  .post-meta,
  .post-actions,
  .comment-meta,
  .reply-meta,
  .like-meta {
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .profile-container {
    padding: 12px;
  }

  .section-title {
    margin-bottom: 16px;
    padding-bottom: 12px;
  }

  .action-bar {
    gap: 8px;
  }

  .action-bar .search-group {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    min-width: 0;
    flex: 1;
  }

  .action-bar .search-btn,
  .action-bar .create-post-btn {
    padding-inline: 12px;
    white-space: nowrap;
  }
}

.comment-item,
.reply-item,
.like-item,
.quota-log-item,
.post-item {
  border: 1px solid var(--border-color);
  transition: box-shadow 0.3s;

  &:hover {
    box-shadow: var(--shadow-card-hover);
  }
}

// 额度流水卡片适当增加内边距，避免内容显得拥挤
.quota-log-item {
  padding: 22px 24px;
}

.quota-log-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quota-log-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.quota-log-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.quota-log-source {
  color: var(--text-primary);
  font-weight: 600;
}

.quota-log-delta {
  white-space: nowrap;
  font-size: 18px;
  font-weight: 700;
}

.quota-log-delta.positive {
  color: #16a34a;
}

.quota-log-delta.negative {
  color: #dc2626;
}

.quota-log-remark {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.quota-log-meta {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.pagination-box {
  display: flex;
  justify-content: center;
  margin-top: $spacing-lg;

  // 复刻原 el-pagination.is-background 观感：圆角页码、激活主色
  :deep(.n-pagination-item) {
    color: var(--text-secondary);
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 999px;
    transition: all 0.3s ease;

    &:hover {
      color: $primary;
      border-color: $primary;
    }

    &.n-pagination-item--active {
      background: $primary;
      color: #fff;
      border-color: $primary;
      font-weight: bold;

      &:hover {
        color: #fff;
      }
    }

    &.n-pagination-item--disabled {
      cursor: not-allowed;

      &:hover {
        color: var(--text-secondary);
        border-color: var(--border-color);
      }
    }
  }
}

.feedback-item {
  margin-bottom: 16px;
}

.feedback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.feedback-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.feedback-time {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-secondary);
  font-size: 14px;
}

.feedback-content {
  margin-bottom: 16px;
  color: var(--text-primary);
  line-height: 1.6;
}

.feedback-reply {
  padding: 12px;
  border-radius: 8px;
  background: var(--hover-bg);
}

.feedback-reply .reply-title {
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-weight: 500;
}

.feedback-reply .reply-title i {
  margin-right: 4px;
}

.feedback-reply .reply-content {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
}

.sign-in-section {
  margin: 16px 0;
  padding: 16px 0;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);
}

.sign-in-stats {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}

.sign-in-stats .stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sign-in-stats .label {
  color: var(--text-secondary);
  font-size: 12px;
}

.sign-in-stats .value {
  color: var(--primary-color);
  font-size: 16px;
  font-weight: 600;
}

:global(html[data-theme='dark']) .user-card .ai-quota-card {
  border-color: rgba(96, 165, 250, 0.24);
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.24) 0%, rgba(37, 99, 235, 0.1) 100%);
}

:global(html[data-theme='dark']) .user-card .ai-quota-item {
  background: rgba(255, 255, 255, 0.04);
}
</style>
