package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysNotificationsMapper;
import top.fxmarkbrown.blog.service.NotificationsService;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.notifications.NotificationsListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationsServiceImpl implements NotificationsService {

    private final SysNotificationsMapper baseMapper;

    public IPage<NotificationsListVo> page(SysNotifications notifications) {
        notifications.setUserId(StpUtil.getLoginIdAsLong());
        return baseMapper.selectNotificationsPage(PageUtil.getPage(),notifications);
    }

    @Override
    public void doRead(Long id) {
        SysNotifications notifications = baseMapper.selectById(id);
        if (notifications == null) {
            throw new ServiceException("消息通知不存在");
        }
        if (!Objects.equals(notifications.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new ServiceException("无权操作该消息通知");
        }
        notifications.setIsRead(Boolean.TRUE);
        baseMapper.updateById(notifications);
    }

    @Override
    public void allRead() {
        baseMapper.update(SysNotifications.builder().isRead(Boolean.TRUE).build(),new LambdaQueryWrapper<SysNotifications>()
                .eq(SysNotifications::getUserId, StpUtil.getLoginIdAsLong()));
    }

    @Override
    public void delete(Long id) {
        SysNotifications notifications = baseMapper.selectById(id);
        if (notifications == null) {
            throw new ServiceException("消息通知不存在");
        }
        if (!Objects.equals(notifications.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new ServiceException("无权操作该消息通知");
        }
        baseMapper.deleteById(id);
    }

    @Override
    public Map<String, Integer> getUnReadNum() {
        return baseMapper.getUnReadNum(StpUtil.getLoginIdAsLong());
    }

    @Override
    public Boolean getMyIsUnread() {
        List<SysNotifications> sysNotifications = baseMapper.selectList(new LambdaQueryWrapper<SysNotifications>()
                .eq(SysNotifications::getUserId, StpUtil.getLoginIdAsLong())
                .eq(SysNotifications::getIsRead, Boolean.FALSE));
        return sysNotifications != null && !sysNotifications.isEmpty();
    }

}
