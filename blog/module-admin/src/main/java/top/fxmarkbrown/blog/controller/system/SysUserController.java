package top.fxmarkbrown.blog.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.annotation.OperationLogger;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.user.SysUserAddAndUpdateDto;
import top.fxmarkbrown.blog.dto.user.UpdatePwdDTO;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.service.SysUserService;
import top.fxmarkbrown.blog.vo.user.SysUserProfileVo;
import top.fxmarkbrown.blog.vo.user.SysUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
@Tag(name = "用户管理")
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping
    @Operation(summary = "获取用户列表")
    public Result<IPage<SysUserVo>> listUsers(SysUser sysUser) {
        return Result.success(sysUserService.listUsers(sysUser));
    }

    @PostMapping
    @OperationLogger("新增用户")
    @Operation(summary = "新增用户")
    @SaCheckPermission("sys:user:add")
    public Result<Void> addUser(@RequestBody SysUserAddAndUpdateDto sysUserAddDto) {
        sysUserService.add(sysUserAddDto);
        return Result.success();
    }

    @PutMapping
    @OperationLogger("修改用户")
    @Operation(summary = "修改用户")
    @SaCheckPermission("sys:user:update")
    public Result<Void> update(@RequestBody SysUserAddAndUpdateDto user) {
        sysUserService.update(user);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @OperationLogger("批量删除用户")
    @Operation(summary = "批量删除用户")
    @SaCheckPermission("sys:user:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysUserService.delete(ids);
        return Result.success();
    }

    @PutMapping("/updatePwd")
    @Operation(summary = "修改密码")
    @SaCheckLogin
    public Result<Void> updatePwd(@RequestBody UpdatePwdDTO updatePwdDTO) {
        sysUserService.updatePwd(updatePwdDTO);
        return Result.success();
    }

    @GetMapping("/profile")
    @Operation(summary = "获取个人信息")
    public Result<SysUserProfileVo> profile() {
        return Result.success(sysUserService.profile());
    }

    @PutMapping("/updProfile")
    @OperationLogger("修改个人信息")
    @Operation(summary = "修改个人信息")
    @SaCheckLogin
    public Result<SysUserProfileVo> updateProfile(@RequestBody SysUser user) {
        sysUserService.updateProfile(user);
        return Result.success();
    }

    @SaCheckLogin
    @PostMapping("/verifyPassword")
    @Operation(summary = "锁屏界面验证密码")
    public Result<Boolean> verifyPassword(@RequestBody Map<String, String> params) {
        return Result.success(sysUserService.verifyPassword(params.get("password")));
    }

    @PutMapping("/reset")
    @OperationLogger("重置密码")
    @Operation(summary = "重置密码")
    @SaCheckPermission("sys:user:reset")
    public Result<Boolean> resetPassword(@RequestBody SysUser user) {
        return Result.success(sysUserService.resetPassword(user));
    }
}
