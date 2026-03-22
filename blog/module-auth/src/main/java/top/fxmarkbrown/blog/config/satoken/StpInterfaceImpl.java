package top.fxmarkbrown.blog.config.satoken;

import cn.dev33.satoken.stp.StpInterface;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.enums.MenuTypeEnum;
import top.fxmarkbrown.blog.mapper.SysMenuMapper;
import top.fxmarkbrown.blog.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysMenuMapper menuMapper;

    private final SysRoleMapper roleMapper;


    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = resolveUserId(loginId);
        List<String> roles = roleMapper.selectRolesCodeByUserId(userId);

        if (roles.contains(Constants.ADMIN)) {
            return menuMapper.getPermissionList(MenuTypeEnum.BUTTON.getCode());
        }
        return menuMapper.getPermissionListByUserId(userId, MenuTypeEnum.BUTTON.getCode());
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleMapper.selectRolesCodeByUserId(resolveUserId(loginId));
    }

    private Long resolveUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(loginId));
    }
}
