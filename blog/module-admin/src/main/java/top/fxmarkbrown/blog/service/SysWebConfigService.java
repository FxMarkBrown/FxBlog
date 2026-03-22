package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysWebConfig;

public interface SysWebConfigService extends IService<SysWebConfig> {

    void update(SysWebConfig sysWebConfig);
}
