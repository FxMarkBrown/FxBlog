package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysMessage;

public interface SysMessageService extends IService<SysMessage> {

    /**
     * 获取消息列表
     */
    Page<SysMessage> selectList();

}
