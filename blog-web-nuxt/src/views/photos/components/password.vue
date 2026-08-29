<script setup lang="ts">
import type { FormInst, FormRules } from 'naive-ui'

const emit = defineEmits<{
  submit: [password: string, done: () => void]
  cancel: []
}>()

const visible = ref(false)
const loading = ref(false)
const formRef = ref<FormInst | null>(null)
const form = reactive({
  password: ''
})

const rules: FormRules = {
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function show() {
  visible.value = true
  loading.value = false
  form.password = ''
  nextTick(() => {
    formRef.value?.restoreValidation()
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

  try {
    await formRef.value.validate()
  } catch {
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
  <NModal
    v-model:show="visible"
    preset="card"
    title="相册密码验证"
    style="width: 400px; border-radius: 10px"
    :mask-closable="false"
    :close-on-esc="false"
    :closable="false"
  >
    <div class="password-dialog">
      <div class="dialog-icon">
        <i class="fas fa-lock"></i>
      </div>
      <p class="dialog-tip">这是一个加密相册，请输入密码访问</p>
      <NForm ref="formRef" :model="form" :rules="rules" @submit.prevent="handleSubmit">
        <NFormItem path="password" :show-label="false">
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入相册密码"
            @keyup.enter="handleSubmit"
          >
            <template #prefix>
              <i class="fas fa-key"></i>
            </template>
          </NInput>
        </NFormItem>
      </NForm>
      <div class="dialog-footer">
        <NButton @click="handleCancel">返回</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit"> 确认 </NButton>
      </div>
    </div>
  </NModal>
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
