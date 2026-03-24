<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  forgotPasswordApi,
  getAuthRenderApi,
  getCaptchaSwitchApi,
  getUserInfoApi,
  getWechatIsLoginApi,
  getWechatLoginCodeApi,
  loginApi,
  registerApi,
  sendEmailCodeApi
} from '@/api/auth'
import { useNoIndexSeo } from '@/composables/useSeo'
import LoginParticles from '@/views/login/components/LoginParticles.vue'
import SliderVerify from '@/views/login/components/SliderVerify.vue'
import { WECHAT_QR_PLACEHOLDER } from '@/utils/placeholders'
import { setCookieExpires } from '@/utils/cookie'
import { unwrapResponseData } from '@/utils/response'
import { getThemeMode, initTheme } from '@/utils/theme'
import type { LoginUserInfo } from '@/types/auth'

type LoginFormType = 'login' | 'account' | 'register' | 'forgot'
type LoginType = 'github' | 'qq' | 'wechat' | 'gitee' | 'weibo'

interface LoginOptionItem {
  title: string
  icon: string
}

interface SliderVerifyExpose {
  refresh: () => void
  verifySuccessEvent: () => void
}

const authStore = useAuthStore()
const siteStore = useSiteStore()
const router = useRouter()

const ruleFormRef = ref<FormInstance | null>(null)
const registerFormRef = ref<FormInstance | null>(null)
const forgotFormRef = ref<FormInstance | null>(null)
const sliderVerifyRef = ref<SliderVerifyExpose | null>(null)

const loading = ref(false)
const codeSending = ref(false)
const codeButtonText = ref('发送验证码')
const currentForm = ref<LoginFormType>('login')
const rememberMe = ref(false)
const isShowSliderVerify = ref(false)
const isDarkMode = ref(false)
const previousBodyOverflow = ref('')
const codeTimer = ref<ReturnType<typeof setInterval> | null>(null)
const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)
const wechatQrPlaceholder = WECHAT_QR_PLACEHOLDER

const wechatForm = reactive({
  code: '',
  showQrcode: false
})

const loginForm = reactive<Record<string, unknown>>({
  username: '',
  password: ''
})

const registerForm = reactive({
  nickname: '',
  email: '',
  password: '',
  code: ''
})

const forgotForm = reactive({
  email: '',
  code: '',
  password: ''
})

const loginTypes: Record<LoginType, LoginOptionItem> = {
  github: { title: 'GitHub账号登录', icon: 'fab fa-github' },
  qq: { title: 'QQ账号登录', icon: 'fab fa-qq' },
  wechat: { title: '微信扫码登录', icon: 'fab fa-weixin' },
  gitee: { title: 'Gitee账号登录', icon: 'fab fa-git-alt' },
  weibo: { title: '微博账号登录', icon: 'fab fa-weibo' }
}

const rules = reactive<FormRules>({
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 3, max: 10, message: '长度在 3 到 10 个字符', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '长度在 3 到 50 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 16, message: '长度在 6 到 16 个字符', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
})

const enabledLoginTypeList = computed<string[]>(() => {
  const raw = siteStore.websiteInfo.loginTypeList
  if (Array.isArray(raw)) {
    return raw.map((item) => String(item))
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed.map((item) => String(item)) : []
    } catch {
      return []
    }
  }
  return []
})

const oauthLoginTypes = computed(() => Object.fromEntries(
  Object.entries(loginTypes).filter(([type]) => type !== 'wechat' && enabledLoginTypeList.value.includes(type))
) as Record<string, LoginOptionItem>)

const hasWechatLogin = computed(() => enabledLoginTypeList.value.includes('wechat'))
const hasOauthLogin = computed(() => Object.keys(oauthLoginTypes.value).length > 0)
const showThirdPartySwitch = computed(() => hasWechatLogin.value || hasOauthLogin.value)
const switchFormTooltip = computed(() => {
  if (currentForm.value === 'login') {
    return '账号密码登录'
  }
  return hasWechatLogin.value ? '扫码登录' : '第三方登录'
})

useNoIndexSeo({
  title: '登录',
  description: '登录页面'
})

watch(
  enabledLoginTypeList,
  () => {
    syncThirdPartyLoginState()
  },
  { immediate: true }
)

onMounted(async () => {
  isDarkMode.value = initTheme()
  previousBodyOverflow.value = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('theme-change', syncThemeState)

  if (!siteStore.loaded) {
    await siteStore.fetchWebsiteInfo().catch(() => null)
  }

  if (authStore.isLoggedIn) {
    handleClose()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', syncThemeState)
  document.body.style.overflow = previousBodyOverflow.value
  clearTimer()
})

/**
 * 同步登录页主题状态。
 */
function syncThemeState() {
  isDarkMode.value = getThemeMode() === 'dark'
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
 * 判断指定登录方式是否已开放。
 */
function isLoginTypeEnabled(type: string) {
  return enabledLoginTypeList.value.includes(type)
}

/**
 * 根据站点配置同步当前第三方登录表单状态。
 */
function syncThirdPartyLoginState() {
  if (!['register', 'forgot'].includes(currentForm.value)) {
    currentForm.value = hasWechatLogin.value ? 'login' : 'account'
  }

  clearTimer()
  if (currentForm.value === 'login' && hasWechatLogin.value) {
    getWechatLoginCode()
  }
}

/**
 * 处理滑块验证成功回调。
 */
function onSuccess(captcha: { nonceStr?: string; value: number }) {
  loginForm.nonceStr = captcha.nonceStr || ''
  loginForm.value = captcha.value
  login()
}

/**
 * 登录后静默补全用户资料，确保角色等信息立即生效。
 */
async function hydrateUserInfo(initialUserInfo: LoginUserInfo) {
  authStore.setUserInfo(initialUserInfo)

  try {
    const response = await getUserInfoApi('web')
    const fullUserInfo = unwrapResponseData<LoginUserInfo | null>(response)
    if (fullUserInfo) {
      authStore.setUserInfo(fullUserInfo)
    }
  } catch {
    return
  }
}

/**
 * 执行账号密码登录。
 */
async function login() {
  loading.value = true
  try {
    const response = await loginApi(loginForm)
    const userInfo = unwrapResponseData<LoginUserInfo | null>(response)
    if (!userInfo?.token) {
      showError(String(response.message || response.msg || '登录失败，请重试'))
      refresh()
      return
    }

    authStore.setToken(String(userInfo.token))
    await hydrateUserInfo(userInfo)
    sliderVerifyRef.value?.verifySuccessEvent()
    await siteStore.fetchUnreadStatus().catch(() => null)
    showSuccess('登录成功')
    handleClose()
  } catch (error) {
    showError((error as Error)?.message || '登录失败，请重试')
    refresh()
  } finally {
    loading.value = false
  }
}

/**
 * 处理滑块验证失败回调。
 */
function onFail() {
  showError('验证失败，请重试')
}

/**
 * 处理滑块重复验证回调。
 */
function onAgain() {
  showError('验证失败，请重试')
}

/**
 * 刷新滑块验证码。
 */
function refresh() {
  sliderVerifyRef.value?.refresh()
}

/**
 * 切换登录、注册和找回密码表单。
 */
function switchForm(form: LoginFormType) {
  if (form === 'login' && !showThirdPartySwitch.value) {
    currentForm.value = 'account'
    loading.value = false
    clearTimer()
    return
  }

  currentForm.value = form
  loading.value = false
  clearTimer()
  if (form === 'login' && hasWechatLogin.value) {
    getWechatLoginCode()
  }
}

/**
 * 判断是否需要启用滑块验证码。
 */
function isSliderCaptchaEnabled(payload: unknown) {
  if (typeof payload === 'boolean') {
    return payload
  }
  if (payload && typeof payload === 'object') {
    const configValue = String((payload as Record<string, unknown>).configValue || '')
    return configValue === 'Y'
  }
  return true
}

/**
 * 触发账号密码登录前的表单校验和验证码开关判断。
 */
async function handleLogin() {
  const valid = await ruleFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  try {
    const response = await getCaptchaSwitchApi()
    const switchValue = unwrapResponseData<boolean | Record<string, unknown> | null>(response)
    if (isSliderCaptchaEnabled(switchValue)) {
      isShowSliderVerify.value = true
      return
    }
    await login()
  } catch (error) {
    showError((error as Error)?.message || '获取验证码配置失败')
  }
}

/**
 * 处理邮箱注册。
 */
async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await registerApi(registerForm)
    showSuccess('注册成功')
    switchForm('login')
  } catch (error) {
    showError((error as Error)?.message || '注册失败，请重试')
  } finally {
    loading.value = false
  }
}

/**
 * 处理找回密码。
 */
async function handleResetPassword() {
  const valid = await forgotFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await forgotPasswordApi(forgotForm)
    showSuccess('密码重置成功')
    switchForm('login')
  } catch (error) {
    showError((error as Error)?.message || '重置失败，请重试')
  } finally {
    loading.value = false
  }
}

/**
 * 发送找回密码验证码。
 */
function sendVerificationCode() {
  if (codeSending.value) {
    return
  }
  if (!forgotForm.email) {
    showError('请先输入邮箱')
    return
  }

  codeSending.value = true
  sendEmailCode(String(forgotForm.email))
}

/**
 * 处理第三方登录跳转。
 */
async function handleThirdPartyLogin(type: string) {
  if (!isLoginTypeEnabled(type)) {
    showError('当前登录方式未开放')
    return
  }

  if (type === 'wechat') {
    wechatForm.showQrcode = true
    await getWechatLoginCode()
    return
  }

  try {
    const response = await getAuthRenderApi(type)
    const authUrl = unwrapResponseData<string | null>(response)
    if (!authUrl) {
      showError(String(response.message || response.msg || '获取第三方登录地址失败'))
      return
    }

    const referrer = document.referrer
    if (referrer && !referrer.includes('/login')) {
      setCookieExpires('redirectUrl', referrer, 1)
    }
    window.open(authUrl, '_self')
  } catch (error) {
    showError((error as Error)?.message || '获取第三方登录地址失败')
  }
}

/**
 * 获取微信登录验证码。
 */
async function getWechatLoginCode() {
  if (!hasWechatLogin.value) {
    clearTimer()
    return
  }

  clearTimer()
  try {
    const response = await getWechatLoginCodeApi()
    const code = unwrapResponseData<string | Record<string, unknown> | null>(response)
    wechatForm.code = typeof code === 'string' ? code : String((code as Record<string, unknown> | null)?.code || '')
    pollingWechatIsLogin()
    startWechatCountdown()
  } catch (error) {
    wechatForm.code = '验证码获取失败'
    showError((error as Error)?.message || '获取微信登录验证码失败')
  }
}

/**
 * 开始微信验证码有效期倒计时。
 */
function startWechatCountdown() {
  let countdown = 60
  codeTimer.value = setInterval(() => {
    countdown -= 1
    if (countdown <= 0) {
      clearTimer()
      wechatForm.code = '验证码已失效'
    }
  }, 1000)
}

/**
 * 轮询微信扫码登录状态。
 */
function pollingWechatIsLogin() {
  if (!hasWechatLogin.value) {
    return
  }

  pollingTimer.value = setInterval(async () => {
    try {
      const response = await getWechatIsLoginApi(wechatForm.code)
      const userInfo = unwrapResponseData<LoginUserInfo | null>(response)
      if (!userInfo?.token) {
        return
      }

      authStore.setToken(String(userInfo.token))
      await hydrateUserInfo(userInfo)
      clearTimer()
      await siteStore.fetchUnreadStatus().catch(() => null)
      showSuccess('登录成功')
      handleClose()
    } catch {
      return
    }
  }, 1000)
}

/**
 * 登录成功后关闭登录页。
 */
function handleClose() {
  if (window.history.length > 1) {
    router.go(-1)
    return
  }
  router.push('/')
}

/**
 * 发送注册邮箱验证码。
 */
function sendRegisterCode() {
  if (codeSending.value) {
    return
  }
  if (!registerForm.email) {
    showError('请先输入邮箱')
    return
  }
  codeSending.value = true
  sendEmailCode(registerForm.email)
}

/**
 * 发送邮箱验证码并启动倒计时。
 */
async function sendEmailCode(email: string) {
  try {
    await sendEmailCodeApi(email)
    showSuccess('发送成功，请前往邮箱查看验证码')
    startEmailCodeCountdown()
  } catch (error) {
    showError((error as Error)?.message || '发送失败')
    codeSending.value = false
  }
}

/**
 * 启动邮箱验证码按钮倒计时。
 */
function startEmailCodeCountdown() {
  let countdown = 60
  codeButtonText.value = `${countdown}秒后重试`
  codeTimer.value = setInterval(() => {
    countdown -= 1
    if (countdown <= 0) {
      clearTimer()
      codeSending.value = false
      codeButtonText.value = '发送验证码'
      return
    }
    codeButtonText.value = `${countdown}秒后重试`
  }, 1000)
}

/**
 * 清理所有验证码和轮询定时器。
 */
function clearTimer() {
  if (codeTimer.value) {
    clearInterval(codeTimer.value)
    codeTimer.value = null
  }
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

/**
 * 在扫码登录和账号登录之间切换。
 */
function handleSwitchForm() {
  if (!showThirdPartySwitch.value) {
    switchForm('account')
    return
  }
  if (currentForm.value === 'login') {
    switchForm('account')
    return
  }
  switchForm('login')
}

/**
 * 返回首页。
 */
function backToHome() {
  router.push('/')
}

</script>

<template>
  <div class="login-container" :class="{ 'is-dark': isDarkMode }">
    <ClientOnly>
      <LoginParticles />
    </ClientOnly>
    <div class="login-body">
      <button
        class="back-btn"
        :class="{ 'is-right': !showThirdPartySwitch }"
        type="button"
        :title="'回到首页'"
        aria-label="回到首页"
        @click="backToHome"
      >
        <i class="fas fa-arrow-left"></i>
      </button>

      <button
        v-if="showThirdPartySwitch"
        class="switch-form-btn"
        type="button"
        :title="switchFormTooltip"
        :aria-label="switchFormTooltip"
        @click="handleSwitchForm"
      >
        <i :class="currentForm === 'login' ? 'fas fa-user' : 'fas fa-qrcode'"></i>
      </button>

      <div v-show="currentForm === 'login'" class="form-container">
        <div v-if="hasWechatLogin" class="qrcode-content">
          <div class="qrcode-box">
            <img :src="wechatQrPlaceholder" alt="微信二维码">
          </div>
          <p class="qrcode-tip">
            登录验证码：
            <span class="code-text">{{ wechatForm.code }}</span>
            <span v-if="wechatForm.code === '验证码已失效'" class="code-text">
              <i class="fas fa-sync-alt" @click="getWechatLoginCode"></i>
            </span>
          </p>
          <p class="qrcode-tip">微信扫码关注公众号，并发送验证码</p>
        </div>

        <div v-if="hasOauthLogin" class="divider">
          <ElDivider>{{ hasWechatLogin ? '其他登录方式' : '第三方登录' }}</ElDivider>
        </div>

        <div v-if="hasOauthLogin" class="third-party-login">
          <div
            v-for="(item, type) in oauthLoginTypes"
            :key="type"
            class="login-icon-wrapper"
            :title="item.title"
            :aria-label="item.title"
            @click="handleThirdPartyLogin(type)"
          >
            <div :class="['login-icon', type]">
              <i :class="item.icon"></i>
            </div>
          </div>
        </div>
      </div>

      <div v-show="currentForm === 'account'" class="form-container">
        <div class="form-header">
          <h2 class="form-title">账号密码登录</h2>
          <p class="form-subtitle">欢迎回来,请输入您的账号</p>
        </div>

        <ElForm ref="ruleFormRef" :model="loginForm" :rules="rules">
          <ElFormItem class="form-item" prop="username">
            <ElInput v-model="loginForm.username" placeholder="请输入用户名" size="large" @keyup.enter="handleLogin">
              <template #prefix>
                <i class="fas fa-user"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item" prop="password">
            <ElInput v-model="loginForm.password" placeholder="请输入密码" show-password size="large" @keyup.enter="handleLogin">
              <template #prefix>
                <i class="fas fa-lock"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <div class="form-options">
            <ElCheckbox v-model="rememberMe">记住我</ElCheckbox>
          </div>

          <ElFormItem class="form-item">
            <ElButton class="submit-btn ripple" :loading="loading" type="primary" @click="handleLogin">
              登 录
            </ElButton>
          </ElFormItem>
        </ElForm>

        <div class="form-switch">
          <a @click="switchForm('register')">立即注册</a>
          <span class="divider-line">|</span>
          <a @click="switchForm('forgot')">忘记密码?</a>
        </div>
      </div>

      <div v-show="currentForm === 'register'" class="form-container">
        <div class="form-header">
          <h2 class="form-title">注册账号</h2>
          <p class="form-subtitle">欢迎注册,请输入您的账号</p>
        </div>

        <ElForm ref="registerFormRef" :model="registerForm" :rules="rules">
          <ElFormItem class="form-item" prop="nickname">
            <ElInput v-model="registerForm.nickname" placeholder="请输入昵称">
              <template #prefix>
                <i class="fas fa-user"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item" prop="email">
            <ElInput v-model="registerForm.email" placeholder="请输入邮箱">
              <template #prefix>
                <i class="fas fa-envelope"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item" prop="code">
            <ElInput v-model="registerForm.code" placeholder="请输入验证码">
              <template #prefix>
                <i class="fas fa-key"></i>
              </template>
              <template #append>
                <ElButton :disabled="codeSending" @click="sendRegisterCode">
                  {{ codeButtonText }}
                </ElButton>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item" prop="password">
            <ElInput v-model="registerForm.password" placeholder="请输入密码" show-password>
              <template #prefix>
                <i class="fas fa-lock"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item">
            <ElButton class="submit-btn" :loading="loading" @click="handleRegister">
              注 册
            </ElButton>
          </ElFormItem>

          <div class="form-switch">
            已有账号？<a @click="switchForm('account')">立即登录</a>
          </div>
        </ElForm>
      </div>

      <div v-show="currentForm === 'forgot'" class="form-container">
        <div class="form-header">
          <h2 class="form-title">找回账号</h2>
          <p class="form-subtitle">重置密码,请输入您的邮箱</p>
        </div>

        <ElForm ref="forgotFormRef" :model="forgotForm" :rules="rules">
          <ElFormItem class="form-item" prop="email">
            <ElInput v-model="forgotForm.email" placeholder="请输入注册邮箱">
              <template #prefix>
                <i class="fas fa-envelope"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item" prop="code">
            <ElInput v-model="forgotForm.code" placeholder="请输入验证码">
              <template #prefix>
                <i class="fas fa-key"></i>
              </template>
              <template #append>
                <ElButton :disabled="codeSending" @click="sendVerificationCode">
                  {{ codeButtonText }}
                </ElButton>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item" prop="password">
            <ElInput v-model="forgotForm.password" placeholder="请输入新密码" show-password>
              <template #prefix>
                <i class="fas fa-lock"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem class="form-item">
            <ElButton class="submit-btn" :loading="loading" @click="handleResetPassword">
              重置密码
            </ElButton>
          </ElFormItem>

          <div class="form-switch">
            <a @click="switchForm('account')">返回登录</a>
          </div>
        </ElForm>
      </div>
    </div>

    <ElDialog
      v-model="isShowSliderVerify"
      title="请拖动滑块完成拼图"
      width="360px"
      :close-on-click-modal="false"
      append-to-body
      class="login-slider-dialog"
      @close="refresh"
    >
      <SliderVerify ref="sliderVerifyRef" @success="onSuccess" @fail="onFail" @again="onAgain" />
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.login-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh + 70px);
  margin-top: -70px;
  padding: 24px;
  overflow: hidden;
  isolation: isolate;
  background:
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.22), transparent 30%),
    radial-gradient(circle at bottom right, rgba(139, 92, 246, 0.2), transparent 28%),
    linear-gradient(135deg, #dbeafe 0%, #e0e7ff 38%, #f8fafc 100%);
}

.login-body {
  position: relative;
  width: min(420px, 100%);
  padding: 32px;
  z-index: 2;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow:
    0 30px 70px rgba(15, 23, 42, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(12px) saturate(132%);
}

.form-container {
  animation: fadeIn 0.3s ease;
}

.form-item {
  margin-bottom: 20px;
}

.form-item :deep(.el-input__inner) {
  height: 44px;
  font-size: 14px;
}

.form-item :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.16) inset;
}

.form-item :deep(.el-input__inner)::placeholder {
  color: #9ca3af;
}

.form-item :deep(.el-input__prefix) {
  left: 12px;
  color: #6b7280;
}

.submit-btn {
  width: 100%;
  height: 52px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #5b67f1 0%, #6677ff 100%);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.22em;
  cursor: pointer;
  box-shadow: 0 14px 30px rgba(99, 102, 241, 0.26);
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.submit-btn:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
  box-shadow: 0 18px 36px rgba(99, 102, 241, 0.3);
}

.submit-btn:active {
  transform: translateY(0);
  box-shadow: 0 10px 22px rgba(99, 102, 241, 0.22);
}

.form-item :deep(.el-input-group__append) {
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.form-item :deep(.el-input-group__append .el-button) {
  height: 100%;
  padding: 0 18px;
  border: none;
  border-left: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 0 12px 12px 0;
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
  font-weight: 600;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.form-item :deep(.el-input-group__append .el-button:hover) {
  background: rgba(99, 102, 241, 0.14);
  color: #4338ca;
}

.divider {
  margin: 24px 0;
  color: #9ca3af;
}

.divider :deep(.el-divider__text) {
  padding: 0 12px;
  background-color: rgba(255, 255, 255, 0.74);
  font-size: 14px;
}

.third-party-login {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 24px;
}

.login-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #f3f4f6;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.login-icon:hover {
  transform: translateY(-2px);
}

.login-icon.github {
  color: #24292e;
}

.login-icon.qq {
  color: #12b7f5;
}

.login-icon.wechat {
  color: #07c160;
}

.login-icon.gitee {
  color: #c71d23;
}

.login-icon.weibo {
  color: #e6162d;
}

.form-switch {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: 24px;
  color: #6b7280;
  font-size: 14px;
}

.form-switch a {
  color: $primary;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}

.form-switch a:hover {
  color: darken($primary, 10%);
}

.divider-line {
  margin: 0 12px;
  color: #e5e7eb;
}

.qrcode-content {
  padding: 24px;
  text-align: center;
  animation: fadeIn 0.3s ease;
}

.qrcode-box {
  width: 200px;
  height: 200px;
  margin: 0 auto 16px;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.qrcode-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.qrcode-tip {
  margin: 8px 0;
  color: #6b7280;
  font-size: 14px;
}

.code-text {
  color: #6366f1;
  font-weight: 500;
}

.code-text i {
  margin-left: $spacing-sm;
  cursor: pointer;
}

.form-header {
  margin-bottom: 32px;
  text-align: center;
}

.form-title {
  margin: 0 0 8px;
  color: #1a1a1a;
  font-size: 24px;
  font-weight: 600;
}

.form-subtitle {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.form-options :deep(.el-checkbox__label) {
  color: #64748b;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.switch-form-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #f3f4f6;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.switch-form-btn:hover {
  background: #e5e7eb;
  transform: rotate(180deg);
}

.switch-form-btn i {
  font-size: 20px;
}

.back-btn {
  position: absolute;
  top: 16px;
  right: 60px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #f3f4f6;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn.is-right {
  right: 16px;
}

.back-btn:hover {
  background: #e5e7eb;
  color: #6366f1;
  transform: translateX(-4px);
}

.back-btn i {
  font-size: 20px;
}

.login-container.is-dark {
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.2), transparent 34%),
    radial-gradient(circle at bottom right, rgba(129, 140, 248, 0.18), transparent 30%),
    linear-gradient(135deg, #020617 0%, #0f172a 45%, #111827 100%);
}

.login-container.is-dark .login-body {
  border-color: rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.9);
  box-shadow:
    0 30px 80px rgba(2, 6, 23, 0.48),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.login-container.is-dark .form-title {
  color: #f8fafc;
}

.login-container.is-dark .form-subtitle,
.login-container.is-dark .qrcode-tip,
.login-container.is-dark .form-switch,
.login-container.is-dark .divider {
  color: #94a3b8;
}

.login-container.is-dark .divider :deep(.el-divider__text) {
  background-color: rgba(15, 23, 42, 0.76);
  color: #cbd5e1;
}

.login-container.is-dark .qrcode-box {
  border-color: rgba(148, 163, 184, 0.24);
  background: rgba(15, 23, 42, 0.4);
}

.login-container.is-dark .login-icon,
.login-container.is-dark .switch-form-btn,
.login-container.is-dark .back-btn {
  background: rgba(30, 41, 59, 0.72);
  color: #cbd5e1;
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.12);
}

.login-container.is-dark .switch-form-btn:hover,
.login-container.is-dark .back-btn:hover {
  background: rgba(51, 65, 85, 0.82);
}

.login-container.is-dark .form-item :deep(.el-input__wrapper) {
  background: rgba(15, 23, 42, 0.72);
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.16) inset;
}

.login-container.is-dark .form-item :deep(.el-input__inner) {
  color: #e2e8f0;
}

.login-container.is-dark .form-item :deep(.el-input__inner)::placeholder {
  color: #64748b;
}

.login-container.is-dark .form-item :deep(.el-input__prefix) {
  color: #94a3b8;
}

.login-container.is-dark .form-options :deep(.el-checkbox__label) {
  color: #cbd5e1;
}

.login-container.is-dark .submit-btn {
  background: linear-gradient(135deg, #6466f1 0%, #6d63f4 100%);
  box-shadow: 0 16px 34px rgba(79, 70, 229, 0.28);
}

.login-container.is-dark .form-item :deep(.el-input-group__append .el-button) {
  border-left-color: rgba(148, 163, 184, 0.18);
  background: rgba(99, 102, 241, 0.16);
  color: #c7d2fe;
}

.login-container.is-dark .form-item :deep(.el-input-group__append .el-button:hover) {
  background: rgba(99, 102, 241, 0.24);
  color: #e0e7ff;
}

@media (max-width: 768px) {
  .login-container {
    align-items: flex-start;
    min-height: 100vh;
    margin-top: 0;
    padding: calc(env(safe-area-inset-top, 0px) + 18px) 20px 24px;
  }

  .login-body {
    margin-top: 0;
    padding: 24px 20px 22px;
    border-radius: 24px;
  }

  .form-header {
    margin-bottom: 24px;
  }

  .form-title {
    font-size: 22px;
  }

  .submit-btn {
    height: 48px;
    font-size: 17px;
  }
}
</style>
