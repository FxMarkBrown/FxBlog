<script setup lang="ts">
interface Point {
  x: number
  y: number
}

const rootRef = ref<HTMLElement | null>(null)
const previewWrapper = ref<HTMLElement | null>(null)
const navButtons = ref<HTMLElement | null>(null)

const visible = ref(false)
const images = ref<string[]>([])
const currentIndex = ref(0)
const scale = ref(1)
const rotation = ref(0)
const position = reactive<Point>({ x: 0, y: 0 })
const isDragging = ref(false)
const lastMousePosition = reactive<Point>({ x: 0, y: 0 })
const initialDistance = ref(0)
const initialScale = ref(1)
const justClosedAt = ref(0)
const suppressCloseTimer = ref<number | null>(null)

const currentImage = computed(() => images.value[currentIndex.value] || '')

function reset() {
  scale.value = 1
  rotation.value = 0
  position.x = 0
  position.y = 0
}

function stopDrag() {
  isDragging.value = false
}

function stopTouch() {
  isDragging.value = false
}

function suppressUnderlyingClick() {
  const swallow = (event: Event) => {
    event.preventDefault()
    event.stopPropagation()
    if ('stopImmediatePropagation' in event && typeof event.stopImmediatePropagation === 'function') {
      event.stopImmediatePropagation()
    }
  }

  const removeListeners = () => {
    document.removeEventListener('mouseup', swallow, true)
    document.removeEventListener('click', swallow, true)
    if (suppressCloseTimer.value) {
      clearTimeout(suppressCloseTimer.value)
      suppressCloseTimer.value = null
    }
  }

  document.addEventListener('mouseup', swallow, true)
  document.addEventListener('click', swallow, true)
  suppressCloseTimer.value = window.setTimeout(removeListeners, 80)
}

function show(nextImages: string[] | string, startIndex = 0) {
  if (!import.meta.client || Date.now() - justClosedAt.value < 300) {
    return
  }

  images.value = Array.isArray(nextImages) ? nextImages : [nextImages]
  currentIndex.value = startIndex
  visible.value = true
  reset()
  document.body.style.overflow = 'hidden'

  nextTick(() => {
    rootRef.value?.focus()
  })
}

function close() {
  if (!visible.value || !import.meta.client) {
    return
  }

  justClosedAt.value = Date.now()
  stopDrag()
  stopTouch()
  suppressUnderlyingClick()
  visible.value = false
  document.body.style.overflow = ''
}

function prev() {
  if (currentIndex.value <= 0) {
    return
  }

  currentIndex.value -= 1
  reset()
}

function next() {
  if (currentIndex.value >= images.value.length - 1) {
    return
  }

  currentIndex.value += 1
  reset()
}

function rotate(deg: number) {
  rotation.value = (rotation.value + deg) % 360
}

function zoom(delta: number) {
  const nextScale = scale.value + delta
  if (nextScale >= 0.1 && nextScale <= 3) {
    scale.value = nextScale
  }
}

function handleWheel(event: WheelEvent) {
  zoom(event.deltaY > 0 ? -0.1 : 0.1)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && visible.value) {
    close()
  }
}

function handleDocumentMouseDown(event: MouseEvent) {
  if (!visible.value) {
    return
  }

  const target = event.target as Node | null
  const clickedInsidePreview = !!(
    (previewWrapper.value && target && previewWrapper.value.contains(target))
    || (navButtons.value && target && navButtons.value.contains(target))
  )

  if (clickedInsidePreview) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation?.()
  close()
}

function startDrag(event: MouseEvent) {
  isDragging.value = true
  lastMousePosition.x = event.clientX
  lastMousePosition.y = event.clientY
}

function onDrag(event: MouseEvent) {
  if (!isDragging.value) {
    return
  }

  const deltaX = event.clientX - lastMousePosition.x
  const deltaY = event.clientY - lastMousePosition.y
  position.x += deltaX
  position.y += deltaY
  lastMousePosition.x = event.clientX
  lastMousePosition.y = event.clientY
}

function startTouch(event: TouchEvent) {
  if (event.touches.length === 2) {
    event.preventDefault()
    const [touch1, touch2] = [event.touches[0], event.touches[1]]
    initialDistance.value = Math.hypot(touch2.clientX - touch1.clientX, touch2.clientY - touch1.clientY)
    initialScale.value = scale.value
    return
  }

  const target = event.target as HTMLElement | null
  if (event.touches.length === 1 && target?.tagName.toLowerCase() === 'img') {
    event.preventDefault()
    isDragging.value = true
    lastMousePosition.x = event.touches[0].clientX
    lastMousePosition.y = event.touches[0].clientY
  }
}

function onTouch(event: TouchEvent) {
  if (event.touches.length === 2) {
    const [touch1, touch2] = [event.touches[0], event.touches[1]]
    const currentDistance = Math.hypot(touch2.clientX - touch1.clientX, touch2.clientY - touch1.clientY)
    const nextScale = (currentDistance / initialDistance.value) * initialScale.value
    if (nextScale >= 0.1 && nextScale <= 3) {
      scale.value = nextScale
    }
    return
  }

  if (isDragging.value && event.touches.length === 1) {
    const deltaX = event.touches[0].clientX - lastMousePosition.x
    const deltaY = event.touches[0].clientY - lastMousePosition.y
    position.x += deltaX
    position.y += deltaY
    lastMousePosition.x = event.touches[0].clientX
    lastMousePosition.y = event.touches[0].clientY
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('mousedown', handleDocumentMouseDown, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('mousedown', handleDocumentMouseDown, true)
  if (suppressCloseTimer.value) {
    clearTimeout(suppressCloseTimer.value)
    suppressCloseTimer.value = null
  }
  if (import.meta.client) {
    document.body.style.overflow = ''
  }
})

defineExpose({
  show,
  close,
  prev,
  next,
  rotate,
  zoom,
  reset
})
</script>

<template>
  <Transition name="fade">
    <div
      v-if="visible"
      ref="rootRef"
      class="image-preview"
      tabindex="-1"
      @mousewheel.prevent="handleWheel"
      @mousemove="onDrag"
      @mouseup="stopDrag"
      @keydown.left="prev"
      @keydown.right="next"
      @touchstart="startTouch"
      @touchmove.prevent="onTouch"
      @touchend="stopTouch"
    >
      <div class="preview-mask"></div>

      <div ref="previewWrapper" class="preview-wrapper" @click.stop @mousedown.stop>
        <img
          :src="currentImage"
          :style="{
            transform: `translate(${position.x}px, ${position.y}px) scale(${scale}) rotate(${rotation}deg)`
          }"
          @click.stop
          @mousedown.stop.prevent="startDrag"
          @touchstart.stop="startTouch"
        >
      </div>

      <div v-if="images.length > 1" ref="navButtons" class="nav-buttons" @click.stop @mousedown.stop>
        <button class="nav-btn prev" :disabled="currentIndex <= 0" @click.stop="prev">
          <i class="fas fa-chevron-left"></i>
        </button>
        <button class="nav-btn next" :disabled="currentIndex >= images.length - 1" @click.stop="next">
          <i class="fas fa-chevron-right"></i>
        </button>
      </div>

      <div v-if="images.length > 1" class="image-counter">
        {{ currentIndex + 1 }} / {{ images.length }}
      </div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
.image-preview {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: zoom-out;

  .preview-mask {
    position: absolute;
    inset: 0;
    cursor: zoom-out;
  }

  .preview-wrapper {
    position: relative;
    z-index: 1;
    max-width: 90vw;
    max-height: 90vh;

    img {
      max-width: 100%;
      max-height: 90vh;
      object-fit: contain;
      cursor: grab;
      user-select: none;

      &:active {
        cursor: grabbing;
      }
    }
  }
}

.fade-enter-active {
  animation: fadeIn 0.3s ease;

  img {
    animation: zoomIn 0.3s ease;
  }
}

.fade-leave-active {
  animation: fadeOut 0.3s ease;

  img {
    animation: zoomOut 0.3s ease;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes fadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

@keyframes zoomIn {
  from {
    opacity: 0;
    transform: translate(0, 0) scale(0.3) rotate(0deg);
  }
  to {
    opacity: 1;
    transform: translate(0, 0) scale(1) rotate(0deg);
  }
}

@keyframes zoomOut {
  from {
    opacity: 1;
    transform: translate(0, 0) scale(1) rotate(0deg);
  }
  to {
    opacity: 0;
    transform: translate(0, 0) scale(0.3) rotate(0deg);
  }
}

.nav-buttons {
  position: fixed;
  z-index: 2;
  top: 50%;
  left: 0;
  right: 0;
  transform: translateY(-50%);
  display: flex;
  justify-content: space-between;
  padding: 0 20px;
  pointer-events: none;

  .nav-btn {
    pointer-events: auto;
    width: 44px;
    height: 44px;
    border: none;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    cursor: pointer;
    backdrop-filter: blur(8px);
    transition: all 0.25s ease;

    &:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.28);
      transform: scale(1.05);
    }

    &:disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }
  }
}

.image-counter {
  position: fixed;
  z-index: 2;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 14px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  font-size: 14px;
}
</style>
