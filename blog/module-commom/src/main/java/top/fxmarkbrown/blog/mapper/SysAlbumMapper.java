package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.fxmarkbrown.blog.entity.SysAlbum;

import java.util.List;

/**
 * 相册 Mapper接口
 */
@Mapper
public interface SysAlbumMapper extends BaseMapper<SysAlbum> {

    List<SysAlbum> getAlbumList();

}
