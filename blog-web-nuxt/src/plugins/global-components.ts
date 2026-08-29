import EmojiPicker from '@/components/Common/EmojiPicker.vue'
import ImagePreview from '@/components/Common/ImagePreview.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { animateOnScroll } from '@/directives/animate'
import ClickOutside from '@/directives/clickOutside'
import vLoading from '@/directives/loading'

export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.component('blog-emoji', EmojiPicker)
  nuxtApp.vueApp.component('blog-image-preview', ImagePreview)
  nuxtApp.vueApp.component('svg-icon', SvgIcon)

  nuxtApp.vueApp.directive('click-outside', ClickOutside)
  nuxtApp.vueApp.directive('animate-on-scroll', animateOnScroll)
  nuxtApp.vueApp.directive('loading', vLoading)
})
