<script setup lang="ts">
import {Mesh, Program, Renderer, Triangle} from 'ogl'

const hostRef = ref<HTMLElement | null>(null)
let renderer: Renderer | null = null
let program: Program | null = null
let mesh: Mesh | null = null
let frameHandle = 0
let startTime = 0
let lastRenderAt = 0
let paused = false

const TARGET_FPS = 16
const FRAME_INTERVAL = 1000 / TARGET_FPS

const vertexShader = /* glsl */ `
attribute vec2 position;
attribute vec2 uv;

varying vec2 vUv;

void main() {
  vUv = uv;
  gl_Position = vec4(position, 0.0, 1.0);
}
`

const fragmentShader = /* glsl */ `
precision highp float;

varying vec2 vUv;

uniform vec2 uResolution;
uniform float uTime;
uniform float uDark;
uniform float uMobile;

float hash21(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  float a = hash21(i);
  float b = hash21(i + vec2(1.0, 0.0));
  float c = hash21(i + vec2(0.0, 1.0));
  float d = hash21(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
  float value = 0.0;
  float amplitude = 0.5;
  for (int i = 0; i < 3; i++) {
    value += amplitude * noise(p);
    p = p * 2.03 + vec2(7.1, 11.4);
    amplitude *= 0.53;
  }
  return value;
}

mat2 rotate2d(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat2(c, -s, s, c);
}

float ribbon(vec2 uv, float seed, float width, float speed) {
  vec2 p = uv;
  p -= 0.5;
  p *= rotate2d(seed * 0.65 - 0.35);
  p += 0.5;

  float center = 0.32 + seed * 0.12
    + 0.06 * sin(p.x * (1.1 + seed * 0.8) - uTime * speed + seed * 4.0)
    + 0.03 * fbm(vec2(p.x * 1.4 + seed * 4.0, seed * 2.0));

  return smoothstep(width, width * 0.36, abs(p.y - center));
}

float softCircle(vec2 uv, vec2 center, float radius, float blur) {
  return smoothstep(radius + blur, radius - blur, distance(uv, center));
}

vec3 lightPalette(float t) {
  vec3 milk = vec3(0.97, 0.97, 0.995);
  vec3 sky = vec3(0.86, 0.9, 0.98);
  vec3 lavender = vec3(0.89, 0.87, 0.98);
  vec3 lilac = vec3(0.82, 0.84, 0.98);
  vec3 base = mix(milk, sky, smoothstep(0.12, 0.58, t));
  base = mix(base, lavender, smoothstep(0.4, 0.82, t));
  return mix(base, lilac, smoothstep(0.72, 1.0, t));
}

vec3 darkPalette(float t) {
  vec3 ink = vec3(0.05, 0.07, 0.14);
  vec3 indigo = vec3(0.12, 0.18, 0.34);
  vec3 violet = vec3(0.22, 0.24, 0.46);
  vec3 glow = vec3(0.38, 0.46, 0.82);
  vec3 base = mix(ink, indigo, smoothstep(0.08, 0.58, t));
  base = mix(base, violet, smoothstep(0.36, 0.82, t));
  return mix(base, glow, smoothstep(0.76, 1.0, t));
}

void main() {
  vec2 uv = vUv;
  float aspect = uResolution.x / max(uResolution.y, 1.0);
  vec2 p = uv - 0.5;
  p.x *= aspect;

  float flowA = fbm(vec2(p.x * 0.48 + 2.3, p.y * 0.62));
  float flowB = fbm(vec2(p.x * 0.74 - 1.8, p.y * 0.86));
  float blend = smoothstep(0.1, 0.9, flowA * 0.62 + flowB * 0.38);

  vec3 color = mix(lightPalette(blend), darkPalette(blend), uDark);

  float paper = fbm(vec2(uv.x * 8.0, uv.y * 10.0));
  float fibers = fbm(vec2(uv.x * 18.0, uv.y * 30.0));
  color += mix(vec3(0.06, 0.08, 0.1), vec3(0.02, 0.03, 0.05), uDark) * (paper - 0.5) * 0.06;
  color += mix(vec3(0.05, 0.06, 0.07), vec3(0.02, 0.03, 0.04), uDark) * (fibers - 0.5) * 0.02;

  float ribbonA = ribbon(uv, 0.24, 0.26, 0.024);
  float ribbonB = ribbon(uv, 0.58, 0.22, 0.03);
  float ribbonC = ribbon(uv, 0.88, 0.18, 0.034);
  vec3 ribbonColorA = mix(vec3(0.92, 0.93, 0.99), vec3(0.24, 0.34, 0.68), uDark);
  vec3 ribbonColorB = mix(vec3(0.85, 0.9, 0.99), vec3(0.2, 0.28, 0.56), uDark);
  vec3 ribbonColorC = mix(vec3(0.91, 0.88, 0.99), vec3(0.28, 0.26, 0.58), uDark);
  color = mix(color, ribbonColorA, ribbonA * 0.08);
  color = mix(color, ribbonColorB, ribbonB * 0.07);
  color = mix(color, ribbonColorC, ribbonC * 0.05);

  float washA = softCircle(uv, vec2(0.78, 0.18), mix(0.3, 0.24, uMobile), 0.52);
  float washB = softCircle(uv, vec2(0.14, 0.82), mix(0.22, 0.18, uMobile), 0.44);
  color += mix(vec3(0.94, 0.92, 1.0), vec3(0.14, 0.18, 0.36), uDark) * washA * 0.05;
  color += mix(vec3(0.88, 0.91, 1.0), vec3(0.1, 0.14, 0.3), uDark) * washB * 0.04;

  float vignette = smoothstep(1.18, 0.12, length(p));
  color *= mix(0.94, 1.0, vignette);

  gl_FragColor = vec4(color, 1.0);
}
`

function isDarkMode() {
  if (!import.meta.client) {
    return false
  }

  return document.documentElement.getAttribute('data-theme') === 'dark'
}

function updateThemeUniform() {
  if (!program) {
    return
  }

  program.uniforms.uDark.value = isDarkMode() ? 1 : 0
}

function updateMobileUniform() {
  if (!program || !import.meta.client) {
    return
  }

  program.uniforms.uMobile.value = window.innerWidth < 768 ? 1 : 0
}

function resizeScene() {
  if (!renderer || !program || !hostRef.value) {
    return
  }

  const width = Math.max(hostRef.value.clientWidth, 1)
  const height = Math.max(hostRef.value.clientHeight, 1)
  const isMobile = window.innerWidth < 768
  const deviceMemory = Number((navigator as Navigator & { deviceMemory?: number }).deviceMemory || 8)
  const renderScale = isMobile ? 0.42 : deviceMemory <= 4 ? 0.5 : 0.58
  const internalWidth = Math.max(Math.round(width * renderScale), 1)
  const internalHeight = Math.max(Math.round(height * renderScale), 1)
  renderer.setSize(internalWidth, internalHeight)
  renderer.gl.canvas.style.width = `${width}px`
  renderer.gl.canvas.style.height = `${height}px`
  program.uniforms.uResolution.value = [width, height]
}

function renderFrame(now: number) {
  if (!renderer || !mesh || !program || paused) {
    frameHandle = 0
    return
  }

  if (lastRenderAt && now - lastRenderAt < FRAME_INTERVAL) {
    frameHandle = requestAnimationFrame(renderFrame)
    return
  }

  lastRenderAt = now
  program.uniforms.uTime.value = (now - startTime) / 1000
  renderer.render({ scene: mesh })
  frameHandle = requestAnimationFrame(renderFrame)
}

function initScene() {
  if (!hostRef.value || renderer) {
    return
  }

  renderer = new Renderer({
    alpha: false,
    antialias: false,
    dpr: 1
  })

  const gl = renderer.gl
  gl.canvas.style.position = 'absolute'
  gl.canvas.style.inset = '0'
  gl.canvas.style.width = '100%'
  gl.canvas.style.height = '100%'
  gl.canvas.style.pointerEvents = 'none'
  gl.canvas.style.display = 'block'
  gl.canvas.setAttribute('aria-hidden', 'true')

  const geometry = new Triangle(gl)
  program = new Program(gl, {
    vertex: vertexShader,
    fragment: fragmentShader,
    uniforms: {
      uResolution: { value: [1, 1] },
      uTime: { value: 0 },
      uDark: { value: 0 },
      uMobile: { value: 0 }
    },
    depthTest: false,
    depthWrite: false
  })
  mesh = new Mesh(gl, { geometry, program })
  hostRef.value.innerHTML = ''
  hostRef.value.appendChild(gl.canvas)

  resizeScene()
  updateThemeUniform()
  updateMobileUniform()
}

function startScene() {
  initScene()
  if (!renderer || !program || !mesh || frameHandle) {
    return
  }

  paused = false
  startTime = typeof performance === 'undefined' ? 0 : performance.now()
  lastRenderAt = 0
  frameHandle = requestAnimationFrame(renderFrame)
}

function destroyScene() {
  paused = true
  if (frameHandle) {
    cancelAnimationFrame(frameHandle)
    frameHandle = 0
  }

  if (renderer) {
    renderer.gl.canvas.remove()
  }

  renderer = null
  program = null
  mesh = null

  if (hostRef.value) {
    hostRef.value.innerHTML = ''
  }
}

function handleThemeChange() {
  updateThemeUniform()
}

function handleResize() {
  updateMobileUniform()
  resizeScene()
}

function handleVisibilityChange() {
  if (document.hidden) {
    paused = true
    if (frameHandle) {
      cancelAnimationFrame(frameHandle)
      frameHandle = 0
    }
    return
  }

  if (!frameHandle) {
    paused = false
    lastRenderAt = 0
    frameHandle = requestAnimationFrame(renderFrame)
  }
}

onMounted(() => {
  startScene()
  window.addEventListener('theme-change', handleThemeChange)
  window.addEventListener('resize', handleResize, { passive: true })
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', handleThemeChange)
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  destroyScene()
})
</script>

<template>
  <div class="login-particles" aria-hidden="true">
    <div ref="hostRef" class="login-particles__canvas"></div>
  </div>
</template>

<style scoped lang="scss">
.login-particles {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.login-particles__canvas {
  position: absolute;
  inset: 0;
}
</style>
