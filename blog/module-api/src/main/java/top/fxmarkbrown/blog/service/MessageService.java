package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysMessage;

import java.util.List;


public interface MessageService {

    /**
     * 获取留言列表
     */
    List<SysMessage> getMessageList();

    /**
     * 添加留言
     */
    Boolean add(SysMessage sysMessage);
}
