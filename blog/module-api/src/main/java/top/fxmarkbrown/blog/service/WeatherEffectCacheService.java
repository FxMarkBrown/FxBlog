package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.vo.home.WeatherEffectVo;

import java.util.function.Supplier;

public interface WeatherEffectCacheService {

    /**
     * 获取天气缓存，不存在时加载并按动态 TTL 写入
     */
    WeatherEffectVo getOrLoad(String cacheKey, int ttlMinutes, Supplier<WeatherEffectVo> loader);
}
