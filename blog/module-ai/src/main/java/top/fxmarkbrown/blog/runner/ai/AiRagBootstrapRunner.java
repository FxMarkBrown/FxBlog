package top.fxmarkbrown.blog.runner.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.service.AiArticleRagService;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class AiRagBootstrapRunner implements ApplicationRunner {

    private final AiRagProperties aiRagProperties;
    private final AiArticleRagService aiArticleRagService;
    private final SysArticleMapper articleMapper;
    private final Executor ragTaskExecutor;

    public AiRagBootstrapRunner(AiRagProperties aiRagProperties,
                                AiArticleRagService aiArticleRagService,
                                SysArticleMapper articleMapper,
                                @Qualifier("ragTaskExecutor") Executor ragTaskExecutor) {
        this.aiRagProperties = aiRagProperties;
        this.aiArticleRagService = aiArticleRagService;
        this.articleMapper = articleMapper;
        this.ragTaskExecutor = ragTaskExecutor;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!aiRagProperties.isEnabled() || !aiRagProperties.isSyncOnStartup() || !aiArticleRagService.isReady()) {
            return;
        }
        LambdaQueryWrapper<SysArticle> wrapper = new LambdaQueryWrapper<SysArticle>()
                .orderByAsc(SysArticle::getId);
        if (aiRagProperties.isIndexPublishedOnly()) {
            wrapper.eq(SysArticle::getStatus, Constants.YES);
        }
        List<SysArticle> articles = articleMapper.selectList(wrapper);
        if (articles.isEmpty()) {
            log.info("RAG 启动重建跳过：没有可索引文章");
            return;
        }
        log.info("RAG 启动重建已提交后台执行，articleCount={}", articles.size());
        ragTaskExecutor.execute(() -> rebuildArticles(articles));
    }

    private void rebuildArticles(List<SysArticle> articles) {
        log.info("RAG 启动重建开始，articleCount={}", articles.size());
        for (SysArticle article : articles) {
            aiArticleRagService.syncArticleIndex(article);
        }
        log.info("RAG 启动重建完成，articleCount={}", articles.size());
    }
}
