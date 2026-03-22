package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysMoment;

/**
 * 说说 服务接口
 */
public interface SysMomentService extends IService<SysMoment> {
    /**
     * 查询说说分页列表
     */
    IPage<SysMoment> selectPage(SysMoment sysMoment);

    Object add(SysMoment sysMoment);

}
