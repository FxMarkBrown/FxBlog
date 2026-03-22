package top.fxmarkbrown.blog.config.mybatisplus;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.dto.user.LoginUserInfo;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        LoginUserInfo loginUserInfo = getCurrentUser();
        if (loginUserInfo != null) {
            this.strictInsertFill(metaObject, "createBy", String.class, loginUserInfo.getUsername());
            this.strictInsertFill(metaObject, "updateBy", String.class, loginUserInfo.getUsername());
        }
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LoginUserInfo loginUserInfo = getCurrentUser();
        if (loginUserInfo != null) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, loginUserInfo.getUsername());
        }
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    private LoginUserInfo getCurrentUser() {
        try {
            Object obj = StpUtil.getSession().get(Constants.CURRENT_USER);
            if (obj instanceof LoginUserInfo) {
                return (LoginUserInfo) obj;
            }
            return null;
        } catch (Exception e) {
            // 匿名链路或非登录上下文下允许自动填充静默退化。
            return null;
        }
    }
}