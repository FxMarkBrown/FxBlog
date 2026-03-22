<script setup lang="ts">
import { tsParticles } from '@tsparticles/engine'
import { loadSlim } from '@tsparticles/slim'

const hostRef = ref<HTMLElement | null>(null)
const instanceId = `login-particles-${Math.random().toString(36).slice(2, 10)}`
let particlesContainer: Awaited<ReturnType<typeof tsParticles.load>> | null = null
let slimLoader: Promise<void> | null = null

/**
 * 读取当前主题模式。
 */
function getThemeMode() {
  if (!import.meta.client) {
    return 'light'
  }

  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

/**
 * 确保粒子引擎预设仅加载一次。
 */
function ensureSlimLoaded() {
  if (!slimLoader) {
    slimLoader = loadSlim(tsParticles)
  }

  return slimLoader
}

/**
 * 生成登录页粒子参数。
 */
function createParticleOptions() {
  const isDarkMode = getThemeMode() === 'dark'

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
    fpsLimit: 60,
    interactivity: {
      events: {
        onHover: {
          enable: true,
          mode: 'grab'
        },
        resize: {
          enable: true
        }
      },
      modes: {
        grab: {
          distance: 160,
          links: {
            opacity: isDarkMode ? 0.2 : 0.22
          }
        }
      }
    },
    particles: {
      color: {
        value: isDarkMode ? ['#dbeafe', '#93c5fd', '#c4b5fd'] : ['#0f766e', '#2563eb', '#7c3aed']
      },
      links: {
        enable: true,
        distance: 130,
        color: isDarkMode ? '#93c5fd' : '#2563eb',
        opacity: isDarkMode ? 0.16 : 0.18,
        width: 1
      },
      move: {
        enable: true,
        direction: 'none',
        outModes: {
          default: 'bounce'
        },
        random: false,
        speed: isDarkMode ? 0.52 : 0.62,
        straight: false
      },
      number: {
        value: 42,
        density: {
          enable: true,
          width: 1200,
          height: 720
        }
      },
      opacity: {
        value: { min: isDarkMode ? 0.1 : 0.16, max: isDarkMode ? 0.28 : 0.34 }
      },
      shape: {
        type: ['circle']
      },
      size: {
        value: { min: 1.2, max: 3.4 }
      }
    }
  }
}

/**
 * 重建粒子实例以同步主题与布局。
 */
async function renderParticles() {
  if (!hostRef.value || !import.meta.client) {
    return
  }

  await ensureSlimLoaded()

  if (particlesContainer) {
    await particlesContainer.destroy()
    particlesContainer = null
  }

  hostRef.value.id = instanceId
  particlesContainer = await tsParticles.load({
    id: instanceId,
    options: createParticleOptions()
  })
}

/**
 * 强制销毁粒子实例与残留 canvas。
 */
async function destroyParticles() {
  if (particlesContainer) {
    await particlesContainer.destroy()
    particlesContainer = null
  }

  if (!import.meta.client) {
    return
  }

  const host = hostRef.value
  if (host) {
    host.innerHTML = ''
    host.removeAttribute('style')
  }

  document.querySelectorAll(`#${instanceId}`).forEach((element) => {
    if (element instanceof HTMLElement && element !== host) {
      element.remove()
    }
  })
}

/**
 * 响应主题变化事件。
 */
function handleThemeChange() {
  void renderParticles()
}

onMounted(() => {
  void renderParticles()
  window.addEventListener('theme-change', handleThemeChange)
})

onBeforeUnmount(async () => {
  window.removeEventListener('theme-change', handleThemeChange)
  await destroyParticles()
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
