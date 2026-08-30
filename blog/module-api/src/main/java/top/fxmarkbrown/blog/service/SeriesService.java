package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SeriesListVo;

import java.util.List;

public interface SeriesService {

    /**
     * 获取全部系列（含已发布文章数）
     */
    List<SeriesListVo> getSeriesList();

    /**
     * 获取系列下的已发布文章（按创建时间正序）
     */
    List<ArticleListVo> getSeriesArticles(Integer seriesId);
}
