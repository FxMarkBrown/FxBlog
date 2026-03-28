package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.entity.SysSiteStats;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysMessageMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.IndexService;
import top.fxmarkbrown.blog.service.SiteStatsService;
import top.fxmarkbrown.blog.vo.dashboard.ContributionData;
import top.fxmarkbrown.blog.vo.dashboard.IndexVo;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

    private final SysUserMapper sysUserMapper;

    private final SysArticleMapper sysArticleMapper;

    private final SysMessageMapper sysMessageMapper;

    private final SiteStatsService siteStatsService;

    @Override
    public IndexVo index() {
        Long userCount = sysUserMapper.selectCount(null);
        Long articleCount = sysArticleMapper.selectCount(null);
        Long messageCount = sysMessageMapper.selectCount(null);
        SysSiteStats siteStats = siteStatsService.getCurrentStats();
        long visitCount = siteStats.getBlogViewsCount() == null ? 0L : siteStats.getBlogViewsCount();

        List<ContributionData> list = sysArticleMapper.getThisYearContributionData();

        return IndexVo.builder()
                .articleCount(articleCount)
                .userCount(userCount)
                .messageCount(messageCount)
                .visitCount(visitCount)
                .contributionData(list)
                .build();
    }

    @Override
    public List<Map<String, Integer>> getCategories() {
        List<Map<String, Integer>> list = sysArticleMapper.selectCountByCategory();
        return list;
    }
}
