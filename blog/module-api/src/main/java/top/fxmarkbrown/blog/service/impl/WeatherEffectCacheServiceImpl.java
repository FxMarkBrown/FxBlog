package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.service.WeatherEffectCacheService;
import top.fxmarkbrown.blog.vo.home.WeatherEffectVo;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class WeatherEffectCacheServiceImpl implements WeatherEffectCacheService {

    private final CacheManager cacheManager;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public WeatherEffectVo getOrLoad(String cacheKey, int ttlMinutes, Supplier<WeatherEffectVo> loader) {
        Cache cache = requireWeatherCache();
        WeatherEffectVo cached = cache.get(cacheKey, WeatherEffectVo.class);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = cache.get(cacheKey, WeatherEffectVo.class);
            if (cached != null) {
                return cached;
            }
            WeatherEffectVo loaded = loader.get();
            cache.put(cacheKey, loaded);
            redisTemplate.expire(buildRedisKey(cacheKey), normalizeTtlMinutes(ttlMinutes), TimeUnit.MINUTES);
            return loaded;
        }
    }

    private Cache requireWeatherCache() {
        Cache cache = cacheManager.getCache(CacheNames.WEATHER_EFFECT);
        if (cache == null) {
            throw new IllegalStateException("天气缓存还未配置");
        }
        return cache;
    }

    private String buildRedisKey(String cacheKey) {
        return CacheNames.redisPrefix(CacheNames.WEATHER_EFFECT) + cacheKey;
    }

    private long normalizeTtlMinutes(int ttlMinutes) {
        if (ttlMinutes < 10) {
            return Constants.DEFAULT_WEATHER_REFRESH_MINUTES;
        }
        return ttlMinutes;
    }
}
