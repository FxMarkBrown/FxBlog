import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'

export interface ArticleDraftData {
  savedAt: number
  form: Record<string, any>
}

/**
 * 文章编辑草稿：
 * - 表单变化后防抖 1s 自动写入 localStorage
 * - 进入页面时检测草稿并提示恢复/丢弃
 * - dirty 标记表示有输入尚未写入草稿，用于路由离开/页面卸载前确认
 */
export function useArticleDraft(options: {
  key: string
  form: Record<string, any>
  onRestore: (draftForm: Record<string, any>) => void
}) {
  const dirty = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let stopWatch: (() => void) | null = null

  const readDraft = (): ArticleDraftData | null => {
    try {
      const raw = localStorage.getItem(options.key)
      if (!raw) return null
      const parsed = JSON.parse(raw)
      if (!parsed || typeof parsed !== 'object' || typeof parsed.form !== 'object') return null
      return parsed as ArticleDraftData
    } catch {
      return null
    }
  }

  const writeDraft = () => {
    try {
      const snapshot: ArticleDraftData = {
        savedAt: Date.now(),
        form: JSON.parse(JSON.stringify(options.form))
      }
      localStorage.setItem(options.key, JSON.stringify(snapshot))
    } catch {
      // 隐私模式 / 存储超限时静默失败
    } finally {
      dirty.value = false
    }
  }

  const clearDraft = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    try {
      localStorage.removeItem(options.key)
    } catch {}
    dirty.value = false
  }

  // 卸载前把尚未持久化的输入立即写入草稿
  const flushDraft = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    if (dirty.value) writeDraft()
  }

  const scheduleWrite = () => {
    dirty.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      writeDraft()
    }, 1000)
  }

  // 页面初始化（数据回填 + 草稿检测）完成后再调用，避免把初始数据当成用户输入
  const startWatch = () => {
    if (stopWatch) return
    stopWatch = watch(() => options.form, scheduleWrite, { deep: true })
  }

  /**
   * 检测草稿：草稿与基线（新增页为空白表单、编辑页为服务器数据）不一致时提示恢复。
   * 选择"丢弃"会删除草稿；直接关闭弹窗则保留草稿不做处理。
   */
  const checkDraft = async (baseline: Record<string, any>) => {
    const draft = readDraft()
    if (!draft) return
    if (JSON.stringify(draft.form) === JSON.stringify(baseline)) return

    const timeText = dayjs(draft.savedAt).format('M月D日 HH:mm')
    try {
      await ElMessageBox.confirm(
        `检测到 ${timeText} 的草稿，是否恢复？选择"丢弃草稿"将删除该草稿。`,
        '草稿提示',
        {
          confirmButtonText: '恢复草稿',
          cancelButtonText: '丢弃草稿',
          type: 'info',
          distinguishCancelAndClose: true
        }
      )
      options.onRestore(draft.form)
      ElMessage.success('草稿已恢复')
    } catch (action) {
      if (action === 'cancel') clearDraft()
    }
  }

  const handleBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!dirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => {
    window.addEventListener('beforeunload', handleBeforeUnload)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
    flushDraft()
    stopWatch?.()
    stopWatch = null
  })

  onBeforeRouteLeave(() => {
    if (!dirty.value) return true
    return ElMessageBox.confirm('有修改尚未写入草稿，确定要离开吗？', '提示', {
      confirmButtonText: '离开',
      cancelButtonText: '取消',
      type: 'warning'
    })
      .then(() => true)
      .catch(() => false)
  })

  return {
    dirty,
    checkDraft,
    startWatch,
    clearDraft,
    flushDraft
  }
}
