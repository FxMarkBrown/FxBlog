<template>
    <div v-show="showWatermark" class="watermark-container">
      <canvas ref="watermarkRef" style="display: none;"></canvas>
      <div class="watermark-wrapper" ref="wrapperRef"></div>
    </div>
  </template>
  
  <script setup lang="ts">
  import { useSettingsStore,useUserStore } from '@/store'
  import settings from '@/config/settings'  // 导入系统配置
  
  const settingsStore = useSettingsStore()
  const userStore = useUserStore()
  const watermarkRef = ref<HTMLCanvasElement | null>(null)
  const wrapperRef = ref<HTMLDivElement | null>(null)
  const showWatermark = computed(() => settingsStore.watermark)
  let timer: number | null = null
  
  const createWatermark = () => {
    const canvas = watermarkRef.value
    const wrapper = wrapperRef.value
    if (!canvas || !wrapper) return
  
    const ctx = canvas.getContext('2d')
    if (!ctx) return
  
    // 设置画布大小
    canvas.width = 400
    canvas.height = 200
  
    // 清除画布
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    
    // 设置水印样式
    ctx.font = ' 15px Arial'
    ctx.fillStyle = settingsStore.theme === 'dark' 
      ? 'rgba(255, 255, 255, 0.15)'
      : 'rgba(0, 0, 0, 0.15)'
    ctx.rotate(-15 * Math.PI / 180)
    
    // 获取当前时间
    const currentTime = new Date().toLocaleString()
    // 使用系统名称
    const systemName = settings.title
    
    // 绘制水印文本
    ctx.fillText(systemName, 20, 100)
    ctx.fillText(currentTime, 20, 120)
  
    // 将 canvas 转换为背景图片
    const base64Url = canvas.toDataURL('image/png')
    
    // 设置水印容器样式
    wrapper.style.backgroundImage = `url(${base64Url})`
  }
  
  const stopTimer = () => {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  const startTimer = () => {
    stopTimer()
    timer = window.setInterval(createWatermark, 60000)
  }

  watch([
    () => settingsStore.watermark,
    () => settingsStore.theme
  ], ([newWatermark]) => {
    if (newWatermark) {
      createWatermark()
      startTimer()
      return
    }
    stopTimer()
  }, { immediate: true })
  
  // 防止水印被篡改
  const observer = new MutationObserver(() => {
    if (settingsStore.watermark) {
      createWatermark()
    }
  })
  
  onMounted(() => {
    if (showWatermark.value) {
      createWatermark()
    }
    if (wrapperRef.value) {
      observer.observe(wrapperRef.value, {
        attributes: true,
        childList: true,
        characterData: true,
        subtree: true
      })
    }
  })
  
  onUnmounted(() => {
    stopTimer()
    observer.disconnect()
  })
  </script>
  
  <style scoped>
  .watermark-container {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    z-index: 3000;
  }
  
  .watermark-wrapper {
    width: 100%;
    height: 100%;
    background-repeat: repeat;
    pointer-events: none;
  }
  </style> 
