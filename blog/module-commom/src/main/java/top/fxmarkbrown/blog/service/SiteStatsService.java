package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysSiteStats;

/**
 * 站点统计服务
 */
public interface SiteStatsService {

    /**
     * 初始化持久化统计，并与 Redis 中现有数据对齐
     */
    void initializePersistentStats();

    /**
     * 获取当前站点统计
     */
    SysSiteStats getCurrentStats();

    /**
     * 记录一次访问
     */
    void reportVisit(String visitorKey);
}
