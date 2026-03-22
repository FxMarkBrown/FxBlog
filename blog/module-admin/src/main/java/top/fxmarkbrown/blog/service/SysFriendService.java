package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysFriend;

/**
 * 友情链接 服务接口
 */
public interface SysFriendService extends IService<SysFriend> {
    /**
     * 查询友情链接分页列表
     */
    IPage<SysFriend> selectPage(SysFriend sysFriend);

    /**
     * 修改友情链接
     */
    boolean update(SysFriend sysFriend);

}