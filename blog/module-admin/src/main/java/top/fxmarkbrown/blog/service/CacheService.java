package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.vo.cache.CacheInfoVo;
import top.fxmarkbrown.blog.vo.cache.CacheKeyQuery;
import top.fxmarkbrown.blog.vo.cache.CacheKeyVo;
import top.fxmarkbrown.blog.vo.cache.CacheMemoryVo;

public interface CacheService {
    
    /**
     * 获取缓存基本信息
     */
    CacheInfoVo getCacheInfo();
    
    /**
     * 获取内存信息
     */
    CacheMemoryVo getMemoryInfo();
    
    /**
     * 获取缓存键列表
     */
    IPage<CacheKeyVo> getKeyList(CacheKeyQuery query);
    
    /**
     * 清空缓存
     */
    void clearCache();
} 