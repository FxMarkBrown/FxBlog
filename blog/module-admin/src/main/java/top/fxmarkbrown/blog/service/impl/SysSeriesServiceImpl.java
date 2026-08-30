package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.common.ResultCode;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysSeries;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysSeriesMapper;
import top.fxmarkbrown.blog.service.SysSeriesService;
import top.fxmarkbrown.blog.utils.PageUtil;

import java.util.List;

/**
 * 系列表 服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysSeriesServiceImpl extends ServiceImpl<SysSeriesMapper, SysSeries> implements SysSeriesService {

    private final SysArticleMapper sysArticleMapper;

    /**
     * 查询系列表分页列表
     */
    @Override
    public IPage<SysSeries> selectPage(SysSeries sysSeries) {
        LambdaQueryWrapper<SysSeries> wrapper = new LambdaQueryWrapper<SysSeries>()
                .like(StringUtils.isNotBlank(sysSeries.getName()), SysSeries::getName, sysSeries.getName())
                .orderByAsc(SysSeries::getSort)
                .orderByAsc(SysSeries::getId);
        return page(PageUtil.getPage(), wrapper);
    }

    /**
     * 查询系列表列表
     */
    @Override
    public List<SysSeries> selectList(SysSeries sysSeries) {
        return list(new LambdaQueryWrapper<SysSeries>()
                .orderByAsc(SysSeries::getSort)
                .orderByAsc(SysSeries::getId));
    }

    /**
     * 新增系列表
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SERIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SERIES_ARTICLES, allEntries = true)
    })
    public boolean insert(SysSeries sysSeries) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<SysSeries>()
                .eq(SysSeries::getName, sysSeries.getName()));
        if (count > 0) {
            throw new ServiceException(ResultCode.SERIES_IS_EXIST.desc);
        }
        return save(sysSeries);
    }

    /**
     * 修改系列表
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SERIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SERIES_ARTICLES, allEntries = true)
    })
    public boolean update(SysSeries sysSeries) {
        SysSeries sysSeries1 = baseMapper.selectOne(new LambdaQueryWrapper<SysSeries>().eq(SysSeries::getName, sysSeries.getName()));
        if (sysSeries1 != null && !sysSeries1.getId().equals(sysSeries.getId())) {
            throw new ServiceException(ResultCode.SERIES_IS_EXIST.desc);
        }
        return updateById(sysSeries);
    }

    /**
     * 批量删除系列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SERIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SERIES_ARTICLES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_LIST, allEntries = true)
    })
    public boolean deleteByIds(List<Integer> ids) {
        //解除系列下的文章关联
        sysArticleMapper.update(null, new LambdaUpdateWrapper<SysArticle>()
                .in(SysArticle::getSeriesId, ids)
                .set(SysArticle::getSeriesId, null));
        return removeByIds(ids);
    }
}
