import type { Directive } from 'vue'

/**
 * 自研 `v-loading` 指令，替代 Element Plus 的 v-loading。
 * 用法：`v-loading="boolean"`，为宿主元素叠加半透明遮罩 + 主色旋转 spinner。
 * 样式定义在 `src/styles/global.scss`（.v-loading-mask / .v-loading-spinner）。
 */
const MASK_CLASS = 'v-loading-mask'
const POSITION_FLAG = 'vLoadingPosition'

function showMask(el: HTMLElement) {
  if (el.querySelector(`:scope > .${MASK_CLASS}`)) {
    return
  }

  // 遮罩绝对定位需要宿主非 static；记住是否由我们改的，卸载时还原
  if (getComputedStyle(el).position === 'static') {
    el.style.position = 'relative'
    el.dataset[POSITION_FLAG] = 'true'
  }

  const mask = document.createElement('div')
  mask.className = MASK_CLASS
  const spinner = document.createElement('div')
  spinner.className = 'v-loading-spinner'
  mask.appendChild(spinner)
  el.appendChild(mask)
}

function hideMask(el: HTMLElement) {
  el.querySelector(`:scope > .${MASK_CLASS}`)?.remove()

  if (el.dataset[POSITION_FLAG]) {
    el.style.position = ''
    Reflect.deleteProperty(el.dataset, POSITION_FLAG)
  }
}

export const vLoading: Directive<HTMLElement, boolean> = {
  mounted(el, binding) {
    if (binding.value) {
      showMask(el)
    }
  },
  updated(el, binding) {
    if (binding.value) {
      showMask(el)
    } else {
      hideMask(el)
    }
  },
  unmounted(el) {
    hideMask(el)
  }
}

export default vLoading
