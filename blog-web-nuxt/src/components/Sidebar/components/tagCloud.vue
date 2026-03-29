<script setup lang="ts">
import {getTagsApi} from '@/api/tags'
import type {TagSummary} from '@/types/article'
import {unwrapResponseData} from '@/utils/response'

const router = useRouter()
const data = ref<TagSummary[]>([])
const hoveredIndex = ref<number | null>(null)
const wrapper = ref<HTMLElement | null>(null)
const tagRefs = ref<HTMLElement[]>([])
const rafId = ref<number | null>(null)
const rotateAngleX = ref(Math.PI / 600)
const rotateAngleY = ref(Math.PI / 600)
const isDocumentHidden = ref(false)

const option = {
  radius: 140,
  maxFont: 24
}

const tagList = ref<Array<{ x: number; y: number; z: number; ele: HTMLElement }>>([])

onMounted(async () => {
  if (import.meta.client) {
    document.addEventListener('visibilitychange', handleVisibilityChange)
    isDocumentHidden.value = document.hidden
  }
  const response = await getTagsApi().catch(() => null)
  data.value = unwrapResponseData<TagSummary[] | null>(response) || []
  await nextTick()
  initTags()
})

onBeforeUnmount(() => {
  stopRotate()
  if (import.meta.client) {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
})

watch(data, async () => {
  await nextTick()
  initTags()
})

/**
 * 初始化标签
 */
function initTags() {
  stopRotate()
  tagList.value = []
  const elements = tagRefs.value || []
  if (!data.value.length || !elements.length) {
    return
  }

  for (let index = 0; index < data.value.length; index += 1) {
    const angleX = Math.acos((2 * (index + 1) - 1) / data.value.length - 1)
    const angleY = angleX * Math.sqrt(data.value.length * Math.PI)
    const x = option.radius * Math.sin(angleX) * Math.cos(angleY)
    const y = option.radius * Math.sin(angleX) * Math.sin(angleY)
    const z = option.radius * Math.cos(angleX)
    const element = elements[index]

    if (!element) {
      continue
    }

    element.style.color = `rgb(${Math.round(255 * Math.random())},${Math.round(255 * Math.random())},${Math.round(255 * Math.random())})`
    const tag = { x, y, z, ele: element }
    tagList.value.push(tag)
    setPosition(tag)
  }

  startRotate()
}

/**
 * 设置标签位置
 * @param tag 标签
 */
function setPosition(tag: { x: number; y: number; z: number; ele: HTMLElement }) {
  const scale = (tag.z / option.radius / 2 + 0.5) * (option.maxFont / 16)
  tag.ele.style.transform =
    `translate3d(${tag.x}px, ${tag.y}px, 0) translate3d(-50%, -50%, 0) scale(${scale})`
  tag.ele.style.opacity = String(tag.z / option.radius / 2 + 0.7)
}

/**
 * 旋转 X 轴
 * @param tag 标签
 */
function rotateX(tag: { x: number; y: number; z: number }) {
  const cos = Math.cos(rotateAngleX.value)
  const sin = Math.sin(rotateAngleX.value)
  const y1 = tag.y * cos - tag.z * sin
  const z1 = tag.y * sin + tag.z * cos
  tag.y = y1
  tag.z = z1
}

/**
 * 旋转 Y 轴
 * @param tag 标签
 */
function rotateY(tag: { x: number; y: number; z: number }) {
  const cos = Math.cos(rotateAngleY.value)
  const sin = Math.sin(rotateAngleY.value)
  const x1 = tag.z * sin + tag.x * cos
  const z1 = tag.z * cos - tag.x * sin
  tag.x = x1
  tag.z = z1
}

/**
 * 停止旋转
 */
function stopRotate() {
  if (rafId.value) {
    cancelAnimationFrame(rafId.value)
    rafId.value = null
  }
}

/**
 * 开始旋转
 */
function startRotate() {
  if (rafId.value || !tagList.value.length || isDocumentHidden.value) {
    return
  }

  const tick = () => {
    for (const tag of tagList.value) {
      rotateX(tag)
      rotateY(tag)
      setPosition(tag)
    }

    rafId.value = requestAnimationFrame(tick)
  }

  rafId.value = requestAnimationFrame(tick)
}

/**
 * 页面不可见时暂停动画，恢复可见时继续。
 */
function handleVisibilityChange() {
  if (!import.meta.client) {
    return
  }

  isDocumentHidden.value = document.hidden
  if (isDocumentHidden.value) {
    stopRotate()
    return
  }

  startRotate()
}

/**
 * 跳转标签页
 * @param item 标签
 */
function clickTag(item: TagSummary) {
  router.push({
    path: '/tags',
    query: {
      tagId: String(item.id || ''),
      tagName: String(item.name || '')
    }
  })
}
</script>

<template>
  <div class="tag-wall">
    <div ref="wrapper" class="tag-cloud" @mouseenter="stopRotate" @mouseleave="startRotate">
      <p
        v-for="(item, index) in data"
        :key="item.id || index"
        :ref="(el) => { if (el) tagRefs[index] = el as HTMLElement }"
        :class="{ 'tag-dimmed': hoveredIndex !== null && hoveredIndex !== index }"
        @click="clickTag(item)"
        @mouseenter="hoveredIndex = index"
        @mouseleave="hoveredIndex = null"
      >
        {{ item.name }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.tag-cloud {
  width: 300px;
  height: 300px;
  position: relative;
  margin: 0 auto;
  text-align: center;
  cursor: default;
}

.tag-cloud p {
  position: absolute;
  top: 50%;
  left: 50%;
  margin: 0;
  line-height: 18px;
  font-size: 16px;
  padding: 4px 9px;
  display: inline-block;
  border-radius: 3px;
  transition: opacity 0.3s ease;
  will-change: transform, opacity;
  transform-origin: center center;
}

.tag-cloud p:hover {
  cursor: pointer;
}

.tag-dimmed {
  opacity: 0.05 !important;
}
</style>
