package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.vo.notifications.NotificationsListVo;

import java.util.Map;

public interface NotificationsService {

    /**
     * 分页查询
     */
    IPage<NotificationsListVo> page(SysNotifications notifications);

    /**
     * 已读
     */
    void doRead(Long id);

    /**
     * 全部已读
     */
    void allRead();

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 未读消息数量
     */
    Map<String, Integer> getUnReadNum();

    /**
     * 是否有未读消息
     */
    Boolean getMyIsUnread();

}
