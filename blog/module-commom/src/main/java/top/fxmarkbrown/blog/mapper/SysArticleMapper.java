package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.dto.article.ArticleQueryDto;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.vo.article.ArticleDetailVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.dashboard.ContributionData;

import java.util.List;
import java.util.Map;

/**
 * 文章 Mapper接口
 */
@Mapper
public interface SysArticleMapper extends BaseMapper<SysArticle> {

    /**
     * 门户-获取文章列表
     */
    IPage<ArticleListVo> getArticleListApi(@Param("page") Page<Object> page, @Param("tagId") Integer tagId,
                                           @Param("categoryId") Integer categoryId, @Param("keyword") String keyword);

    /**
     * 获取文章详情
     */
    ArticleDetailVo getArticleDetail(Long id);

    /**
     * 获取文章归档
     */
    List<Integer> getArticleArchive();

    /**
     * 获取文章列表按年分组
     */
    List<ArticleListVo> getArticleByYear(Integer year);

    /**
     * 自定义分页查询
     */
    IPage<ArticleListVo> selectPageList(@Param("page") Page<Object> page, @Param("query") ArticleQueryDto articleQueryDto);

    /**
     * 获取今年贡献度
     */
    List<ContributionData> getThisYearContributionData();

    /**
     * 获取用户是否文章点赞
     */
    Boolean getUserIsLike(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 获取用户对文章的点赞次数
     */
    Long countUserLikeTimes(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 文章取消点赞
     */
    void unLike(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 文章点赞
     */
    void like(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 获取用户是否文章收藏
     */
    Boolean getUserIsFavorite(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 文章取消收藏
     */
    void unFavorite(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 文章收藏
     */
    void favorite(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 统计各分类下的文章数量
     */
    @MapKey("name")
    List<Map<String, Integer>> selectCountByCategory();

    /**
     * 分页查询当前用户点赞过的文章
     */
    IPage<ArticleListVo> selectMyLike(@Param("page") Page<Object> page, @Param("userId") long userId);

    /**
     * 分页查询当前用户收藏的文章
     */
    IPage<ArticleListVo> selectMyFavorite(@Param("page") Page<Object> page, @Param("userId") long userId);

    /**
     * 分页查询当前用户发布的文章
     */
    IPage<ArticleListVo> selectMyArticle(@Param("page") Page<Object> page, @Param("article") SysArticle article);

    /**
     * 统计用户收到的点赞数
     */
    Long selectReceivedLikeCount(@Param("userId") long userId);

    /**
     * 统计用户发出的点赞数
     */
    Long selectGivenLikeCount(@Param("userId") long userId);

    /**
     * 统计用户发出的收藏数
     */
    Long selectGivenFavoriteCount(@Param("userId") long userId);

    /**
     * 增加文章阅读量
     */
    void incrementQuantity(@Param("articleId") Long articleId);

    /**
     * 批量同步文章阅读量
     */
    void updateBatchQuantity(@Param("articles") List<SysArticle> articles);
}
