package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.entity.SysMenu;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    /**
     * 获取用户菜单列表
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 获取当前登录用户所拥有的权限
     */
    List<String> getPermissionListByUserId(@Param("userId") Long userId,@Param("type") String type);

    /**
     * 获取所有权限
     */
    List<String> getPermissionList(String type);

    /**
     * 根据用户id获取菜单
     */
    List<SysMenu> getMenusByUserId(@Param("userId") Long userId,@Param("type") String type);
}
