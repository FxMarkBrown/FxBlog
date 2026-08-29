<script setup lang="ts">
/**
 * 首页 Hero：自设计抽象渐变封面（光斑 + 网格 + 噪点，非动漫图）
 * + 居中大标题 + 打字机胶囊 + 双层 SVG 错速流动波浪 + 下箭头。
 * 挂载时通过 ui store 置 hasHero，驱动 Header 透明悬浮模式。
 */
interface Props {
  title?: string
  phrases?: string[]
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  phrases: () => [],
  height: '60vh'
})

const siteStore = useSiteStore()
const uiStore = useUiStore()

const heroTitle = computed(
  () => props.title || siteStore.websiteInfo.name || siteStore.websiteInfo.title || '个人知识库'
)
const heroPhrases = computed(() => {
  if (props.phrases.length > 0) {
    return props.phrases
  }
  const summary = siteStore.websiteInfo.summary || siteStore.websiteInfo.description
  return [summary || '记录知识，沉淀思考', '相信记录的力量']
})

// ---- 打字机 ----
const typedText = ref('')
let typerTimer: ReturnType<typeof setTimeout> | undefined

function typeLoop(phraseIndex: number, charIndex: number, deleting: boolean) {
  const list = heroPhrases.value
  if (list.length === 0) {
    return
  }

  const phrase = list[phraseIndex % list.length]
  if (phrase === undefined) {
    return
  }

  if (!deleting) {
    typedText.value = phrase.slice(0, charIndex + 1)
    if (charIndex + 1 >= phrase.length) {
      typerTimer = setTimeout(() => typeLoop(phraseIndex, charIndex, true), 2200)
      return
    }
    typerTimer = setTimeout(() => typeLoop(phraseIndex, charIndex + 1, false), 130)
    return
  }

  typedText.value = phrase.slice(0, charIndex - 1)
  if (charIndex - 1 <= 0) {
    typerTimer = setTimeout(() => typeLoop(phraseIndex + 1, 0, false), 500)
    return
  }
  typerTimer = setTimeout(() => typeLoop(phraseIndex, charIndex - 1, true), 55)
}

const heroRef = ref<HTMLElement | null>(null)

function handleScrollDown() {
  const heroHeight = heroRef.value?.offsetHeight ?? window.innerHeight * 0.6
  window.scrollTo({ top: Math.max(heroHeight - 60, 0), behavior: 'smooth' })
}

onMounted(() => {
  uiStore.setHasHero(true)
  typerTimer = setTimeout(() => typeLoop(0, 0, false), 400)
})

onBeforeUnmount(() => {
  uiStore.setHasHero(false)
  clearTimeout(typerTimer)
})
</script>

<template>
  <section ref="heroRef" class="hero" :style="{ height }">
    <div class="hero-bg">
      <div class="hero-spots"></div>
      <div class="hero-grid"></div>
      <div class="hero-noise"></div>
    </div>
    <div class="hero-mask"></div>

    <div class="hero-content">
      <h1 class="hero-title">{{ heroTitle }}</h1>
      <div class="hero-typer">
        <span class="hero-typer-text">{{ typedText }}</span>
        <span class="hero-typer-cursor">|</span>
      </div>
    </div>

    <div class="hero-waves" aria-hidden="true">
      <svg class="wave wave-back" viewBox="0 0 2880 120" preserveAspectRatio="none">
        <defs>
          <path
            id="hero-wave-back"
            d="M0,64 C180,96 360,32 540,48 C720,64 900,112 1080,96 C1260,80 1380,48 1440,64 L1440,120 L0,120 Z"
          />
        </defs>
        <use href="#hero-wave-back" />
        <use href="#hero-wave-back" x="1440" />
      </svg>
      <svg class="wave wave-front" viewBox="0 0 2880 120" preserveAspectRatio="none">
        <defs>
          <path
            id="hero-wave-front"
            d="M0,84 C240,52 480,108 720,92 C960,76 1200,44 1440,84 L1440,120 L0,120 Z"
          />
        </defs>
        <use href="#hero-wave-front" />
        <use href="#hero-wave-front" x="1440" />
      </svg>
    </div>

    <button class="hero-arrow" type="button" aria-label="向下滚动" @click="handleScrollDown">
      <i class="fas fa-chevron-down"></i>
    </button>
  </section>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.hero {
  position: relative;
  // 抵消 #__nuxt 的 70px 顶距，让 Hero 铺满视口顶部、Header 透明悬浮其上
  margin-top: -70px;
  min-height: 380px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(160deg, #16213c 0%, #1a3a4a 52%, #12203a 100%);
}

// 柔和多色光斑层，缓慢漂移
.hero-spots {
  position: absolute;
  inset: -10%;
  background:
    radial-gradient(ellipse 55% 45% at 22% 30%, rgba(57, 197, 187, 0.5), transparent 62%),
    radial-gradient(ellipse 45% 55% at 76% 22%, rgba(3, 169, 244, 0.38), transparent 60%),
    radial-gradient(ellipse 50% 42% at 62% 78%, rgba($primary, 0.3), transparent 65%),
    radial-gradient(ellipse 42% 40% at 30% 82%, rgba(255, 165, 0, 0.24), transparent 62%);
  animation: hero-drift 22s ease-in-out infinite alternate;
}

@keyframes hero-drift {
  from {
    transform: translate3d(-2%, -1.5%, 0) scale(1);
  }

  to {
    transform: translate3d(2%, 2%, 0) scale(1.05);
  }
}

// 细网格纹理
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 75% 70% at 50% 45%, black 30%, transparent 100%);
}

// 噪点
.hero-noise {
  position: absolute;
  inset: 0;
  opacity: 0.05;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>");
}

.hero-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
}

.hero-content {
  position: relative;
  z-index: 2;
  padding-top: 70px;
  text-align: center;
  color: #fff;
  animation: hero-rise 0.9s ease both;
}

@keyframes hero-rise {
  from {
    opacity: 0;
    transform: translateY(28px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 4px;
  text-shadow: 0 2px 16px rgba(0, 0, 0, 0.35);

  @include responsive(md) {
    font-size: 28px;
    letter-spacing: 2px;
  }
}

// 打字机胶囊：半透黑底 + 圆角
.hero-typer {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 18px;
  padding: 10px 20px;
  min-height: 44px;
  max-width: min(86vw, 640px);
  background: rgba(0, 0, 0, 0.45);
  border-radius: 10px;
  font-size: 17px;
  line-height: 1.5;

  @include responsive(md) {
    font-size: 14px;
  }
}

.hero-typer-text {
  white-space: nowrap;
  overflow: hidden;
}

.hero-typer-cursor {
  animation: hero-blink 0.75s infinite;
  font-weight: 300;
}

@keyframes hero-blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

// 双层错速流动波浪，填充页面底色与下方内容衔接
.hero-waves {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 90px;
  z-index: 1;

  @include responsive(md) {
    height: 56px;
  }
}

.wave {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 200%;
  height: 100%;
  fill: var(--background);
}

.wave-back {
  opacity: 0.5;
  animation: wave-slide-left 26s linear infinite;
}

.wave-front {
  animation: wave-slide-right 18s linear infinite;
}

@keyframes wave-slide-left {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(-50%);
  }
}

@keyframes wave-slide-right {
  from {
    transform: translateX(-50%);
  }

  to {
    transform: translateX(0);
  }
}

.hero-arrow {
  position: absolute;
  bottom: 96px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  font-size: 22px;
  cursor: pointer;
  animation: hero-float 1.8s ease-in-out infinite;

  @include responsive(md) {
    bottom: 60px;
  }

  &:hover {
    color: #fff;
  }
}

@keyframes hero-float {
  0%,
  100% {
    transform: translate(-50%, 0);
  }

  50% {
    transform: translate(-50%, 10px);
  }
}
</style>
