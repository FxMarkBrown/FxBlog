package top.fxmarkbrown.blog.runner.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.service.SiteStatsService;

/**
 * 启动时预热站点统计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteStatsWarmupRunner implements ApplicationRunner {

    private final SiteStatsService siteStatsService;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        try {
            siteStatsService.initializePersistentStats();
        } catch (Exception e) {
            log.error("站点统计初始化失败", e);
        }
    }
}
