package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.fxmarkbrown.blog.entity.FileDetail;

/**
 * 文件记录表 Mapper接口
 */
@Mapper
public interface FileDetailMapper extends BaseMapper<FileDetail> {
}
