<script setup lang="ts">
import type {FormInstance, FormRules} from 'element-plus'

const emit = defineEmits<{
  submit: [password: string, done: () => void]
  cancel: []
}>()

const visible = ref(false)
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  password: ''
})

const rules: FormRules<typeof form> = {
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

function show() {
  visible.value = true
  loading.value = false
  form.password = ''
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

function setLoading(value: boolean) {
  loading.value = value
}

function handleCancel() {
  loading.value = false
  visible.value = false
  emit('cancel')
}

async function handleSubmit(event?: Event) {
  event?.preventDefault?.()
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  emit('submit', form.password, () => {
    loading.value = false
    visible.value = false
  })
}

defineExpose({
  show,
  setLoading
})
</script>

<template>
  <ElDialog
    v-model="visible"
    title="相册密码验证"
    width="400px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <div class="password-dialog">
      <div class="dialog-icon">
        <i class="fas fa-lock"></i>
      </div>
      <p class="dialog-tip">这是一个加密相册，请输入密码访问</p>
      <ElForm ref="formRef" :model="form" :rules="rules" @submit.prevent="handleSubmit">
        <ElFormItem prop="password">
          <ElInput
            v-model="form.password"
            type="password"
            placeholder="请输入相册密码"
          >
            <template #prefix>
              <i class="fas fa-key"></i>
            </template>
          </ElInput>
        </ElFormItem>
      </ElForm>
      <div class="dialog-footer">
        <ElButton @click="handleCancel">返回</ElButton>
        <ElButton type="primary" :loading="loading" @click="handleSubmit">
          确认
        </ElButton>
      </div>
    </div>
  </ElDialog>
</template>

<style lang="scss" scoped>
.password-dialog {
  text-align: center;
  padding: 20px 0;

  .dialog-icon {
    font-size: 48px;
    color: #e6a23c;
    margin-bottom: 20px;

    i {
      animation: shake 0.5s ease-in-out;
    }
  }

  .dialog-tip {
    color: var(--text-secondary);
    margin-bottom: 25px;
    font-size: 14px;
  }

  .el-input {
    width: 100%;
  }

  .dialog-footer {
    margin-top: 30px;
    display: flex;
    justify-content: center;
    gap: 15px;
  }
}

@keyframes shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-5px);
  }
  75% {
    transform: translateX(5px);
  }
}
</style>
