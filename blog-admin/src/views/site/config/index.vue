<template>
  <div class="app-container">
    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 基本信息 Tab -->
        <el-tab-pane name="basic">
          <template #label>
            <el-icon>
              <Setting />
            </el-icon>
            <span class="tab-label">基本信息</span>
          </template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="网站Logo" prop="logo">
                  <upload-image v-model="form.logo" :limit="1" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="网站名称" prop="name">
                  <el-input v-model="form.name" placeholder="请输入网站名称" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="网站介绍" prop="summary">
                  <el-input v-model="form.summary" type="textarea" placeholder="请输入网站介绍" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="备案号" prop="recordNum">
                  <el-input v-model="form.recordNum" placeholder="请输入备案号" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="网站地址" prop="webUrl">
                  <el-input v-model="form.webUrl" placeholder="请输入网站地址" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <!-- 作者信息 Tab -->
        <el-tab-pane name="author" lazy>
          <template #label>
            <el-icon>
              <User />
            </el-icon>
            <span class="tab-label">作者信息</span>
          </template>
          <el-form ref="formRef" :model="form" :rules="rules">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="作者头像" prop="authorAvatar">
                  <upload-image v-model="form.authorAvatar" :limit="1" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="作者名称" prop="author">
                  <el-input v-model="form.author" placeholder="请输入作者名称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="个性签名" prop="authorInfo">
                  <el-input v-model="form.authorInfo" placeholder="请输入个性签名" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="关于我" prop="aboutMe" class="about-me-form-item">
              <div v-if="activeTab === 'author'" class="fx-wang-editor fx-wang-editor--page">
                <Toolbar
                  class="fx-wang-editor__toolbar"
                  :editor="editorRef"
                  :defaultConfig="toolbarConfig"
                  :mode="mode"
                />
                <Editor
                  v-model="form.aboutMe"
                  class="fx-wang-editor__content"
                  style="height: 420px; overflow-y: hidden"
                  :defaultConfig="editorConfig"
                  :mode="mode"
                  @onCreated="handleCreated"
                />
              </div>
            </el-form-item>

          </el-form>
        </el-tab-pane>

        <!-- 社交信息 Tab -->
        <el-tab-pane name="social">
          <template #label>
            <el-icon>
              <Share />
            </el-icon>
            <span class="tab-label">社交信息</span>
          </template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
            <el-form-item label="Github地址" prop="github">
              <el-input v-model="form.github" placeholder="请输入Github地址">
                <template #prefix>
                  <el-icon>
                    <ElementPlus />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="Gitee地址" prop="gitee">
              <el-input v-model="form.gitee" placeholder="请输入Gitee地址">
                <template #prefix>
                  <el-icon>
                    <Platform />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="QQ号" prop="qqNumber">
              <el-input v-model="form.qqNumber" placeholder="请输入QQ号">
                <template #prefix>
                  <el-icon>
                    <ChatDotRound />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="QQ群" prop="qqGroup">
              <el-input v-model="form.qqGroup" placeholder="请输入QQ群">
                <template #prefix>
                  <el-icon>
                    <User />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="微信" prop="wechat">
              <el-input v-model="form.wechat" placeholder="请输入微信号">
                <template #prefix>
                  <el-icon>
                    <ChatLineRound />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱地址">
                <template #prefix>
                  <el-icon>
                    <Message />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 网站设置 Tab -->
        <el-tab-pane name="settings">
          <template #label>
            <el-icon>
              <Tools />
            </el-icon>
            <span class="tab-label">网站设置</span>
          </template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="游客头像" prop="touristAvatar">
                  <upload-image v-model="form.touristAvatar" :limit="1" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="显示的社交信息" prop="showList">
                  <el-select v-model="showList" multiple placeholder="请选择要显示的社交信息">
                    <el-option label="邮箱" value="email" />
                    <el-option label="QQ" value="qq" />
                    <el-option label="QQ群" value="qqGroup" />
                    <el-option label="Github" value="github" />
                    <el-option label="Gitee" value="gitee" />
                    <el-option label="微信" value="wechat" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="登录方式" prop="loginTypeList">
                  <el-select v-model="loginTypeList" multiple placeholder="请选择登录方式">
                    <el-option v-for="item in loginTypes" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="开启评论">
                  <el-switch v-model="form.openComment" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="开启灯笼">
                  <el-switch v-model="form.openLantern" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <el-tab-pane name="weather">
          <template #label>
            <el-icon>
              <Cloudy />
            </el-icon>
            <span class="tab-label">天气氛围</span>
          </template>
          <el-form ref="formRef" :model="form" label-width="120px">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="启用天气氛围">
                  <el-switch v-model="form.weatherEnabled" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="天气城市" prop="weatherCity">
                  <el-input v-model="form.weatherCity" placeholder="默认北京，可填写城市名" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="天气模式">
                  <el-radio-group v-model="form.weatherMode">
                    <el-radio-button value="auto">自动</el-radio-button>
                    <el-radio-button value="manual">手动</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="手动天气" v-if="form.weatherMode === 'manual'">
                  <el-select v-model="form.weatherManualType" placeholder="请选择天气类型">
                    <el-option v-for="item in weatherTypes" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="特效强度">
                  <el-select v-model="form.weatherIntensity" placeholder="请选择强度">
                    <el-option label="轻量" value="light" />
                    <el-option label="标准" value="normal" />
                    <el-option label="丰富" value="rich" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="刷新间隔(分钟)">
                  <el-input-number v-model="form.weatherRefreshMinutes" :min="10" :max="180" :step="10" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="行政区编码">
                  <el-input v-model="form.weatherAdcode" placeholder="可选，提升定位稳定性" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="经度">
                  <el-input v-model="form.weatherLng" placeholder="可选" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="纬度">
                  <el-input v-model="form.weatherLat" placeholder="可选" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <!-- 底部按钮 -->
      <div class="bottom-buttons">
        <el-button icon="Refresh" type="primary" :loading="submitLoading" v-permission="['sys:web:update']" @click="submitForm">保存配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import UploadImage from '@/components/Upload/Image.vue'
import { getWebConfigApi, updateWebConfigApi } from '@/api/site/config'
import { getDictDataByDictTypesApi } from '@/api/system/dict'
import { uploadApi } from '@/api/file'

import { Editor, Toolbar } from '@wangeditor-next/editor-for-vue'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor-next/editor'
import '@wangeditor-next/editor/dist/css/style.css'
import {
  ChatDotRound,
  ChatLineRound,
  Cloudy,
  ElementPlus,
  Message,
  Platform, Setting,
  Share,
  Tools,
  User
} from "@element-plus/icons-vue";

type WangEditorInsertFn = (url: string, alt?: string, href?: string) => void

const editorRef = shallowRef<IDomEditor | null>(null)
const mode = 'default'
const toolbarConfig: Partial<IToolbarConfig> = {}
const editorConfig: Partial<IEditorConfig> = {
  placeholder: "请输入内容...",
  MENU_CONF: {
    // 配置上传图片
    uploadImage: {
      customUpload: contentUpload,
    },

    codeSelectLang: {
      // 代码语言
      codeLangs: [
        { text: "CSS", value: "css" },
        { text: "HTML", value: "html" },
        { text: "XML", value: "xml" },
        { text: "Java", value: "java" },
        // 其他
      ],
    },
  },
}


const activeTab = ref('basic')
const formRef = ref<FormInstance>()
const form = ref({
  logo: '',
  name: '',
  summary: '',
  recordNum: '',
  webUrl: '',
  author: '',
  authorInfo: '',
  authorAvatar: '',
  github: '',
  gitee: '',
  qqNumber: '',
  qqGroup: '',
  wechat: '',
  email: '',
  showList: '',
  loginTypeList: '',
  openComment: 1,
  touristAvatar: '',
  bulletin: '',
  aboutMe: '',
  weatherEnabled: 1,
  weatherCity: '北京',
  weatherMode: 'auto',
  weatherManualType: '',
  weatherIntensity: 'normal',
  weatherRefreshMinutes: 30,
  weatherAdcode: '',
  weatherLng: '',
  weatherLat: '',
  openLantern: 0
})
const showList = ref([])
const loginTypeList = ref([])
const weatherTypes = [
  { label: '晴天', value: 'sunny' },
  { label: '多云', value: 'cloudy' },
  { label: '阴天', value: 'overcast' },
  { label: '小雨', value: 'light_rain' },
  { label: '大雨', value: 'heavy_rain' },
  { label: '雷暴', value: 'thunderstorm' },
  { label: '下雪', value: 'snow' },
  { label: '大雾', value: 'fog' },
  { label: '大风', value: 'windy' },
  { label: '沙尘', value: 'dust' }
]
const loginTypes = ref<any>([])
const DEFAULT_LOGIN_TYPES = [
  { label: 'QQ', value: 'qq' },
  { label: '微博', value: 'weibo' },
  { label: '微信', value: 'wechat' },
  { label: 'GitHub', value: 'github' },
  { label: 'Gitee', value: 'gitee' }
]
const mergeLoginTypes = (items: any[] = []) => {
  const map = new Map(DEFAULT_LOGIN_TYPES.map((item) => [item.value, item]))
  items.forEach((item) => {
    if (item?.value) {
      map.set(item.value, item)
    }
  })
  return Array.from(map.values())
}

const submitLoading = ref(false)

const rules = {
  name: [{ required: true, message: '请输入网站名称', trigger: 'blur' }],
  logo: [{ required: true, message: '请上传网站Logo', trigger: 'change' }],
  summary: [{ required: true, message: '请输入网站介绍', trigger: 'blur' }],
  recordNum: [{ required: true, message: '请输入备案号', trigger: 'blur' }],
  author: [{ required: true, message: '请输入作者名称', trigger: 'blur' }]
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    form.value.showList = JSON.stringify(showList.value)
    form.value.loginTypeList = JSON.stringify(loginTypeList.value)
    await updateWebConfigApi(form.value)
    ElMessage.success('保存成功')
  } catch (error) {
  } finally {
    submitLoading.value = false
  }
}
// 获取字典数据
const getDictDataByDictTypes = async () => {
  try {
    const res = await getDictDataByDictTypesApi(['login_type'])
    loginTypes.value = mergeLoginTypes(res.data.login_type.list || [])
  } catch (error) {
    loginTypes.value = DEFAULT_LOGIN_TYPES
  }
}

const handleCreated = (editor: IDomEditor) => {
  editorRef.value = editor // 记录 editor 实例，重要！
}

//编辑器上传图片
function contentUpload(file: File, insertFn: WangEditorInsertFn) {
  const formData = new FormData()
  formData.append("file", file)
  uploadApi(formData).then((res: any) => {
    insertFn(res.data, "", res.data)
  })
}

onMounted(() => {
  getWebConfigApi().then((res) => {
    form.value = {
      ...form.value,
      ...res.data,
      weatherEnabled: Number(res.data.weatherEnabled ?? 1),
      weatherCity: res.data.weatherCity || '北京',
      weatherMode: res.data.weatherMode || 'auto',
      weatherManualType: res.data.weatherManualType || '',
      weatherIntensity: res.data.weatherIntensity || 'normal',
      weatherRefreshMinutes: Number(res.data.weatherRefreshMinutes ?? 30),
      weatherAdcode: res.data.weatherAdcode || '',
      weatherLng: res.data.weatherLng || '',
      weatherLat: res.data.weatherLat || ''
    }
    try {
      showList.value = form.value.showList ? JSON.parse(form.value.showList) : []
    } catch (error) {
      showList.value = []
    }
    try {
      loginTypeList.value = form.value.loginTypeList ? JSON.parse(form.value.loginTypeList) : []
    } catch (error) {
      loginTypeList.value = []
    }
  }).catch(() => {
    showList.value = []
    loginTypeList.value = []
  })

  getDictDataByDictTypes();
})

onBeforeUnmount(() => {
  editorRef.value?.destroy?.()
})
</script>

<style scoped>
.app-container {
  padding: 10px;
}

.bottom-buttons {
  margin-top: 20px;
  text-align: center;
}

.tab-label {
  margin-left: 4px;
  vertical-align: middle;
}

:deep(.el-tabs__item) {
  display: flex !important;
  align-items: center;
  justify-content: center;
}

:deep(.el-input-group__prepend) {
  padding: 0 10px;
}

.el-form-item {
  max-width: 600px;
}

.about-me-form-item {
  max-width: none;
}

.about-me-form-item :deep(.el-form-item__content) {
  width: 100%;
}
</style>
