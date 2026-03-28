<script setup lang="ts">
import { Mesh, Program, Renderer, Triangle } from 'ogl'

const hostRef = ref<HTMLElement | null>(null)
let renderer: Renderer | null = null
let program: Program | null = null
let mesh: Mesh | null = null
let frameHandle = 0
let startTime = 0
let paused = false

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
  for (int i = 0; i < 6; i++) {
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

  float center = 0.28 + seed * 0.16
    + 0.12 * sin(p.x * (2.0 + seed * 1.3) - uTime * speed + seed * 4.0)
    + 0.05 * fbm(vec2(p.x * 2.4 + seed * 6.0, uTime * 0.05 + seed * 2.0));

  return smoothstep(width, width * 0.18, abs(p.y - center));
}

float softCircle(vec2 uv, vec2 center, float radius, float blur) {
  return smoothstep(radius + blur, radius - blur, distance(uv, center));
}

vec3 lightPalette(float t) {
  vec3 cream = vec3(0.98, 0.95, 0.9);
  vec3 mist = vec3(0.82, 0.9, 0.96);
  vec3 mint = vec3(0.73, 0.86, 0.82);
  vec3 peach = vec3(0.98, 0.83, 0.68);
  vec3 base = mix(cream, mist, smoothstep(0.12, 0.62, t));
  base = mix(base, mint, smoothstep(0.44, 0.82, t));
  return mix(base, peach, smoothstep(0.7, 1.0, t));
}

vec3 darkPalette(float t) {
  vec3 ink = vec3(0.05, 0.09, 0.16);
  vec3 blue = vec3(0.13, 0.23, 0.38);
  vec3 teal = vec3(0.16, 0.34, 0.35);
  vec3 glow = vec3(0.35, 0.62, 0.72);
  vec3 base = mix(ink, blue, smoothstep(0.08, 0.58, t));
  base = mix(base, teal, smoothstep(0.36, 0.82, t));
  return mix(base, glow, smoothstep(0.76, 1.0, t));
}

void main() {
  vec2 uv = vUv;
  float aspect = uResolution.x / max(uResolution.y, 1.0);
  vec2 p = uv - 0.5;
  p.x *= aspect;

  float flowA = fbm(vec2(p.x * 0.9 + 2.3, p.y * 1.1 - uTime * 0.02));
  float flowB = fbm(vec2(p.x * 1.7 - 1.8, p.y * 1.4 + uTime * 0.03));
  float blend = smoothstep(0.08, 0.92, flowA * 0.55 + flowB * 0.45);

  vec3 color = mix(lightPalette(blend), darkPalette(blend), uDark);

  float paper = fbm(vec2(uv.x * 8.0, uv.y * 10.0));
  float fibers = fbm(vec2(uv.x * 22.0 + uTime * 0.004, uv.y * 38.0));
  color += mix(vec3(0.06, 0.08, 0.1), vec3(0.02, 0.03, 0.05), uDark) * (paper - 0.5) * 0.09;
  color += mix(vec3(0.05, 0.06, 0.07), vec3(0.02, 0.03, 0.04), uDark) * (fibers - 0.5) * 0.05;

  float ribbonA = ribbon(uv, 0.25, 0.23, 0.06);
  float ribbonB = ribbon(uv, 0.62, 0.19, 0.08);
  float ribbonC = ribbon(uv, 0.88, 0.16, 0.1);
  vec3 ribbonColorA = mix(vec3(0.98, 0.92, 0.84), vec3(0.28, 0.54, 0.66), uDark);
  vec3 ribbonColorB = mix(vec3(0.84, 0.92, 0.94), vec3(0.18, 0.36, 0.52), uDark);
  vec3 ribbonColorC = mix(vec3(0.95, 0.84, 0.78), vec3(0.2, 0.42, 0.36), uDark);
  color += ribbonColorA * ribbonA * 0.22;
  color += ribbonColorB * ribbonB * 0.16;
  color += ribbonColorC * ribbonC * 0.12;

  float glowMain = softCircle(uv, vec2(0.74, 0.24), mix(0.34, 0.26, uMobile), 0.42);
  float glowSecondary = softCircle(uv, vec2(0.18, 0.78), mix(0.24, 0.2, uMobile), 0.32);
  color += mix(vec3(0.96, 0.84, 0.62), vec3(0.18, 0.44, 0.56), uDark) * glowMain * 0.24;
  color += mix(vec3(0.8, 0.9, 0.92), vec3(0.12, 0.24, 0.42), uDark) * glowSecondary * 0.18;

  for (int i = 0; i < 12; i++) {
    float fi = float(i);
    vec2 seed = vec2(fi * 7.7, fi * 13.1);
    vec2 pos = vec2(
      fract(hash21(seed) + sin(uTime * (0.03 + fi * 0.002)) * 0.04),
      fract(hash21(seed + 4.2) + cos(uTime * (0.026 + fi * 0.003)) * 0.05)
    );
    float mote = softCircle(uv, pos, 0.004 + hash21(seed + 8.1) * 0.01, 0.024);
    vec3 moteColor = mix(vec3(1.0, 0.96, 0.86), vec3(0.74, 0.9, 0.98), hash21(seed + 1.7));
    color += moteColor * mote * mix(0.18, 0.12, uDark);
  }

  float vignette = smoothstep(1.18, 0.12, length(p));
  color *= vignette;

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
  renderer.setSize(width, height)
  program.uniforms.uResolution.value = [width, height]
}

function renderFrame(now: number) {
  if (!renderer || !mesh || !program || paused) {
    frameHandle = 0
    return
  }

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
    antialias: true,
    dpr: typeof window === 'undefined' ? 1 : Math.min(window.devicePixelRatio || 1, 2)
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

onMounted(() => {
  startScene()
  window.addEventListener('theme-change', handleThemeChange)
  window.addEventListener('resize', handleResize, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', handleThemeChange)
  window.removeEventListener('resize', handleResize)
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
