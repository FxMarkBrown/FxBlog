package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.fxmarkbrown.blog.entity.SysJobLog;

@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
    
    void cleanJobLog();
} 