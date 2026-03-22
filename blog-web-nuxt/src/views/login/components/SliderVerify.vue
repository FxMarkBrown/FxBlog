<script setup lang="ts">
import { getCaptchaApi } from '@/api/auth'
import { unwrapResponseData } from '@/utils/response'
import { getThemeMode } from '@/utils/theme'

type CaptchaPayload = {
  nonceStr?: string
  blockSrc?: string
  blockY?: number | string
  canvasSrc?: string
}

type EventName = 'success' | 'fail' | 'again'

interface EventPayloadMap {
  success: { nonceStr?: string; value: number }
  fail: string | undefined
  again: undefined
}

const props = withDefaults(defineProps<{
  blockLength?: number
  blockRadius?: number
  canvasWidth?: number
  canvasHeight?: number
  sliderHint?: string
  accuracy?: number
  imageList?: string[]
}>(), {
  blockLength: 42,
  blockRadius: 10,
  canvasWidth: 320,
  canvasHeight: 155,
  sliderHint: '向右滑动',
  accuracy: 3,
  imageList: () => []
})

const emit = defineEmits<{
  <T extends EventName>(event: T, payload: EventPayloadMap[T]): void
}>()

const canvasRef = ref<HTMLCanvasElement | HTMLImageElement | null>(null)
const blockRef = ref<HTMLCanvasElement | HTMLImageElement | null>(null)
const sliderButtonRef = ref<HTMLElement | null>(null)

const isFrontCheck = ref(false)
const verifyActive = ref(false)
const verifySuccess = ref(false)
const verifyFail = ref(false)
const isMouseDown = ref(false)
const isLoading = ref(true)
const isDarkMode = ref(false)
const successHint = ref('')
const nonceStr = ref('')
const sliderBoxWidth = ref('0px')
const sliderButtonLeft = ref('0px')
const blockX = ref(0)
const blockY = ref(0)
const originX = ref(0)
const originY = ref(0)
const timestamp = ref(0)
const dragDistanceList = ref<number[]>([])

let blockObj: HTMLCanvasElement | HTMLImageElement | null = null
let sliderButtonEl: HTMLElement | null = null
let image: HTMLImageElement | null = null
let canvasCtx: CanvasRenderingContext2D | null = null
let blockCtx: CanvasRenderingContext2D | null = null
let eventsBound = false

const eventHandlers = {
  mousedown: null as ((event: MouseEvent) => void) | null,
  mousemove: null as ((event: MouseEvent) => void) | null,
  mouseup: null as ((event: MouseEvent) => void) | null,
  touchstart: null as ((event: TouchEvent) => void) | null,
  touchmove: null as ((event: TouchEvent) => void) | null,
  touchend: null as ((event: TouchEvent) => void) | null
}

const blockWidth = computed(() => props.blockLength * 2)

onMounted(() => {
  syncThemeState()
  init()
  window.addEventListener('theme-change', syncThemeState)
})

onBeforeUnmount(() => {
  unbindEvents()
  window.removeEventListener('theme-change', syncThemeState)
})

/**
 * 同步滑块主题状态。
 */
function syncThemeState() {
  isDarkMode.value = getThemeMode() === 'dark'
}

/**
 * 初始化滑块验证码组件。
 */
function init() {
  initDom()
  bindEvents()
}

/**
 * 初始化 DOM 和验证码画布对象。
 */
function initDom() {
  blockObj = blockRef.value
  if (!blockObj) {
    return
  }

  if (isFrontCheck.value) {
    canvasCtx = (canvasRef.value as HTMLCanvasElement | null)?.getContext('2d') || null
    blockCtx = (blockObj as HTMLCanvasElement).getContext('2d')
    initImage()
    return
  }

  getCaptcha()
}

/**
 * 从后端拉取验证码图片和位置信息。
 */
async function getCaptcha() {
  try {
    const response = await getCaptchaApi()
    const data = unwrapResponseData<CaptchaPayload | null>(response) || {}
    nonceStr.value = String(data.nonceStr || '')
    if (blockRef.value instanceof HTMLImageElement) {
      blockRef.value.src = String(data.blockSrc || '')
      blockRef.value.style.top = `${Number(data.blockY || 0)}px`
    }
    if (canvasRef.value instanceof HTMLImageElement) {
      canvasRef.value.src = String(data.canvasSrc || '')
    }
  } finally {
    isLoading.value = false
  }
}

/**
 * 初始化前端验证码图片资源。
 */
function initImage() {
  image = createImage(() => {
    drawBlock()
    if (!canvasCtx || !blockCtx || !image) {
      return
    }

    const yAxis = blockY.value - props.blockRadius * 2
    canvasCtx.drawImage(image, 0, 0, props.canvasWidth, props.canvasHeight)
    blockCtx.drawImage(image, 0, 0, props.canvasWidth, props.canvasHeight)

    const imageData = blockCtx.getImageData(blockX.value, yAxis, blockWidth.value, blockWidth.value)
    ;(blockObj as HTMLCanvasElement).width = blockWidth.value
    blockCtx.putImageData(imageData, 0, yAxis)
    isLoading.value = false
    nonceStr.value = 'loyer'
  })
}

/**
 * 创建加载验证码图片的 Image 对象。
 */
function createImage(onload: () => void) {
  const nextImage = document.createElement('img')
  nextImage.crossOrigin = 'Anonymous'
  nextImage.onload = onload
  nextImage.onerror = () => {}
  nextImage.src = getImageSrc()
  return nextImage
}

/**
 * 获取验证码背景图地址。
 */
function getImageSrc() {
  const length = props.imageList.length
  if (length > 0) {
    return props.imageList[getNonceByRange(0, length - 1)]
  }
  return `https://loyer.wang/view/ftp/wallpaper/${getNonceByRange(1, 1000)}.jpg`
}

/**
 * 按指定范围生成随机整数。
 */
function getNonceByRange(start: number, end: number) {
  return Math.round(Math.random() * (end - start) + start)
}

/**
 * 绘制阻塞块并生成拼图缺口位置。
 */
function drawBlock() {
  blockX.value = getNonceByRange(blockWidth.value + 10, props.canvasWidth - (blockWidth.value + 10))
  blockY.value = getNonceByRange(10 + props.blockRadius * 2, props.canvasHeight - (blockWidth.value + 10))
  draw(canvasCtx, 'fill')
  draw(blockCtx, 'clip')
}

/**
 * 在画布上绘制拼图块路径。
 */
function draw(ctx: CanvasRenderingContext2D | null, operation: 'fill' | 'clip') {
  if (!ctx) {
    return
  }

  const pi = Math.PI
  const x = blockX.value
  const y = blockY.value
  const length = props.blockLength
  const radius = props.blockRadius

  ctx.beginPath()
  ctx.moveTo(x, y)
  ctx.arc(x + length / 2, y - radius + 2, radius, 0.72 * pi, 2.26 * pi)
  ctx.lineTo(x + length, y)
  ctx.arc(x + length + radius - 2, y + length / 2, radius, 1.21 * pi, 2.78 * pi)
  ctx.lineTo(x + length, y + length)
  ctx.lineTo(x, y + length)
  ctx.arc(x + radius - 2, y + length / 2, radius + 0.4, 2.76 * pi, 1.24 * pi, true)
  ctx.lineTo(x, y)
  ctx.lineWidth = 2
  ctx.fillStyle = 'rgba(255, 255, 255, 0.9)'
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)'
  ctx.stroke()
  ctx[operation]()
  ctx.globalCompositeOperation = 'destination-over'
}

/**
 * 绑定拖动相关的鼠标和触摸事件。
 */
function bindEvents() {
  if (eventsBound || !sliderButtonRef.value) {
    return
  }

  sliderButtonEl = sliderButtonRef.value
  eventHandlers.mousedown = (event) => startEvent(event.clientX, event.clientY)
  eventHandlers.mousemove = (event) => moveEvent(event.clientX, event.clientY)
  eventHandlers.mouseup = (event) => endEvent(event.clientX)
  eventHandlers.touchstart = (event) => {
    const touch = event.changedTouches?.[0]
    if (touch) {
      startEvent(touch.pageX, touch.pageY)
    }
  }
  eventHandlers.touchmove = (event) => {
    const touch = event.changedTouches?.[0]
    if (touch) {
      moveEvent(touch.pageX, touch.pageY)
    }
  }
  eventHandlers.touchend = (event) => {
    const touch = event.changedTouches?.[0]
    if (touch) {
      endEvent(touch.pageX)
    }
  }

  sliderButtonEl.addEventListener('mousedown', eventHandlers.mousedown)
  sliderButtonEl.addEventListener('touchstart', eventHandlers.touchstart)
  document.addEventListener('mousemove', eventHandlers.mousemove)
  document.addEventListener('mouseup', eventHandlers.mouseup)
  document.addEventListener('touchmove', eventHandlers.touchmove)
  document.addEventListener('touchend', eventHandlers.touchend)
  eventsBound = true
}

/**
 * 解绑全局拖动事件。
 */
function unbindEvents() {
  if (!eventsBound) {
    return
  }

  if (sliderButtonEl && eventHandlers.mousedown) {
    sliderButtonEl.removeEventListener('mousedown', eventHandlers.mousedown)
  }
  if (sliderButtonEl && eventHandlers.touchstart) {
    sliderButtonEl.removeEventListener('touchstart', eventHandlers.touchstart)
  }
  if (eventHandlers.mousemove) {
    document.removeEventListener('mousemove', eventHandlers.mousemove)
  }
  if (eventHandlers.mouseup) {
    document.removeEventListener('mouseup', eventHandlers.mouseup)
  }
  if (eventHandlers.touchmove) {
    document.removeEventListener('touchmove', eventHandlers.touchmove)
  }
  if (eventHandlers.touchend) {
    document.removeEventListener('touchend', eventHandlers.touchend)
  }

  sliderButtonEl = null
  eventsBound = false
}

/**
 * 校验验证码图片资源是否已准备完成。
 */
function checkImgSrc() {
  if (isFrontCheck.value) {
    return true
  }
  return Boolean((canvasRef.value as HTMLImageElement | null)?.src)
}

/**
 * 开始拖动滑块。
 */
function startEvent(nextOriginX: number, nextOriginY: number) {
  if (!checkImgSrc() || isLoading.value || verifySuccess.value) {
    return
  }

  originX.value = nextOriginX
  originY.value = nextOriginY
  isMouseDown.value = true
  timestamp.value = Date.now()
}

/**
 * 处理滑块拖动中的位移更新。
 */
function moveEvent(nextOriginX: number, nextOriginY: number) {
  if (!isMouseDown.value || !blockObj) {
    return
  }

  const moveX = nextOriginX - originX.value
  const moveY = nextOriginY - originY.value
  if (moveX < 0 || moveX + 40 >= props.canvasWidth) {
    return
  }

  sliderButtonLeft.value = `${moveX}px`
  const blockLeft = ((props.canvasWidth - 40 - 20) / (props.canvasWidth - 40)) * moveX
  blockObj.style.left = `${blockLeft}px`
  verifyActive.value = true
  sliderBoxWidth.value = `${moveX}px`
  dragDistanceList.value.push(moveY)
}

/**
 * 结束拖动并触发验证码校验。
 */
function endEvent(nextOriginX: number) {
  if (!isMouseDown.value || !blockObj) {
    return
  }

  isMouseDown.value = false
  if (nextOriginX === originX.value) {
    return
  }

  isLoading.value = true
  verifyActive.value = false
  timestamp.value = Date.now() - timestamp.value
  const moveLength = parseInt(blockObj.style.left || '0', 10)

  if (timestamp.value > 10000) {
    verifyFailEvent()
    return
  }

  if (!turingTest()) {
    verifyFail.value = true
    isLoading.value = false
    emit('again', undefined)
    return
  }

  if (isFrontCheck.value) {
    const accuracy = props.accuracy <= 1 ? 1 : (props.accuracy > 10 ? 10 : props.accuracy)
    const spliced = Math.abs(moveLength - blockX.value) <= accuracy
    if (!spliced) {
      verifyFailEvent()
      return
    }
  }

  emit('success', { nonceStr: nonceStr.value, value: moveLength })
}

/**
 * 利用轨迹方差做简单的人机行为判断。
 */
function turingTest() {
  const distances = dragDistanceList.value
  if (!distances.length) {
    return false
  }

  const average = distances.reduce((sum, item) => sum + item, 0) / distances.length
  const deviations = distances.map((item) => item - average)
  const stdDev = Math.sqrt(deviations.map((item) => item * item).reduce((sum, item) => sum + item, 0) / distances.length)
  return average !== stdDev
}

/**
 * 在外部登录成功后展示成功提示。
 */
function verifySuccessEvent() {
  isLoading.value = false
  verifySuccess.value = true
  const elapsedTime = (timestamp.value / 1000).toFixed(1)
  if (Number(elapsedTime) < 1) {
    successHint.value = `仅仅${elapsedTime}S，你的速度快如闪电`
    return
  }
  if (Number(elapsedTime) < 2) {
    successHint.value = `只用了${elapsedTime}S，这速度简直完美`
    return
  }
  successHint.value = `耗时${elapsedTime}S，争取下次再快一点`
}

/**
 * 触发失败状态并刷新验证码。
 */
function verifyFailEvent(message?: string) {
  verifyFail.value = true
  emit('fail', message)
  refresh()
}

/**
 * 刷新验证码状态和图像资源。
 */
function refresh() {
  setTimeout(() => {
    verifyFail.value = false
  }, 500)

  isLoading.value = true
  verifyActive.value = false
  verifySuccess.value = false
  dragDistanceList.value = []
  sliderBoxWidth.value = '0px'
  sliderButtonLeft.value = '0px'

  if (blockObj) {
    blockObj.style.left = '0px'
  }

  if (isFrontCheck.value) {
    if (canvasCtx && blockCtx) {
      canvasCtx.clearRect(0, 0, props.canvasWidth, props.canvasHeight)
      blockCtx.clearRect(0, 0, props.canvasWidth, props.canvasHeight)
    }
    if (blockObj instanceof HTMLCanvasElement) {
      blockObj.width = props.canvasWidth
    }
    if (image) {
      image.src = getImageSrc()
    }
    return
  }

  getCaptcha()
}

defineExpose({
  refresh,
  verifySuccessEvent
})
</script>

<template>
  <div
    class="slide-verify"
    :class="{ 'is-dark': isDarkMode }"
    :style="{ width: `${props.canvasWidth}px` }"
    onselectstart="return false;"
  >
    <div v-if="isLoading" class="img-loading" :style="{ height: `${props.canvasHeight}px` }" />

    <div v-if="verifySuccess" class="success-hint" :style="{ height: `${props.canvasHeight}px` }">
      {{ successHint }}
    </div>

    <div class="refresh-icon" @click="refresh" />

    <template v-if="isFrontCheck">
      <canvas ref="canvasRef" class="slide-canvas" :width="props.canvasWidth" :height="props.canvasHeight" />
      <canvas ref="blockRef" class="slide-block" :width="props.canvasWidth" :height="props.canvasHeight" />
    </template>

    <template v-else>
      <img ref="canvasRef" class="slide-canvas" :width="props.canvasWidth" :height="props.canvasHeight">
      <img ref="blockRef" :class="['slide-block', { 'verify-fail': verifyFail }]">
    </template>

    <div
      class="slider"
      :class="{
        'verify-active': verifyActive,
        'verify-success': verifySuccess,
        'verify-fail': verifyFail
      }"
    >
      <div class="slider-box" :style="{ width: sliderBoxWidth }">
        <div ref="sliderButtonRef" class="slider-button" :style="{ left: sliderButtonLeft }">
          <div class="slider-button-icon">
            <i class="fas fa-angle-right slider-arrow"></i>
          </div>
        </div>
      </div>
      <span class="slider-hint">{{ props.sliderHint }}</span>
    </div>
  </div>
</template>

<style scoped>
.slide-verify {
  position: relative;
}

.img-loading {
  position: absolute;
  inset: 0;
  z-index: 999;
  border-radius: 5px;
  background-color: #737c8e;
  background-repeat: no-repeat;
  background-position: center center;
  background-size: 100px;
  animation: loading 1.5s infinite;
}

@keyframes loading {
  0% {
    opacity: 0.7;
  }

  100% {
    opacity: 0.9;
  }
}

.success-hint {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
  color: #2cd000;
  font-size: large;
}

.refresh-icon {
  position: absolute;
  top: 0;
  right: 0;
  width: 35px;
  height: 35px;
  cursor: pointer;
}

.slide-canvas {
  border-radius: 5px;
}

.slide-block {
  position: absolute;
  top: 0;
  left: 0;
}

.slide-block.verify-fail {
  transition: left 0.5s linear;
}

.slider {
  position: relative;
  width: 100%;
  height: 40px;
  margin-top: 15px;
  border: 1px solid #e4e7eb;
  border-radius: 5px;
  background: #f7f9fa;
  color: #45494c;
  line-height: 40px;
  text-align: center;
}

.slider-box {
  position: absolute;
  top: 0;
  left: 0;
  height: 40px;
  border: 0 solid #1991fa;
  border-radius: 5px;
  background: #d1e9fe;
}

.slider-button {
  position: absolute;
  top: 0;
  left: 0;
  width: 40px;
  height: 40px;
  border-radius: 5px;
  background: #fff;
  box-shadow: 0 0 3px rgba(0, 0, 0, 0.3);
  cursor: pointer;
  transition: background 0.2s linear;
}

.slider-button:hover {
  background: #1991fa;
}

.slider-button:hover i {
  color: #fff;
}

.slider-button-icon {
  position: absolute;
  top: 15px;
  left: 13px;
  width: 15px;
  height: 13px;
}

.slider-arrow {
  position: absolute;
  top: -10px;
  left: -10px;
  font-size: 30px;
}

.verify-active .slider-button {
  top: -1px;
  height: 38px;
  border: 1px solid #1991fa;
}

.verify-active .slider-box {
  height: 38px;
  border-width: 1px;
}

.verify-success .slider-box {
  height: 38px;
  border: 1px solid #52ccba;
  background-color: #d2f4ef;
}

.verify-success .slider-button {
  top: -1px;
  height: 38px;
  border: 1px solid #52ccba;
  background-color: #52ccba !important;
}

.verify-fail .slider-box {
  height: 38px;
  border: 1px solid #f57a7a;
  background-color: #fce1e1;
  transition: width 0.5s linear;
}

.verify-fail .slider-button {
  top: -1px;
  height: 38px;
  border: 1px solid #f57a7a;
  background-color: #f57a7a !important;
  transition: left 0.5s linear;
}

.verify-active .slider-hint,
.verify-success .slider-hint,
.verify-fail .slider-hint {
  display: none;
}

.slide-verify.is-dark .img-loading {
  background-color: #334155;
}

.slide-verify.is-dark .success-hint {
  background: rgba(15, 23, 42, 0.82);
  color: #5eead4;
}

.slide-verify.is-dark .slide-canvas {
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.2);
}

.slide-verify.is-dark .slider {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.72);
  color: #cbd5e1;
}

.slide-verify.is-dark .slider-box {
  background: rgba(59, 130, 246, 0.28);
  border-color: #60a5fa;
}

.slide-verify.is-dark .slider-button {
  background: #1e293b;
  box-shadow: 0 8px 18px rgba(2, 6, 23, 0.28);
}

.slide-verify.is-dark .slider-button:hover {
  background: #2563eb;
}

.slide-verify.is-dark .slider-arrow {
  color: #cbd5e1;
}

.slide-verify.is-dark .verify-active .slider-button {
  border-color: #60a5fa;
}

.slide-verify.is-dark .verify-active .slider-box {
  border-color: #60a5fa;
}

.slide-verify.is-dark .verify-success .slider-box {
  border-color: #14b8a6;
  background-color: rgba(20, 184, 166, 0.22);
}

.slide-verify.is-dark .verify-success .slider-button {
  border-color: #14b8a6;
  background-color: #14b8a6 !important;
}

.slide-verify.is-dark .verify-fail .slider-box {
  border-color: #f87171;
  background-color: rgba(248, 113, 113, 0.18);
}

.slide-verify.is-dark .verify-fail .slider-button {
  border-color: #f87171;
  background-color: #ef4444 !important;
}
</style>
