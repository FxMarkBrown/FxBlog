package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.fxmarkbrown.blog.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 留言 Mapper接口
 */
@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {
}