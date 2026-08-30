package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.mapper.SysSeriesMapper;
import top.fxmarkbrown.blog.service.SeriesService;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SeriesListVo;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeriesServiceImpl implements SeriesService {

    private final SysSeriesMapper sysSeriesMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_SERIES, key = "'list'", sync = true)
    public List<SeriesListVo> getSeriesList() {
        return sysSeriesMapper.getSeriesWithCount();
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_SERIES_ARTICLES, key = "#seriesId", sync = true)
    public List<ArticleListVo> getSeriesArticles(Integer seriesId) {
        return sysSeriesMapper.getSeriesArticles(seriesId);
    }
}
