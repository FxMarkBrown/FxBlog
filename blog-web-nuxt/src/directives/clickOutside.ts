import type { Directive, DirectiveBinding } from 'vue'

interface ClickOutsideElement extends HTMLElement {
  clickOutsideEvent?: (event: MouseEvent) => void
}

const ClickOutside: Directive<ClickOutsideElement, (event: MouseEvent) => void> = {
  beforeMount(el, binding: DirectiveBinding<(event: MouseEvent) => void>) {
    el.clickOutsideEvent = (event: MouseEvent) => {
      const target = event.target as Node | null
      if (!target || el === target || el.contains(target)) {
        return
      }

      binding.value?.(event)
    }

    document.addEventListener('click', el.clickOutsideEvent)
  },
  unmounted(el) {
    if (el.clickOutsideEvent) {
      document.removeEventListener('click', el.clickOutsideEvent)
    }
  }
}

export default ClickOutside
