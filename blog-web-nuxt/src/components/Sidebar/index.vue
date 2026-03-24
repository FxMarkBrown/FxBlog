<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { getRecommendArticlesApi } from '@/api/article'
import TagCloud from '@/components/Sidebar/components/tagCloud.vue'
import { IMAGE_ERROR_PLACEHOLDER } from '@/utils/placeholders'
import type { ArticleSummary } from '@/types/article'
import { unwrapResponseData } from '@/utils/response'

const router = useRouter()
const siteStore = useSiteStore()
const hot = ref<ArticleSummary[]>([])
const avatarRef = ref<HTMLElement | null>(null)
let avatarAnimationFrame = 0
let avatarAnimationStart = 0

const socialLinks = [
  { icon: 'fab fa-github', type: 'github', content: '点击跳转GitHub主页', icCopy: false },
  { icon: 'fab fa-qq', title: 'QQ', type: 'qq', content: '点击复制QQ号', icCopy: true },
  { icon: 'fas fa-users', title: 'QQ群', type: 'qqGroup', content: '点击复制QQ群号', icCopy: true },
  { icon: 'fas fa-at', title: '邮箱', type: 'email', content: '点击复制邮箱', icCopy: true },
  { icon: 'fab fa-weixin', title: '微信', type: 'wechat', content: '点击复制微信号', icCopy: true }
]

const announcements = computed(() => getValidAnnouncements((siteStore.notice?.right as Array<Record<string, unknown>> | undefined) || []))
const profileAvatar = computed(() => siteStore.websiteInfo.profileAvatar || siteStore.websiteInfo.logo)
const profileName = computed(() => siteStore.websiteInfo.profileName || siteStore.websiteInfo.author)
const profileSignature = computed(() => siteStore.websiteInfo.profileSignature || siteStore.websiteInfo.authorInfo)
const visibleSocialLinks = computed(() => {
  const showList = siteStore.websiteInfo.showList || []
  const linkMap: Record<string, string | undefined> = {
    github: siteStore.websiteInfo.github,
    qq: siteStore.websiteInfo.qqNumber,
    qqGroup: siteStore.websiteInfo.qqGroup,
    email: siteStore.websiteInfo.email,
    wechat: siteStore.websiteInfo.wechat
  }

  return socialLinks
    .map((item) => ({ ...item, link: linkMap[item.type] }))
    .filter((item) => showList.includes(item.type))
})

onMounted(async () => {
  const response = await getRecommendArticlesApi().catch(() => null)
  hot.value = unwrapResponseData<ArticleSummary[] | null>(response) || []
})

/**
 * 获取有效公告
 * @param list 公告列表
 * @returns 有效公告
 */
function getValidAnnouncements(list: Array<Record<string, unknown>>) {
  return (list || []).filter((item) => {
    const content = String(item?.content || '')
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/gi, ' ')
      .trim()
    return content.length > 0
  })
}

/**
 * 格式化数量
 * @param value 数值
 * @returns 格式化结果
 */
function formatCount(value: unknown) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

/**
 * 处理图片异常
 * @param event 图片事件
 */
function handleImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.src = IMAGE_ERROR_PLACEHOLDER
  target.classList.add('fallback')
}

/**
 * 复制联系方式
 * @param item 联系方式
 */
async function copyToClipboard(item: { title?: string; link?: string; icCopy?: boolean }) {
  if (!item.link) {
    ElMessage.warning('当前未配置该联系方式')
    return
  }

  if (item.icCopy && import.meta.client) {
    try {
      await navigator.clipboard.writeText(item.link)
      ElMessage.success(`${item.title || '联系方式'}已复制到剪贴板`)
    } catch {
      ElMessage.error('复制失败，请手动复制')
    }
    return
  }

  if (import.meta.client) {
    window.open(item.link, '_blank')
  }
}

/**
 * 缓出曲线
 * @param value 进度值
 * @returns 曲线结果
 */
function easeOutCubic(value: number) {
  return 1 - (1 - value) ** 3
}

/**
 * 启动头像动效
 */
function startAvatarMotion() {
  if (!avatarRef.value || !import.meta.client) {
    return
  }

  if (avatarAnimationFrame) {
    cancelAnimationFrame(avatarAnimationFrame)
    avatarAnimationFrame = 0
  }

  avatarAnimationStart = performance.now()
  avatarRef.value.style.willChange = 'transform'
  const duration = 450

  const animate = (now: number) => {
    if (!avatarRef.value) {
      return
    }

    const progress = Math.min((now - avatarAnimationStart) / duration, 1)
    const rotation = easeOutCubic(progress) * 360
    const lift = Math.sin(progress * Math.PI) * 10 * (1 - progress * 0.18)
    const scale = 1 + Math.sin(progress * Math.PI) * 0.035
    avatarRef.value.style.transform = `translate3d(0, ${-lift}px, 0) rotate(${rotation}deg) scale(${scale})`

    if (progress < 1) {
      avatarAnimationFrame = requestAnimationFrame(animate)
      return
    }

    avatarRef.value.style.transform = ''
    avatarRef.value.style.willChange = ''
    avatarAnimationFrame = 0
  }

  avatarAnimationFrame = requestAnimationFrame(animate)
}

/**
 * 跳转留言页
 */
function goToMessages() {
  router.push('/messages')
}

onBeforeUnmount(() => {
  if (avatarAnimationFrame) {
    cancelAnimationFrame(avatarAnimationFrame)
    avatarAnimationFrame = 0
  }

  if (avatarRef.value) {
    avatarRef.value.style.transform = ''
    avatarRef.value.style.willChange = ''
  }
})
</script>

<template>
  <aside class="sidebar">
    <ElCard class="author-card">
      <div class="author-avatar-wrap">
        <div ref="avatarRef" class="avatar-hitbox" @mouseenter="startAvatarMotion" @click="goToMessages">
          <ElAvatar class="avatar" :src="profileAvatar" alt="管理员头像" />
        </div>
      </div>
      <div class="author-info">
        <h3>{{ profileName }}</h3>
        <p v-if="profileSignature" class="bio">{{ profileSignature }}</p>
      </div>
      <div class="site-stats">
        <div class="stat-item">
          <div class="stat-label">
            <i class="fas fa-book-open"></i>
            <span>文章</span>
          </div>
          <strong>{{ formatCount(siteStore.websiteInfo.articleCount) }}</strong>
        </div>
        <div class="stat-item">
          <div class="stat-label">
            <i class="fas fa-heart"></i>
            <span>点赞</span>
          </div>
          <strong>{{ formatCount(siteStore.websiteInfo.likeCount) }}</strong>
        </div>
        <div class="stat-item">
          <div class="stat-label">
            <i class="fas fa-fire"></i>
            <span>访问</span>
          </div>
          <strong>{{ formatCount(siteStore.siteAccess) }}</strong>
        </div>
      </div>
      <div v-if="visibleSocialLinks.length" class="social-links">
        <ElTooltip v-for="item in visibleSocialLinks" :key="item.type" placement="top" :content="item.content" :teleported="false">
          <a href="javascript:void(0)" :title="item.title" :class="`social-btn ${item.type}`" @click="copyToClipboard(item)">
            <i :class="item.icon"></i>
          </a>
        </ElTooltip>
      </div>
    </ElCard>

    <ElCard v-if="announcements.length" class="section announcement">
      <h3>
        <i class="fas fa-bullhorn"></i>
        公告
      </h3>
      <div class="announcement-content">
        <div v-for="(item, index) in announcements" :key="index" class="announcement-item">
          <span v-html="String(item.content || '')"></span>
        </div>
      </div>
    </ElCard>

    <ElCard v-if="hot.length > 0" class="section">
      <h3>
        <i class="fas fa-star"></i>
        推荐文章
      </h3>
      <div class="post-list">
        <NuxtLink v-for="post in hot" :key="post.id" :to="`/post/${post.id}`" class="post-item">
          <img :src="post.cover || IMAGE_ERROR_PLACEHOLDER" :alt="post.title" @error="handleImageError">
          <div class="post-meta">
            <h4>{{ post.title }}</h4>
            <time>{{ post.createTime }}</time>
          </div>
        </NuxtLink>
      </div>
    </ElCard>

    <ElCard class="section">
      <h3>
        <i class="fas fa-tags"></i>
        标签云
      </h3>
      <TagCloud />
    </ElCard>
  </aside>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.sidebar {
  position: sticky;
  top: 80px;
  width: 100%;
  max-width: 320px;
  --sidebar-author-bg: linear-gradient(180deg, rgba(211, 236, 253, 0.92) 0%, rgba(235, 247, 255, 0.96) 100%);
  --sidebar-author-border: rgba(103, 166, 220, 0.22);
  --sidebar-author-shadow: 0 18px 36px rgba(85, 131, 173, 0.14);
  --sidebar-avatar-border: rgba(255, 255, 255, 0.96);
  --sidebar-avatar-bg: #fff;
  --sidebar-avatar-shadow: 0 8px 20px rgba(78, 128, 172, 0.18);
  --sidebar-avatar-shadow-hover: 0 14px 28px rgba(78, 128, 172, 0.24);
  --sidebar-title-color: var(--text-primary);
  --sidebar-bio-color: var(--text-secondary);
  --sidebar-stat-label: var(--text-secondary);
  --sidebar-stat-value: var(--text-primary);
  --sidebar-social-bg: rgba(255, 255, 255, 0.72);
  --sidebar-social-shadow: 0 8px 20px rgba(98, 144, 181, 0.12);
  --sidebar-section-accent: #6366f1;
  --sidebar-section-accent-strong: #8b5cf6;
  --sidebar-section-divider: rgba(99, 102, 241, 0.1);
  --sidebar-post-hover: #6366f1;
  --sidebar-post-time: var(--text-secondary);
  --sidebar-rank-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  --sidebar-image-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);

  .author-card {
    margin-bottom: $spacing-lg;
    padding: 22px 0 0;
    background: var(--sidebar-author-bg);
    border: 1px solid var(--sidebar-author-border);
    box-shadow: var(--sidebar-author-shadow);
  }

  .author-avatar-wrap {
    display: flex;
    justify-content: center;
    position: relative;
    z-index: 1;

    .avatar-hitbox {
      display: inline-flex;
      border-radius: 50%;
      cursor: pointer;
    }

    .avatar {
      width: 88px;
      height: 88px;
      border: 4px solid var(--sidebar-avatar-border);
      box-shadow: var(--sidebar-avatar-shadow);
      background: var(--sidebar-avatar-bg);
      transition: box-shadow 0.25s ease;
    }

    .avatar-hitbox:hover .avatar {
      box-shadow: var(--sidebar-avatar-shadow-hover);
    }
  }

  .author-info {
    padding: 16px 22px 8px;
    text-align: center;

    h3 {
      margin: 0;
      font-size: 1.6rem;
      font-weight: 700;
      color: var(--sidebar-title-color);
      letter-spacing: 0.5px;
      line-height: 1.2;
    }

    .bio {
      margin: 10px 0 0;
      color: var(--sidebar-bio-color);
      font-size: 0.95rem;
      line-height: 1.7;
    }
  }

  .site-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    padding: 12px 18px 4px;
    align-items: stretch;

    .stat-item {
      min-width: 0;
      min-height: 74px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      gap: 8px;
      text-align: center;
      padding: 6px 2px;
    }

    .stat-label {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 5px;
      min-width: 0;
      color: var(--sidebar-stat-label);
      font-size: 0.9rem;
      font-weight: 600;
      line-height: 1.2;
      white-space: nowrap;

      span {
        white-space: nowrap;
      }
    }

    strong {
      display: block;
      width: 100%;
      color: var(--sidebar-stat-value);
      font-size: 1.35rem;
      line-height: 1.15;
      font-variant-numeric: tabular-nums;
      letter-spacing: 0.02em;
      word-break: break-word;
    }

    .fa-book-open {
      color: #4f8fdd;
    }

    .fa-heart {
      color: #ff6b81;
    }

    .fa-fire {
      color: #ff6b57;
    }
  }

  .social-links {
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 0;
    padding: 8px 18px 16px;

    .social-btn {
      width: 38px;
      height: 38px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 12px;
      background: var(--sidebar-social-bg);
      box-shadow: var(--sidebar-social-shadow);
      font-size: 1.05rem;
      transition: all 0.25s ease;
      text-decoration: none;

      &:hover {
        transform: translateY(-2px);
      }
    }

    .qq {
      color: #60a5fa;

      &:hover {
        background: #60a5fa;
        color: #fff;
      }
    }

    .qqGroup {
      color: #e1c235;

      &:hover {
        background: #e1c235;
        color: #fff;
      }
    }

    .github {
      color: #000;

      &:hover {
        background: #000;
        color: #fff;
      }
    }

    .gitee {
      color: #ee3434;

      &:hover {
        background: #ee3434;
        color: #fff;
      }
    }

    .email {
      color: #d872a7;

      &:hover {
        background: #d872a7;
        color: #fff;
      }
    }

    .wechat {
      color: #10b981;

      &:hover {
        background: #10b981;
        color: #fff;
      }
    }
  }
}

.section {
  margin-bottom: $spacing-lg;

  h3 {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--sidebar-section-accent);
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 2px solid var(--sidebar-section-divider);

    &::before {
      content: '';
      display: inline-block;
      width: 4px;
      height: 16px;
      background: linear-gradient(to bottom, var(--sidebar-section-accent), var(--sidebar-section-accent-strong));
      margin-right: 8px;
      border-radius: 2px;
      vertical-align: middle;
      transform: translateY(-1px);
    }
  }

  .post-list {
    display: flex;
    flex-direction: column;
    gap: 20px;
    counter-reset: post-counter;

    .post-item {
      display: flex;
      gap: 16px;
      text-decoration: none;
      transition: all 0.3s ease;
      position: relative;
      padding-left: 32px;

      &::before {
        content: counter(post-counter);
        counter-increment: post-counter;
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 22px;
        height: 22px;
        background: var(--number-bg, #f87171);
        color: white;
        border-radius: 6px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 600;
        box-shadow: var(--sidebar-rank-shadow);
      }

      &:nth-child(2)::before {
        --number-bg: #fbbf24;
      }

      &:nth-child(3)::before {
        --number-bg: #60a5fa;
      }

      &:nth-child(n+4)::before {
        --number-bg: #9ca3af;
      }

      &:hover {
        transform: translateX(4px);

        h4 {
          color: var(--sidebar-post-hover);
        }

        img {
          transform: scale(1.03);
        }
      }

      img {
        width: 100px;
        height: 70px;
        border-radius: 6px;
        object-fit: cover;
        transition: transform 0.3s ease;
        box-shadow: var(--sidebar-image-shadow);

        &.fallback {
          opacity: 0.7;
        }
      }

      .post-meta {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        justify-content: space-between;

        h4 {
          font-size: 0.95rem;
          font-weight: 500;
          color: var(--text-primary);
          margin-bottom: 6px;
          overflow: hidden;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          line-height: 1.4;
          line-clamp: 2;
          transition: color 0.3s ease;
        }

        time {
          font-size: 0.8rem;
          color: var(--sidebar-post-time);
          display: flex;
          align-items: center;
          gap: 4px;

          &::before {
            content: '\f017';
            font-family: 'Font Awesome 5 Free',serif;
            font-size: 0.75rem;
            opacity: 0.8;
          }
        }
      }
    }
  }
}

.announcement {
  h3 {
    i {
      margin-right: 8px;
      color: #f59e0b;
      animation: shake 1.5s ease-in-out infinite;
    }
  }

  .announcement-content {
    .announcement-item {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 0;
      border-bottom: 1px dashed var(--sidebar-section-divider);

      &:last-child {
        border-bottom: none;
        padding-bottom: 0;
      }

      &:first-child {
        padding-top: 0;
      }

      span {
        font-size: 0.9rem;
        color: var(--text-secondary);
        line-height: 1.6;
      }
    }
  }
}

:global(html[data-theme='dark']) .sidebar {
  --sidebar-author-bg: linear-gradient(180deg, rgba(31, 43, 56, 0.96) 0%, rgba(20, 28, 38, 0.98) 100%);
  --sidebar-author-border: rgba(113, 156, 194, 0.16);
  --sidebar-author-shadow: 0 20px 36px rgba(0, 0, 0, 0.2);
  --sidebar-avatar-border: rgba(255, 255, 255, 0.1);
  --sidebar-avatar-bg: rgba(255, 255, 255, 0.04);
  --sidebar-avatar-shadow: 0 12px 24px rgba(0, 0, 0, 0.24);
  --sidebar-avatar-shadow-hover: 0 18px 30px rgba(0, 0, 0, 0.3);
  --sidebar-title-color: var(--text-primary);
  --sidebar-bio-color: var(--text-secondary);
  --sidebar-stat-label: #bdd0e1;
  --sidebar-stat-value: #f4f8fc;
  --sidebar-social-bg: rgba(255, 255, 255, 0.06);
  --sidebar-social-shadow: none;
  --sidebar-post-hover: #a5b4fc;
  --sidebar-post-time: #a3b2c5;
  --sidebar-rank-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  --sidebar-image-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

@include responsive(lg) {
  .sidebar {
    display: none;
  }
}

@keyframes shake {
  0% { transform: rotate(0deg); }
  25% { transform: rotate(-10deg); }
  75% { transform: rotate(10deg); }
  100% { transform: rotate(0deg); }
}

.fa-star {
  color: #ef5151;
}

.fa-tags {
  color: #e329d3;
}
</style>
