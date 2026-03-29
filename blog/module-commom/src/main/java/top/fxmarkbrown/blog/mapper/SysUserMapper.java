package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.vo.user.SysUserVo;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    SysUser selectByUsername(@Param("username") String username);

    SysUser selectPublicProfileByUsername(@Param("username") String username);

    IPage<SysUserVo> selectUserPage(@Param("page") Page<Object> page, @Param("sysUser") SysUser sysUser);
}
