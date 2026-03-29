package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.entity.SysNotice;
import top.fxmarkbrown.blog.enums.NoticePosttionEnum;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysNoticeMapper;
import top.fxmarkbrown.blog.service.SysNoticeService;
import top.fxmarkbrown.blog.utils.PageUtil;

import java.util.List;

/**
 * 公告 服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    /**
     * 查询公告分页列表
     */
    @Override
    public IPage<SysNotice> selectPage(SysNotice sysNotice) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        // 构建查询条件
        wrapper.like(sysNotice.getContent() != null, SysNotice::getContent, sysNotice.getContent());
        wrapper.eq(sysNotice.getIsShow() != null, SysNotice::getIsShow, sysNotice.getIsShow());
        wrapper.eq(sysNotice.getPosition() != null, SysNotice::getPosition, sysNotice.getPosition());
        return page(PageUtil.getPage(), wrapper);
    }

    /**
     * 查询公告列表
     */
    @Override
    public List<SysNotice> selectList(SysNotice sysNotice) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        // 构建查询条件
        wrapper.eq(sysNotice.getId() != null, SysNotice::getId, sysNotice.getId());
        wrapper.eq(sysNotice.getContent() != null, SysNotice::getContent, sysNotice.getContent());
        wrapper.eq(sysNotice.getIsShow() != null, SysNotice::getIsShow, sysNotice.getIsShow());
        wrapper.eq(sysNotice.getPosition() != null, SysNotice::getPosition, sysNotice.getPosition());
        wrapper.eq(sysNotice.getCreateTime() != null, SysNotice::getCreateTime, sysNotice.getCreateTime());
        return list(wrapper);
    }

    /**
     * 新增公告
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_NOTICE, allEntries = true)
    public boolean insert(SysNotice sysNotice) {
        if (sysNotice.getIsShow() == Constants.YES && sysNotice.getPosition().equals(NoticePosttionEnum.TOP.getCode())) {
            SysNotice one = baseMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
                    .eq(SysNotice::getPosition, sysNotice.getPosition())
                    .eq(SysNotice::getIsShow,sysNotice.getIsShow()));
            if(one != null) {
                throw new ServiceException("显示的顶部公告只能有一个!");
            }
        }
        return save(sysNotice);
    }

    /**
     * 修改公告
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_NOTICE, allEntries = true)
    public boolean update(SysNotice sysNotice) {

        if (sysNotice.getIsShow() == Constants.YES && sysNotice.getPosition().equals(NoticePosttionEnum.TOP.getCode())) {
            SysNotice one = baseMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
                    .eq(SysNotice::getPosition, sysNotice.getPosition())
                    .eq(SysNotice::getIsShow,sysNotice.getIsShow()));
            if(one != null && !one.getId().equals(sysNotice.getId())) {
                throw new ServiceException("显示的顶部公告只能有一个!");
            }
        }
        return updateById(sysNotice);
    }

    /**
     * 批量删除公告
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_NOTICE, allEntries = true)
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
