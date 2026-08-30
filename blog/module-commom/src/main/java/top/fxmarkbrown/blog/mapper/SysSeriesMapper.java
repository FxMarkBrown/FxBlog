package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.entity.SysSeries;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SeriesListVo;

import java.util.List;

/**
 * 系列 Mapper接口
 */
@Mapper
public interface SysSeriesMapper extends BaseMapper<SysSeries> {

    /**
     * 获取全部系列（含已发布文章数）
     */
    List<SeriesListVo> getSeriesWithCount();

    /**
     * 获取系列下的已发布文章（按创建时间正序）
     */
    List<ArticleListVo> getSeriesArticles(@Param("seriesId") Integer seriesId);
}
