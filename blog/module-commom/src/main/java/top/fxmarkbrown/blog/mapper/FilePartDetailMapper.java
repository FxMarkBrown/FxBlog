package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.fxmarkbrown.blog.entity.FilePartDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件分片信息表，仅在手动分片上传时使用 Mapper接口
 */
@Mapper
public interface FilePartDetailMapper extends BaseMapper<FilePartDetail> {
}
