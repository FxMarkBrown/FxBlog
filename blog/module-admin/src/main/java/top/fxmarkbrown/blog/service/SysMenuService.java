package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysMenu;
import top.fxmarkbrown.blog.vo.menu.RouterVO;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    /**
     * 获取菜单树
     */
    List<SysMenu> getMenuTree();
    
    /**
     * 新增菜单
     */
    void addMenu(SysMenu menu);
    
    /**
     * 更新菜单
     */
    void updateMenu(SysMenu menu);
    
    /**
     * 删除菜单
     */
    void deleteMenu(Integer id);

    /**
     * 获取当前登录用户所拥有的菜单
     */
    List<RouterVO> getCurrentUserMenu();

}