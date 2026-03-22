<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { applyFriendApi, getFriendsApi } from '@/api/friends'
import type { FriendApplyPayload, FriendItem } from '@/types/article'
import { IMAGE_ERROR_PLACEHOLDER } from '@/utils/placeholders'
import { unwrapResponseData } from '@/utils/response'

const runtimeConfig = useRuntimeConfig()
const siteStore = useSiteStore()
const formRef = ref<FormInstance>()
const showApplyForm = ref(false)
const friends = ref<FriendItem[]>([])

const form = reactive<FriendApplyPayload>({
  name: '',
  url: '',
  info: '',
  avatar: '',
  email: ''
})

const rules: FormRules<typeof form> = {
  name: [{ required: true, message: '请输入网站名称', trigger: 'blur' }],
  url: [
    { required: true, message: '请输入网站链接', trigger: 'blur' },
    { type: 'url', message: '请输入正确的链接格式', trigger: 'blur' }
  ],
  info: [{ required: true, message: '请输入网站描述', trigger: 'blur' }],
  avatar: [
    { required: true, message: '请输入头像链接', trigger: 'blur' },
    { type: 'url', message: '请输入正确的链接格式', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入联系邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const siteLogo = computed(() => String(siteStore.websiteInfo.logo || siteStore.websiteInfo.authorAvatar || IMAGE_ERROR_PLACEHOLDER))
const siteName = computed(() => String(siteStore.websiteInfo.name || siteStore.websiteInfo.title || runtimeConfig.public.siteName || 'Open Source Blog'))
const siteSummary = computed(() => String(siteStore.websiteInfo.summary || siteStore.websiteInfo.description || '与优秀的人同行，分享技术与生活'))
const siteUrl = computed(() => String(siteStore.websiteInfo.webUrl || ''))

useSeoMeta({
  title: () => `友链 - ${runtimeConfig.public.siteName}`,
  description: '与优秀的人同行，分享技术与生活'
})

await Promise.all([
  siteStore.fetchWebsiteInfo().catch(() => null),
  fetchFriends()
])

/**
 * 重置友链申请表单。
 */
function resetForm() {
  form.name = ''
  form.url = ''
  form.info = ''
  form.avatar = ''
  form.email = ''
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
 * 标准化友链列表数据结构。
 */
function normalizeFriends(records: FriendItem[]) {
  return records.map((friend, index) => ({
    ...friend,
    id: friend.id || `friend-${index}`,
    name: String(friend.name || ''),
    url: String(friend.url || ''),
    info: String(friend.info || ''),
    avatar: String(friend.avatar || IMAGE_ERROR_PLACEHOLDER),
    online: Boolean(friend.online)
  }))
}

/**
 * 拉取友链列表。
 */
async function fetchFriends() {
  try {
    const response = await getFriendsApi()
    friends.value = normalizeFriends(unwrapResponseData<FriendItem[] | null>(response) || [])
  } catch {
    friends.value = []
    showError('获取友链列表失败')
  }
}

/**
 * 打开指定友链。
 */
function visitFriend(url?: string) {
  if (!import.meta.client || !url) {
    return
  }

  window.open(url, '_blank', 'noopener,noreferrer')
}

/**
 * 打开友链申请弹窗。
 */
function handleApply() {
  resetForm()
  showApplyForm.value = true
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

/**
 * 提交友链申请。
 */
async function submitApplication() {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  try {
    await applyFriendApi({ ...form })
    showApplyForm.value = false
    showSuccess('申请已提交，请等待审核')
    resetForm()
    formRef.value?.resetFields()
  } catch (error) {
    showError((error as Error)?.message || '申请提交失败')
  }
}

/**
 * 复制指定文本到剪贴板。
 */
async function copyText(text: string, successMessage: string) {
  if (!import.meta.client || !text) {
    return
  }

  try {
    await navigator.clipboard.writeText(text)
    showSuccess(successMessage)
  } catch {
    showError('复制失败，请手动复制')
  }
}

/**
 * 复制站点 Logo 链接。
 */
function copyLogoUrl() {
  void copyText(siteLogo.value, 'Logo链接已复制到剪贴板')
}

/**
 * 复制站点链接。
 */
function copyUrl() {
  void copyText(siteUrl.value, '链接已复制到剪贴板')
}

/**
 * 处理友链图片加载失败。
 */
function handleImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.src = IMAGE_ERROR_PLACEHOLDER
}
</script>

<template>
  <div class="friends-page">
    <ElCard class="content-card">
      <div class="page-header">
        <h1>友情链接</h1>
        <div class="header-divider">
          <span class="line"></span>
          <i class="fas fa-link"></i>
          <span class="line"></span>
        </div>
        <p>与优秀的人同行，分享技术与生活</p>

        <div class="site-info">
          <div class="site-avatar">
            <div class="avatar-wrapper">
              <ElAvatar class="avatar" :src="siteLogo" />
              <div class="copy-overlay" @click="copyLogoUrl">
                <i class="fas fa-copy"></i>
                <span>复制图片链接</span>
              </div>
            </div>
          </div>

          <div class="site-details">
            <h2>{{ siteName }}</h2>
            <p>{{ siteSummary }}</p>
            <div class="site-url">
              <i class="fas fa-link"></i>
              <input :value="siteUrl" readonly @click="copyUrl">
              <button class="copy-btn" type="button" @click="copyUrl">
                <i class="fas fa-copy"></i>
              </button>
            </div>
          </div>
        </div>

        <div class="copy-tip">
          <i class="fas fa-info-circle"></i>
          申请友链前请先添加本站链接
          <span class="tip-highlight">「 点击上方链接可快速复制 」</span>
        </div>

        <button class="apply-btn" type="button" @click="handleApply">
          <i class="fas fa-plus"></i>
          申请友链
        </button>
      </div>

      <div class="friends-container">
        <div class="section-title">
          <h2>友链列表</h2>
          <span class="count">{{ friends.length }} 个伙伴</span>
        </div>

        <div class="friends-grid">
          <div v-for="friend in friends" :key="friend.id" class="friend-card" @click="visitFriend(friend.url)">
            <div class="friend-avatar">
              <img :src="friend.avatar" :alt="friend.name" @error="handleImageError">
              <div class="status" :class="{ online: friend.online }"></div>
            </div>
            <div class="friend-info">
              <h3>{{ friend.name }}</h3>
              <p>{{ friend.info }}</p>
            </div>
          </div>
        </div>
      </div>

      <ElDialog
        v-model="showApplyForm"
        class="friend-apply-dialog"
        title="申请友链"
        width="560px"
        top="3vh"
        :append-to-body="true"
      >
        <div class="apply-form">
          <ElForm ref="formRef" size="small" :model="form" :rules="rules" label-width="100px">
            <div class="form-group">
              <ElFormItem prop="name">
                <template #label>
                  <span class="form-label">
                    <i class="fas fa-signature"></i>
                    <span>网站名称</span>
                  </span>
                </template>
                <ElInput v-model="form.name" type="text" placeholder="请输入您的网站名称" />
              </ElFormItem>
            </div>

            <div class="form-group">
              <ElFormItem prop="url">
                <template #label>
                  <span class="form-label">
                    <i class="fas fa-link"></i>
                    <span>网站链接</span>
                  </span>
                </template>
                <ElInput v-model="form.url" type="url" placeholder="请输入您的网站链接" />
              </ElFormItem>
            </div>

            <div class="form-group">
              <ElFormItem prop="info">
                <template #label>
                  <span class="form-label">
                    <i class="fas fa-quote-left"></i>
                    <span>网站描述</span>
                  </span>
                </template>
                <ElInput v-model="form.info" placeholder="一句话描述您的网站" />
              </ElFormItem>
            </div>

            <div class="form-group">
              <ElFormItem prop="avatar">
                <template #label>
                  <span class="form-label">
                    <i class="fas fa-image"></i>
                    <span>头像链接</span>
                  </span>
                </template>
                <ElInput v-model="form.avatar" type="url" placeholder="请输入您的头像链接" />
              </ElFormItem>
            </div>

            <div class="form-group">
              <ElFormItem prop="email">
                <template #label>
                  <span class="form-label">
                    <i class="fas fa-envelope"></i>
                    <span>联系邮箱</span>
                  </span>
                </template>
                <ElInput v-model="form.email" type="email" placeholder="邮箱用于联系" />
              </ElFormItem>
            </div>

            <div class="form-footer">
              <ElButton class="submit-btn" type="primary" @click="submitApplication">
                <i class="fas fa-paper-plane"></i>
                提交申请
              </ElButton>
            </div>
          </ElForm>
        </div>
      </ElDialog>
    </ElCard>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.friends-page {
  max-width: 1320px;
  margin: 0 auto;
  padding: 28px 24px;
  min-height: calc(100vh - 200px);
  animation: fade-in 0.8s ease-out;
}

.content-card {
  border: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--card-bg) 92%, transparent);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

:deep(.content-card > .el-card__body) {
  padding: 32px;
}

.page-header {
  text-align: center;
  margin-bottom: 36px;
  position: relative;

  h1 {
    font-size: 2em;
    margin-bottom: 16px;
    font-weight: 800;
    background: linear-gradient(135deg, #409eff, #7c3aed);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: 2px;
    text-shadow: 3px 3px 6px rgba(0, 0, 0, 0.1);
  }

  p {
    color: var(--text-secondary);
    font-size: 1.2em;
    margin-bottom: 24px;
    font-weight: 300;
    letter-spacing: 0.5px;
  }
}

.header-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
  opacity: 0.8;

  .line {
    width: 60px;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--text-secondary), transparent);
  }

  i {
    color: #409eff;
    font-size: 1.4em;
    transform: rotate(45deg);
    animation: pulse 2s infinite;
  }
}

.friends-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
  padding: 18px 0 8px;
}

.friend-card {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 18px;
  padding: 26px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 4px;
    background: linear-gradient(90deg, #409eff, #7c3aed);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  &:hover {
    transform: translateY(-5px) scale(1.02);
    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
    border-color: #409eff;

    &::before {
      opacity: 1;
    }

    .friend-avatar img {
      transform: scale(1.1) rotate(5deg);
    }
  }
}

.friend-avatar {
  position: relative;
  width: 60px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 50%;
  padding: 3px;

  img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
    transition: transform 0.5s ease;
    border: 3px solid var(--card-bg);
  }

  .status {
    position: absolute;
    bottom: 5px;
    right: 5px;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #9ca3af;
    border: 2px solid var(--card-bg);
    box-shadow: 0 0 0 2px var(--card-bg);

    &.online {
      background: #10b981;
      animation: pulse 2s infinite;
    }
  }
}

.friend-info {
  flex: 1;
  min-width: 0;

  h3 {
    color: var(--text-primary);
    font-size: 1.2em;
    margin-bottom: 6px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  p {
    color: var(--text-secondary);
    font-size: 0.95em;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.site-info {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 24px;
  padding: 24px;
  margin: 24px auto;
  max-width: 700px;
  display: flex;
  align-items: center;
  gap: 24px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);

    .site-avatar .avatar {
      transform: scale(1.05);
    }
  }

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(90deg, #409eff, #7c3aed);
  }
}

.site-avatar {
  width: 100px;
  height: 100px;
  flex-shrink: 0;
  position: relative;

  .avatar-wrapper {
    width: 100%;
    height: 100%;
    position: relative;
    cursor: pointer;
    border-radius: 50%;
    overflow: hidden;
  }

  .avatar {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    border: 3px solid var(--border-color);
    background: var(--card-bg);
    transition: transform 0.5s ease;
  }

  .copy-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.3s ease;
    color: white;
    border-radius: 50%;

    i {
      font-size: 1.5em;
      margin-bottom: 5px;
    }

    span {
      font-size: 0.8em;
    }
  }

  .avatar-wrapper:hover {
    .copy-overlay {
      opacity: 1;
    }

    .avatar {
      transform: scale(1.05);
    }
  }
}

.site-details {
  flex: 1;
  min-width: 0;

  h2 {
    color: var(--text-primary);
    font-size: 1.5em;
    margin-bottom: 6px;
    font-weight: 700;
    background: linear-gradient(135deg, #409eff, #7c3aed);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  p {
    color: var(--text-secondary);
    margin-bottom: 12px;
    font-size: 1em;
    line-height: 1.5;
  }
}

.site-url {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--input-bg);
  border-radius: 18px;
  border: 1px solid var(--border-color);
  transition: all 0.3s ease;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
  }

  i {
    font-size: 1em;
    color: #409eff;
  }

  input {
    background: none;
    border: none;
    color: var(--text-primary);
    font-size: 1em;
    flex: 1;
    min-width: 0;
    cursor: pointer;
    padding: 4px;

    &:focus {
      outline: none;
    }
  }

  .copy-btn {
    background: none;
    border: none;
    color: var(--text-secondary);
    cursor: pointer;
    padding: 4px 8px;
    transition: all 0.3s ease;
    border-radius: 10px;

    &:hover {
      color: #409eff;
      background: rgba(64, 158, 255, 0.1);
    }
  }
}

.copy-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-secondary);
  margin: 24px 0;
  font-size: 1em;
  padding: 12px;
  background: rgba(64, 158, 255, 0.05);
  border-radius: 18px;

  i {
    color: #409eff;
    animation: bounce 2s infinite;
  }

  .tip-highlight {
    color: #409eff;
    margin-left: 12px;
    font-weight: 500;
  }
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--border-color);
  position: relative;

  &::after {
    content: '';
    position: absolute;
    bottom: -2px;
    left: 0;
    width: 100px;
    height: 2px;
    background: linear-gradient(90deg, #409eff, #7c3aed);
  }

  h2 {
    color: var(--text-primary);
    font-size: 1.8em;
    font-weight: 700;
  }

  .count {
    color: var(--text-secondary);
    font-size: 1.2em;
    padding: 4px 12px;
    background: rgba(64, 158, 255, 0.1);
    border-radius: 18px;
    font-weight: 500;
  }
}

.apply-btn {
  padding: 12px 24px;
  background: linear-gradient(135deg, #409eff, #7c3aed);
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 4px 15px rgba(64, 158, 255, 0.3);

  i {
    font-size: 1.1em;
    transition: transform 0.3s ease;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(64, 158, 255, 0.4);

    i {
      transform: rotate(180deg);
    }
  }

  &:active {
    transform: translateY(1px);
  }
}

.apply-form {
  :deep(.el-form-item) {
    margin-bottom: 0;
    opacity: 1;
    transform: none;
  }

  :deep(.el-form-item__label) {
    font-weight: 500;
    color: var(--text-primary);
    display: flex;
    align-items: center;
  }

  :deep(.el-form-item__content) {
    min-height: 0;
  }

  :deep(.el-input__wrapper) {
    border-radius: 14px;
    border: 1px solid var(--border-color);
    background: var(--input-bg);
    color: var(--text-primary);
    box-shadow: none;
  }

  :deep(.el-input__wrapper.is-focus) {
    border-color: #409eff;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
  }

  :deep(.el-input__inner) {
    color: var(--text-primary);

    &::placeholder {
      color: var(--text-secondary);
    }
  }
}

.form-group {
  margin-bottom: 18px;
}

.form-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  line-height: 1;

  i {
    color: #409eff;
    font-size: 0.95rem;
  }
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;

  .submit-btn {
    background: linear-gradient(135deg, #409eff, #7c3aed);
    border: none;
    padding: 12px 28px;
    font-size: 1em;
    border-radius: 25px;

    i {
      margin-right: 6px;
    }

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
    }

    &:active {
      transform: translateY(1px);
    }
  }
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.4);
  }

  70% {
    box-shadow: 0 0 0 10px rgba(64, 158, 255, 0);
  }

  100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0);
  }
}

@keyframes bounce {
  0%,
  20%,
  50%,
  80%,
  100% {
    transform: translateY(0);
  }

  40% {
    transform: translateY(-5px);
  }

  60% {
    transform: translateY(-3px);
  }
}

@media (max-width: 768px) {
  .friends-page {
    padding: 12px;
  }

  :deep(.content-card > .el-card__body) {
    padding: 20px 16px;
  }

  .site-info {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }

  .site-url {
    padding: 10px 12px;
  }

  .copy-tip {
    flex-direction: column;
    gap: 8px;
  }

  .section-title {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .friends-grid {
    grid-template-columns: 1fr;
    padding: 0;
  }
}
</style>
