package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.fxmarkbrown.blog.dto.article.ArticleQueryDto;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.vo.article.ArticleDetailVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.dashboard.ContributionData;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    @MapKey("name")
    List<Map<String, Integer>> selectCountByCategory();

    IPage<ArticleListVo> selectMyLike(@Param("page") Page<Object> page, @Param("userId") long userId);

    IPage<ArticleListVo> selectMyFavorite(@Param("page") Page<Object> page, @Param("userId") long userId);

    IPage<ArticleListVo> selectMyArticle(@Param("page") Page<Object> page, @Param("article") SysArticle article);

    Long selectReceivedLikeCount(@Param("userId") long userId);

    Long selectGivenLikeCount(@Param("userId") long userId);

    Long selectGivenFavoriteCount(@Param("userId") long userId);

    void updateBatchQuantity(@Param("articles") List<SysArticle> articles);
}
