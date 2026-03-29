package top.fxmarkbrown.blog.controller.article;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.annotation.AccessLimit;
import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.service.ArticleService;
import top.fxmarkbrown.blog.vo.article.ArchiveListVo;
import top.fxmarkbrown.blog.vo.article.ArticleDetailVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.CategoryListVo;

import java.util.List;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
@Tag(name = "门户-文章管理")
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/list")
    @Operation(summary = "获取文章列表")
    public Result<PageResponse<ArticleListVo>> getArticleList(PageQuery pageQuery, String tagId, String categoryId, String keyword) {
        return Result.success(articleService.getArticleList(pageQuery, parseInteger(tagId), parseInteger(categoryId), keyword));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "获取文章详情")
    public Result<ArticleDetailVo> getArticleDetail(@PathVariable Long id) {
        return Result.success(articleService.getArticleDetail(id));
    }

    @GetMapping("/archive")
    @Operation(summary = "获取归档")
    public Result<List<ArchiveListVo>> getArticleArchive() {
        return Result.success(articleService.getArticleArchive());
    }

    @GetMapping("/categories")
    @Operation(summary = "获取分类")
    public Result<List<CategoryListVo>> getArticleCategories() {
        return Result.success(articleService.getArticleCategories());
    }

    @GetMapping("/categorie-all")
    @Operation(summary = "获取所有分类")
    public Result<List<SysCategory>> getCategoryAll() {
        return Result.success(articleService.getCategoryAll());
    }


    @GetMapping("/getCarousels")
    @Operation(summary = "获取轮播文章")
    public Result<List<ArticleListVo>> getCarouselArticle() {
        return Result.success(articleService.getCarouselArticle());
    }

    @GetMapping("/getRecommends")
    @Operation(summary = "获取推荐文章")
    public Result<List<ArticleListVo>> getRecommendArticle() {
        return Result.success(articleService.getRecommendArticle());
    }

    @SaCheckLogin
    @GetMapping("/like/{id}")
    @AccessLimit(time = 5, count = 1)
    @Operation(summary = "点赞文章")
    public Result<Boolean> like(@PathVariable Long id) {
        return Result.success(articleService.like(id));
    }

    @SaCheckLogin
    @GetMapping("/unlike/{id}")
    @AccessLimit(time = 5, count = 1)
    @Operation(summary = "取消点赞文章")
    public Result<Boolean> unlike(@PathVariable Long id) {
        return Result.success(articleService.unlike(id));
    }

    @SaCheckLogin
    @GetMapping("/favorite/{id}")
    @AccessLimit(time = 5, count = 1)
    @Operation(summary = "收藏文章")
    public Result<Boolean> favorite(@PathVariable Long id) {
        return Result.success(articleService.favorite(id));
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
