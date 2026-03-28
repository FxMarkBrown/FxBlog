<script setup lang="ts">
import { ElMessage } from 'element-plus'
import 'vue-cropper/dist/index.css'
import { VueCropper } from 'vue-cropper'
import { uploadFileApi } from '@/api/file'
import { updateProfileApi } from '@/api/user'
import { unwrapResponseData } from '@/utils/response'

interface CropperPreview {
  url?: string
  div?: Record<string, string>
  img?: Record<string, string>
  w?: number
  h?: number
  [key: string]: unknown
}

interface CropperExpose {
  refresh?: () => void
  rotateLeft?: () => void
  rotateRight?: () => void
  changeScale?: (value: number) => void
  getCropData?: (callback: (data: string) => void) => void
  getCropBlob?: (callback: (blob: Blob) => void) => void
}

const props = defineProps<{
  user?: Record<string, unknown>
  visible?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'update-avatar', value: string): void
}>()

const cropperRef = ref<CropperExpose | null>(null)
const dialogVisible = ref(Boolean(props.visible))
const previews = ref<CropperPreview>({})
const previewImageUrl = ref('')
let resizeHandler: (() => void) | null = null
let previewSyncFrame = 0

const title = '修改头像'
const options = reactive({
  img: null as string | ArrayBuffer | null,
  autoCrop: true,
  autoCropWidth: 160,
  autoCropHeight: 160,
  fixedBox: true,
  canMove: false,
  canMoveBox: true,
  centerBox: true,
  outputType: 'png'
})

watch(
  () => props.visible,
  (value) => {
    dialogVisible.value = Boolean(value)
    options.img = (props.user?.avatar as string) || null
  },
  { immediate: true }
)

watch(dialogVisible, (value) => {
  if (value !== Boolean(props.visible)) {
    emit('update:visible', value)
  }
})

onBeforeUnmount(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  if (previewSyncFrame) {
    cancelAnimationFrame(previewSyncFrame)
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
 * 在弹窗打开后绑定 resize 刷新事件。
 */
function modalOpened() {
  if (!resizeHandler) {
    resizeHandler = () => {
      refresh()
    }
  }
  window.addEventListener('resize', resizeHandler)
}

/**
 * 刷新裁剪器布局。
 */
function refresh() {
  cropperRef.value?.refresh?.()
  syncPreview()
}

/**
 * 兼容 Element Plus 上传入口，不实际走默认上传。
 */
function requestUpload() {}

/**
 * 左旋裁剪图像。
 */
function rotateLeft() {
  cropperRef.value?.rotateLeft?.()
}

/**
 * 右旋裁剪图像。
 */
function rotateRight() {
  cropperRef.value?.rotateRight?.()
}

/**
 * 调整裁剪缩放。
 */
function changeScale(value: number) {
  cropperRef.value?.changeScale?.(value || 1)
}

/**
 * 选择本地图片并加载到裁剪器。
 */
function beforeUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    showError('文件格式错误，请上传图片类型，如 JPG、PNG。')
    return false
  }

  const reader = new FileReader()
  reader.readAsDataURL(file)
  reader.onload = () => {
    options.img = reader.result
  }
  return false
}

/**
 * 上传裁剪后的头像并回写用户资料。
 */
function uploadImg() {
  if (!cropperRef.value?.getCropBlob) {
    return
  }

  cropperRef.value.getCropBlob(async (blob) => {
    const file = new File([blob], 'avatar.jpg', { type: 'image/jpg' })
    const formData = new FormData()
    formData.append('file', file)

    try {
      const uploadResponse = await uploadFileApi(formData, 'userAvatar')
      const avatarUrl = unwrapResponseData<string | null>(uploadResponse)
      if (!avatarUrl) {
        showError(String(uploadResponse.message || uploadResponse.msg || '头像上传失败'))
        return
      }

      await updateProfileApi({
        id: props.user?.id,
        avatar: avatarUrl
      })

      emit('update:visible', false)
      emit('update-avatar', avatarUrl)
      showSuccess('头像更新成功')
    } catch (error) {
      showError((error as Error)?.message || '头像更新失败')
    }
  })
}

/**
 * 实时更新右侧头像预览。
 */
function realTime(data: CropperPreview) {
  previews.value = data
  syncPreview()
}

/**
 * 使用裁剪后的真实结果刷新右侧预览，避免 CSS 预览与实际裁剪不一致。
 */
function syncPreview() {
  if (!import.meta.client || !cropperRef.value?.getCropData) {
    return
  }
  if (previewSyncFrame) {
    cancelAnimationFrame(previewSyncFrame)
  }
  previewSyncFrame = requestAnimationFrame(() => {
    cropperRef.value?.getCropData?.((data) => {
      previewImageUrl.value = data
    })
  })
}

/**
 * 关闭裁剪弹窗并清理状态。
 */
function closeDialog() {
  options.img = null
  previewImageUrl.value = ''
  emit('update:visible', false)
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  if (previewSyncFrame) {
    cancelAnimationFrame(previewSyncFrame)
    previewSyncFrame = 0
  }
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="title"
    width="min(92vw, 860px)"
    append-to-body
    class="avatar-cropper-dialog"
    @opened="modalOpened"
    @close="closeDialog"
  >
    <div class="avatar-cropper-layout">
      <div class="avatar-cropper-stage">
        <ClientOnly>
          <div class="avatar-cropper-canvas">
            <div class="avatar-cropper-canvas__hint">拖动裁剪框调整头像区域</div>
            <VueCropper
              v-if="dialogVisible"
              ref="cropperRef"
              :img="options.img"
              :info="true"
              :auto-crop="options.autoCrop"
              :auto-crop-width="options.autoCropWidth"
              :auto-crop-height="options.autoCropHeight"
              :fixed-box="options.fixedBox"
              :can-move="options.canMove"
              :can-move-box="options.canMoveBox"
              :center-box="options.centerBox"
              :output-type="options.outputType"
              @realTime="realTime"
            />
          </div>
        </ClientOnly>
      </div>

      <div class="avatar-cropper-sidebar">
        <div class="avatar-cropper-sidebar__title">预览</div>
        <div class="avatar-upload-preview">
          <img :src="previewImageUrl || String(user?.avatar || '')" alt="头像裁剪预览">
        </div>
      </div>
    </div>

    <div class="avatar-cropper-toolbar">
      <div class="avatar-cropper-toolbar__group">
        <ElUpload action="#" :http-request="requestUpload" :show-file-list="false" :before-upload="beforeUpload">
          <ElButton size="small">
            选择
            <i class="fas fa-upload upload-icon"></i>
          </ElButton>
        </ElUpload>
      </div>

      <div class="avatar-cropper-toolbar__group">
        <ElButton size="small" @click="changeScale(1)">
          <i class="fas fa-plus"></i>
        </ElButton>
        <ElButton size="small" @click="changeScale(-1)">
          <i class="fas fa-minus"></i>
        </ElButton>
        <ElButton size="small" @click="rotateLeft">
          <i class="fas fa-undo"></i>
        </ElButton>
        <ElButton size="small" @click="rotateRight">
          <i class="fas fa-redo"></i>
        </ElButton>
      </div>

      <div class="avatar-cropper-toolbar__submit">
        <ElButton type="primary" size="small" @click="uploadImg">提 交</ElButton>
      </div>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.avatar-cropper-layout {
  display: flex;
  align-items: stretch;
  gap: 24px;
  min-height: 360px;
}

.avatar-cropper-stage {
  flex: 1 1 0;
  min-width: 0;
}

.avatar-cropper-canvas {
  position: relative;
  height: 360px;
  overflow: hidden;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.avatar-cropper-canvas__hint {
  position: absolute;
  top: 14px;
  left: 16px;
  z-index: 4;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: rgba(241, 245, 249, 0.92);
  font-size: 12px;
  line-height: 1;
  pointer-events: none;
  backdrop-filter: blur(8px);
}

.avatar-cropper-canvas :deep(.vue-cropper) {
  width: 100%;
  height: 100%;
  background: transparent;
}

.avatar-cropper-canvas :deep(.cropper-modal) {
  background: rgba(15, 23, 42, 0.42);
}

.avatar-cropper-canvas :deep(.cropper-crop-box) {
  z-index: 3;
  border-radius: 24px;
}

.avatar-cropper-canvas :deep(.cropper-view-box) {
  position: relative;
  z-index: 3;
  overflow: hidden;
  border-radius: 24px;
  background: transparent;
  outline: 2px solid rgba(96, 165, 250, 0.96);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.9),
    0 10px 30px rgba(15, 23, 42, 0.24);
}

.avatar-cropper-canvas :deep(.cropper-face) {
  z-index: 4;
  border-radius: 24px;
  background-color: transparent !important;
  background-image:
    linear-gradient(rgba(96, 165, 250, 0.12), rgba(96, 165, 250, 0.12)),
    linear-gradient(
          90deg,
          rgba(255, 255, 255, 0.18) 0,
          rgba(255, 255, 255, 0.18) 1px,
          transparent 1px,
          transparent 33.333%
    ),
    linear-gradient(
          rgba(255, 255, 255, 0.18) 0,
          rgba(255, 255, 255, 0.18) 1px,
          transparent 1px,
          transparent 33.333%
    );
  background-position: center;
  background-repeat: no-repeat, repeat, repeat;
  background-size: auto, 33.333% 100%, 100% 33.333%;
  opacity: 1;
  cursor: grab;
}

.avatar-cropper-canvas :deep(.crop-info) {
  left: 50%;
  top: 12px !important;
  transform: translateX(-50%);
  min-width: auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.82);
  color: #f8fafc;
  font-size: 12px;
  line-height: 1.4;
}

.avatar-cropper-sidebar {
  display: flex;
  width: 220px;
  flex: 0 0 220px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.16);
  padding: 20px 16px;
}

.avatar-cropper-sidebar__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #94a3b8);
  letter-spacing: 0.08em;
}

.avatar-upload-preview {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200px;
  height: 200px;
  overflow: hidden;
  border-radius: 50%;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18);
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.06);
}

.avatar-upload-preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-cropper-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.avatar-cropper-toolbar__group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.avatar-cropper-toolbar__submit {
  margin-left: auto;
}

.upload-icon {
  margin-left: 6px;
}

@media (max-width: 768px) {
  .avatar-cropper-layout {
    flex-direction: column;
    gap: 16px;
    min-height: unset;
  }

  .avatar-cropper-canvas {
    height: 320px;
  }

  .avatar-cropper-canvas__hint {
    top: 12px;
    left: 12px;
    font-size: 11px;
  }

  .avatar-cropper-sidebar {
    width: 100%;
    flex-basis: auto;
    padding: 18px 12px;
  }

  .avatar-upload-preview {
    width: 168px;
    height: 168px;
  }

  .avatar-cropper-toolbar__submit {
    width: 100%;
    margin-left: 0;
  }

  .avatar-cropper-toolbar__submit :deep(.el-button) {
    width: 100%;
  }
}
</style>
