<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { VueCropper } from 'vue-cropper'
import { uploadFileApi } from '@/api/file'
import { updateProfileApi } from '@/api/user'
import { unwrapResponseData } from '@/utils/response'

interface CropperPreview {
  url?: string
  img?: Record<string, string>
  [key: string]: unknown
}

interface CropperExpose {
  refresh?: () => void
  rotateLeft?: () => void
  rotateRight?: () => void
  changeScale?: (value: number) => void
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
let resizeHandler: (() => void) | null = null

const title = '修改头像'
const options = reactive({
  img: null as string | ArrayBuffer | null,
  autoCrop: true,
  autoCropWidth: 200,
  autoCropHeight: 200,
  fixedBox: true,
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
        throw new Error(String(uploadResponse.message || uploadResponse.msg || '头像上传失败'))
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
}

/**
 * 关闭裁剪弹窗并清理状态。
 */
function closeDialog() {
  options.img = null
  emit('update:visible', false)
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="title"
    width="800px"
    append-to-body
    @opened="modalOpened"
    @close="closeDialog"
  >
    <ElRow>
      <ElCol :xs="24" :md="12" :style="{ height: '350px' }">
        <ClientOnly>
          <VueCropper
            v-if="dialogVisible"
            ref="cropperRef"
            :img="options.img"
            :info="true"
            :auto-crop="options.autoCrop"
            :auto-crop-width="options.autoCropWidth"
            :auto-crop-height="options.autoCropHeight"
            :fixed-box="options.fixedBox"
            :output-type="options.outputType"
            @realTime="realTime"
          />
        </ClientOnly>
      </ElCol>

      <ElCol :xs="24" :md="12" :style="{ height: '350px' }">
        <div class="avatar-upload-preview">
          <img :src="previews.url || String(user?.avatar || '')" :style="previews.img || {}">
        </div>
      </ElCol>
    </ElRow>

    <br>

    <ElRow>
      <ElCol :lg="2" :sm="3" :xs="3">
        <ElUpload action="#" :http-request="requestUpload" :show-file-list="false" :before-upload="beforeUpload">
          <ElButton size="small">
            选择
            <i class="fas fa-upload upload-icon"></i>
          </ElButton>
        </ElUpload>
      </ElCol>

      <ElCol :lg="{ span: 1, offset: 2 }" :sm="2" :xs="2">
        <ElButton size="small" @click="changeScale(1)">
          <i class="fas fa-plus"></i>
        </ElButton>
      </ElCol>

      <ElCol :lg="{ span: 1, offset: 1 }" :sm="2" :xs="2">
        <ElButton size="small" @click="changeScale(-1)">
          <i class="fas fa-minus"></i>
        </ElButton>
      </ElCol>

      <ElCol :lg="{ span: 1, offset: 1 }" :sm="2" :xs="2">
        <ElButton size="small" @click="rotateLeft">
          <i class="fas fa-undo"></i>
        </ElButton>
      </ElCol>

      <ElCol :lg="{ span: 1, offset: 1 }" :sm="2" :xs="2">
        <ElButton size="small" @click="rotateRight">
          <i class="fas fa-redo"></i>
        </ElButton>
      </ElCol>

      <ElCol :lg="{ span: 2, offset: 6 }" :sm="2" :xs="2">
        <ElButton type="primary" size="small" @click="uploadImg">提 交</ElButton>
      </ElCol>
    </ElRow>
  </ElDialog>
</template>

<style scoped lang="scss">
.avatar-upload-preview {
  position: relative;
  top: 50%;
  left: 50%;
  width: 200px;
  height: 200px;
  overflow: hidden;
  border-radius: 50%;
  box-shadow: 0 0 4px #ccc;
  transform: translate(-50%, -50%);
}

.upload-icon {
  margin-left: 6px;
}
</style>
