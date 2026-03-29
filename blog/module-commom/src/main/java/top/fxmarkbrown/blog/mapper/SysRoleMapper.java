package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.entity.SysRole;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    List<Integer> getRoleMenus(Integer id);

    void deleteMenuByRoleId(@Param("ids") List<Integer> ids);

    void insertRoleMenus(@Param("id") Integer id, @Param("menuIds") List<Integer> menuIds);

    List<String> selectRolesByUserId(Long userId);

    List<String> selectRolesCodeByUserId(Long loginId);

    void deleteRoleByUserId(@Param("userIds") List<Long> userId);

    void addRoleUser(@Param("userId") Long userId, @Param("roleIds") List<Integer> roleIds);

}
