export const useUiStore = defineStore('ui', () => {
  const searchVisible = ref(false)
  const mobileMenuVisible = ref(false)

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

  return {
    searchVisible,
    mobileMenuVisible,
    setSearchVisible,
    setMobileMenuVisible
  }
})
