package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.fxmarkbrown.blog.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
    
    void cleanJobLog();
} 