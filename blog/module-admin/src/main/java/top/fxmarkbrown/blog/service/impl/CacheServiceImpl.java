package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.service.CacheService;
import top.fxmarkbrown.blog.vo.cache.CacheInfoVo;
import top.fxmarkbrown.blog.vo.cache.CacheKeyQuery;
import top.fxmarkbrown.blog.vo.cache.CacheKeyVo;
import top.fxmarkbrown.blog.vo.cache.CacheMemoryVo;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    @Override
    public CacheInfoVo getCacheInfo() {
        Properties info = redisTemplate.execute((RedisCallback<Properties>) RedisServerCommands::info);

        CacheInfoVo vo = new CacheInfoVo();
        vo.setVersion(info.getProperty("redis_version"));
        vo.setMode(info.getProperty("redis_mode"));
        vo.setPort(info.getProperty("tcp_port"));

        String uptimeInSeconds = info.getProperty("uptime_in_seconds");
        long days = Long.parseLong(uptimeInSeconds) / (24 * 3600);
        vo.setUptime(String.valueOf(days));

        // 客户端连接数
        vo.setClients(info.getProperty("connected_clients"));

        // 内存信息
        vo.setUsedMemory(formatBytes(Long.parseLong(info.getProperty("used_memory"))));
        String maxMemory = info.getProperty("maxmemory");
        vo.setMaxmemory(maxMemory.equals("0") ? "不限制" : formatBytes(Long.parseLong(maxMemory)));

        // 持久化信息
        vo.setAofEnabled("1".equals(info.getProperty("aof_enabled")) ? "是" : "否");
        vo.setRdbLastSaveStatus("ok".equals(info.getProperty("rdb_last_bgsave_status")) ? "成功" : "失败");

        // 获取所有数据库的key总数
        Long dbSize = redisTemplate.execute(RedisServerCommands::dbSize);
        vo.setKeys(String.valueOf(dbSize));

        // 网络信息
        vo.setInstantaneousInputKbps(info.getProperty("instantaneous_input_kbps") + " kbps");
        vo.setInstantaneousOutputKbps(info.getProperty("instantaneous_output_kbps") + " kbps");

        return vo;
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public CacheMemoryVo getMemoryInfo() {
        Properties info = redisTemplate.execute((RedisCallback<Properties>) RedisServerCommands::info);

        CacheMemoryVo vo = new CacheMemoryVo();
        vo.setUsed(Long.parseLong(info.getProperty("used_memory")));
        vo.setTotal(Runtime.getRuntime().maxMemory());
        return vo;
    }

    @Override
    public IPage<CacheKeyVo> getKeyList(CacheKeyQuery query) {
        IPage<CacheKeyVo> page = new Page<>();
        Set<String> keys = scanBusinessCacheKeys(query.getKey());
        if (keys == null || keys.isEmpty()) {
            page.setTotal(0);
            page.setRecords(Collections.emptyList());
            return page;
        }

        List<CacheKeyVo> list = keys.stream().sorted().map(key -> {
            CacheKeyVo vo = new CacheKeyVo();
            vo.setKey(key);
            vo.setType(Objects.requireNonNull(redisTemplate.type(key)).name());
            vo.setSize(getKeySize(key));
            vo.setTtl(redisTemplate.getExpire(key));
            return vo;
        }).collect(Collectors.toList());

        // 分页处理
        int start = (query.getPageNum() - 1) * query.getPageSize();
        if (start >= list.size()) {
            page.setRecords(Collections.emptyList());
            page.setTotal(list.size());
            return page;
        }
        int end = Math.min(start + query.getPageSize(), list.size());
        page.setRecords(list.subList(start, end));
        page.setTotal(list.size());
        return page;
    }

    @Override
    public void clearCache() {
        for (String cacheName : CacheNames.ALL) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private Set<String> scanBusinessCacheKeys(String keyword) {
        Set<String> keys = new LinkedHashSet<>();
        for (String cacheName : CacheNames.ALL) {
            String pattern = StringUtils.hasText(keyword)
                    ? CacheNames.redisPrefix(cacheName) + "*" + keyword.trim() + "*"
                    : CacheNames.redisPattern(cacheName);
            keys.addAll(scanKeys(pattern));
        }
        return keys;
    }

    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> doScan(connection, pattern));
    }

    private Set<String> doScan(RedisConnection connection, String pattern) {
        Set<String> keys = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(500)
                .build();
        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new IllegalStateException("scan redis cache keys failed", e);
        }
        return keys;
    }

    private Long getKeySize(String key) {
        try {
            return redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().strLen(key.getBytes()));
        } catch (Exception e) {
            return 0L;
        }
    }
}
