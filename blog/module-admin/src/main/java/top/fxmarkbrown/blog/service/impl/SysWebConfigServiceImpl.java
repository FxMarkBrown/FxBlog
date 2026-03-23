package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.mapper.SysWebConfigMapper;
import top.fxmarkbrown.blog.service.SysWebConfigService;

@Service
@RequiredArgsConstructor
public class SysWebConfigServiceImpl extends ServiceImpl<SysWebConfigMapper, SysWebConfig> implements SysWebConfigService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.WEB_CONFIG, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.WEATHER_EFFECT, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_HOME_CONFIG, allEntries = true)
    })
    public void update(SysWebConfig sysWebConfig) {
        baseMapper.updateById(sysWebConfig);
    }
}
