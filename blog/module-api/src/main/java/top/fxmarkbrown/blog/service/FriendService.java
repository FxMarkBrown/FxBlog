package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysFriend;

import java.util.List;

public interface FriendService {

    /**
     * 获取友情链接列表
     */
    List<SysFriend> getFriendList();

    /**
     * 申请友链
     */
    Boolean apply(SysFriend sysFriend);
}
