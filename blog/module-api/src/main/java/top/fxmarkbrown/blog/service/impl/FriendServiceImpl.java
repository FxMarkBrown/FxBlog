package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysFriend;
import top.fxmarkbrown.blog.enums.FriendStatusEnum;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysFriendMapper;
import top.fxmarkbrown.blog.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final SysFriendMapper friendMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_FRIEND, key = "'list'", sync = true)
    public List<SysFriend> getFriendList() {
        return friendMapper.selectList(new LambdaQueryWrapper<SysFriend>()
                .select(SysFriend::getId,SysFriend::getName,SysFriend::getInfo,SysFriend::getAvatar
                        ,SysFriend::getUrl)
                .eq(SysFriend::getStatus, FriendStatusEnum.UP.getCode())
                .orderByAsc(SysFriend::getSort));
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_FRIEND, allEntries = true)
    public Boolean apply(SysFriend sysFriend) {
        SysFriend obj = friendMapper.selectOne(new LambdaQueryWrapper<SysFriend>()
                .eq(SysFriend::getUrl, sysFriend.getUrl()));
        if (ObjectUtils.isNotEmpty(obj)) {
            throw new ServiceException("申请友链失败，该网站已存在");
        }

        sysFriend.setStatus(FriendStatusEnum.APPLY.getCode());
        friendMapper.insert(sysFriend);

        return true;
    }
}
