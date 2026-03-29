<script setup lang="ts">
import {ElMessage} from 'element-plus'
import VueDanmaku from 'vue-danmaku'
import 'vue-danmaku/style.css'
import {addMessageApi, getMessagesApi} from '@/api/message'
import {usePageSeo} from '@/composables/useSeo'
import type {MessageItem} from '@/types/article'
import {unwrapResponseData} from '@/utils/response'

const authStore = useAuthStore()
const siteStore = useSiteStore()
const runtimeConfig = useRuntimeConfig()

const danmakuRef = ref<{ play: () => void } | null>(null)
const show = ref(false)
const content = ref('')
const count = ref<number | null>(null)
const timer = ref<ReturnType<typeof setInterval> | null>(null)
const barrageList = ref<MessageItem[]>([])

const currentUser = computed(() => authStore.userInfo)
const touristAvatar = computed(() => String(siteStore.websiteInfo.touristAvatar || siteStore.websiteInfo.authorAvatar || siteStore.websiteInfo.profileAvatar || ''))

usePageSeo({
  title: () => `留言 - ${runtimeConfig.public.siteName}`,
  description: '留言板'
})

onMounted(async () => {
  if (!siteStore.loaded) {
    await siteStore.fetchWebsiteInfo().catch(() => null)
  }

  await listMessage()
})

onBeforeUnmount(() => {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
})

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
 * 启动留言发送冷却计时。
 */
function startCooldown() {
  const timeCount = 30
  if (timer.value) {
    return
  }

  count.value = timeCount
  timer.value = setInterval(() => {
    if ((count.value || 0) > 0 && (count.value || 0) <= timeCount) {
      count.value = (count.value || 0) - 1
      return
    }

    if (timer.value) {
      clearInterval(timer.value)
      timer.value = null
    }
  }, 1000)
}

/**
 * 播放当前弹幕列表。
 */
async function playDanmaku() {
  if (!import.meta.client) {
    return
  }

  await nextTick()
  danmakuRef.value?.play()
}

/**
 * 提交一条新的留言。
 */
async function addToList() {
  if (count.value) {
    showError('30秒后才能再次留言')
    return
  }

  if (!content.value.trim()) {
    showError('留言不能为空')
    return
  }

  const message: MessageItem = {
    avatar: String(currentUser.value?.avatar || touristAvatar.value || ''),
    status: 1,
    nickname: String(currentUser.value?.nickname || '游客'),
    content: content.value.trim()
  }

  try {
    await addMessageApi(message)
    barrageList.value.push(message)
    content.value = ''
    show.value = false
    startCooldown()
    showSuccess('留言成功')
    await playDanmaku()
  } catch {
    showError('留言失败')
  }
}

/**
 * 拉取留言列表并初始化弹幕。
 */
async function listMessage() {
  try {
    const response = await getMessagesApi()
    barrageList.value = unwrapResponseData<MessageItem[] | null>(response) || []
    await playDanmaku()
  } catch {
    barrageList.value = []
    showError('获取留言列表失败')
  }
}
</script>

<template>
  <div class="messages-page">
    <div class="message-banner">
      <div class="message-container">
        <h1 class="message-title">留言板</h1>
        <div class="message-input-wrapper">
          <ElInput
            v-model="content"
            class="input"
            placeholder="说点什么吧"
            @keyup.enter="addToList"
            @focus="show = true"
          />
          <ElButton v-show="show" class="send-btn ml-3" round @click="addToList">
            发送
          </ElButton>
        </div>
      </div>

      <div class="barrage-container">
        <ClientOnly>
          <VueDanmaku
            ref="danmakuRef"
            v-model:danmus="barrageList"
            class="danmaku"
            style="height: 100%; width: 100%"
            :speeds="150"
            :channels="15"
          >
            <template #danmu="{ danmu }">
              <span class="barrage-items">
                <img
                  :src="String(danmu.avatar || touristAvatar)"
                  :alt="`${danmu.nickname || '游客'}头像`"
                  width="30"
                  height="30"
                  style="border-radius: 50%"
                >
                {{ danmu.nickname }}:{{ danmu.content }}
              </span>
            </template>
          </VueDanmaku>
        </ClientOnly>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.messages-page {
  width: 100%;
  min-height: 100vh;
  margin-top: -70px;
}

.message-input-wrapper :deep(.el-input__wrapper) {
  border-radius: 50px;
  background: rgba(255, 255, 255, 0.82) !important;
  box-shadow:
    0 18px 34px rgba(15, 23, 42, 0.14),
    inset 0 0 0 1px rgba(255, 255, 255, 0.2) !important;
  padding: 0 18px;
}

.message-input-wrapper :deep(.el-input__inner) {
  height: 48px;
  background: transparent !important;
  color: #1f2937 !important;

  &::placeholder {
    color: rgba(71, 85, 105, 0.72) !important;
  }
}

:deep(.el-button.send-btn) {
  min-width: 104px;
  height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  backdrop-filter: blur(10px);
  transition: all 0.25s ease;

  &:hover,
  &:focus {
    background: rgba(255, 255, 255, 0.88);
    color: #0f172a;
    transform: translateY(-1px);
  }
}

:global(:root[data-theme='dark'] .message-banner .message-input-wrapper .el-input__wrapper) {
  background: rgba(15, 23, 42, 0.72) !important;
  box-shadow:
    0 18px 34px rgba(2, 6, 23, 0.24),
    inset 0 0 0 1px rgba(148, 163, 184, 0.14) !important;
}

:global(:root[data-theme='dark'] .message-banner .message-input-wrapper .el-input__inner) {
  color: #e2e8f0 !important;

  &::placeholder {
    color: rgba(226, 232, 240, 0.62) !important;
  }
}

:global(:root[data-theme='dark'] .message-banner .el-button.send-btn) {
  background: rgba(148, 163, 184, 0.18);
  border-color: rgba(148, 163, 184, 0.24);
  color: #e2e8f0;

  &:hover,
  &:focus {
    background: rgba(148, 163, 184, 0.28);
    color: #ffffff;
  }
}

.message-banner {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(180deg, rgba(59, 130, 246, 0.92), rgba(14, 116, 144, 0.9));
  animation: header-effect 1s;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(15, 23, 42, 0.16), rgba(15, 23, 42, 0.32));
    pointer-events: none;
  }

  .message-container {
    position: absolute;
    width: min(560px, calc(100vw - 32px));
    top: 35%;
    left: 0;
    right: 0;
    text-align: center;
    z-index: 5;
    margin: 0 auto;
    color: #fff;

    .message-title {
      color: rgba(255, 255, 255, 0.96);
      text-shadow: 0 6px 18px rgba(15, 23, 42, 0.24);
      animation: title-scale 1s;
    }

    .message-input-wrapper {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 12px;
      height: 2.5rem;
      margin-top: 2rem;

      .input {
        flex: 1;
      }

      .ml-3 {
        animation: left-in 1s ease;

        @keyframes left-in {
          0% {
            transform: translateY(-500%);
          }

          100% {
            transform: translateX(0);
          }
        }
      }
    }
  }

  .barrage-container {
    position: absolute;
    top: 80px;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;

    .barrage-items {
      background: #000;
      border-radius: 100px;
      color: #fff;
      padding: 5px 10px 5px 5px;
      align-items: center;
      display: flex;
      gap: 8px;
      margin-top: 10px;
    }
  }
}

:global(:root[data-theme='dark'] .message-banner::before) {
  background: linear-gradient(180deg, rgba(2, 6, 23, 0.34), rgba(2, 6, 23, 0.54));
}

:global(:root[data-theme='dark'] .message-banner) {
  background: linear-gradient(180deg, #0f172a, #111827);
}

@keyframes header-effect {
  from {
    opacity: 0;
    transform: translateY(-12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes title-scale {
  from {
    opacity: 0;
    transform: scale(0.92);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media screen and (max-width: 768px) {
  .messages-page,
  .message-banner {
    min-height: 100dvh;
  }

  .message-banner {
    .message-container {
      top: 28%;

      .message-title {
        font-size: 2.2rem;
      }

      .message-input-wrapper {
        gap: 10px;
      }
    }
  }
}
</style>
