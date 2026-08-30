package top.fxmarkbrown.blog.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.fxmarkbrown.blog.event.IndexNowArticleEvent;
import top.fxmarkbrown.blog.service.IndexNowService;

/**
 * IndexNow 提交监听器：事务提交后异步执行，避免阻塞文章主流程，
 * 也避免事务回滚时误报搜索引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexNowAsyncListener {

    private final IndexNowService indexNowService;

    @Async("blogAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(IndexNowArticleEvent event) {
        if (event == null || event.articleIds() == null || event.articleIds().isEmpty()) {
            return;
        }
        indexNowService.submitArticles(event.articleIds());
    }
}
