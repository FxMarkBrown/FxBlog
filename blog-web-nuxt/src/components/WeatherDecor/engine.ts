import { tsParticles } from '@tsparticles/engine'
import { loadSlim } from '@tsparticles/slim'

let slimLoader: Promise<void> | null = null
let instanceSeed = 0

export function createWeatherEngine(host: HTMLElement) {
  return new WeatherDecorEngine(host)
}

class WeatherDecorEngine {
  host: HTMLElement
  container: Awaited<ReturnType<typeof tsParticles.load>> | null
  scene: Record<string, unknown> | null
  sceneKey: string
  isPaused: boolean
  instanceId: string
  requestId: number

  constructor(host: HTMLElement) {
    this.host = host
    this.container = null
    this.scene = null
    this.sceneKey = ''
    this.isPaused = false
    this.instanceId = `weather-decor-particles-${instanceSeed++}`
    this.requestId = 0
    this.host.id = this.instanceId
  }

  async start(scene: Record<string, unknown>) {
    this.scene = scene
    const nextSceneKey = buildSceneKey(scene)

    if (!scene || !scene.particleEnabled) {
      await this.stop()
      return
    }

    if (this.container && this.sceneKey === nextSceneKey) {
      if (this.isPaused) {
        this.resume()
      }
      return
    }

    const activeRequestId = ++this.requestId
    await ensureSlimLoaded()
    if (activeRequestId !== this.requestId) {
      return
    }

    await this.destroyContainer()
    if (activeRequestId !== this.requestId) {
      return
    }

    this.sceneKey = nextSceneKey
    this.container = await tsParticles.load({
      id: this.instanceId,
      options: buildParticleOptions(scene)
    })
    this.isPaused = false

    if (activeRequestId !== this.requestId) {
      await this.destroyContainer()
      return
    }

    if (typeof document !== 'undefined' && document.hidden) {
      this.pause()
    }
  }

  pause() {
    if (!this.container || this.isPaused) {
      return
    }

    this.container.pause()
    this.isPaused = true
  }

  resume() {
    if (!this.container || !this.isPaused) {
      return
    }

    this.container.play()
    this.isPaused = false
  }

  async stop() {
    this.requestId++
    this.sceneKey = ''
    this.isPaused = false
    await this.destroyContainer()
  }

  resize() {
    if (!this.container) {
      return
    }

    Promise.resolve(this.container.refresh?.()).catch(() => {})
  }

  async destroy() {
    await this.stop()
    this.host.removeAttribute('id')
  }

  private async destroyContainer() {
    if (!this.container) {
      this.host.innerHTML = ''
      return
    }

    const current = this.container
    this.container = null
    await Promise.resolve(current.destroy?.())
    this.host.innerHTML = ''
  }
}

function ensureSlimLoaded() {
  if (!slimLoader) {
    slimLoader = loadSlim(tsParticles)
  }

  return slimLoader
}

function buildSceneKey(scene: Record<string, unknown>) {
  return JSON.stringify({
    particlePreset: scene?.particlePreset || null,
    densityScale: Number(scene?.densityScale || 0).toFixed(2),
    themeMode: scene?.themeMode || 'light',
    isNight: Boolean(scene?.isNight),
    isMobile: Boolean(scene?.isMobile)
  })
}

function buildParticleOptions(scene: Record<string, unknown>) {
  const preset = String(scene.particlePreset || '')
  const darkTheme = scene.themeMode === 'dark'
  const density = Number(scene.densityScale || 1)
  const particlePreset = PARTICLE_PRESETS[preset] || PARTICLE_PRESETS.sunny
  const colors = particlePreset.colors(darkTheme)
  const shape = typeof particlePreset.shape === 'function'
    ? particlePreset.shape(darkTheme)
    : particlePreset.shape

  return {
    fullScreen: {
      enable: false,
      zIndex: 0
    },
    background: {
      color: {
        value: 'transparent'
      }
    },
    detectRetina: true,
    fpsLimit: darkTheme ? 60 : 54,
    pauseOnBlur: false,
    pauseOnOutsideViewport: false,
    particles: {
      number: {
        value: particlePreset.number(density, Boolean(scene.isMobile)),
        density: {
          enable: false
        }
      },
      color: {
        value: colors
      },
      shape,
      opacity: particlePreset.opacity(density, darkTheme),
      size: particlePreset.size(density, Boolean(scene.isMobile)),
      move: particlePreset.move(density),
      rotate: particlePreset.rotate,
      tilt: particlePreset.tilt,
      roll: particlePreset.roll,
      wobble: particlePreset.wobble,
      life: particlePreset.life,
      zIndex: {
        value: {
          min: 0,
          max: 100
        },
        opacityRate: 1,
        sizeRate: 1,
        velocityRate: 1
      }
    }
  }
}

const PARTICLE_PRESETS = {
  sunny: {
    colors: (darkTheme: boolean) => darkTheme ? ['#ffe6a8', '#fff4d0', '#ffd488'] : ['#ffd15c', '#ffc46b', '#ffe1a5'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 14 : 22) * density),
    shape: {
      type: 'circle'
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.16 : 0.18, max: darkTheme ? 0.42 : 0.32 },
      animation: {
        enable: true,
        speed: 0.7 * density,
        sync: false
      }
    }),
    size: (density: number) => ({
      value: { min: 1.2, max: 4.2 * density }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'top-right',
      drift: 1.4,
      random: true,
      outModes: {
        default: 'out'
      },
      speed: { min: 0.18, max: 0.8 * density },
      straight: false
    }),
    wobble: {
      enable: true,
      distance: 8,
      speed: { min: -4, max: 4 }
    }
  },
  light_rain: createRainPreset(false),
  heavy_rain: createRainPreset(true),
  thunderstorm: createRainPreset(true),
  snow: {
    colors: (darkTheme: boolean) => darkTheme ? ['#ffffff', '#dfe9ff'] : ['#f6fbff', '#deecff'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 28 : 46) * density),
    shape: {
      type: 'circle'
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.3 : 0.54, max: darkTheme ? 0.86 : 0.98 },
      animation: {
        enable: true,
        speed: 0.28 * density,
        sync: false
      }
    }),
    size: (density: number) => ({
      value: { min: 1.6, max: 5.2 * density }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'bottom',
      drift: 0.18,
      gravity: {
        enable: true,
        acceleration: 0.008,
        maxSpeed: 0.9
      },
      outModes: {
        default: 'out'
      },
      random: true,
      speed: { min: 0.24, max: 0.72 * density },
      straight: false
    }),
    wobble: {
      enable: true,
      distance: 14,
      speed: { min: -5, max: 5 }
    }
  },
  windy: {
    colors: (darkTheme: boolean) => darkTheme ? ['#d7ebff', '#f0f7ff'] : ['#82b4f3', '#b7d2f7'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 14 : 24) * density),
    shape: {
      type: 'image',
      options: {
        image: [
          createImageShape(`
            <svg xmlns="http://www.w3.org/2000/svg" width="84" height="18" viewBox="0 0 84 18">
              <path d="M2 9c8-8 18-8 28 0 10 8 20 8 30 0 6-5 13-6 22-3" fill="none" stroke="#d8ecff" stroke-linecap="round" stroke-width="2.4"/>
            </svg>
          `, 84, 18)
        ]
      }
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.24 : 0.34, max: darkTheme ? 0.56 : 0.64 }
    }),
    size: () => ({
      value: { min: 22, max: 34 }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'right',
      drift: 2.8,
      outModes: {
        default: 'out'
      },
      speed: { min: 1.8, max: 3.8 * density },
      straight: false
    }),
    rotate: {
      value: { min: -4, max: 4 }
    }
  },
  dust: {
    colors: (darkTheme: boolean) => darkTheme ? ['#e3a95f', '#f2c57a'] : ['#b9783f', '#d9a164'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 24 : 38) * density),
    shape: {
      type: 'circle'
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.18 : 0.16, max: darkTheme ? 0.38 : 0.28 },
      animation: {
        enable: true,
        speed: 0.35 * density,
        sync: false
      }
    }),
    size: (density: number) => ({
      value: { min: 1.2, max: 4.8 * density }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'right',
      drift: 1.8,
      outModes: {
        default: 'out'
      },
      speed: { min: 0.3, max: 1.2 * density },
      straight: false
    }),
    wobble: {
      enable: true,
      distance: 10,
      speed: { min: -3, max: 3 }
    }
  },
  sakura: {
    colors: (darkTheme: boolean) => darkTheme ? ['#ffd7ec', '#f7bfdc', '#ffe7f3'] : ['#f6a7c6', '#f8bfd3', '#ffd7e6'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 16 : 26) * density),
    shape: {
      type: 'image',
      options: {
        image: [
          createImageShape(`
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
              <g fill="none" fill-rule="evenodd">
                <path fill="#F4B6D2" d="M16 4.7c1.7 0 3.4 2.2 3.6 5.1.2 1.8-.4 3-1.3 4 .8-.1 1.6-.1 2.6.1 2.8.6 4.7 2.5 4.7 4.2 0 1.8-2.2 3.5-5.1 3.6-1.8.2-3-.4-4-1.2.1.8.1 1.6-.1 2.6-.6 2.8-2.5 4.7-4.2 4.7-1.8 0-3.5-2.2-3.6-5.1-.2-1.8.4-3 1.2-4-.8.1-1.6.1-2.6-.1-2.8-.6-4.7-2.5-4.7-4.2 0-1.8 2.2-3.5 5.1-3.6 1.8-.2 3 .4 4 1.2-.1-.8-.1-1.6.1-2.6.6-2.8 2.5-4.7 4.3-4.7Z"/>
                <path fill="#FFDCEB" d="M16 7.2c1.2 0 2.3 1.6 2.4 3.5.2 1.9-.7 2.9-1.8 3.8-.4.3-.3 1 .2 1.1 1.4.3 2.7-.1 4.5.3 2 .4 3.4 1.6 3.4 2.6 0 1.1-1.5 2.2-3.5 2.4-1.9.2-2.9-.7-3.8-1.8-.3-.4-1-.3-1.1.2-.3 1.4.1 2.7-.3 4.5-.4 2-1.6 3.4-2.6 3.4-1.1 0-2.2-1.5-2.4-3.5-.2-1.9.7-2.9 1.8-3.8.4-.3.3-1-.2-1.1-1.4-.3-2.7.1-4.5-.3-2-.4-3.4-1.6-3.4-2.6 0-1.1 1.5-2.2 3.5-2.4 1.9-.2 2.9.7 3.8 1.8.3.4 1 .3 1.1-.2.3-1.4-.1-2.7.3-4.5.4-2 1.6-3.4 2.6-3.4Z"/>
                <path fill="#EA5D95" d="M16 13.4c1 0 1.8.8 1.8 1.8S17 17 16 17s-1.8-.8-1.8-1.8.8-1.8 1.8-1.8Z"/>
                <path stroke="#EA5D95" stroke-linecap="round" stroke-width="1.1" d="M16 12.1v-2M13.2 13.2l-1.6-1.5M18.8 13.2l1.6-1.5M12.9 16.2l-2 .5M19.1 16.2l2 .5M14.3 18l-.8 1.8M17.7 18l.8 1.8"/>
                <circle cx="16" cy="10.1" r=".9" fill="#F7D154"/>
                <circle cx="11.7" cy="11.8" r=".9" fill="#F7D154"/>
                <circle cx="20.3" cy="11.8" r=".9" fill="#F7D154"/>
                <circle cx="10.8" cy="16.7" r=".9" fill="#F7D154"/>
                <circle cx="21.2" cy="16.7" r=".9" fill="#F7D154"/>
                <circle cx="13.5" cy="19.8" r=".9" fill="#F7D154"/>
                <circle cx="18.5" cy="19.8" r=".9" fill="#F7D154"/>
              </g>
            </svg>
          `, 32, 32)
        ]
      }
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.22 : 0.32, max: darkTheme ? 0.72 : 0.82 }
    }),
    size: (density: number) => ({
      value: { min: 10, max: 18 * density }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'bottom',
      angle: {
        value: 104,
        offset: { min: -10, max: 12 }
      },
      drift: 0,
      gravity: {
        enable: false
      },
      outModes: {
        default: 'out'
      },
      speed: { min: 0.3, max: 0.68 * density },
      straight: false
    }),
    rotate: {
      value: { min: 0, max: 360 },
      animation: {
        enable: true,
        speed: 8,
        sync: false
      }
    },
    wobble: {
      enable: true,
      distance: 30,
      speed: { min: -4.2, max: 4.2 }
    }
  },
  leaves: {
    colors: (darkTheme: boolean) => darkTheme ? ['#e9b064', '#cf7e34', '#9e5925'] : ['#cb6c2b', '#da8e3a', '#e6b35f'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 12 : 20) * density),
    shape: {
      type: 'image',
      options: {
        image: [
          createImageShape(`
            <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 36 36">
              <g fill="none" fill-rule="evenodd">
                <path fill="#F28E46" d="M18 4.5c1.4 2.2 2.7 3.4 4.9 4.1 2.2.7 3.7.7 5.5 2.4-1 2.4-2.7 3.2-4.2 4.1 1.9.1 3.5.5 5.1 1.9-.8 2.4-2.3 3.4-4.1 4.1 1.8.5 3.1 1.4 4 3.2-1.6 1.7-3.1 2.2-5.3 2.1-1.7-.1-2.8-.8-4.6-2 .1 1.9-.2 3.5-1.7 5.4-2.5-.3-3.8-1.5-4.7-3.2-.9 1.7-2.2 2.9-4.7 3.2-1.5-1.9-1.8-3.5-1.7-5.4-1.8 1.2-2.9 1.9-4.6 2-2.2.1-3.7-.4-5.3-2.1.9-1.8 2.2-2.7 4-3.2-1.8-.7-3.3-1.7-4.1-4.1 1.6-1.4 3.2-1.8 5.1-1.9-1.5-.9-3.2-1.7-4.2-4.1 1.8-1.7 3.3-1.7 5.5-2.4 2.2-.7 3.5-1.9 4.9-4.1Z"/>
                <path fill="#F8B15E" d="M18 7.2c1 1.5 1.7 2.4 3 2.9 1.4.5 2.5.6 3.8 1.6-.8 1.5-1.8 2.2-2.9 2.9-1 .6-.7 2 .5 2 1.6.1 2.8.3 4 1-.7 1.6-1.8 2.3-3.2 2.8-1 .4-.9 1.8.2 2.1 1.4.5 2.4 1.1 3.1 2.3-1.2.9-2.2 1.2-3.8 1.1-1.5-.1-2.4-.8-4-1.9-.9-.7-2.2.1-2 1.2.2 1.7 0 2.8-.9 4.2-1.6-.4-2.4-1.3-2.9-2.8-.4-1-1.8-1-2.2 0-.5 1.5-1.3 2.4-2.9 2.8-.9-1.4-1.1-2.5-.9-4.2.2-1.1-1.1-1.9-2-1.2-1.6 1.1-2.5 1.8-4 1.9-1.6.1-2.6-.2-3.8-1.1.7-1.2 1.7-1.8 3.1-2.3 1.1-.3 1.2-1.7.2-2.1-1.4-.5-2.5-1.2-3.2-2.8 1.2-.7 2.4-.9 4-1 1.2 0 1.5-1.4.5-2-1.1-.7-2.1-1.4-2.9-2.9 1.3-1 2.4-1.1 3.8-1.6 1.3-.5 2-1.4 3-2.9Z"/>
                <path stroke="#A94E25" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.4" d="M18 7.6v20.8M18 14.4l-5-4.2M18 16.4l6.3-4.5M18 19l-7 0M18 19l7 0M18 21.8l-6.3 4.5M18 23l5 4.2"/>
              </g>
            </svg>
          `, 36, 36),
          createImageShape(`
            <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 36 36">
              <g fill="none" fill-rule="evenodd">
                <path fill="#D79552" d="M12.7 8.6c2.3-.1 4.4.6 6.1 2.1 1-.6 2.1-1.1 3.6-1.4 2.2-.4 4.1-.1 6 .9-.2 2.4-1 4.2-2.4 5.7 1.6.7 2.8 1.8 3.8 3.6-1.2 1.9-2.8 3-4.7 3.6.8 1.6 1 3.1.8 5.3-2.3.7-4.3.5-6-.2-1 1.1-2.3 2.1-4.2 2.9-2-.9-3.3-1.8-4.2-2.9-1.7.7-3.7.9-6 .2-.2-2.2 0-3.7.8-5.3-1.9-.6-3.5-1.7-4.7-3.6 1-1.8 2.2-2.9 3.8-3.6-1.4-1.5-2.2-3.3-2.4-5.7 1.9-1 3.8-1.3 6-.9 1.5.3 2.7.8 3.7 1.4 1.6-1.5 3.7-2.2 6-2.1Z"/>
                <path fill="#E6B26F" d="M13.4 10.8c1.9 0 3.6.7 4.9 2.1.6.6 1.5.6 2.1 0 1.4-1.4 3-2.1 5-2.1.4 1.8.1 3.2-.8 4.6-.5.7-.1 1.7.8 1.9 1.7.5 3 1.3 4.1 2.9-.9 1.3-2 2-3.6 2.4-.9.2-1.3 1.2-.8 2 .8 1.4 1 2.5.8 4.2-1.8.3-3.2.1-4.7-.7-.7-.4-1.7-.1-1.9.7-.5 1.4-1.4 2.6-3 3.8-1.6-1.2-2.5-2.4-3-3.8-.2-.8-1.2-1.1-1.9-.7-1.5.8-2.9 1-4.7.7-.2-1.7 0-2.8.8-4.2.5-.8.1-1.8-.8-2-1.6-.4-2.8-1.1-3.6-2.4 1.1-1.6 2.4-2.4 4.1-2.9.9-.2 1.3-1.2.8-1.9-.9-1.4-1.2-2.8-.8-4.6Z"/>
                <path stroke="#8E5A32" stroke-linecap="round" stroke-width="1.4" d="M17.8 10.9c1.5 4.8.7 10.8-2.6 15.8M17.1 16.3c-3.3-.8-5.8-2.7-8-5.4M17.8 19c3.3-.2 6.2-1.5 9-3.7"/>
              </g>
            </svg>
          `, 36, 36)
        ]
      }
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.24 : 0.34, max: darkTheme ? 0.76 : 0.82 }
    }),
    size: (density: number) => ({
      value: { min: 12, max: 22 * density }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'bottom',
      angle: {
        value: 100,
        offset: { min: -12, max: 14 }
      },
      drift: 0,
      gravity: {
        enable: false
      },
      outModes: {
        default: 'out'
      },
      speed: { min: 0.78, max: 1.58 * density },
      straight: false
    }),
    rotate: {
      value: { min: 0, max: 360 },
      animation: {
        enable: true,
        speed: 12,
        sync: false
      }
    },
    wobble: {
      enable: true,
      distance: 24,
      speed: { min: -7, max: 7 }
    }
  },
  fireflies: {
    colors: (darkTheme: boolean) => darkTheme ? ['#ffed8f', '#c9ff9d', '#ffe17a'] : ['#f6cb59', '#d8e962', '#f9d574'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? 10 : 18) * density),
    shape: {
      type: 'circle'
    },
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.12 : 0.08, max: darkTheme ? 0.82 : 0.56 },
      animation: {
        enable: true,
        speed: 1.4 * density,
        sync: false
      }
    }),
    size: (density: number) => ({
      value: { min: 2, max: 5.5 * density },
      animation: {
        enable: true,
        speed: 1.2,
        sync: false,
        minimumValue: 1.2
      }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'none',
      drift: 0.8,
      outModes: {
        default: 'bounce'
      },
      random: true,
      speed: { min: 0.18, max: 0.65 * density },
      straight: false
    }),
    life: {
      count: 0,
      duration: {
        sync: false,
        value: { min: 1.6, max: 4.2 }
      }
    }
  }
}

function createRainPreset(isHeavy: boolean) {
  return {
    colors: (darkTheme: boolean) => darkTheme ? ['#c7e0ff', '#e2f1ff'] : ['#4d82c7', '#76aee8'],
    number: (density: number, isMobile: boolean) => Math.round((isMobile ? (isHeavy ? 48 : 34) : (isHeavy ? 92 : 70)) * density),
    shape: (darkTheme: boolean) => ({
      type: 'image',
      options: {
        image: [
          createImageShape(buildRainDropSvg(darkTheme), 16, 58)
        ]
      }
    }),
    opacity: (density: number, darkTheme: boolean) => ({
      value: { min: darkTheme ? 0.24 : 0.42, max: darkTheme ? 0.74 : 0.94 }
    }),
    size: () => ({
      value: { min: isHeavy ? 18 : 14, max: isHeavy ? 24 : 20 }
    }),
    move: (density: number) => ({
      enable: true,
      direction: 'bottom',
      drift: isHeavy ? 0.14 : 0.08,
      outModes: {
        default: 'out'
      },
      speed: { min: isHeavy ? 6 : 4.2, max: (isHeavy ? 10 : 7.5) * density },
      straight: true
    })
  }
}

function buildRainDropSvg(darkTheme: boolean) {
  if (darkTheme) {
    return `
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="58" viewBox="0 0 16 58">
        <path d="M8.2 2.5 6.5 55.5" fill="none" stroke="#cfe4ff" stroke-linecap="round" stroke-width="2.4" opacity="0.94"/>
        <path d="M8 3.5 6.9 53.5" fill="none" stroke="#ffffff" stroke-linecap="round" stroke-width="1.05" opacity="0.28"/>
      </svg>
    `
  }

  return `
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="58" viewBox="0 0 16 58">
      <path d="M8.2 2.5 6.5 55.5" fill="none" stroke="#5e87bc" stroke-linecap="round" stroke-width="2.7" opacity="0.98"/>
      <path d="M8 3.5 6.9 53.5" fill="none" stroke="#dcebff" stroke-linecap="round" stroke-width="1.15" opacity="0.52"/>
    </svg>
  `
}

function createImageShape(svg: string, width: number, height: number) {
  return {
    src: svgToDataUri(svg),
    width,
    height
  }
}

function svgToDataUri(svg: string) {
  return `data:image/svg+xml,${encodeURIComponent(minifySvg(svg))}`
}

function minifySvg(svg: string) {
  return svg.replace(/\s+/g, ' ').trim()
}
