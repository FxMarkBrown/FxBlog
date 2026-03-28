package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.entity.SysSiteVisitor;

/**
 * 站点访客指纹 Mapper
 */
@Mapper
public interface SysSiteVisitorMapper extends BaseMapper<SysSiteVisitor> {

    /**
     * 插入访客指纹，已存在则忽略
     */
    @Insert("INSERT INTO sys_site_visitor(visitor_key, create_time, update_time) " +
            "VALUES(#{visitorKey}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (visitor_key) DO NOTHING")
    int insertIgnore(@Param("visitorKey") String visitorKey);
}
