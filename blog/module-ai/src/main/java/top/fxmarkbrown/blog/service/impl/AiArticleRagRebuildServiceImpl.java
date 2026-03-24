package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.service.AiArticleRagRebuildService;
import top.fxmarkbrown.blog.service.AiArticleRagService;
import top.fxmarkbrown.blog.vo.ai.AiRagRebuildSubmitVo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AiArticleRagRebuildServiceImpl implements AiArticleRagRebuildService {

    private final AiArticleRagService aiArticleRagService;
    private final SysArticleMapper articleMapper;
    private final Executor ragTaskExecutor;
    private final AiRagProperties aiRagProperties;
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

    public AiArticleRagRebuildServiceImpl(AiArticleRagService aiArticleRagService,
                                          SysArticleMapper articleMapper,
                                          AiRagProperties aiRagProperties,
                                          @Qualifier("ragTaskExecutor") Executor ragTaskExecutor) {
        this.aiArticleRagService = aiArticleRagService;
        this.articleMapper = articleMapper;
        this.aiRagProperties = aiRagProperties;
        this.ragTaskExecutor = ragTaskExecutor;
    }

    @Override
    public AiRagRebuildSubmitVo submitAsync(boolean publishedOnly, String trigger) {
        if (!aiArticleRagService.isReady()) {
            throw new ServiceException("RAG 服务未就绪");
        }
        if (!rebuilding.compareAndSet(false, true)) {
            return new AiRagRebuildSubmitVo(false, true, 0, publishedOnly, trigger);
        }

        List<SysArticle> articles = listArticles(publishedOnly);
        if (articles.isEmpty()) {
            rebuilding.set(false);
            log.info("RAG 全量重建跳过：没有可索引文章, trigger={}, publishedOnly={}", trigger, publishedOnly);
            return new AiRagRebuildSubmitVo(false, false, 0, publishedOnly, trigger);
        }

        try {
            ragTaskExecutor.execute(() -> rebuildArticles(articles, publishedOnly, trigger));
            return new AiRagRebuildSubmitVo(true, true, articles.size(), publishedOnly, trigger);
        } catch (RuntimeException ex) {
            rebuilding.set(false);
            throw ex;
        }
    }

    @Override
    public boolean isRunning() {
        return rebuilding.get();
    }

    private List<SysArticle> listArticles(boolean publishedOnly) {
        LambdaQueryWrapper<SysArticle> wrapper = new LambdaQueryWrapper<SysArticle>()
                .orderByAsc(SysArticle::getId);
        if (publishedOnly) {
            wrapper.eq(SysArticle::getStatus, Constants.YES);
        }
        return articleMapper.selectList(wrapper);
    }

    private void rebuildArticles(List<SysArticle> articles, boolean publishedOnly, String trigger) {
        int parallelism = Math.max(aiRagProperties.getRebuildConcurrency(), 1);
        log.info("RAG 全量重建开始, trigger={}, articleCount={}, publishedOnly={}, concurrency={}",
                trigger, articles.size(), publishedOnly, parallelism);
        try {
            List<CompletableFuture<Void>> inFlightTasks = new ArrayList<>();
            AtomicInteger completedCount = new AtomicInteger(0);
            for (SysArticle article : articles) {
                inFlightTasks.add(CompletableFuture.runAsync(
                        () -> syncArticleIndexSafely(article, completedCount, articles.size(), trigger),
                        ragTaskExecutor
                ));
                if (inFlightTasks.size() >= parallelism) {
                    waitForAny(inFlightTasks);
                    inFlightTasks.removeIf(CompletableFuture::isDone);
                }
            }
            waitForAll(inFlightTasks);
            log.info("RAG 全量重建完成, trigger={}, articleCount={}, publishedOnly={}", trigger, articles.size(), publishedOnly);
        } finally {
            rebuilding.set(false);
        }
    }

    private void syncArticleIndexSafely(SysArticle article, AtomicInteger completedCount, int totalCount, String trigger) {
        try {
            aiArticleRagService.syncArticleIndex(article);
        } catch (Exception ex) {
            log.warn("RAG 全量重建单篇索引失败, trigger={}, articleId={}", trigger, article == null ? null : article.getId(), ex);
        } finally {
            int current = completedCount.incrementAndGet();
            if (current == totalCount || current % 10 == 0) {
                log.info("RAG 全量重建进度, trigger={}, completed={}/{}", trigger, current, totalCount);
            }
        }
    }

    private void waitForAny(List<CompletableFuture<Void>> futures) {
        if (futures.isEmpty()) {
            return;
        }
        CompletableFuture.anyOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void waitForAll(List<CompletableFuture<Void>> futures) {
        if (futures.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }
}
