package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysOperateLog;

/**
 *  服务接口
 */
public interface SysOperateLogService extends IService<SysOperateLog> {
    /**
     * 查询分页列表
     */
    IPage<SysOperateLog> listSysOperateLog(SysOperateLog sysOperateLog);
}