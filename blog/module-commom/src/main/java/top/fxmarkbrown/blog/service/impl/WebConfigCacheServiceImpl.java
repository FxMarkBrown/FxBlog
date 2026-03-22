package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.mapper.SysWebConfigMapper;
import top.fxmarkbrown.blog.service.WebConfigCacheService;

@Service
@RequiredArgsConstructor
public class WebConfigCacheServiceImpl implements WebConfigCacheService {

    private final SysWebConfigMapper sysWebConfigMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.WEB_CONFIG, key = "'current'", sync = true)
    public SysWebConfig getCurrentWebConfig() {
        return sysWebConfigMapper.selectOne(new LambdaQueryWrapper<SysWebConfig>().last("limit 1"));
    }
}
