import type {Directive} from 'vue'

export const animateOnScroll: Directive<HTMLElement> = {
  mounted(el) {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) {
            continue
          }

          el.classList.add('animate-in')
          observer.unobserve(el)
        }
      },
      {
        threshold: 0.1
      }
    )

    observer.observe(el)
  }
}
