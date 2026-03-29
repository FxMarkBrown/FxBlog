package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.entity.SysPhoto;

import java.util.List;

@Mapper
public interface SysPhotoMapper extends BaseMapper<SysPhoto> {

    void move(@Param("ids") List<Long> ids, @Param("albumId") Long albumId);
}
