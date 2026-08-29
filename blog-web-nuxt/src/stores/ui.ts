export const useUiStore = defineStore('ui', () => {
  const searchVisible = ref(false)
  const mobileMenuVisible = ref(false)
  // 当前页面是否带 Hero 大图（由 Hero 组件挂载/卸载时维护），驱动 Header 透明模式
  const hasHero = ref(false)

  /**
   * 设置搜索框显示状态
   * @param visible 是否显示
   */
  function setSearchVisible(visible: boolean) {
    searchVisible.value = visible
  }

  /**
   * 设置移动端菜单显示状态
   * @param visible 是否显示
   */
  function setMobileMenuVisible(visible: boolean) {
    mobileMenuVisible.value = visible
  }

  /**
   * 设置当前页面 Hero 状态
   * @param value 是否存在 Hero
   */
  function setHasHero(value: boolean) {
    hasHero.value = value
  }

  return {
    searchVisible,
    mobileMenuVisible,
    hasHero,
    setSearchVisible,
    setMobileMenuVisible,
    setHasHero
  }
})
