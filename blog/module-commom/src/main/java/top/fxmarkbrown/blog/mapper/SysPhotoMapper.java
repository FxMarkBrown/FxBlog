package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.fxmarkbrown.blog.entity.SysPhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysPhotoMapper extends BaseMapper<SysPhoto> {

    void move(@Param("ids") List<Long> ids, @Param("albumId") Long albumId);
}
