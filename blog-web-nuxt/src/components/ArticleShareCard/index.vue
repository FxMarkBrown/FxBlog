<script setup lang="ts">
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import type { ArticleDetail } from '@/types/article'

const CARD_WIDTH = 900
const CARD_HEIGHT = 1320
const CARD_RADIUS = 36
const COVER_HEIGHT = 500

const props = withDefaults(defineProps<{
  modelValue: boolean
  article?: ArticleDetail | null
  url?: string
  siteName?: string
}>(), {
  modelValue: false,
  article: null,
  url: '',
  siteName: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const generating = ref(false)
const downloading = ref(false)
const cardImage = ref('')

/**
 * 处理弹窗打开事件。
 */
async function handleOpen() {
  if (generating.value) {
    return
  }

  await generateCard()
}

/**
 * 处理弹窗关闭事件。
 */
function handleClose() {
  emit('update:modelValue', false)
}

/**
 * 重新生成分享卡片。
 */
async function generateCard() {
  generating.value = true
  try {
    cardImage.value = await renderCardToDataUrl()
  } catch {
    ElMessage.error('卡片生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

/**
 * 生成分享卡片的 Data URL。
 */
async function renderCardToDataUrl() {
  if (!import.meta.client) {
    return ''
  }

  const canvas = document.createElement('canvas')
  canvas.width = CARD_WIDTH
  canvas.height = CARD_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return ''
  }

  drawBackground(ctx)
  drawCardShadow(ctx)
  drawRoundedRect(ctx, 72, 96, CARD_WIDTH - 144, CARD_HEIGHT - 196, CARD_RADIUS, '#fffdf5')

  const avatar = await loadImage(props.article?.avatar)
  const cover = await loadImage(props.article?.cover)
  const qrImage = await createQrImage()

  const contentX = 118
  const contentWidth = CARD_WIDTH - 236

  if (avatar) {
    drawCircularImage(ctx, avatar, contentX, 144, 64)
  } else {
    drawAvatarFallback(ctx, contentX, 144, 64)
  }

  ctx.fillStyle = '#111827'
  ctx.font = '600 34px sans-serif'
  ctx.fillText(props.article?.nickname || props.siteName || 'Blog', contentX + 92, 186)

  ctx.fillStyle = '#6b7280'
  ctx.font = '500 28px sans-serif'
  ctx.fillText(formatDate(props.article?.createTime), contentX, 258)

  ctx.fillStyle = '#111827'
  ctx.font = '700 52px sans-serif'
  drawMultilineText(ctx, props.article?.title || props.siteName || '文章分享', contentX, 346, contentWidth, 72, 2)

  const coverTop = 468
  if (cover) {
    drawRoundedImage(ctx, cover, contentX, coverTop, contentWidth, COVER_HEIGHT, 18)
  } else {
    drawCoverFallback(ctx, contentX, coverTop, contentWidth, COVER_HEIGHT)
  }

  ctx.strokeStyle = 'rgba(17, 24, 39, 0.08)'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(contentX, 1042)
  ctx.lineTo(contentX + contentWidth, 1042)
  ctx.stroke()

  ctx.fillStyle = '#111827'
  ctx.font = '600 34px sans-serif'
  ctx.fillText(getBottomLabel(), contentX, 1114)

  ctx.fillStyle = '#6b7280'
  ctx.font = '500 24px sans-serif'
  drawMultilineText(ctx, props.url || window.location.href, contentX, 1162, contentWidth - 148, 34, 2)

  if (qrImage) {
    const qrBoxX = contentX + contentWidth - 132
    const qrBoxY = 1064
    const qrBoxSize = 96
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(qrBoxX - 8, qrBoxY - 8, qrBoxSize + 16, qrBoxSize + 16)
    ctx.drawImage(qrImage, qrBoxX, qrBoxY, qrBoxSize, qrBoxSize)
  }

  return canvas.toDataURL('image/png', 1)
}

/**
 * 生成二维码图片对象。
 */
async function createQrImage() {
  if (!import.meta.client) {
    return null
  }

  const qrDataUrl = await QRCode.toDataURL(props.url || window.location.href, {
    margin: 1,
    width: 180,
    color: {
      dark: '#111827',
      light: '#ffffff'
    }
  }).catch(() => '')

  return loadImage(qrDataUrl)
}

/**
 * 绘制卡片整体背景。
 */
function drawBackground(ctx: CanvasRenderingContext2D) {
  const gradient = ctx.createLinearGradient(0, 0, CARD_WIDTH, CARD_HEIGHT)
  gradient.addColorStop(0, '#fef9c3')
  gradient.addColorStop(0.5, '#fff7d6')
  gradient.addColorStop(1, '#fde68a')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT)
}

/**
 * 绘制卡片投影。
 */
function drawCardShadow(ctx: CanvasRenderingContext2D) {
  ctx.save()
  ctx.shadowColor = 'rgba(217, 119, 6, 0.18)'
  ctx.shadowBlur = 48
  ctx.shadowOffsetY = 20
  drawRoundedRect(ctx, 72, 96, CARD_WIDTH - 144, CARD_HEIGHT - 196, CARD_RADIUS, '#fffdf5')
  ctx.restore()
}

/**
 * 绘制圆角矩形。
 */
function drawRoundedRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
  fillStyle: string
) {
  ctx.save()
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + width - radius, y)
  ctx.quadraticCurveTo(x + width, y, x + width, y + radius)
  ctx.lineTo(x + width, y + height - radius)
  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
  ctx.lineTo(x + radius, y + height)
  ctx.quadraticCurveTo(x, y + height, x, y + height - radius)
  ctx.lineTo(x, y + radius)
  ctx.quadraticCurveTo(x, y, x + radius, y)
  ctx.closePath()
  ctx.fillStyle = fillStyle
  ctx.fill()
  ctx.restore()
}

/**
 * 绘制圆角图片。
 */
function drawRoundedImage(
  ctx: CanvasRenderingContext2D,
  image: HTMLImageElement,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
) {
  ctx.save()
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + width - radius, y)
  ctx.quadraticCurveTo(x + width, y, x + width, y + radius)
  ctx.lineTo(x + width, y + height - radius)
  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
  ctx.lineTo(x + radius, y + height)
  ctx.quadraticCurveTo(x, y + height, x, y + height - radius)
  ctx.lineTo(x, y + radius)
  ctx.quadraticCurveTo(x, y, x + radius, y)
  ctx.closePath()
  ctx.clip()
  drawCoverImage(ctx, image, x, y, width, height)
  ctx.restore()
}

/**
 * 绘制圆形头像。
 */
function drawCircularImage(ctx: CanvasRenderingContext2D, image: HTMLImageElement, x: number, y: number, size: number) {
  ctx.save()
  ctx.beginPath()
  ctx.arc(x + size / 2, y + size / 2, size / 2, 0, Math.PI * 2)
  ctx.closePath()
  ctx.clip()
  ctx.drawImage(image, x, y, size, size)
  ctx.restore()
}

/**
 * 绘制头像兜底占位。
 */
function drawAvatarFallback(ctx: CanvasRenderingContext2D, x: number, y: number, size: number) {
  const gradient = ctx.createLinearGradient(x, y, x + size, y + size)
  gradient.addColorStop(0, '#60a5fa')
  gradient.addColorStop(1, '#2563eb')
  ctx.fillStyle = gradient
  ctx.beginPath()
  ctx.arc(x + size / 2, y + size / 2, size / 2, 0, Math.PI * 2)
  ctx.fill()
}

/**
 * 绘制封面缺失时的兜底背景。
 */
function drawCoverFallback(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number) {
  const gradient = ctx.createLinearGradient(x, y, x + width, y + height)
  gradient.addColorStop(0, '#1f2937')
  gradient.addColorStop(1, '#334155')
  ctx.fillStyle = gradient
  ctx.fillRect(x, y, width, height)

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.18)'
  ctx.lineWidth = 4
  ctx.beginPath()
  ctx.moveTo(x + 40, y + height - 80)
  ctx.bezierCurveTo(x + 160, y + 40, x + 260, y + height - 20, x + 420, y + 80)
  ctx.bezierCurveTo(x + 540, y + 20, x + 640, y + height - 40, x + width - 40, y + 96)
  ctx.stroke()
}

/**
 * 绘制封面图片。
 */
function drawCoverImage(ctx: CanvasRenderingContext2D, image: HTMLImageElement, x: number, y: number, width: number, height: number) {
  const imgRatio = image.width / image.height
  const boxRatio = width / height
  let drawWidth = width
  let drawHeight = height
  let drawX = x
  let drawY = y

  if (imgRatio > boxRatio) {
    drawWidth = height * imgRatio
    drawX = x - (drawWidth - width) / 2
  } else {
    drawHeight = width / imgRatio
    drawY = y - (drawHeight - height) / 2
  }

  ctx.drawImage(image, drawX, drawY, drawWidth, drawHeight)
  const overlay = ctx.createLinearGradient(0, y, 0, y + height)
  overlay.addColorStop(0, 'rgba(15, 23, 42, 0.04)')
  overlay.addColorStop(1, 'rgba(15, 23, 42, 0.18)')
  ctx.fillStyle = overlay
  ctx.fillRect(x, y, width, height)
}

/**
 * 按宽度自动换行绘制文本。
 */
function drawMultilineText(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  maxWidth: number,
  lineHeight: number,
  maxLines: number
) {
  const chars = String(text || '').split('')
  const lines: string[] = []
  let currentLine = ''

  for (const char of chars) {
    const nextLine = currentLine + char
    if (ctx.measureText(nextLine).width > maxWidth && currentLine) {
      lines.push(currentLine)
      currentLine = char
      continue
    }

    currentLine = nextLine
  }

  if (currentLine) {
    lines.push(currentLine)
  }

  const finalLines = lines.slice(0, maxLines)
  if (lines.length > maxLines) {
    finalLines[maxLines - 1] = `${finalLines[maxLines - 1].replace(/[，。；：、\s]*$/, '')}...`
  }

  finalLines.forEach((line, index) => {
    ctx.fillText(line, x, y + index * lineHeight)
  })
}

/**
 * 获取卡片底部站点名。
 */
function getBottomLabel() {
  return props.siteName || 'Open Source Blog'
}

/**
 * 格式化日期文案。
 */
function formatDate(value?: string) {
  if (!value) {
    return new Date().toLocaleDateString('zh-CN')
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  }).format(date)
}

/**
 * 加载图片资源。
 */
async function loadImage(src?: string) {
  if (!import.meta.client || !src) {
    return null
  }

  return new Promise<HTMLImageElement | null>((resolve) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null)
    image.src = src
  })
}

/**
 * 复制当前文章链接。
 */
async function copyLink() {
  if (!import.meta.client) {
    return
  }

  try {
    await navigator.clipboard.writeText(props.url || window.location.href)
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

/**
 * 下载当前分享卡片。
 */
async function downloadCard() {
  if (!cardImage.value) {
    await generateCard()
  }

  if (!cardImage.value || !import.meta.client) {
    return
  }

  downloading.value = true
  try {
    const link = document.createElement('a')
    link.href = cardImage.value
    link.download = `${String(props.article?.title || 'article-share-card').replace(/[\\/:*?"<>|]/g, '-').slice(0, 40)}.png`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('卡片已开始下载')
  } finally {
    downloading.value = false
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      void handleOpen()
    }
  }
)

watch(
  () => props.article,
  () => {
    if (props.modelValue) {
      void handleOpen()
    }
  },
  { deep: true }
)

watch(
  () => props.url,
  () => {
    if (props.modelValue) {
      void handleOpen()
    }
  }
)
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    class="share-card-dialog"
    width="min(92vw, 720px)"
    append-to-body
    destroy-on-close
    @open="handleOpen"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-header">
        <div>
          <h3>卡片分享</h3>
          <p>生成适合转发的文章卡片</p>
        </div>
      </div>
    </template>

    <div v-loading="generating" class="share-card-body">
      <div class="share-card-stage">
        <span class="stage-orb stage-orb-left"></span>
        <span class="stage-orb stage-orb-right"></span>
        <span class="stage-spark stage-spark-left"></span>
        <span class="stage-spark stage-spark-right"></span>
        <span class="stage-cross stage-cross-top"></span>
        <span class="stage-cross stage-cross-bottom"></span>
        <div class="share-card-preview-shell">
          <span class="shell-glow shell-glow-top"></span>
          <span class="shell-glow shell-glow-bottom"></span>
          <div class="share-card-preview">
            <img
              v-if="cardImage"
              :src="cardImage"
              class="share-card-image"
              alt="文章分享卡片"
            >
            <div v-else class="share-card-placeholder">
              <i class="fas fa-image"></i>
              <span>正在生成卡片...</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <ElButton @click="copyLink">复制链接</ElButton>
        <ElButton type="primary" :loading="downloading" @click="downloadCard">
          下载卡片
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.dialog-header {
  h3 {
    margin: 0;
    font-size: 1.35rem;
    color: var(--text-primary);
  }

  p {
    margin: 6px 0 0;
    color: var(--text-secondary);
    font-size: 0.95rem;
  }
}

.share-card-body {
  display: flex;
  justify-content: center;
}

.share-card-stage {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px 0 6px;
  isolation: isolate;
}

.share-card-preview-shell {
  position: relative;
  width: min(100%, 360px);
}

.shell-glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(24px);
  opacity: 0.56;
  pointer-events: none;
}

.shell-glow-top {
  width: 180px;
  height: 84px;
  left: -18px;
  top: -12px;
  background: rgba(255, 255, 255, 0.78);
}

.shell-glow-bottom {
  width: 200px;
  height: 120px;
  right: -28px;
  bottom: -18px;
  background: rgba(251, 191, 36, 0.24);
}

.share-card-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 900 / 1320;
  border-radius: 28px;
  overflow: hidden;
  background: linear-gradient(135deg, #fff7d6, #fde68a);
  z-index: 1;
}

.stage-orb,
.stage-spark,
.stage-cross {
  position: absolute;
  pointer-events: none;
}

.stage-orb {
  border-radius: 999px;
  filter: blur(10px);
  opacity: 0.42;
}

.stage-orb-left {
  width: 84px;
  height: 84px;
  left: 6%;
  top: 24%;
  background: radial-gradient(circle, rgba(255, 245, 157, 0.9) 0%, rgba(255, 245, 157, 0) 72%);
}

.stage-orb-right {
  width: 112px;
  height: 112px;
  right: 6%;
  bottom: 16%;
  background: radial-gradient(circle, rgba(251, 191, 36, 0.2) 0%, rgba(251, 191, 36, 0) 74%);
}

.stage-spark {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  opacity: 0.72;
}

.stage-spark-left {
  left: 8%;
  bottom: 18%;
  background: #fde68a;
  box-shadow: 18px -16px 0 0 rgba(191, 219, 254, 0.82), 30px 10px 0 0 rgba(252, 211, 77, 0.58);
}

.stage-spark-right {
  right: 10%;
  top: 20%;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: -18px 14px 0 0 rgba(253, 224, 71, 0.52), -34px -8px 0 0 rgba(191, 219, 254, 0.72);
}

.stage-cross {
  width: 18px;
  height: 18px;
}

.stage-cross::before,
.stage-cross::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  background: rgba(245, 158, 11, 0.42);
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.stage-cross::before {
  width: 18px;
  height: 2px;
}

.stage-cross::after {
  width: 2px;
  height: 18px;
}

.stage-cross-top {
  right: 12%;
  top: 10%;
}

.stage-cross-bottom {
  display: none;
}

.share-card-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.share-card-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: #92400e;

  i {
    font-size: 2rem;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.share-card-dialog .el-dialog) {
  border-radius: 28px;
  overflow: hidden;
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
}

:deep(.share-card-dialog .el-dialog__body) {
  padding-top: 8px;
  overflow: auto;
}

@media (max-width: 768px) {
  .share-card-stage {
    padding: 6px 0 2px;
  }

  .share-card-preview-shell {
    width: min(100%, 320px);
  }

  .stage-orb-left,
  .stage-orb-right,
  .stage-cross-bottom {
    display: none;
  }

  .stage-spark-left {
    left: 6%;
    bottom: 18%;
  }
}
</style>
