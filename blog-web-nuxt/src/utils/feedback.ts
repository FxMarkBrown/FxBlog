import { createDiscreteApi, NInput, type DialogApi, type MessageApi } from 'naive-ui'
import { h, ref } from 'vue'
import { naiveThemeOverrides } from '@/utils/naive-theme'

/**
 * 命令式反馈 API（toast / 确认框）。
 */
interface FeedbackApis {
  message: MessageApi
  dialog: DialogApi
}

let apis: FeedbackApis | null = null

function getApis(): FeedbackApis {
  if (!apis) {
    apis = createDiscreteApi(['message', 'dialog'], {
      configProviderProps: { themeOverrides: naiveThemeOverrides }
    })
  }
  return apis
}

function lazyApi<T extends object>(pick: (apis: FeedbackApis) => T): T {
  return new Proxy({} as T, {
    get: (_target, prop: string | symbol) => {
      const api = pick(getApis()) as unknown as Record<string | symbol, unknown>
      const member = api[prop]
      return typeof member === 'function' ? member.bind(api) : member
    }
  })
}

export const message = lazyApi((a) => a.message)
export const dialog = lazyApi((a) => a.dialog)

export interface PromptInputOptions {
  title: string
  content?: string
  placeholder?: string
  defaultValue?: string
  positiveText?: string
  negativeText?: string
  /** 返回错误文案则阻止提交；返回 true/undefined 通过 */
  validator?: (value: string) => string | true | undefined
}

/**
 * 文本输入对话框。
 * 确认时 resolve 输入值，取消/关闭时 reject（调用方用 try/catch 或 .catch 忽略）。
 */
export function promptInput(options: PromptInputOptions): Promise<string> {
  return new Promise((resolve, reject) => {
    const value = ref(options.defaultValue ?? '')
    const error = ref('')

    const submit = () => {
      const result = options.validator?.(value.value)
      if (typeof result === 'string') {
        error.value = result
        return false
      }
      resolve(value.value)
      return true
    }

    dialog.warning({
      title: options.title,
      content: () =>
        h('div', null, [
          options.content ? h('p', { style: 'margin: 0 0 12px;' }, options.content) : null,
          h(NInput, {
            value: value.value,
            placeholder: options.placeholder,
            autofocus: true,
            'onUpdate:value': (v: string) => {
              value.value = v
              error.value = ''
            },
            onKeyup: (event: KeyboardEvent) => {
              if (event.key === 'Enter' && submit()) {
                // naive 会在 onPositiveClick 返回 true 时关闭；回车提交复用同一逻辑
              }
            }
          }),
          error.value ? h('p', { style: 'color: #d03050; margin: 8px 0 0;' }, error.value) : null
        ]),
      positiveText: options.positiveText ?? '确定',
      negativeText: options.negativeText ?? '取消',
      onPositiveClick: submit,
      onNegativeClick: () => reject(new Error('cancel')),
      onClose: () => reject(new Error('cancel')),
      onMaskClick: () => reject(new Error('cancel'))
    })
  })
}
