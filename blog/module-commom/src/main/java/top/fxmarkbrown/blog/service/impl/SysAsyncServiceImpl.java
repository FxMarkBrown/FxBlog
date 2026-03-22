package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.event.article.ArticleViewRecordEvent;
import top.fxmarkbrown.blog.event.notification.NotificationPublishEvent;

@Service
@RequiredArgsConstructor
public class SysAsyncServiceImpl {

    private final ApplicationEventPublisher eventPublisher;

    public void recordArticleView(Long articleId, String ip) {
        if (articleId == null || ip == null || ip.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new ArticleViewRecordEvent(articleId, ip));
    }

    public void publishNotification(SysNotifications notifications) {
        if (notifications == null) {
            return;
        }
        eventPublisher.publishEvent(new NotificationPublishEvent(notifications));
    }
}
