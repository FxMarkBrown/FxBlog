package top.fxmarkbrown.blog.config.satoken;

import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.utils.IpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.RedisUtil;
import top.fxmarkbrown.blog.utils.UserAgentUtil;
import top.fxmarkbrown.blog.vo.user.OnlineUserVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 自定义侦听器的实现
 */
@Component
@RequiredArgsConstructor
public class MySaTokenListener implements SaTokenListener {

    private final SysUserMapper userMapper;

    private final HttpServletRequest request;

    private final RedisUtil redisUtil;

    @Value("${sa-token.timeout}")
    private Integer timeout;

    /**
     * 每次登录时触发
     */
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        Long userId = resolveLoginUserId(loginId);
        if (userId == null) {
            return;
        }
        String ip = IpUtil.getIp();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        // 更新登录信息
        String userAgent = request.getHeader("User-Agent");
        user.setLastLoginTime(LocalDateTime.now());
        user.setIp(ip);
        user.setIpLocation(IpUtil.getIp2region(ip));
        user.setOs(UserAgentUtil.getOs(userAgent));
        user.setBrowser(UserAgentUtil.getBrowser(userAgent));
        userMapper.updateById(user);

        OnlineUserVo onlineUserVo = new OnlineUserVo();
        BeanUtils.copyProperties(user, onlineUserVo);
        onlineUserVo.setTokenValue(tokenValue);
        onlineUserVo.setPassword("");
        redisUtil.set(RedisConstants.LOGIN_TOKEN + tokenValue, JsonUtil.toJsonString(onlineUserVo), timeout, TimeUnit.SECONDS);
    }

    private Long resolveLoginUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        if (loginId == null) {
            return null;
        }
        return Long.parseLong(loginId.toString());
    }

    /**
     * 每次注销时触发
     */
    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        redisUtil.delete(RedisConstants.LOGIN_TOKEN + tokenValue);
    }

    /**
     * 每次被踢下线时触发
     */
    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        redisUtil.delete(RedisConstants.LOGIN_TOKEN + tokenValue);
    }

    /**
     * 每次被顶下线时触发
     */
    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        redisUtil.delete(RedisConstants.LOGIN_TOKEN + tokenValue);
    }

    /**
     * 每次被封禁时触发
     */
    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
    }

    /**
     * 每次被解封时触发
     */
    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
    }

    /**
     * 每次二级认证时触发
     */
    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
    }

    /**
     * 每次退出二级认证时触发
     */
    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
    }

    /**
     * 每次创建Session时触发
     */
    @Override
    public void doCreateSession(String id) {
    }

    /**
     * 每次注销Session时触发
     */
    @Override
    public void doLogoutSession(String id) {
    }

    /**
     * 每次Token续期时触发
     */
    @Override
    public void doRenewTimeout(String loginType, Object loginId, String tokenValue, long timeout) {
    }
}
