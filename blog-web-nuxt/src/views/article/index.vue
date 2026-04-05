<script setup lang="ts">
import {defineAsyncComponent} from 'vue'
import {ElMessage} from 'element-plus'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'
import 'md-editor-v3/lib/style.css'
import {favoriteArticleApi, getArticleDetailApi, likeArticleApi, unlikeArticleApi} from '@/api/article'
import ArticleShareCard from '@/components/ArticleShareCard/index.vue'
import Comment from '@/components/Comment/index.vue'
import ImagePreview from '@/components/Common/ImagePreview.vue'
import {usePageSeo} from '@/composables/useSeo'
import type {ArticleDetail} from '@/types/article'
import {unwrapResponseData} from '@/utils/response'

interface TocItem {
  id: string
  text: string
  level: number
  path: string
}

const MdPreview = defineAsyncComponent(() => import('md-editor-v3').then((module) => module.MdPreview))

const route = useRoute()
const runtimeConfig = useRuntimeConfig()
const siteStore = useSiteStore()

const articleContentRef = ref<HTMLElement | null>(null)
const imagePreviewRef = ref<InstanceType<typeof ImagePreview> | null>(null)
const article = ref<ArticleDetail | null>(null)
const tocItems = ref<TocItem[]>([])
const images = ref<string[]>([])
const activeHeading = ref('')
const readProgress = ref(0)
const readTime = ref(1)
const shareCardVisible = ref(false)
const loading = ref(false)
const showSidebar = ref(true)
const actionBarLeft = ref('0px')
const articleTheme = ref<'light' | 'dark'>('light')
const likeDebounce = ref(false)
const favoriteDebounce = ref(false)
const headingElements = ref<HTMLElement[]>([])
const collapsedCodeBlocks = reactive(new Set<number>())
let headingFrame = 0

const articleId = computed(() => String(route.params.id || ''))
const articleContent = computed(() => String(article.value?.contentMd || article.value?.content || ''))
const articleSummary = computed(() => String(article.value?.summary || article.value?.introduction || siteStore.websiteInfo.summary || '暂无摘要'))
const siteName = computed(() => String(siteStore.websiteInfo.name || siteStore.websiteInfo.title || runtimeConfig.public.siteName || 'Open Source Blog'))
const normalizedSiteUrl = computed(() => String(runtimeConfig.public.siteUrl || '').replace(/\/+$/, ''))
const canonicalUrl = computed(() => `${normalizedSiteUrl.value}/post/${articleId.value}`)
const currentUrl = computed(() => (import.meta.client ? window.location.href : canonicalUrl.value))
const categoryName = computed(() => String(article.value?.category?.name || article.value?.categoryName || '未分类'))

usePageSeo({
  title: () => article.value?.title ? `${article.value.title} - ${siteName.value}` : '文章详情',
  description: () => articleSummary.value,
  path: () => `/post/${articleId.value}`,
  image: () => article.value?.cover || siteStore.websiteInfo.logo || runtimeConfig.public.seoImage,
  type: 'article'
})

useHead(() => ({
  script: article.value
    ? [
        {
          type: 'application/ld+json',
          children: JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'BlogPosting',
            headline: article.value.title,
            description: articleSummary.value,
            datePublished: article.value.createTime,
            dateModified: article.value.updateTime || article.value.createTime,
            mainEntityOfPage: canonicalUrl.value,
            articleSection: categoryName.value,
            author: {
              '@type': 'Person',
              name: article.value.nickname || siteStore.websiteInfo.author || siteName.value
            }
          })
        }
      ]
    : []
}))

const { data: articleData, pending: articlePending } = await useAsyncData(
  () => `article-${articleId.value}`,
  async () => {
    const response = await getArticleDetailApi(articleId.value)
    return unwrapResponseData<ArticleDetail | null>(response)
  },
  {
    watch: [articleId]
  }
)

/**
 * 估算文章阅读时长。
 */
function resolveReadTime(content: string) {
  const textContent = String(content || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/\$\$[\s\S]*?\$\$/g, ' ')
    .replace(/\$[^$\n]+\$/g, ' ')
    .replace(/!\[[^\]]*]\([^)]+\)/g, ' ')
    .replace(/\[[^\]]*]\([^)]+\)/g, ' ')
    .replace(/[#>*_~\-|]/g, ' ')
  const readableLength = textContent.replace(/\s+/g, '').length
  return Math.max(1, Math.ceil(readableLength / 300))
}

/**
 * 根据当前主题同步 Markdown 预览主题。
 */
function syncTheme() {
  if (!import.meta.client) {
    articleTheme.value = 'light'
    return
  }

  articleTheme.value = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

/**
 * 重新收集文章相关增强效果。
 */
async function refreshArticleEnhancements() {
  if (!import.meta.client || !article.value) {
    return
  }

  await nextTick()
  window.setTimeout(() => {
    generateToc()
    headingElements.value = tocItems.value
      .map((item) => document.getElementById(item.id))
      .filter((item): item is HTMLElement => Boolean(item))
    highlightCodeBlocks()
    initializeCodeBlocks()
    addCopyButtons()
    addLineNumbers()
    initImagePreview()
    updateActionBarPosition()
    restoreRequestedHeading()
    updateActiveHeading()
  }, 80)
}

/**
 * 生成文章目录。
 */
function generateToc() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  const headings = articleContentElement.querySelectorAll('h1, h2, h3, h4, h5, h6')
  const slugCounter = new Map<string, number>()
  const headingStack: string[] = []

  tocItems.value = Array.from(headings).map((heading) => {
    const level = Number(heading.tagName.charAt(1))
    while (headingStack.length >= level) {
      headingStack.pop()
    }

    headingStack.push(heading.textContent?.trim() || '')
    const baseId = (heading.textContent || '')
      .trim()
      .toLowerCase()
      .replace(/[^\w\u4e00-\u9fa5-]/g, '')
      .replace(/\s+/g, '-') || 'heading'
    const currentCount = slugCounter.get(baseId) || 0
    slugCounter.set(baseId, currentCount + 1)
    const id = currentCount ? `${baseId}-${currentCount}` : baseId

    heading.id = id

    return {
      id,
      text: heading.textContent?.trim() || '',
      level,
      path: headingStack.join(' > ')
    }
  })
}

/**
 * 获取当前路由请求的标题锚点。
 */
function getRequestedHeadingId() {
  const sectionPath = String(route.query.sectionPath || '').trim()
  if (sectionPath) {
    const matched = tocItems.value.find((item) => item.path === sectionPath)
    if (matched?.id) {
      return matched.id
    }
  }

  return String(route.hash || '').replace(/^#/, '').trim()
}

/**
 * 恢复用户请求的标题滚动位置。
 */
function restoreRequestedHeading() {
  const targetId = getRequestedHeadingId()
  if (!targetId || !import.meta.client) {
    return
  }

  nextTick(() => {
    scrollToHeading(targetId)
    activeHeading.value = targetId
  })
}

/**
 * 执行代码高亮。
 */
function highlightCodeBlocks() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  hljs.configure({
    ignoreUnescapedHTML: true
  })

  articleContentElement.querySelectorAll('pre code').forEach((block) => {
    hljs.highlightElement(block as HTMLElement)
  })
}

/**
 * 初始化长代码块折叠状态。
 */
function initializeCodeBlocks() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  articleContentElement.querySelectorAll('pre').forEach((pre, index) => {
    if (pre.closest('.md-editor-code')) {
      return
    }

    const oldButton = pre.querySelector('.expand-button')
    if (oldButton) {
      oldButton.remove()
    }

    const actualHeight = (pre as HTMLElement).scrollHeight
    if (actualHeight <= 500) {
      pre.classList.remove('collapsed')
      return
    }

    pre.classList.add('collapsed')
    const expandButton = document.createElement('button')
    expandButton.className = 'expand-button'
    expandButton.innerHTML = '<i class="fas fa-chevron-down"></i>展开代码'
    expandButton.onclick = (event) => {
      event.stopPropagation()
      const isCollapsed = pre.classList.contains('collapsed')
      if (isCollapsed) {
        pre.classList.remove('collapsed')
        expandButton.innerHTML = '<i class="fas fa-chevron-up"></i>收起代码'
        collapsedCodeBlocks.delete(index)
        return
      }

      pre.classList.add('collapsed')
      expandButton.innerHTML = '<i class="fas fa-chevron-down"></i>展开代码'
      collapsedCodeBlocks.add(index)
    }
    pre.appendChild(expandButton)
  })
}

/**
 * 为代码块添加复制按钮。
 */
function addCopyButtons() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  articleContentElement.querySelectorAll('pre').forEach((pre) => {
    if (pre.closest('.md-editor-code') || pre.querySelector('.code-header')) {
      return
    }

    const buttonWrapper = document.createElement('div')
    buttonWrapper.className = 'code-header'

    const copyButton = document.createElement('button')
    copyButton.className = 'copy-button'
    copyButton.innerHTML = '<i class="fas fa-copy"></i> 复制'
    copyButton.title = '复制代码'
    copyButton.addEventListener('click', async () => {
      try {
        const code = pre.querySelector('code')
        await navigator.clipboard.writeText(code?.textContent || '')
        copyButton.innerHTML = '<i class="fas fa-check"></i> 已复制'
        copyButton.classList.add('copied')
        window.setTimeout(() => {
          copyButton.innerHTML = '<i class="fas fa-copy"></i> 复制'
          copyButton.classList.remove('copied')
        }, 2000)
        ElMessage.success('复制成功')
      } catch {
        ElMessage.error('复制失败，请手动复制')
      }
    })

    buttonWrapper.appendChild(copyButton)
    pre.appendChild(buttonWrapper)
  })
}

/**
 * 为代码块补充行号。
 */
function addLineNumbers() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  articleContentElement.querySelectorAll('pre code').forEach((code) => {
    const pre = code.parentElement
    if (!pre || pre.closest('.md-editor-code') || pre.querySelector('.line-numbers')) {
      return
    }

    const lines = (code.textContent || '').split('\n').length
    const lineNumbers = document.createElement('div')
    lineNumbers.className = 'line-numbers'

    for (let index = 1; index <= lines; index += 1) {
      const span = document.createElement('span')
      span.textContent = String(index)
      lineNumbers.appendChild(span)
    }

    pre.insertBefore(lineNumbers, code)
  })
}

/**
 * 初始化正文图片预览。
 */
function initImagePreview() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  images.value = Array.from(articleContentElement.querySelectorAll('img'))
    .map((img) => img.getAttribute('src') || '')
    .filter(Boolean)

  articleContentElement.querySelectorAll('img').forEach((img) => {
    img.style.cursor = 'zoom-in'
    img.removeEventListener('click', handleImageClick)
    img.addEventListener('click', handleImageClick)
  })
}

/**
 * 处理正文图片点击预览。
 */
function handleImageClick(event: Event) {
  const target = event.target as HTMLImageElement | null
  if (!target) {
    return
  }

  const currentSrc = target.getAttribute('src') || ''
  imagePreviewRef.value?.show(images.value, Math.max(images.value.indexOf(currentSrc), 0))
}

/**
 * 计算滚动阅读进度。
 */
function handleScroll() {
  if (!import.meta.client) {
    return
  }

  const docEl = document.documentElement
  const docHeight = docEl.scrollHeight - window.innerHeight
  const scrollTop = window.scrollY || docEl.scrollTop
  if (docHeight <= 0) {
    readProgress.value = 0
    return
  }

  readProgress.value = Math.min(100, Math.max(0, Math.round((scrollTop / docHeight) * 100)))
}

/**
 * 滚动到指定标题。
 */
function scrollToHeading(id: string) {
  if (!import.meta.client) {
    return
  }

  const element = document.getElementById(id)
  if (!element) {
    return
  }

  const header = document.querySelector('.site-header') as HTMLElement | null
  const headerHeight = header?.offsetHeight || 0
  window.scrollTo({
    top: Math.max(element.offsetTop - headerHeight - 20, 0),
    behavior: 'smooth'
  })
}

/**
 * 更新当前激活标题。
 */
function updateActiveHeading() {
  if (!import.meta.client) {
    return
  }

  handleScroll()
  const header = document.querySelector('.site-header') as HTMLElement | null
  const headerHeight = header?.offsetHeight || 0

  for (let index = headingElements.value.length - 1; index >= 0; index -= 1) {
    const heading = headingElements.value[index]
    if (!heading) {
      continue
    }
    if (heading.getBoundingClientRect().top <= headerHeight + 100) {
      activeHeading.value = heading.id
      break
    }
  }
}

/**
 * 合并滚动时的目录高亮更新。
 */
function queueActiveHeadingUpdate() {
  if (headingFrame || !import.meta.client) {
    return
  }

  headingFrame = window.requestAnimationFrame(() => {
    headingFrame = 0
    updateActiveHeading()
  })
}

/**
 * 切换文章点赞状态。
 */
async function toggleLike() {
  if (!article.value) {
    return
  }

  if (likeDebounce.value) {
    ElMessage.warning('请于 5 秒后再试')
    return
  }

  try {
    if (article.value.isLike) {
      await unlikeArticleApi(articleId.value)
      article.value.likeNum = Math.max(Number(article.value.likeNum || 0) - 1, 0)
      article.value.isLike = false
      ElMessage.success('已取消点赞')
    } else {
      await likeArticleApi(articleId.value)
      article.value.likeNum = Number(article.value.likeNum || 0) + 1
      article.value.isLike = true
      ElMessage.success('点赞成功')
    }

    likeDebounce.value = true
    window.setTimeout(() => {
      likeDebounce.value = false
    }, 5000)
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

/**
 * 切换文章收藏状态。
 */
async function toggleFavorite() {
  if (!article.value) {
    return
  }

  if (favoriteDebounce.value) {
    ElMessage.warning('请于 5 秒后再试')
    return
  }

  try {
    await favoriteArticleApi(articleId.value)
    if (article.value.isFavorite) {
      article.value.favoriteNum = Math.max(Number(article.value.favoriteNum || 0) - 1, 0)
      article.value.isFavorite = false
      ElMessage.success('取消收藏成功')
    } else {
      article.value.favoriteNum = Number(article.value.favoriteNum || 0) + 1
      article.value.isFavorite = true
      ElMessage.success('收藏成功')
    }

    favoriteDebounce.value = true
    window.setTimeout(() => {
      favoriteDebounce.value = false
    }, 5000)
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

/**
 * 打开分享卡片弹窗。
 */
function openShareCard() {
  shareCardVisible.value = true
}

/**
 * 切换沉浸式阅读侧栏。
 */
function toggleSidebar() {
  showSidebar.value = !showSidebar.value
  nextTick(() => {
    updateActionBarPosition()
  })
}

/**
 * 滚动到评论区。
 */
function scrollToComments() {
  if (!import.meta.client) {
    return
  }

  document.querySelector('.comment-section')?.scrollIntoView({ behavior: 'smooth' })
}

/**
 * 更新悬浮操作栏横向位置。
 */
function updateActionBarPosition() {
  if (!import.meta.client) {
    return
  }

  const articleBox = document.getElementById('articleBox')
  if (!articleBox) {
    return
  }

  const rect = articleBox.getBoundingClientRect()
  actionBarLeft.value = `${rect.left - 95}px`
}

/**
 * 清理文章内容上的事件副作用。
 */
function cleanupArticleEffects() {
  const articleContentElement = articleContentRef.value
  if (!import.meta.client || !articleContentElement) {
    return
  }

  if (headingFrame) {
    window.cancelAnimationFrame(headingFrame)
    headingFrame = 0
  }

  articleContentElement.querySelectorAll('img').forEach((img) => {
    img.removeEventListener('click', handleImageClick)
  })
}

/**
 * 处理评论新增后的计数。
 */
function handleCommentAdded() {
  if (!article.value) {
    return
  }

  article.value.commentNum = Number(article.value.commentNum || 0) + 1
}

/**
 * 处理评论删除后的计数。
 */
function handleCommentDeleted() {
  if (!article.value) {
    return
  }

  article.value.commentNum = Math.max(Number(article.value.commentNum || 0) - 1, 0)
}

watch(
  articleData,
  (value) => {
    cleanupArticleEffects()
    tocItems.value = []
    images.value = []
    activeHeading.value = ''
    readProgress.value = 0
    article.value = value || null
    readTime.value = resolveReadTime(String(value?.contentMd || value?.content || ''))
  },
  { immediate: true }
)

watch(
  articlePending,
  (value) => {
    loading.value = value
  },
  { immediate: true }
)

watch(articleContent, () => {
  readTime.value = resolveReadTime(articleContent.value)
  if (import.meta.client) {
    void refreshArticleEnhancements()
  }
})

watch(articleId, async (value, oldValue) => {
  if (!value || value === oldValue) {
    return
  }

  if (import.meta.client) {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }
})

watch(
  () => route.query.sectionPath,
  (value, oldValue) => {
    if (value !== oldValue) {
      restoreRequestedHeading()
    }
  }
)

watch(
  () => route.hash,
  (value, oldValue) => {
    if (value !== oldValue) {
      restoreRequestedHeading()
    }
  }
)

onMounted(() => {
  if (!siteStore.loaded) {
    void siteStore.fetchWebsiteInfo().catch(() => null)
  }
  syncTheme()
  void refreshArticleEnhancements()
  window.addEventListener('theme-change', syncTheme)
  window.addEventListener('scroll', queueActiveHeadingUpdate, { passive: true })
  window.addEventListener('resize', updateActionBarPosition)
})

onBeforeUnmount(() => {
  window.removeEventListener('theme-change', syncTheme)
  window.removeEventListener('scroll', queueActiveHeadingUpdate)
  window.removeEventListener('resize', updateActionBarPosition)
  cleanupArticleEffects()
})
</script>

<template>
  <div v-loading="loading" class="article-page">
    <template v-if="article">
      <ClientOnly>
        <div class="floating-action-bar" :style="{ left: actionBarLeft }">
          <ElTooltip effect="dark" content="点赞" placement="top-start">
            <div class="action-item" @click="toggleLike">
              <ElBadge :value="article.likeNum || 0" class="item">
                <div class="action-button">
                  <i class="fas fa-thumbs-up" :class="{ active: article.isLike }"></i>
                </div>
              </ElBadge>
            </div>
          </ElTooltip>
          <ElTooltip effect="dark" content="收藏" placement="top-start">
            <div class="action-item" @click="toggleFavorite">
              <ElBadge :value="article.favoriteNum || 0" class="item">
                <div class="action-button">
                  <i class="fas fa-star" :class="{ active: article.isFavorite }"></i>
                </div>
              </ElBadge>
            </div>
          </ElTooltip>
          <ElTooltip effect="dark" content="评论" placement="top-start">
            <div class="action-item" @click="scrollToComments">
              <ElBadge :value="article.commentNum || 0" class="item">
                <div class="action-button">
                  <i class="fas fa-comment"></i>
                </div>
              </ElBadge>
            </div>
          </ElTooltip>
          <ElTooltip effect="dark" content="沉浸式浏览" placement="top-start">
            <div class="action-item" @click="toggleSidebar">
              <div class="action-button">
                <i class="fas fa-expand"></i>
              </div>
            </div>
          </ElTooltip>
        </div>
      </ClientOnly>

      <div id="articleBox" class="content-layout" :class="{ center: !showSidebar }">
        <main class="article-main">
          <header class="article-header">
            <div class="article-title">{{ article.title }}</div>

            <div class="article-info">
              <div class="author-info">
                <img :src="article.avatar" alt="作者头像" class="author-avatar">
                <div class="author-meta">
                  <span class="author-name">{{ article.nickname }}</span>
                  <div class="post-meta">
                    <time class="publish-time">
                      <i class="far fa-calendar-alt"></i>
                      {{ article.createTime }}
                    </time>
                    <span class="meta-divider">·</span>
                    <span class="category">
                      <i class="fas fa-folder"></i>
                      {{ categoryName }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="article-stats">
                <div class="stat-item">
                  <i class="far fa-eye"></i>
                  <span>{{ article.quantity || 0 }} 阅读</span>
                </div>
                <div class="stat-item">
                  <i class="far fa-clock"></i>
                  <span>{{ readTime }} 分钟</span>
                </div>
                <div class="stat-item">
                  <i class="far fa-comment"></i>
                  <span>{{ article.commentNum || 0 }} 评论</span>
                </div>
              </div>
            </div>
          </header>

          <article ref="articleContentRef" class="article-content">
            <MdPreview
              :model-value="articleContent"
              :theme="articleTheme"
              preview-theme="github"
              code-theme="github"
            />
          </article>

          <footer class="article-footer">
            <div class="copyright-notice">
              <div class="notice-header">
                <i class="fas fa-copyright"></i>
                <span>版权声明</span>
              </div>
              <div class="notice-content">
                <div v-if="Number(article.isOriginal || 0) === 1" class="notice-item">
                  <i class="fas fa-check-circle"></i>
                  <span>本文由 {{ article.nickname }} 原创发布</span>
                </div>
                <div v-else class="notice-item">
                  <i class="fas fa-share-alt"></i>
                  <span>本文转载自：<a :href="article.originalUrl" target="_blank" rel="noopener noreferrer">{{ article.originalUrl || '未知来源' }}</a></span>
                </div>
                <div class="notice-item">
                  <i class="fas fa-calendar-alt"></i>
                  <span>发布时间：{{ article.createTime }}</span>
                </div>
                <div class="notice-item">
                  <i class="fab fa-creative-commons-sa"></i>
                  <span>
                    版权协议：
                    <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank" rel="noopener noreferrer">
                      CC BY-NC-SA 4.0
                    </a>
                  </span>
                </div>
                <div class="notice-item notice-warning">
                  <i class="fas fa-exclamation-triangle"></i>
                  <span>未经许可，禁止转载、摘编、复制或建立镜像。欢迎转发分享！</span>
                </div>
              </div>
            </div>

            <div v-if="article.tags?.length" class="tags-section">
              <i class="fas fa-tags"></i>
              <div class="tags-list">
                <NuxtLink
                  v-for="tag in article.tags"
                  :key="tag.id"
                  :to="`/tags?tagId=${tag.id}&tagName=${encodeURIComponent(tag.name || '')}`"
                  class="tag-item"
                >
                  {{ tag.name }}
                </NuxtLink>
              </div>
            </div>

            <div class="article-actions">
              <button class="action-btn like" :class="{ active: article.isLike }" @click="toggleLike">
                <i class="fas fa-heart"></i>
                <span>{{ article.likeNum || 0 }}</span>
              </button>
              <button class="action-btn favorite" :class="{ active: article.isFavorite }" @click="toggleFavorite">
                <i class="fas fa-bookmark"></i>
                <span>{{ article.favoriteNum || 0 }}</span>
              </button>
              <button class="action-btn share" @click="openShareCard">
                <i class="fas fa-share-alt"></i>
                <span class="share-label">卡片分享</span>
              </button>
            </div>
          </footer>

          <Comment
            :article-id="articleId"
            :comment-count="article.commentNum || 0"
            :article-author-id="article.userId || ''"
            @comment-added="handleCommentAdded"
            @comment-deleted="handleCommentDeleted"
          />
        </main>

        <aside v-if="showSidebar" class="article-sidebar desktop-only">
          <div class="toc-container">
            <div class="toc-header">
              <div class="title-wrapper">
                <i class="fas fa-list"></i>
                <span>目录</span>
              </div>
              <div class="progress-wrapper" :class="{ completed: readProgress === 100 }">
                <i class="fas fa-book-reader"></i>
                <span class="progress-text">{{ readProgress }}</span>
              </div>
            </div>
            <div class="toc-content">
              <div
                v-for="item in tocItems"
                :key="item.id"
                class="toc-item"
                :class="{ active: activeHeading === item.id, [`level-${item.level}`]: true }"
                @click="scrollToHeading(item.id)"
              >
                {{ item.text }}
              </div>
            </div>
          </div>
        </aside>
      </div>

      <ArticleShareCard
        v-model="shareCardVisible"
        :article="article"
        :url="currentUrl"
        :site-name="siteName"
      />
      <ImagePreview ref="imagePreviewRef" />
    </template>

    <section v-else class="article-empty page-card">
      <h1>文章不存在</h1>
      <p>当前文章没有查询到可展示内容。</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.article-page {
  max-width: 1300px;
  margin: 0 auto;
  padding: $spacing-lg;

  @include responsive(lg) {
    padding: $spacing-sm;
  }
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: $spacing-md * 2;
  transition: all 0.3s ease;

  &.center {
    grid-template-columns: 1fr;
    max-width: 1100px;
    margin: 0 auto;
  }

  @include responsive(lg) {
    grid-template-columns: 1fr;
    gap: $spacing-lg;
    padding: 0;
  }
}

.article-main {
  background: var(--card-bg);
  border-radius: $border-radius-lg;
  box-shadow: $shadow-md;
  overflow: hidden;
}

.article-header {
  padding: $spacing-lg $spacing-xl;
  position: relative;
  border-bottom: 1px solid var(--border-color);
  background: var(--card-bg);
}

.article-title {
  color: var(--text-primary);
  font-size: 1.8em;
  line-height: 1.4;
  margin-bottom: $spacing-lg;
  text-align: left;
}

.article-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.author-info {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.author-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba($primary, 0.2);
  padding: 2px;
  background: var(--card-bg);
  transition: all 0.3s ease;

  &:hover {
    transform: rotate(360deg);
    border-color: $primary;
  }
}

.author-meta {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.author-name {
  color: $primary;
  font-weight: 600;
  font-size: 1.1em;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  color: var(--text-secondary);
  font-size: 0.9em;

  i {
    color: $primary;
    margin-right: 4px;
  }
}

.meta-divider {
  color: var(--text-secondary);
  opacity: 0.5;
}

.category {
  color: $primary;
}

.article-stats {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  color: var(--text-secondary);
  font-size: 0.95em;

  i {
    color: $primary;
    font-size: 1.1em;
  }
}

.article-content {
  padding: 0 $spacing-md * 2;
  line-height: 1.8;
  color: var(--text-primary);
  font-size: 1.1em;

  :deep(.md-editor) {
    --md-color: var(--text-secondary);
    --md-hover-color: var(--text-primary);
    --md-bk-color: transparent;
    --md-bk-color-outstand: var(--hover-bg);
    --md-bk-hover-color: var(--hover-bg);
    --md-border-color: var(--border-color);
    --md-border-hover-color: var(--border-color);
    --md-border-active-color: #{$primary};
    background: transparent;
    border: none;
    height: auto;
  }

  :deep(.md-editor-content),
  :deep(.md-editor-preview-wrapper),
  :deep(.md-editor-preview) {
    background: transparent;
  }

  :deep(.md-editor-preview) {
    --md-theme-color: var(--text-secondary);
    --md-theme-color-reverse: var(--card-bg);
    --md-theme-border-color: var(--border-color);
    --md-theme-border-color-reverse: var(--border-color);
    --md-theme-border-color-inset: var(--border-color);
    --md-theme-bg-color: transparent;
    --md-theme-bg-color-inset: var(--hover-bg);
    --md-theme-code-copy-tips-color: var(--text-primary);
    --md-theme-code-copy-tips-bg-color: var(--card-bg);
    color: var(--text-secondary);
  }

  :deep(.md-editor-preview table) {
    background: rgba(var(--surface-rgb), 0.24);
  }

  :deep(.md-editor-preview thead th) {
    background: rgba(var(--border-color-rgb), 0.1);
    color: var(--text-primary);
  }

  :deep(.md-editor-preview th),
  :deep(.md-editor-preview td) {
    border-color: rgba(var(--border-color-rgb), 0.16);
  }

  :deep(.md-editor-preview tbody tr:nth-child(even)) {
    background: rgba(var(--border-color-rgb), 0.05);
  }

  :deep(h2) {
    font-size: 1.8em;
    margin: $spacing-xl 0 $spacing-lg;
    padding-bottom: $spacing-sm;
    border-bottom: 2px solid rgba($primary, 0.1);
    position: relative;
    color: var(--text-primary);

    &::after {
      content: '';
      position: absolute;
      bottom: -2px;
      left: 0;
      width: 50px;
      height: 2px;
      background: $primary;
    }
  }

  :deep(h3) {
    font-size: 1.4em;
    margin: $spacing-lg 0;
    color: var(--text-primary);
    position: relative;
    padding-left: $spacing-lg;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 4px;
      height: 20px;
      background: $primary;
      border-radius: $border-radius-sm;
    }
  }

  :deep(p) {
    margin: $spacing-md 0;
    color: var(--text-secondary);
    line-height: 1.8;
  }

  :deep(a) {
    color: $primary;
    text-decoration: none;
    border-bottom: 1px dashed $primary;
    transition: all 0.3s ease;

    &:hover {
      border-bottom-style: solid;
    }
  }

  :deep(blockquote) {
    margin: $spacing-lg 0;
    padding: $spacing-md $spacing-lg;
    background: var(--hover-bg);
    border-left: 4px solid $primary;
    border-radius: $border-radius-sm;
    color: var(--text-secondary);
    font-style: italic;

    p {
      margin: 0;
    }
  }

  :deep(ul),
  :deep(ol) {
    margin: $spacing-md 0;
    padding-left: $spacing-xl;
    color: var(--text-secondary);

    li {
      margin-bottom: $spacing-sm;
      position: relative;

      &::marker {
        color: $primary;
      }
    }
  }

  :deep(code:not([class])) {
    font-size: 14px;
    line-height: 1.5;
    color: rgb(239, 89, 84);
    background: rgb(243, 244, 244);
    border-radius: 6px;
    padding: $spacing-xs;
    margin: 0 $spacing-xs;
  }

  :deep(pre) {
    margin: 1em 0;
    position: relative;
    background: #282c34;
    border-radius: 6px;
    padding-top: 2.5em;
    overflow: hidden;
    max-height: 2000px;
    transition: max-height 0.4s ease-in-out;

    &.collapsed {
      max-height: 300px;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        height: 60px;
        background: linear-gradient(transparent, #282c34);
        pointer-events: none;
        z-index: 2;
      }

      .expand-button {
        display: flex !important;
      }
    }

    &::before {
      content: '';
      position: absolute;
      top: 12px;
      left: 12px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #ff5f56;
      box-shadow: 20px 0 0 #ffbd2e, 40px 0 0 #27c93f;
    }

    .expand-button {
      position: absolute;
      bottom: 15px;
      left: 50%;
      transform: translateX(-50%);
      padding: 6px 16px;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 4px;
      color: #abb2bf;
      cursor: pointer;
      z-index: 3;
      font-size: 0.9em;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
      white-space: nowrap;

      &:hover {
        background: rgba(255, 255, 255, 0.2);
        color: #fff;
        transform: translateX(-50%) translateY(-2px);
      }

      i {
        font-size: 14px;
      }
    }

    .line-numbers {
      position: absolute;
      left: 0;
      top: 2.5em;
      bottom: 0;
      font-size: 14px;
      padding: 1em 0;
      text-align: right;
      color: #666;
      border-right: 1px solid #404040;
      background: #2d323b;
      user-select: none;
      z-index: 1;

      span {
        display: block;
        padding: 0 0.5em;
        min-width: 2.5em;
        line-height: 1.5;
      }
    }

    code {
      display: block;
      padding: 1em 1em 1em 4em;
      margin-left: 0;
      overflow-x: auto;
      font-family: 'Fira Code', monospace;
      font-size: 14px;
      line-height: 1.5;
      position: relative;
    }

    .code-header {
      position: absolute;
      top: 8px;
      right: 12px;
      z-index: 2;
      opacity: 0;
      transition: opacity 0.2s ease;
    }

    &:hover .code-header {
      opacity: 1;
    }

    .copy-button {
      padding: 4px 8px;
      background: rgba(255, 255, 255, 0.1);
      border: none;
      border-radius: 4px;
      color: #abb2bf;
      cursor: pointer;
      font-size: 14px;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      gap: 4px;

      &:hover {
        background: rgba(255, 255, 255, 0.2);
        color: #fff;
      }

      &.copied {
        background: #98c379;
        color: #fff;
      }
    }
  }

  :deep(.md-editor-preview .md-editor-code) {
    margin: $spacing-lg 0;
  }

  :deep(.md-editor-preview .md-editor-code pre) {
    margin: 0;
    padding-top: 0;
    background: transparent;
    max-height: none;
    overflow: visible;
    transition: none;

    &::before,
    &::after {
      display: none;
      content: none;
    }
  }

  :deep(.md-editor-preview .md-editor-code pre code) {
    padding: 1em;
    margin-left: 0;
    font-family: 'Fira Code', monospace;
  }

  :deep(.md-editor-preview .md-editor-code .code-header),
  :deep(.md-editor-preview .md-editor-code .line-numbers),
  :deep(.md-editor-preview .md-editor-code .expand-button) {
    display: none !important;
  }

  :deep(img.lazy-image) {
    opacity: 0;

    &.loaded {
      opacity: 1;
    }

    &.error {
      opacity: 0.5;
    }
  }

  :deep(img) {
    max-width: 100%;
    border-radius: $border-radius-md;
    margin: $spacing-lg 0;
    transition: all 0.3s ease;
    box-shadow: $shadow-md;
    cursor: zoom-in;

    &:hover {
      transform: translateY(-2px);
      box-shadow: $shadow-lg;
    }
  }

  :deep(table) {
    width: 100%;
    margin: $spacing-lg 0;
    border-collapse: collapse;
    border-radius: $border-radius-md;
    overflow: hidden;

    th,
    td {
      padding: $spacing-sm $spacing-md;
      border: 1px solid var(--border-color);
    }

    th {
      background: var(--hover-bg);
      color: var(--text-primary);
      font-weight: 500;
      text-align: left;
    }

    tr:nth-child(even) {
      background: var(--hover-bg);
    }
  }

  :deep(hr) {
    margin: $spacing-xl 0;
    border: none;
    height: 1px;
    background: var(--border-color);
    position: relative;

    &::before {
      content: '§';
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translate(-50%, -50%);
      background: var(--card-bg);
      padding: 0 $spacing-lg;
      color: var(--text-secondary);
      font-size: 1.2em;
    }
  }
}

.article-footer {
  padding: $spacing-md * 2;
  border-top: 1px solid var(--border-color);

  @include responsive(lg) {
    padding: $spacing-sm;
  }
}

.copyright-notice {
  margin-bottom: $spacing-xl;
  background: var(--hover-bg);
  border-radius: $border-radius-lg;
  overflow: hidden;
}

.notice-header {
  padding: $spacing-md $spacing-lg;
  background: rgba($primary, 0.1);
  color: $primary;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.notice-content {
  padding: $spacing-lg;
  color: var(--text-secondary);
  font-size: 0.95em;
  line-height: 1.6;
}

.notice-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-xs 0;

  i {
    color: $primary;
    font-size: 1em;
    width: 16px;
    text-align: center;
  }

  a {
    color: $primary;
    text-decoration: none;
    border-bottom: 1px dashed $primary;
    transition: all 0.2s ease;

    &:hover {
      border-bottom-style: solid;
    }
  }
}

.notice-warning {
  margin-top: $spacing-sm;
  padding: $spacing-sm;
  background: rgba($primary, 0.05);
  border-radius: $border-radius-sm;

  i {
    color: #ff9800;
  }
}

.tags-section {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-xl;

  i {
    color: $primary;
  }
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.tag-item {
  padding: $spacing-xs $spacing-md;
  background: var(--hover-bg);
  color: var(--text-secondary);
  border-radius: $border-radius-lg;
  font-size: 0.9em;
  text-decoration: none;
  transition: all 0.3s ease;

  &:hover {
    background: $primary;
    color: white;
    transform: translateY(-2px);
  }
}

.article-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: $spacing-lg;
  margin-bottom: $spacing-xl;
  flex-wrap: nowrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-xl;
  border: none;
  border-radius: 20px;
  font-size: 1em;
  transition: all 0.3s ease;
  cursor: pointer;

  &.like {
    background: var(--hover-bg);
    color: var(--text-secondary);

    &.active {
      background: $primary;
      color: white;
    }

    &:hover {
      transform: scale(1.05);
    }
  }

  &.share {
    background: $primary;
    color: white;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba($primary, 0.2);
    }
  }

  &.favorite {
    background: rgba($primary, 0.08);
    color: var(--text-secondary);

    &.active {
      background: rgba($primary, 0.16);
      color: $primary;
    }

    &:hover {
      transform: translateY(-2px);
      background: rgba($primary, 0.14);
      color: $primary;
    }
  }
}

.desktop-only {
  @include responsive(lg) {
    display: none;
  }
}

.floating-action-bar {
  position: fixed;
  top: 40%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: $spacing-md * 1.5;
  padding: $spacing-sm;
  border-radius: $border-radius-lg;
  z-index: 100;
  transition: left 0.3s ease;

  @include responsive(lg) {
    display: none;
  }
}

.action-item {
  cursor: pointer;

  .action-button {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--card-bg);
    transition: all 0.3s ease;

    i {
      font-size: 1.2em;
      color: var(--text-secondary);
      transition: all 0.3s ease;

      &.active {
        color: $primary;
      }
    }
  }

  &:hover {
    .action-button {
      background: rgba($primary, 0.1);
      transform: translateY(-2px);

      i {
        color: $primary;
        transform: scale(1.1);
      }
    }
  }
}

.article-sidebar {
  .toc-container {
    position: sticky;
    top: 90px;
    background: var(--card-bg);
    border-radius: $border-radius-lg;
    box-shadow: $shadow-md;
    overflow: hidden;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    border: 1px solid var(--border-color);
    backdrop-filter: blur(10px);

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(45deg, transparent, rgba($primary, 0.03), transparent);
      opacity: 0;
      transition: opacity 0.3s ease;
    }

    .toc-header {
      padding: $spacing-lg;
      background: var(--hover-bg);
      color: var(--text-primary);
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      position: relative;
      border-bottom: 1px solid var(--border-color);
      justify-content: space-between;
    }

    .title-wrapper {
      display: flex;
      align-items: center;
      gap: $spacing-sm;

      i {
        color: $primary;
        font-size: 1.1em;
        transform-origin: center;
      }
    }

    .progress-wrapper {
      font-size: 0.9em;
      color: var(--text-secondary);
      display: flex;
      align-items: center;
      gap: $spacing-xs;
      padding: 4px 8px;
      background: rgba($primary, 0.05);
      border-radius: $border-radius-lg;
      transition: all 0.3s ease;

      i {
        color: $primary;
        font-size: 0.9em;
      }

      .progress-text {
        font-variant-numeric: tabular-nums;
        min-width: 3em;
        text-align: right;

        &::after {
          content: '%';
          margin-left: 2px;
          opacity: 0.7;
        }
      }

      &.completed {
        background: rgba(34, 197, 94, 0.12);
        color: #16a34a;
      }
    }

    .toc-content {
      padding: $spacing-lg;
      max-height: calc(100vh - 200px);
      overflow-y: auto;
      position: relative;

      &::before {
        content: '';
        position: absolute;
        left: 24px;
        top: 0;
        bottom: 0;
        width: 1px;
        background: linear-gradient(
          to bottom,
          transparent,
          rgba($primary, 0.1),
          rgba($primary, 0.1),
          transparent
        );
      }
    }

    .toc-item {
      --toc-indent: 0px;
      padding: $spacing-sm $spacing-md $spacing-sm calc(16px + var(--toc-indent));
      margin: 2px 0;
      cursor: pointer;
      border-radius: $border-radius-sm;
      color: var(--text-secondary);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      font-size: 0.95em;
      line-height: 1.4;
      position: relative;
      display: flex;
      align-items: center;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      &::before {
        content: '';
        position: absolute;
        left: var(--toc-indent);
        top: 50%;
        transform: translateY(-50%);
        width: 0;
        height: 0;
        background: $primary;
        border-radius: 50%;
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        opacity: 0;
        box-shadow: 0 0 4px rgba($primary, 0.4);
      }

      &:hover {
        color: $primary;
        background: linear-gradient(90deg, rgba($primary, 0.05), rgba($primary, 0.02));
        padding-left: calc(20px + var(--toc-indent));

        &::before {
          width: 6px;
          height: 6px;
          opacity: 1;
        }
      }

      &.active {
        color: $primary;
        background: linear-gradient(90deg, rgba($primary, 0.1), rgba($primary, 0.05));
        font-weight: 500;
        padding-left: calc(20px + var(--toc-indent));

        &::before {
          width: 6px;
          height: 6px;
          opacity: 1;
          animation: tocDotPulse 1.5s infinite;
        }
      }

      &.level-1 {
        --toc-indent: 0px;
        font-weight: 500;
        font-size: 1em;
      }

      &.level-2 {
        --toc-indent: 14px;
        font-size: 0.95em;
      }

      &.level-3 {
        --toc-indent: 28px;
        font-size: 0.9em;
      }

      &.level-4 {
        --toc-indent: 42px;
        font-size: 0.88em;
      }

      &.level-5,
      &.level-6 {
        --toc-indent: 56px;
        font-size: 0.86em;
        opacity: 0.8;

        &:hover {
          opacity: 1;
        }
      }
    }
  }
}

.article-empty {
  padding: 40px;
  text-align: center;
}

@keyframes tocDotPulse {
  0% {
    box-shadow: 0 0 0 0 rgba($primary, 0.4);
  }

  70% {
    box-shadow: 0 0 0 4px rgba($primary, 0);
  }

  100% {
    box-shadow: 0 0 0 0 rgba($primary, 0);
  }
}

@include responsive(lg) {
  .article-content,
  .article-footer,
  .article-header {
    padding-left: $spacing-lg;
    padding-right: $spacing-lg;
  }

  .article-actions {
    flex-wrap: wrap;
  }
}

@include responsive(md) {
  .article-info {
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-start;
  }

  .author-info {
    width: 100%;
    align-items: center;
  }

  .post-meta,
  .article-stats,
  .tags-section,
  .tags-list {
    flex-direction: row;
    align-items: center;
    flex-wrap: wrap;
  }

  .article-stats {
    width: 100%;
    gap: $spacing-md;
  }

  .stat-item {
    font-size: 0.92em;
  }

  .tags-section {
    gap: $spacing-sm;
  }

  .article-actions {
    justify-content: center;
    gap: 12px;
    flex-wrap: nowrap;
  }

  .action-btn {
    padding: $spacing-sm $spacing-lg;
  }

  .action-btn.like,
  .action-btn.favorite {
    flex: 0 0 auto;
    justify-content: center;
    min-width: 92px;
  }

  .action-btn.share {
    width: 92px;
    min-width: 92px;
    justify-content: center;
    flex-shrink: 0;
  }

  .share-label {
    display: none;
  }

  .article-title {
    font-size: 1.45em;
  }
}
</style>
