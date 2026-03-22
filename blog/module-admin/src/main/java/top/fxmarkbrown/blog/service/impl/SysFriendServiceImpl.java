package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysFriend;
import top.fxmarkbrown.blog.mapper.SysFriendMapper;
import top.fxmarkbrown.blog.service.SysFriendService;
import top.fxmarkbrown.blog.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class SysFriendServiceImpl extends ServiceImpl<SysFriendMapper, SysFriend> implements SysFriendService {

    @Override
    public IPage<SysFriend> selectPage(SysFriend sysFriend) {
        LambdaQueryWrapper<SysFriend> wrapper = new LambdaQueryWrapper<SysFriend>()
                .eq(sysFriend.getName() != null, SysFriend::getName, sysFriend.getName())
                .eq(sysFriend.getStatus() != null, SysFriend::getStatus, sysFriend.getStatus());
        return page(PageUtil.getPage(), wrapper);
    }

    /**
     * 修改友情链接
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_FRIEND, allEntries = true)
    public boolean update(SysFriend sysFriend) {
        return updateById(sysFriend);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_FRIEND, allEntries = true)
    public boolean save(SysFriend entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_FRIEND, allEntries = true)
    public boolean removeBatchByIds(Collection<?> list) {
        return super.removeBatchByIds(list);
    }
}
