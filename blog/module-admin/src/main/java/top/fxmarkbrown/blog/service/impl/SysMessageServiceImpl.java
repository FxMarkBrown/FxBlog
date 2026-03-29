package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysMessage;
import top.fxmarkbrown.blog.mapper.SysMessageMapper;
import top.fxmarkbrown.blog.service.SysMessageService;
import top.fxmarkbrown.blog.utils.PageUtil;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class SysMessageServiceImpl extends ServiceImpl<SysMessageMapper, SysMessage> implements SysMessageService {

    @Override
    public Page<SysMessage> selectList() {
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>().orderByDesc(SysMessage::getCreateTime);
        return baseMapper.selectPage(PageUtil.getPage(),wrapper);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_MESSAGE, allEntries = true)
    public boolean removeBatchByIds(Collection<?> list) {
        return super.removeBatchByIds(list);
    }
}
