package top.fxmarkbrown.blog.runner.ai;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.service.AiArticleRagRebuildService;
import top.fxmarkbrown.blog.service.AiArticleRagService;
import top.fxmarkbrown.blog.vo.ai.AiRagRebuildSubmitVo;

@Slf4j
@Component
public class AiRagBootstrapRunner implements ApplicationRunner {

    private final AiRagProperties aiRagProperties;
    private final AiArticleRagService aiArticleRagService;
    private final AiArticleRagRebuildService aiArticleRagRebuildService;

    public AiRagBootstrapRunner(AiRagProperties aiRagProperties,
                                AiArticleRagService aiArticleRagService,
                                AiArticleRagRebuildService aiArticleRagRebuildService) {
        this.aiRagProperties = aiRagProperties;
        this.aiArticleRagService = aiArticleRagService;
        this.aiArticleRagRebuildService = aiArticleRagRebuildService;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!aiRagProperties.isEnabled() || !aiRagProperties.isSyncOnStartup() || !aiArticleRagService.isReady()) {
            return;
        }
        AiRagRebuildSubmitVo submitVo = aiArticleRagRebuildService.submitAsync(aiRagProperties.isIndexPublishedOnly(), "startup");
        if (submitVo.isSubmitted()) {
            log.info("RAG 启动重建已提交后台执行，articleCount={}", submitVo.getArticleCount());
        } else if (submitVo.isRunning()) {
            log.info("RAG 启动重建跳过：已有重建任务在运行");
        } else {
            log.info("RAG 启动重建跳过：没有可索引文章");
        }
    }
}
