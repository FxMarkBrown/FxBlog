import type {ApiResponse, PageResult} from '@/types/common'
import type {
    ArchiveGroup,
    ArticleCategoryGroup,
    ArticleComment,
    ArticleCommentPayload,
    ArticleDetail,
    ArticleSummary
} from '@/types/article'

// 获取文章详情
export function getArticleDetailApi(id: number | string) {
  return useApiClient()<ApiResponse<ArticleDetail>>(`/api/article/detail/${id}`)
}

// 获取文章列表
export function getArticlesApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<ArticleSummary>>>('/api/article/list', { query })
}

// 获取归档列表
export function getArchivesApi() {
  return useApiClient()<ApiResponse<ArchiveGroup[]>>('/api/article/archive')
}

// 获取分类列表
export function getCategoriesApi() {
  return useApiClient()<ApiResponse<ArticleCategoryGroup[]>>('/api/article/categories')
}

// 获取轮播文章
export function getCarouselArticlesApi() {
  return useApiClient()<ApiResponse<ArticleSummary[]>>('/api/article/getCarousels')
}

// 获取文章评论列表
export function getCommentsApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<ArticleComment>>>('/api/comment/list', { query })
}

// 添加文章评论
export function addCommentApi(data: ArticleCommentPayload) {
  return useApiClient()<ApiResponse<unknown>>('/api/comment/add', {
    method: 'POST',
    body: data
  })
}

// 获取推荐文章
export function getRecommendArticlesApi() {
  return useApiClient()<ApiResponse<ArticleSummary[]>>('/api/article/getRecommends')
}

// 获取全部分类
export function getAllCategoriesApi() {
  return useApiClient()<ApiResponse<ArticleCategoryGroup[]>>('/api/article/categorie-all')
}

// 获取我的文章列表。
export function getMyArticleApi(query: Record<string, unknown>) {
  return useApiClient()<ApiResponse<PageResult<ArticleSummary>>>('/portal/user/myArticle', {
    query
  })
}

// 取消点赞文章。
export function unlikeArticleApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/article/unlike/${id}`)
}

// 点赞文章。
export function likeArticleApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/article/like/${id}`)
}

// 切换文章收藏状态。
export function favoriteArticleApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/api/article/favorite/${id}`)
}

// 删除文章。
export function delArticleApi(id: number | string) {
  return useApiClient()<ApiResponse<unknown>>(`/sys/article/delete/${id}`, {
    method: 'DELETE'
  })
}

// 创建文章。
export function createArticleApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<unknown>>('/sys/article/add', {
    method: 'POST',
    body: data
  })
}

// 获取文章编辑详情。
export function getArticleInfoApi(id: number | string) {
  return useApiClient()<ApiResponse<ArticleDetail>>(`/sys/article/detail/${id}`)
}

// 更新文章。
export function updateArticleApi(data: Record<string, unknown>) {
  return useApiClient()<ApiResponse<unknown>>('/sys/article/update', {
    method: 'PUT',
    body: data
  })
}
