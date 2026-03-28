package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.entity.SysSiteStats;
import top.fxmarkbrown.blog.mapper.SysSiteStatsMapper;
import top.fxmarkbrown.blog.mapper.SysSiteVisitorMapper;
import top.fxmarkbrown.blog.service.SiteStatsService;
import top.fxmarkbrown.blog.utils.RedisUtil;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 站点统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteStatsServiceImpl implements SiteStatsService {

    private static final long DEFAULT_STATS_ID = 1L;

    private final SysSiteStatsMapper sysSiteStatsMapper;
    private final SysSiteVisitorMapper sysSiteVisitorMapper;
    private final RedisUtil redisUtil;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void initializePersistentStats() {
        if (initialized.get()) {
            return;
        }
        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            doInitializePersistentStats();
            initialized.set(true);
        }
    }

    @Override
    public SysSiteStats getCurrentStats() {
        initializePersistentStats();
        SysSiteStats stats = ensureStatsRow();
        syncRedisCounters(stats);
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportVisit(String visitorKey) {
        initializePersistentStats();

        SysSiteStats currentStats = ensureStatsRow();
        syncRedisCounters(currentStats);
        boolean uniqueVisitor = registerVisitor(visitorKey);
        int updated = sysSiteStatsMapper.incrementStats(DEFAULT_STATS_ID, 1L, uniqueVisitor ? 1L : 0L);
        if (updated <= 0) {
            ensureStatsRow();
            sysSiteStatsMapper.incrementStats(DEFAULT_STATS_ID, 1L, uniqueVisitor ? 1L : 0L);
        }

        redisUtil.increment(RedisConstants.BLOG_VIEWS_COUNT, 1L);
        if (uniqueVisitor) {
            redisUtil.increment(RedisConstants.UNIQUE_VISITOR_COUNT, 1L);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void doInitializePersistentStats() {
        SysSiteStats stats = ensureStatsRow();

        long redisBlogViewsCount = parseLong(redisUtil.get(RedisConstants.BLOG_VIEWS_COUNT));
        long redisUniqueVisitorCount = parseLong(redisUtil.get(RedisConstants.UNIQUE_VISITOR_COUNT));

        Set<Object> visitorKeys = redisUtil.sMembers(RedisConstants.UNIQUE_VISITOR);
        importRedisVisitors(visitorKeys);

        long dbBlogViewsCount = defaultLong(stats.getBlogViewsCount());
        long dbUniqueVisitorCount = defaultLong(stats.getUniqueVisitorCount());
        long persistedVisitorCount = defaultLong(sysSiteVisitorMapper.selectCount(null));

        long targetBlogViewsCount = Math.max(dbBlogViewsCount, redisBlogViewsCount);
        long targetUniqueVisitorCount = Math.max(Math.max(dbUniqueVisitorCount, redisUniqueVisitorCount), persistedVisitorCount);

        if (targetBlogViewsCount != dbBlogViewsCount || targetUniqueVisitorCount != dbUniqueVisitorCount) {
            stats.setBlogViewsCount(targetBlogViewsCount);
            stats.setUniqueVisitorCount(targetUniqueVisitorCount);
            sysSiteStatsMapper.updateById(stats);
        }

        stats.setBlogViewsCount(targetBlogViewsCount);
        stats.setUniqueVisitorCount(targetUniqueVisitorCount);
        syncRedisCounters(stats);

        log.info("站点统计初始化完成, blogViewsCount={}, uniqueVisitorCount={}, importedVisitors={}",
                targetBlogViewsCount, targetUniqueVisitorCount, visitorKeys == null ? 0 : visitorKeys.size());
    }

    private SysSiteStats ensureStatsRow() {
        SysSiteStats stats = sysSiteStatsMapper.selectById(DEFAULT_STATS_ID);
        if (stats != null) {
            return stats;
        }

        SysSiteStats created = new SysSiteStats();
        created.setId(DEFAULT_STATS_ID);
        created.setBlogViewsCount(0L);
        created.setUniqueVisitorCount(0L);
        sysSiteStatsMapper.insert(created);
        return sysSiteStatsMapper.selectById(DEFAULT_STATS_ID);
    }

    private boolean registerVisitor(String visitorKey) {
        if (visitorKey == null || visitorKey.isBlank()) {
            return false;
        }

        if (Boolean.TRUE.equals(redisUtil.sIsMember(RedisConstants.UNIQUE_VISITOR, visitorKey))) {
            return false;
        }

        boolean uniqueVisitor = sysSiteVisitorMapper.insertIgnore(visitorKey) > 0;
        redisUtil.sAdd(RedisConstants.UNIQUE_VISITOR, visitorKey);
        return uniqueVisitor;
    }

    private void importRedisVisitors(Set<Object> visitorKeys) {
        if (visitorKeys == null || visitorKeys.isEmpty()) {
            return;
        }

        for (Object visitorKey : visitorKeys) {
            if (visitorKey == null) {
                continue;
            }
            String normalized = String.valueOf(visitorKey).trim();
            if (normalized.isEmpty()) {
                continue;
            }
            sysSiteVisitorMapper.insertIgnore(normalized);
        }
    }

    private void syncRedisCounters(SysSiteStats stats) {
        if (stats == null) {
            return;
        }

        long blogViewsCount = defaultLong(stats.getBlogViewsCount());
        long uniqueVisitorCount = defaultLong(stats.getUniqueVisitorCount());

        if (!Boolean.TRUE.equals(redisUtil.hasKey(RedisConstants.BLOG_VIEWS_COUNT))
                || parseLong(redisUtil.get(RedisConstants.BLOG_VIEWS_COUNT)) != blogViewsCount) {
            redisUtil.set(RedisConstants.BLOG_VIEWS_COUNT, blogViewsCount);
        }
        if (!Boolean.TRUE.equals(redisUtil.hasKey(RedisConstants.UNIQUE_VISITOR_COUNT))
                || parseLong(redisUtil.get(RedisConstants.UNIQUE_VISITOR_COUNT)) != uniqueVisitorCount) {
            redisUtil.set(RedisConstants.UNIQUE_VISITOR_COUNT, uniqueVisitorCount);
        }
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("站点统计 Redis 值不是有效数字, value={}", value);
            return 0L;
        }
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
