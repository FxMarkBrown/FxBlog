package top.fxmarkbrown.blog.listener.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.event.notification.NotificationPublishEvent;
import top.fxmarkbrown.blog.utils.NotificationsUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAsyncListener {

    private final NotificationsUtil notificationsUtil;

    @Async("blogAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleNotificationPublish(NotificationPublishEvent event) {
        if (event == null || event.notification() == null) {
            return;
        }
        SysNotifications notification = event.notification();
        notificationsUtil.publish(notification);
        log.debug("通知异步发布完成, type={}, articleId={}, fromUserId={}",
                notification.getType(),
                notification.getArticleId(),
                notification.getFromUserId());
    }
}
