import EmojiPicker from '@/components/Common/EmojiPicker.vue'
import ImagePreview from '@/components/Common/ImagePreview.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { animateOnScroll } from '@/directives/animate'
import ClickOutside from '@/directives/clickOutside'

export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.component('blog-emoji', EmojiPicker)
  nuxtApp.vueApp.component('blog-image-preview', ImagePreview)
  nuxtApp.vueApp.component('svg-icon', SvgIcon)

  nuxtApp.vueApp.directive('click-outside', ClickOutside)
  nuxtApp.vueApp.directive('animate-on-scroll', animateOnScroll)
})
