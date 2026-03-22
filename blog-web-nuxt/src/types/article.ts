export interface WebsiteInfo {
  name?: string
  title?: string
  websiteName?: string
  author?: string
  authorAvatar?: string
  summary?: string
  description?: string
  logo?: string
  webUrl?: string
  touristAvatar?: string
  articleCount?: number
  likeCount?: number
  github?: string
  qqNumber?: string
  qqGroup?: string
  email?: string
  wechat?: string
  showList?: string[]
  profileAvatar?: string
  profileName?: string
  profileSignature?: string
  authorInfo?: string
  recordNum?: string
  openLantern?: boolean
  [key: string]: unknown
}

export interface ArticleSummary {
  id: number | string
  title: string
  summary?: string
  introduction?: string
  cover?: string
  createTime?: string
  categoryName?: string
  avatar?: string
  nickname?: string
  quantity?: number
  contentMd?: string
  isStick?: boolean
  [key: string]: unknown
}

export interface ArticleCategoryInfo {
  id?: number | string
  name?: string
  [key: string]: unknown
}

export interface ArticleTagInfo {
  id?: number | string
  name?: string
  [key: string]: unknown
}

export interface ArticleDetail extends ArticleSummary {
  userId?: number | string
  nickname?: string
  avatar?: string
  keywords?: string
  content?: string
  contentMd?: string
  cover?: string
  updateTime?: string
  quantity?: number
  commentNum?: number
  likeNum?: number
  favoriteNum?: number
  isOriginal?: number | boolean
  originalUrl?: string
  isLike?: boolean
  isFavorite?: boolean
  category?: ArticleCategoryInfo
  tags?: ArticleTagInfo[]
}

export interface ArticleComment {
  id?: number | string
  articleId?: number | string
  articleTitle?: string
  parentId?: number | string
  userId?: number | string
  nickname?: string
  avatar?: string
  replyNickname?: string
  content?: string
  isStick?: number
  ip?: string
  ipSource?: string
  browser?: string
  createTime?: string
  likeCount?: number
  isLiked?: boolean
  children?: ArticleComment[]
  [key: string]: unknown
}

export interface ArticleCommentPayload {
  articleId: number | string
  content: string
  browser?: string
  parentId?: number | string
  replyUserId?: number | string
}

export interface ArchiveGroup {
  year?: string
  posts?: ArticleSummary[]
  date?: string
  articleList?: ArticleSummary[]
  [key: string]: unknown
}

export interface ArticleCategoryGroup {
  id?: number | string
  name?: string
  categoryName?: string
  posts?: ArticleSummary[]
  articleList?: ArticleSummary[]
  articles?: ArticleSummary[]
  [key: string]: unknown
}

export interface TagSummary {
  id?: number | string
  name?: string
  articleNum?: number
  count?: number
  articleCount?: number
  [key: string]: unknown
}

export interface MomentSummary {
  id?: number | string
  content?: string
  createTime?: string
  avatar?: string
  nickname?: string
  images?: string[] | string
  [key: string]: unknown
}

export interface AlbumSummary {
  id?: number | string
  name?: string
  description?: string
  cover?: string
  photoNum?: number
  isLock?: number
  createTime?: string
  [key: string]: unknown
}

export interface AlbumPhoto {
  url?: string
  description?: string
  recordTime?: string
  location?: string
  [key: string]: unknown
}

export interface MessageItem {
  id?: number | string
  avatar?: string
  status?: number
  nickname?: string
  content?: string
  createTime?: string
  [key: string]: unknown
}

export interface NotificationItem {
  id?: number | string
  type?: string
  title?: string
  message?: string
  isRead?: boolean
  createTime?: string
  fromUserId?: number | string
  fromNickname?: string
  articleId?: number | string
  articleTitle?: string
  [key: string]: unknown
}

export interface FriendItem {
  id?: number | string
  name?: string
  url?: string
  info?: string
  avatar?: string
  online?: boolean
  [key: string]: unknown
}

export interface FriendApplyPayload {
  name: string
  url: string
  info: string
  avatar: string
  email: string
}
