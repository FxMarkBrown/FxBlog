package top.fxmarkbrown.blog.listener.article;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.event.article.ArticleViewRecordEvent;
import top.fxmarkbrown.blog.utils.RedisUtil;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleViewAsyncListener {

    private final RedisUtil redisUtil;

    @Async("blogAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleArticleViewRecord(ArticleViewRecordEvent event) {
        if (event == null || event.articleId() == null || event.ip() == null || event.ip().isBlank()) {
            return;
        }
        Object cached = redisUtil.hGet(RedisConstants.ARTICLE_QUANTITY, event.articleId().toString());
        List<String> ipList = cached instanceof List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : new ArrayList<>();
        if (!ipList.contains(event.ip())) {
            ipList.add(event.ip());
            redisUtil.hSet(RedisConstants.ARTICLE_QUANTITY, event.articleId().toString(), ipList);
        }
        log.debug("文章阅读记录异步处理完成, articleId={}, ip={}", event.articleId(), event.ip());
    }
}
