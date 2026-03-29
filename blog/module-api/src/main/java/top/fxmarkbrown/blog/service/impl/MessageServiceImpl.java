package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysMessage;
import top.fxmarkbrown.blog.mapper.SysMessageMapper;
import top.fxmarkbrown.blog.service.MessageService;
import top.fxmarkbrown.blog.utils.IpUtil;
import top.fxmarkbrown.blog.utils.SensitiveUtil;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final SysMessageMapper messageMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_MESSAGE, key = "'list'", sync = true)
    public List<SysMessage> getMessageList() {
        return messageMapper.selectList(null);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_MESSAGE, allEntries = true)
    public Boolean add(SysMessage sysMessage) {
        String ip = IpUtil.getIp();
        sysMessage.setIp(ip);
        sysMessage.setSource(IpUtil.getIp2region(ip));
        sysMessage.setContent(SensitiveUtil.filter(sysMessage.getContent()));
        messageMapper.insert(sysMessage);
        return true;
    }
}
