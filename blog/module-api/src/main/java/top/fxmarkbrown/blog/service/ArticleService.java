package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.vo.article.ArchiveListVo;
import top.fxmarkbrown.blog.vo.article.ArticleDetailVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.CategoryListVo;

import java.util.List;

public interface ArticleService {
    /**
     * 获取文章列表
     */
    PageResponse<ArticleListVo> getArticleList(PageQuery pageQuery, Integer tagId, Integer categoryId, String keyword);

    /**
     * 获取文章详情
     *
     * @param id 文章id
     */
    ArticleDetailVo getArticleDetail(Long id);


    /**
     * 获取文章归档
     */
    List<ArchiveListVo> getArticleArchive();

    /**
     * 获取分类
     */
    List<CategoryListVo> getArticleCategories();

    /**
     * 获取轮播文章
     */
    List<ArticleListVo> getCarouselArticle();

    /**
     * 获取推荐文章
     */
    List<ArticleListVo> getRecommendArticle();

    /**
     * 点赞文章
     */
    Boolean like(Long id);

    /**
     * 取消点赞文章
     */
    Boolean unlike(Long id);

    /**
     * 收藏文章
     */
    Boolean favorite(Long id);

    /**
     * 获取所有分类
     */
    List<SysCategory> getCategoryAll();
}
