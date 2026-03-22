package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.dto.user.SysUserAddAndUpdateDto;
import top.fxmarkbrown.blog.dto.user.UpdatePwdDTO;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.vo.user.OnlineUserVo;
import top.fxmarkbrown.blog.vo.user.SysUserProfileVo;
import top.fxmarkbrown.blog.vo.user.SysUserVo;

import java.util.List;

public interface SysUserService extends IService<SysUser> {
    /**
     * 分页查询用户
     */
    IPage<SysUserVo> listUsers(SysUser sysUser);

    /**
     * 新增用户
     */
    void add(SysUserAddAndUpdateDto user);

    /**
     * 更新用户
     */
    void update(SysUserAddAndUpdateDto user);

    /**
     * 删除用户
     */
    void delete(List<Long> ids);


    /**
     * 修改密码
     *
     * @param updatePwdDTO 修改密码参数
     */
    void updatePwd(UpdatePwdDTO updatePwdDTO);

    /**
     * 获取个人信息
     */
    SysUserProfileVo profile();

    /**
     * 修改个人信息
     */
    void updateProfile(SysUser user);

    /**
     * 锁屏界面验证密码
     */
    Boolean verifyPassword(String password);

    /**
     * 重置密码
     */
    Boolean resetPassword(SysUser user);

    /**
     * 获取在线用户列表
     */
    IPage<OnlineUserVo> getOnlineUserList(String username);


}
