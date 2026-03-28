<script setup lang="ts">
import { getWeatherEffectApi } from '@/api/site'
import { createWeatherEngine } from '@/components/WeatherDecor/engine'
import { unwrapResponseData } from '@/utils/response'
import {
  DEFAULT_WEATHER_EFFECT,
  type WeatherEffect,
  type WeatherRenderProfile,
  buildCloudLayers,
  normalizeWeatherEffect,
  resolveRefreshMinutes,
  resolveRenderProfile,
  shouldShowCloudLayer
} from '@/components/WeatherDecor/useWeatherDecor'

const route = useRoute()
const siteStore = useSiteStore()
const canvasRef = ref<HTMLElement | null>(null)
const effect = ref<WeatherEffect>({ ...DEFAULT_WEATHER_EFFECT })
const cloudLayers = ref<Array<{ id: string; style: Record<string, string> }>>([])
const loading = ref(false)
const lastFetchAt = ref(0)
const reducedMotion = ref(false)
const isMobile = ref(false)
const deviceMemory = ref(8)
const motionQuery = ref<MediaQueryList | null>(null)
let engine: ReturnType<typeof createWeatherEngine> | null = null
let refreshTimer: ReturnType<typeof setTimeout> | null = null

const themeMode = computed(() => {
  if (!import.meta.client) {
    return 'light'
  }

  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
})

const renderProfile = computed<WeatherRenderProfile>(() =>
  resolveRenderProfile({
    effect: effect.value,
    reducedMotion: reducedMotion.value,
    isMobile: isMobile.value,
    deviceMemory: deviceMemory.value,
    routePath: route.path,
    viewportWidth: import.meta.client ? window.innerWidth : 1440
  })
)

const rootClasses = computed(() => [
  'weather-decor-root',
  `weather-decor--${effect.value.weather}`,
  `weather-season--${effect.value.season}`,
  effect.value.isNight ? 'weather-decor--night' : 'weather-decor--day',
  effect.value.enabled ? 'is-active' : '',
  renderProfile.value.reducedMotion ? 'is-reduced' : '',
  renderProfile.value.pageFactor < 1 ? 'is-soft' : ''
])

const particleClasses = computed(() => [
  'weather-particle-layer',
  effect.value.enabled ? 'is-active' : '',
  renderProfile.value.reducedMotion ? 'is-reduced' : '',
  `weather-decor--${effect.value.weather}`,
  `weather-preset--${renderProfile.value.particlePreset || 'none'}`
])

const showClouds = computed(() => effect.value.enabled && shouldShowCloudLayer(effect.value.weather))
const refreshMinutes = computed(() => resolveRefreshMinutes(siteStore.websiteInfo as Record<string, unknown>))
const sceneSignature = computed(() => {
  const profile = renderProfile.value
  return [
    effect.value.enabled ? 1 : 0,
    effect.value.weather,
    String(profile.accentEffect || 'none'),
    String(profile.particlePreset || 'none'),
    effect.value.season,
    effect.value.isNight ? 1 : 0,
    themeMode.value,
    profile.particleEnabled ? 1 : 0,
    profile.densityScale.toFixed(2),
    profile.pageFactor.toFixed(2),
    profile.isMobile ? 1 : 0
  ].join('|')
})

watch(
  sceneSignature,
  () => {
    cloudLayers.value = buildCloudLayers(renderProfile.value)
    syncEngine()
  },
  { immediate: true }
)

watch(refreshMinutes, () => {
  scheduleRefresh()
})

/**
 * 获取天气装饰配置
 */
async function fetchWeatherEffect() {
  if (loading.value) {
    return
  }

  loading.value = true
  try {
    const response = await getWeatherEffectApi()
    const data = unwrapResponseData<Record<string, unknown> | null>(response) || {}
    effect.value = normalizeWeatherEffect(data)
    lastFetchAt.value = Date.now()
  } catch {
    effect.value = { ...DEFAULT_WEATHER_EFFECT }
  } finally {
    loading.value = false
    scheduleRefresh()
  }
}

/**
 * 安排下一次刷新
 */
function scheduleRefresh() {
  clearRefreshTimer()
  if (!import.meta.client) {
    return
  }

  refreshTimer = window.setTimeout(() => {
    if (document.hidden) {
      scheduleRefresh()
      return
    }

    void fetchWeatherEffect()
  }, refreshMinutes.value * 60 * 1000)
}

/**
 * 清理刷新定时器
 */
function clearRefreshTimer() {
  if (!refreshTimer) {
    return
  }

  clearTimeout(refreshTimer)
  refreshTimer = null
}

/**
 * 处理窗口尺寸变化
 */
function handleResize() {
  if (!import.meta.client) {
    return
  }

  isMobile.value = window.innerWidth < 768
  if (engine) {
    engine.resize()
  }
}

/**
 * 处理页面显隐
 */
function handleVisibilityChange() {
  if (!engine) {
    return
  }

  if (!import.meta.client) {
    return
  }

  if (document.hidden) {
    engine.pause()
    return
  }

  engine.resume()
  if (Date.now() - lastFetchAt.value >= refreshMinutes.value * 60 * 1000) {
    void fetchWeatherEffect()
  }
}

/**
 * 同步粒子引擎
 */
function syncEngine() {
  nextTick(() => {
    if (!canvasRef.value) {
      return
    }

    if (!engine) {
      engine = createWeatherEngine(canvasRef.value)
    }

    engine.start({
      ...renderProfile.value,
      isNight: effect.value.isNight,
      themeMode: themeMode.value
    })

    if (document.hidden) {
      engine.pause()
    }
  })
}

/**
 * 处理减少动态效果偏好变化
 * @param event 媒体查询事件
 */
function handleReducedMotionChange(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
}

/**
 * 初始化减少动态效果监听
 */
function initReducedMotion() {
  if (!import.meta.client) {
    return
  }

  motionQuery.value = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.value.matches

  motionQuery.value.addEventListener('change', handleReducedMotionChange)
}

/**
 * 移除减少动态效果监听
 */
function removeReducedMotionListener() {
  if (!motionQuery.value) {
    return
  }

  motionQuery.value.removeEventListener('change', handleReducedMotionChange)

  motionQuery.value = null
}

onMounted(() => {
  isMobile.value = window.innerWidth < 768
  deviceMemory.value = Number((navigator as Navigator & { deviceMemory?: number }).deviceMemory || 8)
  initReducedMotion()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('resize', handleResize, { passive: true })
  void fetchWeatherEffect()
})

onBeforeUnmount(() => {
  clearRefreshTimer()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('resize', handleResize)
  removeReducedMotionListener()
  if (engine) {
    void engine.destroy()
    engine = null
  }
})
</script>

<template>
  <div class="weather-decor-host">
    <div :class="rootClasses" aria-hidden="true">
      <div class="weather-atmosphere"></div>
      <div class="weather-season-tint"></div>
      <div class="weather-glow"></div>
      <div v-if="effect.enabled && effect.weather === 'sunny' && !effect.isNight" class="weather-sun">
        <span class="weather-sun-core"></span>
        <span class="weather-sun-ring"></span>
      </div>
      <div v-if="effect.enabled && ['cloudy', 'overcast'].includes(effect.weather) && effect.isNight" class="weather-moon">
        <span class="weather-moon-core"></span>
        <span class="weather-moon-halo"></span>
      </div>
      <div v-if="renderProfile.showMist" class="weather-mist">
        <span class="weather-mist-layer weather-mist-layer--a"></span>
        <span class="weather-mist-layer weather-mist-layer--b"></span>
        <span class="weather-mist-layer weather-mist-layer--c"></span>
      </div>
      <div v-if="showClouds" class="weather-clouds">
        <span v-for="cloud in cloudLayers" :key="cloud.id" class="weather-cloud" :style="cloud.style"></span>
      </div>
      <div v-if="renderProfile.showLightning" class="weather-lightning">
        <span class="weather-lightning-flash weather-lightning-flash--a"></span>
        <span class="weather-lightning-flash weather-lightning-flash--b"></span>
      </div>
      <div v-if="effect.enabled && ['light_rain', 'heavy_rain', 'thunderstorm'].includes(effect.weather)" class="weather-rain-veil">
        <span class="weather-rain-veil__layer weather-rain-veil__layer--a"></span>
        <span class="weather-rain-veil__layer weather-rain-veil__layer--b"></span>
      </div>
      <div v-if="renderProfile.showWindLines" class="weather-wind-lines">
        <span class="weather-wind-line weather-wind-line--a"></span>
        <span class="weather-wind-line weather-wind-line--b"></span>
        <span class="weather-wind-line weather-wind-line--c"></span>
      </div>
      <div v-if="renderProfile.showDustGlow" class="weather-dust-glow"></div>
      <div class="weather-vignette"></div>
    </div>
    <div :class="particleClasses" aria-hidden="true">
      <div ref="canvasRef" class="weather-canvas"></div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.weather-decor-host {
  position: fixed;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.weather-decor-root {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.6s ease, filter 0.6s ease;
}

.weather-decor-root.is-active {
  opacity: 0.34;
}

.weather-decor-root.is-active.weather-decor--sunny,
.weather-decor-root.is-active.weather-decor--cloudy,
.weather-decor-root.is-active.weather-decor--overcast {
  opacity: 0.3;
}

.weather-decor-root.is-active.weather-decor--light_rain,
.weather-decor-root.is-active.weather-decor--snow,
.weather-decor-root.is-active.weather-decor--windy {
  opacity: 0.4;
}

.weather-decor-root.is-active.weather-decor--heavy_rain,
.weather-decor-root.is-active.weather-decor--thunderstorm,
.weather-decor-root.is-active.weather-decor--fog,
.weather-decor-root.is-active.weather-decor--dust {
  opacity: 0.46;
}

.weather-particle-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.weather-particle-layer.is-active {
  opacity: 1;
}

.weather-particle-layer.is-reduced {
  opacity: 0.5;
}

.weather-decor-root.is-soft {
  opacity: 0.82;
}

.weather-decor-root.is-reduced {
  transition-duration: 0.3s;
}

.weather-atmosphere,
.weather-season-tint,
.weather-glow,
.weather-sun,
.weather-moon,
.weather-mist,
.weather-clouds,
.weather-lightning,
.weather-rain-veil,
.weather-wind-lines,
.weather-dust-glow,
.weather-canvas,
.weather-vignette {
  position: absolute;
  inset: 0;
}

.weather-atmosphere,
.weather-season-tint,
.weather-glow,
.weather-vignette {
  transition: background 0.6s ease, opacity 0.6s ease, filter 0.6s ease;
}

.weather-atmosphere {
  opacity: 0.84;
}

.weather-season-tint {
  opacity: 0.18;
}

.weather-glow {
  opacity: 0.38;
  mix-blend-mode: screen;
}

.weather-vignette {
  background:
    radial-gradient(circle at 50% -10%, rgba(255, 255, 255, 0.14), transparent 42%),
    linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.06) 100%);
  opacity: 0.18;
}

.weather-sun {
  opacity: 0.62;
  mix-blend-mode: screen;
}

.weather-sun-core,
.weather-sun-ring,
.weather-moon-core,
.weather-moon-halo {
  position: absolute;
  border-radius: 50%;
  transform: translate3d(0, 0, 0);
}

.weather-moon {
  opacity: 0.68;
  mix-blend-mode: screen;
}

.weather-moon-core {
  top: 9%;
  left: 14%;
  width: 86px;
  height: 86px;
  background:
    radial-gradient(circle at 34% 34%, rgba(255, 255, 255, 0.98) 0%, rgba(223, 235, 255, 0.94) 58%, rgba(193, 213, 247, 0.78) 100%);
  filter: blur(3px);
  box-shadow:
    inset -12px -8px 0 rgba(183, 202, 235, 0.18),
    0 0 22px rgba(214, 229, 255, 0.2),
    0 0 54px rgba(186, 211, 255, 0.12);
}

.weather-moon-core::after {
  content: '';
  position: absolute;
  inset: 10px 10px auto auto;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(188, 205, 236, 0.24);
  box-shadow:
    -18px 16px 0 -3px rgba(188, 205, 236, 0.18),
    -6px 34px 0 -8px rgba(188, 205, 236, 0.16);
}

.weather-moon-halo {
  top: calc(9% - 24px);
  left: calc(14% - 24px);
  width: 134px;
  height: 134px;
  background: radial-gradient(circle, rgba(193, 214, 255, 0.24), rgba(162, 189, 242, 0.08) 48%, transparent 74%);
  filter: blur(16px);
  animation: moonPulse 8s ease-in-out infinite;
}

.weather-sun-core {
  top: 7%;
  left: 12%;
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, rgba(255, 248, 212, 0.96) 0%, rgba(255, 223, 149, 0.88) 42%, rgba(255, 207, 102, 0.12) 78%, transparent 100%);
  filter: blur(6px);
  box-shadow:
    0 0 34px rgba(255, 214, 120, 0.18),
    0 0 72px rgba(255, 210, 132, 0.12);
  animation: sunFloat 9s ease-in-out infinite;
}

.weather-sun-ring {
  top: calc(7% - 26px);
  left: calc(12% - 26px);
  width: 172px;
  height: 172px;
  background: radial-gradient(circle, rgba(255, 228, 162, 0.18), rgba(255, 208, 120, 0.06) 54%, transparent 74%);
  filter: blur(12px);
  animation: sunPulse 7s ease-in-out infinite;
}

.weather-mist {
  opacity: 0.52;
}

.weather-mist-layer {
  position: absolute;
  inset: auto -10% 0;
  height: 34%;
  border-radius: 999px;
  filter: blur(30px);
  background: rgba(255, 255, 255, 0.14);
  animation: mistFlow 24s ease-in-out infinite;
}

.weather-mist-layer--a {
  bottom: 8%;
  opacity: 0.28;
}

.weather-mist-layer--b {
  bottom: 18%;
  height: 28%;
  opacity: 0.22;
  animation-duration: 31s;
  animation-delay: -8s;
}

.weather-mist-layer--c {
  bottom: 0;
  height: 40%;
  opacity: 0.18;
  animation-duration: 36s;
  animation-delay: -16s;
}

.weather-cloud {
  position: absolute;
  top: var(--cloud-top);
  left: var(--cloud-left);
  width: var(--cloud-width);
  height: var(--cloud-height);
  border-radius: 999px;
  background: rgba(255, 255, 255, var(--cloud-opacity));
  filter: blur(var(--cloud-blur));
  opacity: 0.82;
  transform: scale(var(--cloud-scale));
  animation: cloudDrift var(--cloud-duration) linear infinite;
  animation-delay: var(--cloud-delay);
}

.weather-cloud::before,
.weather-cloud::after {
  content: '';
  position: absolute;
  border-radius: inherit;
  background: inherit;
}

.weather-cloud::before {
  width: 56%;
  height: 120%;
  left: 12%;
  top: -38%;
}

.weather-cloud::after {
  width: 38%;
  height: 92%;
  right: 14%;
  top: -20%;
}

.weather-lightning {
  opacity: 0.62;
  mix-blend-mode: screen;
}

.weather-lightning-flash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 26% 14%, rgba(234, 244, 255, 0.58), transparent 16%),
    linear-gradient(180deg, rgba(241, 247, 255, 0.3), transparent 36%);
  opacity: 0;
}

.weather-lightning-flash--a {
  animation: lightningFlash 9s linear infinite;
}

.weather-lightning-flash--b {
  animation: lightningFlash 11s linear infinite;
  animation-delay: -4s;
}

.weather-rain-veil {
  opacity: 0.24;
}

.weather-rain-veil__layer {
  position: absolute;
  inset: 0;
  background-repeat: repeat;
  background-size: 220px 220px;
  filter: blur(0.5px);
}

.weather-rain-veil__layer--a {
  background-image: repeating-linear-gradient(-4deg, rgba(196, 220, 248, 0.12) 0 1px, transparent 1px 18px);
  animation: rainVeilMove 8s linear infinite;
}

.weather-rain-veil__layer--b {
  background-image: repeating-linear-gradient(-6deg, rgba(140, 185, 232, 0.1) 0 1px, transparent 1px 20px);
  opacity: 0.58;
  animation: rainVeilMove 12s linear infinite reverse;
}

.weather-wind-lines {
  opacity: 0.34;
}

.weather-wind-line {
  position: absolute;
  height: 1px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, rgba(238, 246, 255, 0.72), transparent);
  filter: blur(0.3px);
  animation: windSweep 7s linear infinite;
}

.weather-wind-line--a {
  top: 24%;
  width: 28%;
  left: -30%;
}

.weather-wind-line--b {
  top: 42%;
  width: 34%;
  left: -38%;
  opacity: 0.75;
  animation-duration: 6s;
  animation-delay: -2.4s;
}

.weather-wind-line--c {
  top: 61%;
  width: 24%;
  left: -26%;
  opacity: 0.6;
  animation-duration: 8.4s;
  animation-delay: -5s;
}

.weather-dust-glow {
  background:
    radial-gradient(circle at 14% 18%, rgba(255, 214, 145, 0.28), transparent 28%),
    radial-gradient(circle at 78% 28%, rgba(230, 168, 92, 0.18), transparent 24%);
  opacity: 0.36;
  mix-blend-mode: screen;
  animation: dustGlowPulse 8s ease-in-out infinite;
}

.weather-canvas {
  width: 100%;
  height: 100%;
  opacity: 0.82;
}

.weather-canvas :deep(canvas) {
  width: 100% !important;
  height: 100% !important;
  display: block;
  pointer-events: none;
}

.weather-decor--sunny .weather-atmosphere {
  background:
    radial-gradient(circle at 14% 18%, rgba(255, 232, 166, 0.48), transparent 25%),
    linear-gradient(180deg, rgba(146, 198, 255, 0.26), rgba(255, 232, 206, 0.14) 74%, transparent 100%);
}

.weather-decor--sunny .weather-glow {
  background:
    radial-gradient(circle at 18% 16%, rgba(255, 244, 198, 0.52), transparent 12%),
    radial-gradient(circle at 24% 22%, rgba(255, 214, 140, 0.2), transparent 22%);
}

.weather-decor--cloudy .weather-atmosphere,
.weather-decor--overcast .weather-atmosphere {
  background: linear-gradient(180deg, rgba(188, 206, 226, 0.28), rgba(154, 170, 192, 0.2) 55%, transparent 100%);
}

.weather-decor--cloudy .weather-glow {
  background: radial-gradient(circle at 30% 10%, rgba(255, 255, 255, 0.18), transparent 28%);
}

.weather-decor--overcast .weather-glow {
  background: radial-gradient(circle at 40% 0%, rgba(255, 255, 255, 0.08), transparent 30%);
  opacity: 0.44;
}

.weather-decor--light_rain .weather-atmosphere,
.weather-decor--heavy_rain .weather-atmosphere {
  background: linear-gradient(180deg, rgba(92, 118, 156, 0.22), rgba(58, 77, 110, 0.26) 65%, rgba(25, 31, 48, 0.1) 100%);
}

.weather-decor--thunderstorm .weather-atmosphere {
  background: linear-gradient(180deg, rgba(61, 72, 99, 0.3), rgba(26, 31, 46, 0.32) 62%, rgba(8, 11, 19, 0.18) 100%);
}

.weather-decor--light_rain .weather-glow,
.weather-decor--heavy_rain .weather-glow {
  background: radial-gradient(circle at 50% -8%, rgba(208, 227, 255, 0.18), transparent 34%);
}

.weather-decor--thunderstorm .weather-glow {
  background:
    radial-gradient(circle at 24% 8%, rgba(206, 221, 255, 0.14), transparent 20%),
    radial-gradient(circle at 76% 10%, rgba(189, 202, 255, 0.08), transparent 22%);
  opacity: 0.46;
}

.weather-decor--thunderstorm .weather-rain-veil {
  opacity: 0.34;
}

.weather-particle-layer.weather-decor--sunny .weather-canvas {
  opacity: 0.48;
}

.weather-particle-layer.weather-preset--aurora .weather-canvas {
  opacity: 0.82;
}

.weather-particle-layer.weather-preset--sakura .weather-canvas {
  opacity: 0.76;
}

.weather-particle-layer.weather-preset--leaves .weather-canvas {
  opacity: 0.8;
}

.weather-particle-layer.weather-preset--fireflies .weather-canvas {
  opacity: 0.72;
}

.weather-particle-layer.weather-decor--light_rain .weather-canvas,
.weather-particle-layer.weather-decor--snow .weather-canvas,
.weather-particle-layer.weather-decor--windy .weather-canvas {
  opacity: 0.88;
}

.weather-particle-layer.weather-decor--heavy_rain .weather-canvas,
.weather-particle-layer.weather-decor--thunderstorm .weather-canvas,
.weather-particle-layer.weather-decor--dust .weather-canvas {
  opacity: 0.96;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--sunny .weather-canvas {
  opacity: 0.62;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-preset--aurora .weather-canvas {
  opacity: 0.68;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-preset--sakura .weather-canvas {
  opacity: 0.84;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-preset--leaves .weather-canvas {
  opacity: 0.88;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-preset--fireflies .weather-canvas {
  opacity: 0.78;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--light_rain .weather-canvas,
:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--snow .weather-canvas,
:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--windy .weather-canvas {
  opacity: 1;
  filter:
    drop-shadow(0 0 0.42rem rgba(70, 117, 172, 0.26))
    drop-shadow(0 0 0.08rem rgba(116, 156, 204, 0.24));
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--snow .weather-canvas {
  opacity: 1;
}

:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--heavy_rain .weather-canvas,
:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--thunderstorm .weather-canvas,
:root:not([data-theme='dark']) .weather-particle-layer.weather-decor--dust .weather-canvas {
  opacity: 1;
  filter:
    drop-shadow(0 0 0.52rem rgba(66, 111, 165, 0.28))
    drop-shadow(0 0 0.12rem rgba(114, 154, 200, 0.26));
}

:root:not([data-theme='dark']) .weather-decor--light_rain .weather-rain-veil,
:root:not([data-theme='dark']) .weather-decor--heavy_rain .weather-rain-veil,
:root:not([data-theme='dark']) .weather-decor--thunderstorm .weather-rain-veil {
  opacity: 0.42;
}

:root:not([data-theme='dark']) .weather-decor--light_rain .weather-rain-veil__layer--a,
:root:not([data-theme='dark']) .weather-decor--heavy_rain .weather-rain-veil__layer--a,
:root:not([data-theme='dark']) .weather-decor--thunderstorm .weather-rain-veil__layer--a {
  background-image: repeating-linear-gradient(-4deg, rgba(108, 149, 197, 0.2) 0 1px, transparent 1px 18px);
}

:root:not([data-theme='dark']) .weather-decor--light_rain .weather-rain-veil__layer--b,
:root:not([data-theme='dark']) .weather-decor--heavy_rain .weather-rain-veil__layer--b,
:root:not([data-theme='dark']) .weather-decor--thunderstorm .weather-rain-veil__layer--b {
  background-image: repeating-linear-gradient(-6deg, rgba(78, 121, 172, 0.18) 0 1px, transparent 1px 20px);
  opacity: 0.68;
}

.weather-decor--snow .weather-atmosphere {
  background: linear-gradient(180deg, rgba(198, 221, 255, 0.28), rgba(214, 228, 247, 0.16) 58%, transparent 100%);
}

.weather-decor--snow .weather-glow {
  background: radial-gradient(circle at 32% 8%, rgba(255, 255, 255, 0.26), transparent 26%);
}

.weather-decor--snow .weather-vignette {
  background:
    radial-gradient(circle at 50% -10%, rgba(255, 255, 255, 0.2), transparent 42%),
    linear-gradient(180deg, transparent 0%, rgba(103, 130, 173, 0.08) 100%);
  opacity: 0.24;
}

.weather-decor--snow .weather-season-tint {
  opacity: 0.22;
}

.weather-decor--fog .weather-atmosphere {
  background: linear-gradient(180deg, rgba(205, 214, 224, 0.22), rgba(184, 193, 205, 0.18) 64%, rgba(242, 246, 250, 0.08) 100%);
}

.weather-decor--fog .weather-glow {
  background: radial-gradient(circle at 50% 12%, rgba(255, 255, 255, 0.16), transparent 30%);
  opacity: 0.44;
}

.weather-decor--windy .weather-atmosphere {
  background: linear-gradient(180deg, rgba(169, 196, 231, 0.22), rgba(158, 181, 207, 0.18) 58%, transparent 100%);
}

.weather-decor--windy .weather-glow {
  background:
    radial-gradient(circle at 18% 10%, rgba(228, 242, 255, 0.16), transparent 26%),
    radial-gradient(circle at 76% 12%, rgba(245, 249, 255, 0.1), transparent 20%);
}

.weather-decor--dust .weather-atmosphere {
  background: linear-gradient(180deg, rgba(218, 184, 128, 0.24), rgba(190, 144, 94, 0.22) 62%, rgba(163, 118, 69, 0.1) 100%);
}

.weather-decor--dust .weather-glow {
  background:
    radial-gradient(circle at 18% 12%, rgba(255, 212, 142, 0.18), transparent 24%),
    radial-gradient(circle at 78% 14%, rgba(238, 184, 118, 0.12), transparent 22%);
}

.weather-decor--dust .weather-mist-layer {
  background: rgba(224, 173, 112, 0.16);
}

.weather-season--spring .weather-season-tint {
  background:
    radial-gradient(circle at 18% 16%, rgba(255, 183, 197, 0.28), transparent 34%),
    radial-gradient(circle at 82% 10%, rgba(178, 245, 168, 0.18), transparent 30%);
}

.weather-season--summer .weather-season-tint {
  background:
    radial-gradient(circle at 20% 18%, rgba(255, 207, 122, 0.22), transparent 32%),
    radial-gradient(circle at 78% 10%, rgba(125, 211, 252, 0.16), transparent 28%);
}

.weather-season--autumn .weather-season-tint {
  background:
    radial-gradient(circle at 18% 18%, rgba(251, 146, 60, 0.24), transparent 32%),
    radial-gradient(circle at 80% 14%, rgba(245, 158, 11, 0.18), transparent 28%);
}

.weather-season--winter .weather-season-tint {
  background:
    radial-gradient(circle at 18% 12%, rgba(191, 219, 254, 0.24), transparent 32%),
    radial-gradient(circle at 78% 14%, rgba(224, 242, 254, 0.16), transparent 28%);
}

.weather-decor--night {
  filter: saturate(0.92) brightness(0.92);
}

.weather-decor--night .weather-vignette {
  background:
    radial-gradient(circle at 50% -12%, rgba(173, 214, 255, 0.1), transparent 34%),
    linear-gradient(180deg, rgba(7, 12, 24, 0.08), rgba(3, 8, 20, 0.26) 100%);
}

:root[data-theme='dark'] .weather-decor--sunny .weather-atmosphere {
  background:
    radial-gradient(circle at 14% 18%, rgba(255, 214, 122, 0.18), transparent 20%),
    linear-gradient(180deg, rgba(49, 78, 122, 0.2), rgba(62, 80, 116, 0.14) 70%, transparent 100%);
}

:root[data-theme='dark'] .weather-sun-core {
  background: radial-gradient(circle, rgba(255, 235, 179, 0.5) 0%, rgba(255, 193, 102, 0.32) 46%, rgba(255, 176, 64, 0.08) 76%, transparent 100%);
  box-shadow:
    0 0 28px rgba(255, 186, 84, 0.12),
    0 0 64px rgba(255, 176, 64, 0.08);
}

:root[data-theme='dark'] .weather-sun-ring {
  background: radial-gradient(circle, rgba(255, 205, 126, 0.12), rgba(255, 176, 64, 0.04) 54%, transparent 74%);
}

:root[data-theme='dark'] .weather-moon-core {
  background:
    radial-gradient(circle at 34% 34%, rgba(244, 248, 255, 0.94) 0%, rgba(206, 220, 247, 0.88) 58%, rgba(157, 178, 220, 0.72) 100%);
  box-shadow:
    inset -12px -8px 0 rgba(110, 130, 171, 0.18),
    0 0 24px rgba(180, 204, 255, 0.12),
    0 0 56px rgba(145, 174, 235, 0.08);
}

:root[data-theme='dark'] .weather-moon-halo {
  background: radial-gradient(circle, rgba(145, 174, 235, 0.16), transparent 68%);
}

:root[data-theme='dark'] .weather-decor--cloudy .weather-atmosphere,
:root[data-theme='dark'] .weather-decor--overcast .weather-atmosphere {
  background: linear-gradient(180deg, rgba(58, 73, 102, 0.24), rgba(31, 41, 55, 0.16) 68%, transparent 100%);
}

:root[data-theme='dark'] .weather-decor--light_rain .weather-atmosphere,
:root[data-theme='dark'] .weather-decor--heavy_rain .weather-atmosphere,
:root[data-theme='dark'] .weather-decor--thunderstorm .weather-atmosphere {
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.24), rgba(15, 23, 42, 0.26) 72%, transparent 100%);
}

:root[data-theme='dark'] .weather-decor--snow .weather-atmosphere {
  background: linear-gradient(180deg, rgba(52, 70, 102, 0.24), rgba(23, 37, 61, 0.16) 68%, transparent 100%);
}

:root[data-theme='dark'] .weather-decor--fog .weather-atmosphere,
:root[data-theme='dark'] .weather-decor--windy .weather-atmosphere {
  background: linear-gradient(180deg, rgba(55, 68, 92, 0.22), rgba(20, 30, 48, 0.16) 70%, transparent 100%);
}

:root[data-theme='dark'] .weather-decor--dust .weather-atmosphere {
  background: linear-gradient(180deg, rgba(111, 79, 49, 0.24), rgba(77, 55, 35, 0.18) 70%, transparent 100%);
}

:root[data-theme='dark'] .weather-rain-veil__layer--a {
  background-image: repeating-linear-gradient(-18deg, rgba(196, 216, 255, 0.1) 0 2px, transparent 2px 16px);
}

:root[data-theme='dark'] .weather-rain-veil__layer--b {
  background-image: repeating-linear-gradient(-18deg, rgba(164, 193, 255, 0.06) 0 1px, transparent 1px 18px);
}

:root[data-theme='dark'] .weather-cloud {
  background: rgba(201, 215, 255, calc(var(--cloud-opacity) * 0.75));
}

:root[data-theme='dark'] .weather-mist-layer {
  background: rgba(182, 196, 222, 0.12);
}

:root[data-theme='dark'] .weather-decor--dust .weather-mist-layer {
  background: rgba(179, 131, 79, 0.16);
}

:root[data-theme='dark'] .weather-dust-glow {
  opacity: 0.24;
}

:root[data-theme='dark'] .weather-vignette {
  opacity: 0.24;
}

@keyframes cloudDrift {
  from {
    transform: translate3d(0, 0, 0) scale(var(--cloud-scale));
  }
  to {
    transform: translate3d(22vw, 0, 0) scale(var(--cloud-scale));
  }
}

@keyframes sunFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0, 6px, 0);
  }
}

@keyframes sunPulse {
  0%,
  100% {
    transform: scale(0.98);
    opacity: 0.55;
  }
  50% {
    transform: scale(1.03);
    opacity: 0.78;
  }
}

@keyframes moonPulse {
  0%,
  100% {
    transform: scale(0.98);
    opacity: 0.46;
  }
  50% {
    transform: scale(1.02);
    opacity: 0.72;
  }
}

@keyframes mistFlow {
  0%,
  100% {
    transform: translate3d(-2%, 0, 0) scaleX(1);
  }
  50% {
    transform: translate3d(3%, -1.5%, 0) scaleX(1.04);
  }
}

@keyframes lightningFlash {
  0%,
  70%,
  100% {
    opacity: 0;
  }
  71% {
    opacity: 0.36;
  }
  72% {
    opacity: 0.08;
  }
  73% {
    opacity: 0.48;
  }
  74% {
    opacity: 0;
  }
  78% {
    opacity: 0.22;
  }
  79% {
    opacity: 0;
  }
}

@keyframes rainVeilMove {
  from {
    transform: translate3d(-0.4%, -8%, 0);
  }
  to {
    transform: translate3d(0.8%, 10%, 0);
  }
}

@keyframes windSweep {
  from {
    transform: translate3d(0, 0, 0);
    opacity: 0;
  }
  10%,
  70% {
    opacity: 1;
  }
  to {
    transform: translate3d(150vw, 0, 0);
    opacity: 0;
  }
}

@keyframes dustGlowPulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.66;
  }
  50% {
    transform: scale(1.03);
    opacity: 0.82;
  }
}

@media (max-width: 767px) {
  .weather-season-tint {
    opacity: 0.18;
  }

  .weather-cloud {
    filter: blur(22px);
  }

  .weather-mist-layer {
    filter: blur(24px);
  }

  .weather-sun-core {
    width: 88px;
    height: 88px;
  }

  .weather-sun-ring {
    width: 130px;
    height: 130px;
    top: calc(7% - 21px);
    left: calc(12% - 21px);
  }

  .weather-moon-core {
    width: 68px;
    height: 68px;
  }

  .weather-moon-halo {
    width: 108px;
    height: 108px;
    top: calc(9% - 20px);
    left: calc(14% - 20px);
  }
}
</style>
