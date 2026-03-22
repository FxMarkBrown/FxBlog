package top.fxmarkbrown.blog.listener.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.event.ai.AiArticleIndexRemoveEvent;
import top.fxmarkbrown.blog.event.ai.AiArticleIndexSyncEvent;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.service.AiArticleRagService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiArticleRagAsyncListener {

    private final SysArticleMapper sysArticleMapper;
    private final AiArticleRagService aiArticleRagService;

    @Async("ragTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleArticleSync(AiArticleIndexSyncEvent event) {
        if (event == null || event.articleId() == null) {
            return;
        }
        SysArticle article = sysArticleMapper.selectById(event.articleId());
        if (article == null) {
            log.info("文章 RAG 同步跳过：文章不存在, articleId={}", event.articleId());
            return;
        }
        aiArticleRagService.syncArticleIndex(article);
    }

    @Async("ragTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleArticleRemove(AiArticleIndexRemoveEvent event) {
        if (event == null || event.articleIds() == null || event.articleIds().isEmpty()) {
            return;
        }
        for (Long articleId : event.articleIds()) {
            if (articleId != null) {
                aiArticleRagService.removeArticleIndex(articleId);
            }
        }
    }
}
