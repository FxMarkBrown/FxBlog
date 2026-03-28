package top.fxmarkbrown.blog.listener.article;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.fxmarkbrown.blog.event.article.ArticleViewRecordEvent;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleViewAsyncListener {

    private final SysArticleMapper sysArticleMapper;

    @Async("blogAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleArticleViewRecord(ArticleViewRecordEvent event) {
        if (event == null || event.articleId() == null || event.ip() == null || event.ip().isBlank()) {
            return;
        }

        sysArticleMapper.incrementQuantity(event.articleId());
        log.debug("文章阅读量异步增加, articleId={}, ip={}", event.articleId(), event.ip());
    }
}
