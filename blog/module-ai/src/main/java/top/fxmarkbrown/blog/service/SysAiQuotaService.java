package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.vo.ai.AiQuotaAdminLogVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaAdminVo;

public interface SysAiQuotaService {

    /**
     * 分页查询后台用户额度概览。
     */
    IPage<AiQuotaAdminVo> pageQuota(String userKeyword);

    /**
     * 分页查询后台额度变动流水。
     */
    IPage<AiQuotaAdminLogVo> pageQuotaLogs(String bizType, String userKeyword);
}
