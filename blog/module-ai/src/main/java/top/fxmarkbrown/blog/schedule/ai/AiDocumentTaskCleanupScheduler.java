package top.fxmarkbrown.blog.schedule.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiDocumentTaskCleanupScheduler {

    private final AiDocumentTaskService aiDocumentTaskService;

    @Scheduled(cron = "0 15 * * * *")
    public void cleanupExpiredTasks() {
        int deleted = aiDocumentTaskService.cleanupExpiredTasks();
        if (deleted > 0) {
            log.info("清理过期 AI 文档任务完成, deleted={}", deleted);
        }
    }
}
