<template>
    <div class="app-container">
        <!-- 搜索表单 -->
        <div class="search-wrapper">
            <el-form ref="queryFormRef" :model="queryParams" :inline="true">
                <el-form-item label="文件名" prop="filename">
                    <el-input v-model="queryParams.filename" placeholder="请输入文件名" clearable
                        @keyup.enter="handleQuery" />
                </el-form-item>
                <el-form-item label="文件类型" prop="ext">
                    <el-select v-model="queryParams.ext" placeholder="请选择文件类型" clearable>
                        <el-option v-for="item in fileTypeOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                    <el-button icon="Refresh" @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>
        </div>

        <!-- 操作按钮区域 -->
        <el-card class="box-card">
            <template #header>
                <div class="card-header file-toolbar">
                    <ButtonGroup class="file-toolbar-group">
                        <span class="toolbar-label">上传分组</span>
                        <el-input
                            class="upload-source-input"
                            v-model="uploadSource"
                            placeholder="如 manual、docs"
                            clearable
                        />
                        <el-upload
                            :show-file-list="false"
                            :http-request="handleManualUpload"
                            :multiple="true"
                        >
                            <el-button type="primary" icon="Upload">上传文件</el-button>
                        </el-upload>
                        <el-button type="success" icon="Setting" @click="handleOpenOssConfig">云存储配置</el-button>
                    </ButtonGroup>
                </div>
            </template>
            <!-- 数据表格 -->
            <el-table v-loading="loading" :data="fileList" style="width: 100%">
                <el-table-column label="文件内容" align="center" prop="filename" width="92">
                    <template #default="scope">
                        <el-image
                            v-if="isImageFile(scope.row)"
                            :preview-src-list="[scope.row.url]"
                            :initial-index="0"
                            :src="scope.row.url"
                            class="file-preview-image"
                        />
                        <div v-else class="file-placeholder">
                            {{ formatFileBadge(scope.row.ext) }}
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="文件名" align="center" prop="filename" min-width="140" show-overflow-tooltip />
                <el-table-column label="上传分组" align="center" prop="source" width="160" show-overflow-tooltip />
                <el-table-column label="文件类型" align="center" prop="ext" width="100" show-overflow-tooltip />
                <el-table-column label="文件大小" align="center" prop="size" width="110">
                    <template #default="scope">
                        <span>{{ (scope.row.size / 1024).toFixed(1) }} KB</span>
                    </template>
                </el-table-column>
                <el-table-column label="访问路径" align="center" prop="url" min-width="300">
                    <template #default="scope">
                        <div class="file-path-cell">{{ scope.row.url }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="存储地址" align="center" prop="url" min-width="260">
                    <template #default="scope">
                        <div class="file-path-cell">{{ scope.row.basePath }}{{ scope.row.path }}{{ scope.row.filename }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="存储平台" align="center" prop="platform" width="110">
                    <template #default="scope">
                        <span v-for="item in ossOptions">
                            <el-tag :type="item.style" v-if="scope.row.platform === item.value">
                                {{ item.label }}
                            </el-tag>
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="上传时间" align="center" prop="createTime" width="168" />
                <el-table-column label="操作" align="center" width="116" fixed="right">
                    <template #default="scope">
                        <div class="file-actions">
                            <el-button type="success" link icon="DocumentCopy"
                                @click="handleCopyUrl(scope.row)">复制路径</el-button>
                            <el-button v-permission="['sys:file:delete']" type="danger" link icon="Delete"
                                @click="handleDelete(scope.row)">删除</el-button>
                            <el-button type="primary" link icon="Download" @click="handleDownload(scope.row)">下载</el-button>
                        </div>
                    </template>

                </el-table-column>
            </el-table>

            <!-- 分页组件 -->
            <div class="pagination-container">
                <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
                    :page-sizes="[10, 20, 30, 50]" :total="total" :background="true"
                    layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
                    @current-change="handleCurrentChange" />
            </div>
        </el-card>

        <!-- 云存储配置 -->
        <el-drawer v-model="drawerVisible" title="云存储配置" direction="rtl" size="40%">
            <el-form :model="ossConfigForm" label-position="left" label-width="100px" :rules="rules"
                ref="ossConfigFormRef">
                <el-form-item label="平台" prop="platform">
                    <el-radio-group v-model="ossConfigForm.platform" @change="handleChangePlatform">
                        <el-radio v-for="item in ossOptions" :key="item.value" :value="item.value">
                            {{ item.label }}
                        </el-radio>
                    </el-radio-group>
                </el-form-item>
                <div v-if="ossConfigForm.platform !== 'local'">
                    <el-form-item label="access-key" prop="accessKey">
                        <el-input v-model="ossConfigForm.accessKey" placeholder="请输入accessKey" />
                    </el-form-item>
                    <el-form-item label="secret-key" prop="secretKey">
                        <el-input v-model="ossConfigForm.secretKey" placeholder="请输入secretKey" />
                    </el-form-item>
                    <el-form-item label="空间名" prop="bucket">
                        <el-input v-model="ossConfigForm.bucket" placeholder="请输入空间名" />
                    </el-form-item>
                    <el-form-item label="地域" prop="region">
                        <el-input v-model="ossConfigForm.region" placeholder="请输入地域" />
                    </el-form-item>
                </div>

                <el-form-item label="域名" prop="domain">
                    <el-input v-model="ossConfigForm.domain" placeholder="本地推荐填 /static/，云存储可填完整域名并以 / 结尾" />
                </el-form-item>
                <el-form-item label="存储基础路径" prop="basePath">
                    <el-input v-model="ossConfigForm.basePath" placeholder="请输入存储基础路径，/结尾" />
                </el-form-item>
                <div v-if="ossConfigForm.platform === 'local'">
                    <el-form-item label="本地存储路径" prop="storagePath" label-width="120px">
                        <el-input v-model="ossConfigForm.storagePath" placeholder="请输入本地存储路径，/结尾,如 D:/Temp/" />
                    </el-form-item>
                    <el-form-item label="访问路径" v-if="ossConfigForm.enableAccess === 1" prop="pathPatterns"
                        label-width="120px">
                        <el-input v-model="ossConfigForm.pathPatterns" placeholder="例如 /static/**，要与上面的域名路径一致" />
                    </el-form-item>
                    <el-form-item label="启用访问" prop="enableAccess">
                        <el-switch v-model="ossConfigForm.enableAccess" :active-value="1" :inactive-value="0" />
                    </el-form-item>
                </div>
                <el-form-item label="启用存储" prop="isEnable">
                    <el-switch v-model="ossConfigForm.isEnable" :active-value="1" :inactive-value="0" />
                </el-form-item>


            </el-form>
            <div class="dialog-footer">
                <el-button type="primary" v-permission="['sys:oss:submit']" icon="CircleCheck"
                    :loading="ossConfigLoading" @click="handleSaveOssConfig">保存</el-button>
            </div>
        </el-drawer>
    </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import { getFileListApi, deleteFileApi, getOssConfigApi, addOssApi, updateOssApi, uploadApi } from '@/api/file'
import { getDictDataByDictTypesApi } from '@/api/system/dict'

const createDefaultOssConfigForm = () => ({
    id: undefined,
    platform: '',
    accessKey: '',
    secretKey: '',
    bucket: '',
    region: '',
    domain: '',
    basePath: '',
    storagePath: '',
    pathPatterns: '',
    enableAccess: 0,
    isEnable: 0
})

// 查询参数
const queryParams = reactive({
    pageNum: 1,
    pageSize: 10,
    filename: undefined,
    ext: undefined
})

const loading = ref(false)
const total = ref(0)
const fileList = ref([])
const fileTypeOptions = ref<any[]>([])
const ossOptions = ref<any[]>([])

const ossConfigList = ref<any[]>([])
const drawerVisible = ref(false)
const ossConfigLoading = ref(false)

const ossConfigForm = ref<any>(createDefaultOssConfigForm())
const ossConfigFormRef = ref<FormInstance | null>(null)
const uploadSource = ref('manual')

const resetOssConfigForm = (platform = '') => {
    ossConfigForm.value = {
        ...createDefaultOssConfigForm(),
        platform
    }
}

// 表单校验规则
const rules = reactive<FormRules>({
    platform: [
        { required: true, message: '平台不能为空', trigger: 'blur' }
    ],
    accessKey: [{ required: true, message: 'accessKey不能为空', trigger: 'blur' }],
    secretKey: [{ required: true, message: 'secretKey不能为空', trigger: 'blur' }],
    bucket: [{ required: true, message: 'bucket不能为空', trigger: 'blur' }],
    domain: [
        { required: true, message: '域名不能为空', trigger: 'blur' }
    ],
    basePath: [
        { required: false, message: '存储基础路径不能为空', trigger: 'blur' }
    ],
    storagePath: [
        { required: true, message: '本地存储路径不能为空', trigger: 'blur' }
    ],
    enableAccess: [
        { required: true, message: '启用访问不能为空', trigger: 'blur' }
    ],
    pathPatterns: [
        { required: true, message: '访问路径不能为空', trigger: 'blur' }
    ]
})


// 获取文件列表
const getList = async () => {
    loading.value = true
    try {
        const { data } = await getFileListApi(queryParams)
        fileList.value = data.records
        total.value = data.total
    } catch (error) {
        fileList.value = []
        total.value = 0
    }
    loading.value = false
}


// 获取状态列表
const getDictList = async () => {
    try {
        const { data } = await getDictDataByDictTypesApi(['sys_file_type', 'sys_file_oss'])
        fileTypeOptions.value = data.sys_file_type.list
        ossOptions.value = data.sys_file_oss.list
    } catch (error) {
        fileTypeOptions.value = []
        ossOptions.value = []
    }
}

// 删除
const handleDelete = (row: any) => {
    ElMessageBox.confirm(`是否确认删除 ${row.filename} 这个文件?`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await deleteFileApi(row.url)
            ElMessage.success('删除成功')
            getList()
        } catch (error) {
        }
    }).catch(() => undefined)
}

const handleManualUpload = async (options: UploadRequestOptions) => {
    try {
        const formData = new FormData()
        formData.append('file', options.file)
        await uploadApi(formData, uploadSource.value || 'manual')
        ElMessage.success(`${options.file.name} 上传成功`)
        options.onSuccess?.({})
        getList()
    } catch (error) {
        options.onError?.(error as any)
    }
}

const isImageFile = (row: any) => {
    const ext = String(row?.ext || '').toLowerCase()
    const contentType = String(row?.contentType || '').toLowerCase()
    return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'].includes(ext) || contentType.startsWith('image/')
}

const formatFileBadge = (ext?: string) => {
    const normalized = String(ext || '').trim().toUpperCase()
    return normalized || 'FILE'
}

const handleCopyUrl = async (row: any) => {
    if (!row?.url) {
        ElMessage.warning('文件地址不存在')
        return
    }
    try {
        await navigator.clipboard.writeText(row.url)
        ElMessage.success('路径已复制')
    } catch (error) {
        ElMessage.error('复制失败')
    }
}

// 获取存储配置
const getOssConfig = () => {
    getOssConfigApi()
        .then((res) => {
            ossConfigList.value = Array.isArray(res.data) ? res.data : []
        })
        .catch(() => {
            ossConfigList.value = []
        })
}


// 打开存储配置
const handleOpenOssConfig = () => {
    if (ossOptions.value.length === 0) {
        ElMessage.warning('请先在字典添加云存储类型')
        return
    }
    resetOssConfigForm(ossOptions.value[0]?.value || '')

    const ossConfig = ossConfigList.value.find((item: any) => {
        if (item.isEnable === 1) {
            return item
        }
    })
    if (ossConfig) {
        Object.assign(ossConfigForm.value, ossConfig)
    }
    drawerVisible.value = true
    nextTick(() => {
        ossConfigFormRef.value?.clearValidate()
    })
}

// 平台改变
const handleChangePlatform = () => {
    const currentPlatform = ossConfigForm.value.platform
    resetOssConfigForm(currentPlatform)

    const ossConfig = ossConfigList.value.find((item: any) => {
        if (item.platform === ossConfigForm.value.platform) {
            return item
        }
    })

    if (ossConfig) {
        Object.assign(ossConfigForm.value, ossConfig)
    }
}

// 保存云存储配置
const handleSaveOssConfig = async () => {
    const valid = await ossConfigFormRef.value?.validate().catch(() => false)
    if (!valid) {
        return
    }

    ossConfigLoading.value = true
    try {
        if (ossConfigForm.value.id) {
            await updateOssApi(ossConfigForm.value)
            ElMessage.success('修改成功')
        } else {
            await addOssApi(ossConfigForm.value)
            ElMessage.success('保存成功')
        }
        drawerVisible.value = false
        getOssConfig()
    } finally {
        ossConfigLoading.value = false
    }
}

watch(drawerVisible, (visible) => {
    if (!visible) {
        ossConfigLoading.value = false
        resetOssConfigForm()
        nextTick(() => {
            ossConfigFormRef.value?.clearValidate()
        })
    }
})
// 下载
const handleDownload = (row: any) => {
    if (!row?.url) {
        ElMessage.warning('文件地址不存在')
        return
    }
    const openedWindow = window.open(row.url, '_blank', 'noopener,noreferrer')
    if (!openedWindow) {
        ElMessage.error('文件打开失败，请检查浏览器拦截设置')
    }
}

// 分页大小改变
const handleSizeChange = (val: number) => {
    queryParams.pageSize = val
    getList()
}

// 页码改变
const handleCurrentChange = (val: number) => {
    queryParams.pageNum = val
    getList()
}

// 搜索
const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
}

// 重置
const resetQuery = () => {
    queryParams.pageNum = 1
    queryParams.filename = undefined
    queryParams.ext = undefined
    getList()
}

// 初始化
onMounted(() => {
    getList()
    getDictList()
    getOssConfig()
})
</script>

<style scoped>
.file-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
}

.file-toolbar-group {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.toolbar-label {
    color: var(--el-text-color-regular);
    font-size: 13px;
    white-space: nowrap;
}

.upload-source-input {
    width: 190px;
}

.file-preview-image,
.file-placeholder {
    width: 56px;
    height: 56px;
    margin: 0 auto;
    border-radius: 10px;
}

.file-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-regular);
    font-size: 12px;
    font-weight: 600;
}

.file-path-cell {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 12px;
    line-height: 1.5;
    text-align: left;
    color: var(--el-text-color-regular);
    word-break: break-all;
}

.file-actions {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
}
</style>
