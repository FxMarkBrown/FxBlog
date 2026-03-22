package top.fxmarkbrown.blog.utils;

import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysNotificationsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationsUtil {

    private final SysArticleMapper sysArticleMapper;

    private final SysNotificationsMapper baseMapper;

    public void publish(SysNotifications sysNotifications) {
        SysArticle sysArticle = new SysArticle();
        switch (sysNotifications.getType()) {
            case "comment":
                if (sysNotifications.getUserId() == null) {
                    sysArticle = sysArticleMapper.selectById(sysNotifications.getArticleId());
                    sysNotifications.setUserId(sysArticle.getUserId());
                }
                break;
            case "like":
                if (sysNotifications.getArticleId() != null) {
                    sysArticle = sysArticleMapper.selectById(sysNotifications.getArticleId());
                    sysNotifications.setUserId(sysArticle.getUserId());
                }
                sysNotifications.setMessage("点赞了文章：" + sysArticle.getTitle());
                break;
            case "favorite":
                if (sysNotifications.getArticleId() != null) {
                    sysArticle = sysArticleMapper.selectById(sysNotifications.getArticleId());
                    sysNotifications.setUserId(sysArticle.getUserId());
                }
                sysNotifications.setMessage("收藏了文章：" + sysArticle.getTitle());
                break;
            case "unLike":
                break;
            default:
                break;
        }
        baseMapper.insert(sysNotifications);
    }
}
