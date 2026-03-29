import {Mesh, Program, Renderer, Triangle} from 'ogl'
import type {WeatherParticlePreset} from './useWeatherDecor'

interface WeatherScene {
  particleEnabled?: boolean
  particlePreset?: WeatherParticlePreset | null
  densityScale?: number
  themeMode?: 'light' | 'dark'
  isNight?: boolean
  isMobile?: boolean
}

const VERTEX_SHADER = /* glsl */ `
attribute vec2 position;
attribute vec2 uv;

varying vec2 vUv;

void main() {
  vUv = uv;
  gl_Position = vec4(position, 0.0, 1.0);
}
`

const FRAGMENT_SHADER = /* glsl */ `
precision highp float;

varying vec2 vUv;

uniform vec2 uResolution;
uniform float uTime;
uniform float uPreset;
uniform float uDensity;
uniform float uDark;
uniform float uNight;

const float PRESET_SUNNY = 1.0;
const float PRESET_LIGHT_RAIN = 2.0;
const float PRESET_HEAVY_RAIN = 3.0;
const float PRESET_THUNDERSTORM = 4.0;
const float PRESET_SNOW = 5.0;
const float PRESET_WINDY = 6.0;
const float PRESET_DUST = 7.0;
const float PRESET_AURORA = 8.0;

float hash11(float p) {
  p = fract(p * 0.1031);
  p *= p + 33.33;
  p *= p + p;
  return fract(p);
}

float hash21(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
  float n = hash21(p);
  return vec2(n, hash21(p + n + 17.19));
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

  for (int i = 0; i < 5; i++) {
    value += amplitude * noise(p);
    p = p * 2.03 + vec2(13.1, 9.7);
    amplitude *= 0.52;
  }

  return value;
}

float softCircle(vec2 p, vec2 center, float radius, float blur) {
  return smoothstep(radius + blur, radius - blur, distance(p, center));
}

float lineMask(vec2 p, vec2 a, vec2 b, float thickness, float blur) {
  vec2 pa = p - a;
  vec2 ba = b - a;
  float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
  float d = length(pa - ba * h);
  return smoothstep(thickness + blur, thickness - blur, d);
}

vec4 renderAurora(vec2 uv, float aspect, float time) {
  vec2 p = uv;
  p.x *= aspect;
  float vertical = 1.0 - uv.y;
  vec3 color = vec3(0.0);
  float alpha = 0.0;

  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float offset = fi * 1.37;
    float wave = 0.16 + fi * 0.08;
    float ribbon = 0.22
      + 0.12 * sin(p.x * (1.3 + fi * 0.28) - time * (0.08 + fi * 0.02) + offset)
      + 0.05 * fbm(vec2(p.x * 1.8 + offset, time * 0.07 + fi * 0.9));
    float mask = smoothstep(wave, wave * 0.18, abs(vertical - ribbon));
    vec3 ribbonColor = mix(vec3(0.14, 0.72, 0.64), vec3(0.53, 0.76, 1.0), fi * 0.35);
    ribbonColor = mix(ribbonColor, vec3(0.62, 0.42, 0.92), smoothstep(0.55, 1.0, fbm(vec2(p.x * 2.8 - fi, vertical * 3.6 + time * 0.05))));
    color += ribbonColor * mask * (0.52 - fi * 0.08);
    alpha += mask * (0.34 - fi * 0.04);
  }

  float haze = fbm(vec2(p.x * 1.9, uv.y * 3.2 - time * 0.025));
  color += vec3(0.06, 0.14, 0.24) * haze * 0.45;
  alpha += smoothstep(0.18, 0.72, haze) * 0.12;

  for (int i = 0; i < 20; i++) {
    float fi = float(i);
    vec2 seed = vec2(fi * 13.1, fi * 7.7);
    vec2 star = hash22(seed) * vec2(1.0, 0.7);
    float blink = 0.35 + 0.65 * sin(time * (0.4 + hash11(fi + 4.0)) + fi * 1.7);
    float glow = softCircle(uv, vec2(star.x, star.y * 0.5 + 0.45), 0.0016 + hash11(fi + 9.0) * 0.0024, 0.0025);
    color += vec3(0.82, 0.9, 1.0) * glow * blink * 0.26;
    alpha += glow * blink * 0.1;
  }

  alpha *= smoothstep(0.08, 0.58, vertical) * smoothstep(1.12, 0.28, vertical);
  return vec4(color, clamp(alpha, 0.0, 0.82));
}

vec4 renderSunny(vec2 uv, float aspect, float time, float density, float darkMode) {
  vec2 p = uv;
  p.x *= aspect;
  vec3 color = mix(vec3(0.98, 0.85, 0.62), vec3(0.87, 0.94, 1.0), uv.y);
  float veil = fbm(vec2(p.x * 1.6 - time * 0.02, uv.y * 2.3 + time * 0.015));
  color *= 0.18 + veil * 0.46;
  float alpha = 0.12 + veil * 0.12;

  for (int i = 0; i < 14; i++) {
    float fi = float(i);
    vec2 seed = vec2(fi * 17.1, fi * 4.7);
    vec2 drift = hash22(seed);
    vec2 pos = vec2(fract(drift.x + time * (0.006 + hash11(fi) * 0.01)), fract(drift.y + time * (0.01 + hash11(fi + 8.0) * 0.018)));
    pos.y = fract(pos.y + sin(time * 0.08 + fi) * 0.05);
    float glow = softCircle(uv, pos, mix(0.012, 0.038, hash11(fi + 11.0)) * density, 0.03);
    vec3 moteColor = mix(vec3(1.0, 0.81, 0.55), vec3(0.82, 0.93, 1.0), hash11(fi + 3.0));
    color += moteColor * glow * (darkMode > 0.5 ? 0.2 : 0.34);
    alpha += glow * 0.08;
  }

  return vec4(color, clamp(alpha, 0.0, darkMode > 0.5 ? 0.32 : 0.48));
}

vec4 renderRain(vec2 uv, float aspect, float time, float density, float heavy, float thunder, float darkMode) {
  vec2 p = uv;
  p.x *= aspect;
  vec3 color = mix(vec3(0.46, 0.64, 0.82), vec3(0.82, 0.9, 0.98), darkMode);
  float alpha = 0.0;
  int drops = 30;

  for (int i = 0; i < 30; i++) {
    float fi = float(i);
    vec2 seed = vec2(fi * 11.7, fi * 7.1);
    float x = fract(hash21(seed) * 1.2 + time * (0.18 + hash11(fi + 1.0) * 0.08));
    float y = fract(hash21(seed + 3.1) - time * mix(0.95, 1.7, heavy) * (0.52 + hash11(fi + 2.0)));
    vec2 start = vec2(x, y);
    vec2 end = start + vec2(-0.012 * aspect * mix(0.8, 1.4, heavy), 0.09 + 0.06 * heavy);
    float streak = lineMask(p, vec2(start.x * aspect, start.y), vec2(end.x * aspect, end.y), 0.0016 + heavy * 0.0008, 0.0032);
    color += mix(vec3(0.58, 0.72, 0.9), vec3(0.88, 0.95, 1.0), hash11(fi + 5.0)) * streak * (0.38 + heavy * 0.18);
    alpha += streak * (0.08 + heavy * 0.04);
  }

  float mist = fbm(vec2(p.x * 3.0 + time * 0.08, uv.y * 4.8 - time * 0.12));
  color += vec3(0.15, 0.2, 0.28) * mist * (0.26 + heavy * 0.18);
  alpha += mist * (0.06 + heavy * 0.07);

  if (thunder > 0.5) {
    float flash = smoothstep(0.93, 1.0, sin(time * 0.68 + sin(time * 0.17) * 2.0) * 0.5 + 0.5);
    color += vec3(0.7, 0.82, 1.0) * flash * 0.5;
    alpha += flash * 0.16;
  }

  return vec4(color, clamp(alpha, 0.0, 0.62));
}

vec4 renderSnow(vec2 uv, float aspect, float time, float density, float darkMode) {
  vec2 p = uv;
  p.x *= aspect;
  vec3 color = mix(vec3(0.86, 0.92, 1.0), vec3(1.0, 1.0, 1.0), darkMode);
  float alpha = 0.08;

  for (int i = 0; i < 18; i++) {
    float fi = float(i);
    float layer = mod(fi, 3.0);
    float speed = 0.035 + layer * 0.018;
    vec2 seed = vec2(fi * 8.1, fi * 15.7);
    vec2 base = hash22(seed);
    float sway = sin(time * (0.35 + hash11(fi + 4.0)) + fi) * 0.04;
    vec2 pos = vec2(fract(base.x + sway + layer * 0.06), fract(base.y - time * speed));
    float radius = mix(0.004, 0.012, hash11(fi + 7.0)) * density * (0.8 + layer * 0.24);
    float flake = softCircle(uv, pos, radius, radius * 1.8);
    color += vec3(0.96, 0.98, 1.0) * flake * (0.28 + layer * 0.1);
    alpha += flake * (0.08 + layer * 0.03);
  }

  return vec4(color, clamp(alpha, 0.0, darkMode > 0.5 ? 0.52 : 0.64));
}

vec4 renderWind(vec2 uv, float aspect, float time, float density, float darkMode) {
  vec2 p = uv;
  p.x *= aspect;
  vec3 color = mix(vec3(0.58, 0.76, 0.94), vec3(0.9, 0.95, 1.0), darkMode);
  float alpha = 0.0;

  for (int i = 0; i < 9; i++) {
    float fi = float(i);
    float y = fract(hash11(fi * 7.9) + fi * 0.06 + 0.12);
    float sweep = fract(hash11(fi * 4.1) + time * (0.12 + fi * 0.007));
    vec2 a = vec2((sweep - 0.22) * aspect, y);
    vec2 b = a + vec2((0.28 + hash11(fi + 2.0) * 0.18) * aspect, 0.015 * sin(time * 0.45 + fi));
    float line = lineMask(p, a, b, 0.003 + hash11(fi + 8.0) * 0.003, 0.015);
    color += mix(vec3(0.68, 0.84, 1.0), vec3(0.96, 0.98, 1.0), hash11(fi + 5.0)) * line * 0.5;
    alpha += line * 0.12;
  }

  float field = fbm(vec2(p.x * 1.7 - time * 0.12, uv.y * 4.0 + time * 0.04));
  color += vec3(0.2, 0.35, 0.52) * field * 0.12;
  alpha += field * 0.05;

  return vec4(color, clamp(alpha, 0.0, darkMode > 0.5 ? 0.34 : 0.42));
}

vec4 renderDust(vec2 uv, float aspect, float time, float density, float darkMode) {
  vec2 p = uv;
  p.x *= aspect;
  vec3 color = mix(vec3(0.85, 0.62, 0.34), vec3(0.96, 0.78, 0.52), darkMode * 0.4);
  float alpha = 0.0;

  float haze = fbm(vec2(p.x * 2.0 - time * 0.03, uv.y * 3.1 + time * 0.02));
  color += vec3(0.62, 0.38, 0.18) * haze * 0.32;
  alpha += haze * 0.18;

  for (int i = 0; i < 16; i++) {
    float fi = float(i);
    vec2 base = hash22(vec2(fi * 9.4, fi * 3.2));
    vec2 pos = vec2(fract(base.x + time * (0.01 + hash11(fi + 1.0) * 0.03)), fract(base.y + sin(time * 0.12 + fi) * 0.06));
    float mote = softCircle(uv, pos, mix(0.006, 0.018, hash11(fi + 6.0)) * density, 0.03);
    color += mix(vec3(0.92, 0.74, 0.48), vec3(0.72, 0.46, 0.21), hash11(fi + 2.0)) * mote * 0.38;
    alpha += mote * 0.08;
  }

  return vec4(color, clamp(alpha, 0.0, darkMode > 0.5 ? 0.46 : 0.52));
}

void main() {
  vec2 uv = vUv;
  float aspect = uResolution.x / max(uResolution.y, 1.0);
  float time = uTime;
  vec4 scene = vec4(0.0);

  if (uPreset == PRESET_AURORA) {
    scene = renderAurora(uv, aspect, time);
  } else if (uPreset == PRESET_SUNNY) {
    scene = renderSunny(uv, aspect, time, uDensity, uDark);
  } else if (uPreset == PRESET_LIGHT_RAIN) {
    scene = renderRain(uv, aspect, time, uDensity, 0.0, 0.0, uDark);
  } else if (uPreset == PRESET_HEAVY_RAIN) {
    scene = renderRain(uv, aspect, time, uDensity, 1.0, 0.0, uDark);
  } else if (uPreset == PRESET_THUNDERSTORM) {
    scene = renderRain(uv, aspect, time, uDensity, 1.0, 1.0, uDark);
  } else if (uPreset == PRESET_SNOW) {
    scene = renderSnow(uv, aspect, time, uDensity, uDark);
  } else if (uPreset == PRESET_WINDY) {
    scene = renderWind(uv, aspect, time, uDensity, uDark);
  } else if (uPreset == PRESET_DUST) {
    scene = renderDust(uv, aspect, time, uDensity, uDark);
  }

  float vignette = smoothstep(1.1, 0.12, length((uv - 0.5) * vec2(aspect, 1.0)));
  scene.rgb *= vignette * mix(0.82, 1.06, uNight);
  scene.a *= vignette;
  gl_FragColor = vec4(scene.rgb, scene.a);
}
`

let instanceSeed = 0

export function createWeatherEngine(host: HTMLElement) {
  return new WeatherDecorEngine(host)
}

class WeatherDecorEngine {
  host: HTMLElement
  renderer: Renderer | null
  program: Program | null
  mesh: Mesh | null
  scene: WeatherScene | null
  sceneKey: string
  isPaused: boolean
  instanceId: string
  frameHandle: number
  startTime: number
  renderScale: number

  constructor(host: HTMLElement) {
    this.host = host
    this.renderer = null
    this.program = null
    this.mesh = null
    this.scene = null
    this.sceneKey = ''
    this.isPaused = false
    this.instanceId = `weather-decor-ogl-${instanceSeed++}`
    this.frameHandle = 0
    this.startTime = 0
    this.renderScale = 1
  }

  start(scene: WeatherScene) {
    this.scene = scene
    const nextSceneKey = buildSceneKey(scene)

    if (!scene?.particleEnabled || !scene.particlePreset) {
      this.stop()
      return
    }

    if (this.renderer && this.sceneKey === nextSceneKey) {
      this.updateSceneUniforms(scene)
      if (this.isPaused) {
        this.resume()
      }
      return
    }

    this.ensureRenderer()
    this.updateSceneUniforms(scene)

    if (!this.frameHandle || this.isPaused) {
      this.isPaused = false
      this.startLoop()
    }

    this.sceneKey = nextSceneKey
  }

  pause() {
    if (this.isPaused) {
      return
    }

    this.isPaused = true
    if (this.frameHandle) {
      cancelAnimationFrame(this.frameHandle)
      this.frameHandle = 0
    }
  }

  resume() {
    if (!this.renderer || !this.program || !this.mesh) {
      return
    }
    if (!this.isPaused && this.frameHandle) {
      return
    }

    this.isPaused = false
    this.startLoop()
  }

  stop() {
    this.pause()
    this.scene = null
    this.sceneKey = ''

    if (this.renderer) {
      this.renderer.gl.canvas.remove()
      this.renderer = null
      this.program = null
      this.mesh = null
    }

    this.host.innerHTML = ''
  }

  resize() {
    if (!this.renderer || !this.program) {
      return
    }

    resizeRenderer(this.host, this.renderer, this.program, this.renderScale)
    this.renderFrame()
  }

  async destroy() {
    this.stop()
  }

  private ensureRenderer() {
    if (this.renderer && this.program && this.mesh) {
      this.resize()
      return
    }

    const renderer = new Renderer({
      alpha: true,
      antialias: true,
      dpr: typeof window === 'undefined' ? 1 : Math.min(window.devicePixelRatio || 1, 1.2)
    })

    const gl = renderer.gl
    gl.canvas.id = this.instanceId
    gl.canvas.style.position = 'absolute'
    gl.canvas.style.inset = '0'
    gl.canvas.style.width = '100%'
    gl.canvas.style.height = '100%'
    gl.canvas.style.pointerEvents = 'none'
    gl.canvas.style.display = 'block'
    gl.canvas.setAttribute('aria-hidden', 'true')

    const geometry = new Triangle(gl)
    const program = new Program(gl, {
      vertex: VERTEX_SHADER,
      fragment: FRAGMENT_SHADER,
      uniforms: {
        uResolution: { value: [1, 1] },
        uTime: { value: 0 },
        uPreset: { value: 0 },
        uDensity: { value: 1 },
        uDark: { value: 0 },
        uNight: { value: 0 }
      },
      depthTest: false,
      depthWrite: false
    })

    const mesh = new Mesh(gl, { geometry, program })

    this.host.innerHTML = ''
    this.host.appendChild(gl.canvas)

    this.renderer = renderer
    this.program = program
    this.mesh = mesh
    this.startTime = typeof performance === 'undefined' ? 0 : performance.now()
    this.resize()
  }

  private updateSceneUniforms(scene: WeatherScene) {
    if (!this.program) {
      return
    }

    this.program.uniforms.uPreset.value = presetToId(scene.particlePreset)
    this.program.uniforms.uDensity.value = clampNumber(scene.densityScale ?? 1, 0.35, 1.5)
    this.program.uniforms.uDark.value = scene.themeMode === 'dark' ? 1 : 0
    this.program.uniforms.uNight.value = scene.isNight ? 1 : 0
    this.renderScale = resolveRenderScale(scene)

    if (this.renderer) {
      resizeRenderer(this.host, this.renderer, this.program, this.renderScale)
    }
  }

  private startLoop() {
    if (!this.renderer || !this.mesh || !this.program) {
      return
    }

    const loop = (now: number) => {
      if (this.isPaused) {
        this.frameHandle = 0
        return
      }

      this.program.uniforms.uTime.value = (now - this.startTime) / 1000
      this.renderFrame()
      this.frameHandle = requestAnimationFrame(loop)
    }

    this.frameHandle = requestAnimationFrame(loop)
  }

  private renderFrame() {
    if (!this.renderer || !this.mesh) {
      return
    }

    this.renderer.render({ scene: this.mesh })
  }
}

function resizeRenderer(host: HTMLElement, renderer: Renderer, program: Program, renderScale: number) {
  const width = Math.max(host.clientWidth, 1)
  const height = Math.max(host.clientHeight, 1)
  const internalWidth = Math.max(Math.round(width * renderScale), 1)
  const internalHeight = Math.max(Math.round(height * renderScale), 1)
  renderer.setSize(internalWidth, internalHeight)
  renderer.gl.canvas.style.width = `${width}px`
  renderer.gl.canvas.style.height = `${height}px`
  program.uniforms.uResolution.value = [width, height]
}

function buildSceneKey(scene: WeatherScene) {
  return JSON.stringify({
    particlePreset: scene?.particlePreset || null,
    densityScale: Number(scene?.densityScale || 0).toFixed(2),
    themeMode: scene?.themeMode || 'light',
    isNight: Boolean(scene?.isNight),
    isMobile: Boolean(scene?.isMobile)
  })
}

function presetToId(preset?: WeatherParticlePreset | null) {
  switch (preset) {
    case 'sunny':
      return 1
    case 'light_rain':
      return 2
    case 'heavy_rain':
      return 3
    case 'thunderstorm':
      return 4
    case 'snow':
      return 5
    case 'windy':
      return 6
    case 'dust':
      return 7
    case 'aurora':
      return 8
    default:
      return 0
  }
}

function clampNumber(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function resolveRenderScale(scene: WeatherScene) {
  const density = clampNumber(scene.densityScale ?? 1, 0.35, 1.5)
  const preset = scene.particlePreset

  if (scene.isMobile) {
    if (preset === 'aurora') {
      return 0.38
    }
    if (preset === 'heavy_rain' || preset === 'thunderstorm' || preset === 'snow') {
      return 0.44
    }
    return 0.5
  }

  if (preset === 'aurora') {
    return density > 1 ? 0.46 : 0.52
  }

  if (preset === 'heavy_rain' || preset === 'thunderstorm' || preset === 'snow') {
    return density > 1 ? 0.5 : 0.56
  }

  return 0.68
}
