package top.fxmarkbrown.blog.service.impl;

import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysMessageMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.IndexService;
import top.fxmarkbrown.blog.utils.RedisUtil;
import top.fxmarkbrown.blog.vo.dashboard.ContributionData;
import top.fxmarkbrown.blog.vo.dashboard.IndexVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

    private final SysUserMapper sysUserMapper;

    private final SysArticleMapper sysArticleMapper;

    private final SysMessageMapper sysMessageMapper;

    private final RedisUtil redisUtil;

    @Override
    public IndexVo index() {
        Long userCount = sysUserMapper.selectCount(null);
        Long articleCount = sysArticleMapper.selectCount(null);
        Long messageCount = sysMessageMapper.selectCount(null);

        int visitCount = 0;
        Object e = redisUtil.get(RedisConstants.BLOG_VIEWS_COUNT);
        if (e != null) {
            visitCount = Integer.parseInt(e.toString());
        }

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
