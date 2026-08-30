package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysSeries;

import java.util.List;

/**
 * 系列表 服务类
 */
public interface SysSeriesService extends IService<SysSeries> {

    /**
     * 查询系列表分页列表
     */
    IPage<SysSeries> selectPage(SysSeries sysSeries);

    /**
     * 查询系列表列表
     */
    List<SysSeries> selectList(SysSeries sysSeries);

    /**
     * 新增系列表
     */
    boolean insert(SysSeries sysSeries);

    /**
     * 修改系列表
     */
    boolean update(SysSeries sysSeries);

    /**
     * 批量删除系列表
     */
    boolean deleteByIds(List<Integer> ids);
}
