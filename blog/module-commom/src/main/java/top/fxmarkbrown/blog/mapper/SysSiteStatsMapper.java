package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import top.fxmarkbrown.blog.entity.SysSiteStats;

/**
 * 站点统计 Mapper
 */
@Mapper
public interface SysSiteStatsMapper extends BaseMapper<SysSiteStats> {

    /**
     * 原子递增站点统计
     */
    @Update("UPDATE sys_site_stats " +
            "SET blog_views_count = COALESCE(blog_views_count, 0) + #{blogViewsDelta}, " +
            "    unique_visitor_count = COALESCE(unique_visitor_count, 0) + #{uniqueVisitorDelta}, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int incrementStats(@Param("id") Long id,
                       @Param("blogViewsDelta") long blogViewsDelta,
                       @Param("uniqueVisitorDelta") long uniqueVisitorDelta);
}
