package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.event.article.ArticleViewRecordEvent;
import top.fxmarkbrown.blog.event.notification.NotificationPublishEvent;
import top.fxmarkbrown.blog.utils.RedisUtil;

@Service
@RequiredArgsConstructor
public class SysAsyncServiceImpl {

    private final ApplicationEventPublisher eventPublisher;
    private final RedisUtil redisUtil;

    public boolean recordArticleView(Long articleId, String ip) {
        if (articleId == null || ip == null || ip.isBlank()) {
            return false;
        }

        String redisKey = RedisConstants.ARTICLE_VIEW_IP_SET + articleId;
        Long added = redisUtil.sAdd(redisKey, ip);
        if (added == null || added <= 0) {
            return false;
        }
        eventPublisher.publishEvent(new ArticleViewRecordEvent(articleId, ip));
        return true;
    }

    public void publishNotification(SysNotifications notifications) {
        if (notifications == null) {
            return;
        }
        eventPublisher.publishEvent(new NotificationPublishEvent(notifications));
    }
}
