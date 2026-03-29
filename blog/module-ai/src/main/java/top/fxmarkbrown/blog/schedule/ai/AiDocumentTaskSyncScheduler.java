package top.fxmarkbrown.blog.schedule.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class AiDocumentTaskSyncScheduler {

    private final AiDocumentTaskService aiDocumentTaskService;

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    public void syncPendingTasks() {
        aiDocumentTaskService.syncPendingTasks();
    }
}
