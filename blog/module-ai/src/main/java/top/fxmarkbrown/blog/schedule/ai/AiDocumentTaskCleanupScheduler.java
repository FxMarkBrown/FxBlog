package top.fxmarkbrown.blog.schedule.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.entity.SysAiDocumentTask;
import top.fxmarkbrown.blog.mapper.SysAiDocumentTaskMapper;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiDocumentTaskCleanupScheduler {

    private final SysAiDocumentTaskMapper documentTaskMapper;

    @Scheduled(cron = "0 15 * * * *")
    public void cleanupExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = documentTaskMapper.delete(new LambdaQueryWrapper<SysAiDocumentTask>()
                .isNotNull(SysAiDocumentTask::getExpireAt)
                .lt(SysAiDocumentTask::getExpireAt, now));
        if (deleted > 0) {
            log.info("清理过期 AI 文档任务完成, deleted={}", deleted);
        }
    }
}
