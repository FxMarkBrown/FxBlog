package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysDictData;

import java.util.List;
import java.util.Map;

/**
 * 字典数据表 服务接口
 */
public interface SysDictDataService extends IService<SysDictData> {
    /**
     * 查询字典数据分页列表
     */
    IPage<SysDictData> listDictData(Long dictId);

    /**
     * 新增字典数据
     */
    void addDictData(SysDictData sysDictData);

    /**
     * 修改字典数据
     */
    void updateDictData(SysDictData sysDictData);

    /**
     * 根据字典类型查询字典数据
     */
    Map<String, Map<String, Object>> getDictDataByDictType(List<String> dictTypes);

    /**
     * 根据字典类型获取字典数据-缓存版
     */
    List<SysDictData> selectDataByDictTypeCache(String dictType);
}
