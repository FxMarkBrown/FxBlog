package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网站配置表 Mapper接口
 */
@Mapper
public interface SysWebConfigMapper extends BaseMapper<SysWebConfig> {
}