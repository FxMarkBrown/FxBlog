package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysWebConfig;

public interface WebConfigCacheService {

    /**
     * 获取当前网站配置
     */
    SysWebConfig getCurrentWebConfig();
}
