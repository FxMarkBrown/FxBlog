package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.NonNull;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.config.properties.*;
import top.fxmarkbrown.blog.dto.Captcha;
import top.fxmarkbrown.blog.dto.EmailRegisterDto;
import top.fxmarkbrown.blog.dto.LoginDTO;
import top.fxmarkbrown.blog.dto.user.LoginUserInfo;
import top.fxmarkbrown.blog.entity.SysConfig;
import top.fxmarkbrown.blog.entity.SysRole;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.enums.LoginTypeEnum;
import top.fxmarkbrown.blog.enums.MenuTypeEnum;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysConfigMapper;
import top.fxmarkbrown.blog.mapper.SysMenuMapper;
import top.fxmarkbrown.blog.mapper.SysRoleMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.AuthService;
import top.fxmarkbrown.blog.service.WebConfigCacheService;
import top.fxmarkbrown.blog.utils.*;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.*;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {


    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final SysMenuMapper menuMapper;

    private final EmailUtil emailUtil;

    private final RedisUtil redisUtil;

    private final SysUserMapper sysUserMapper;

    private final String[] avatarList = {
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Raccoon",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Kitty",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Puppy",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Bunny",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Fox"
    };
    private final SysRoleMapper sysRoleMapper;

    private final GiteeConfigProperties giteeConfigProperties;

    private final GithubConfigProperties githubConfigProperties;

    private final QqConfigProperties qqConfigProperties;

    private final WeiboConfigProperties weiboConfigProperties;

    private final WechatProperties wechatProperties;

    private final SysConfigMapper sysConfigMapper;

    private final WebConfigCacheService webConfigCacheService;

    @Value("${app.url}")
    private String appUrl;

    @Override
    public LoginUserInfo login(LoginDTO loginDTO) {

        SysConfig verifySwitch = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, "slider_verify_switch"));
        if (verifySwitch != null && verifySwitch.getConfigValue().equals("Y")) {
            //校验验证码
            CaptchaUtil.checkImageCode(loginDTO.getNonceStr(), loginDTO.getValue());
        }


        // 查询用户
        SysUser user = userMapper.selectByUsername(loginDTO.getUsername());

        //校验是否能够登录
        validateLogin(loginDTO, user);

        // 执行登录
        StpUtil.login(user.getId());
        String tokenValue = StpUtil.getTokenValue();

        // 返回用户信息
        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);
        loginUserInfo.setToken(tokenValue);

        StpUtil.getSession().set(Constants.CURRENT_USER, loginUserInfo);
        return loginUserInfo;
    }

    private static void validateLogin(LoginDTO loginDTO, SysUser user) {
        if (user == null) {
            throw new ServiceException("登录用户不存在");
        }

        // 验证密码
        if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            throw new ServiceException("用户名或密码错误");
        }

        // 验证状态
        if (user.getStatus() != 1) {
            throw new ServiceException("账号已被禁用");
        }
    }

    @Override
    public LoginUserInfo getLoginUserInfo(String source) {
        // 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);
        List<String> roles = roleMapper.selectRolesCodeByUserId(userId);
        loginUserInfo.setRoles(roles);

        if (source.equalsIgnoreCase(Constants.ADMIN)) {
            List<String> permissions;
            if (roles.contains(Constants.ADMIN)) {
                permissions = menuMapper.getPermissionList(MenuTypeEnum.BUTTON.getCode());
            } else {
                permissions = menuMapper.getPermissionListByUserId(userId, MenuTypeEnum.BUTTON.getCode());
            }
            loginUserInfo.setPermissions(permissions);
        }

        return loginUserInfo;
    }

    @Override
    public Boolean sendEmailCode(String email) throws MessagingException {
        emailUtil.sendCode(email);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean register(EmailRegisterDto dto) {

        validateEmailCode(dto);

        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getEmail()));
        if (sysUser != null) {
            throw new ServiceException("当前邮箱已注册，请前往登录");
        }

        //获取随机头像
        String avatar = avatarList[(int) (Math.random() * avatarList.length)];
        sysUser = SysUser.builder()
                .username(dto.getEmail())
                .password(BCrypt.hashpw(dto.getPassword()))
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .avatar(avatar)
                .status(Constants.YES)
                .build();
        sysUserMapper.insert(sysUser);

        //添加用户角色信息
        insertRole(sysUser);

        redisUtil.delete(RedisConstants.CAPTCHA_CODE_KEY + dto.getEmail());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean forgot(EmailRegisterDto dto) {
        validateEmailCode(dto);
        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getEmail()));
        if (sysUser == null) {
            throw new ServiceException("当前邮箱未注册，请前往注册");
        }
        sysUser.setPassword(BCrypt.hashpw(dto.getPassword()));
        sysUserMapper.updateById(sysUser);
        redisUtil.delete(RedisConstants.CAPTCHA_CODE_KEY + dto.getEmail());
        return true;
    }

    @Override
    public String getWechatLoginCode() {
        validateWechatLoginAvailable();
        //随机获取4位数字
        String code = "DL" + (int) ((Math.random() * 9 + 1) * 1000);
        redisUtil.set(RedisConstants.WX_LOGIN_USER_CODE + code, "NOT-LOGIN", RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);
        return code;
    }

    @Override
    public LoginUserInfo getWechatIsLogin(String loginCode) {
        validateWechatLoginAvailable();
        Object value = redisUtil.get(RedisConstants.WX_LOGIN_USER + loginCode);

        if (value == null) {
            throw new ServiceException("登录失败");
        }

        LoginUserInfo loginUserInfo = JsonUtil.convertValue(value, LoginUserInfo.class);

        StpUtil.login(loginUserInfo.getId());
        loginUserInfo.setToken(StpUtil.getTokenValue());

        return loginUserInfo;
    }

    @Override
    public String wechatLogin(WxMpXmlMessage message) {
        if (!isThirdPartyLoginEnabled(LoginTypeEnum.WECHAT.getType())) {
            return "当前登录方式未开放";
        }
        if (!isWechatConfigComplete()) {
            return "当前登录方式暂未配置";
        }
        String code = message.getContent().toUpperCase();
        //先判断登录码是否已过期
        Object e = redisUtil.hasKey(RedisConstants.WX_LOGIN_USER_CODE + code);
        if (e == null) {
            return "验证码已过期";
        }
        LoginUserInfo loginUserInfo = wechatLogin(message.getFromUser());
        //修改redis缓存 以便监听是否已经授权成功
        redisUtil.set(RedisConstants.WX_LOGIN_USER + code, JsonUtil.toJsonString(loginUserInfo), RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);
        return "网站登录成功！(若页面长时间未跳转请刷新验证码)";
    }

    @Override
    public String renderAuth(String source) {
        validateOauthLoginAvailable(source);
        AuthRequest authRequest = getAuthRequest(source);
        return authRequest.authorize(AuthStateUtils.createState());
    }


    @Override
    public void authLogin(AuthCallback callback,String source, HttpServletResponse httpServletResponse) throws IOException {
        if (!isThirdPartyLoginEnabled(source) || !isOauthConfigComplete(source)) {
            log.warn("未开放或未配置的第三方登录回调: {}", source);
            httpServletResponse.sendRedirect(appUrl);
            return;
        }
        AuthRequest authRequest = getAuthRequest(source);
        AuthResponse<AuthUser> response = authRequest.login(callback);

        if (response.getData() == null) {
            log.info("用户取消了 {} 第三方登录",source);
            httpServletResponse.sendRedirect(appUrl);
            return;
        }
        AuthUser authUser = response.getData();
        String result = JsonUtil.toJsonString(authUser);
        log.info("第三方登录验证结果:{}", result);
        Object uuid = authUser.getUuid();
        // 获取用户ip信息
        String ipAddress = IpUtil.getIp();
        String ipSource = IpUtil.getIp2region(ipAddress);
        // 判断是否已注册
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, uuid));
        if (ObjectUtils.isEmpty(user)) {
            // 保存账号信息
            user = SysUser.builder()
                    .username(uuid.toString())
                    .password(UUID.randomUUID().toString())
                    .loginType(source)
                    .lastLoginTime(LocalDateTime.now())
                    .ipLocation(ipAddress)
                    .ip(ipSource)
                    .status(Constants.YES)
                    .nickname(source + "-" +getRandomString(6))
                    .avatar(authUser.getAvatar())
                    .build();
            userMapper.insert(user);
            //添加角色
            insertRole(user);
        }

        StpUtil.login(user.getId());
        httpServletResponse.sendRedirect(appUrl + "/?token=" + StpUtil.getTokenValue());
    }

    @Override
    public LoginUserInfo appletLogin(String code) {
        validateAppletLoginAvailable();
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wechatProperties.getAppletAppId()
                + "&secret=" + wechatProperties.getAppletSecret() + "&js_code=" + code + "&grant_type=authorization_code";
        String result = HttpUtil.get(url);
        JsonNode jsonObject = JsonUtil.readTree(result);
        JsonNode openidNode = jsonObject == null ? null : jsonObject.get("openid");
        if (openidNode == null || openidNode.isNull() || !StringUtils.hasText(openidNode.asText())) {
            throw new ServiceException("登录失败");
        }
        String openid = openidNode.asText();

        // 查询用户
        SysUser user = userMapper.selectByUsername(openid);

        if (user == null) {
            String ip = IpUtil.getIp();
            String avatar = avatarList[(int) (Math.random() * avatarList.length)];
            user = SysUser.builder()
                    .username(openid)
                    .password(UUID.randomUUID().toString())
                    .loginType(LoginTypeEnum.APPLET.getType())
                    .lastLoginTime(LocalDateTime.now())
                    .ipLocation(IpUtil.getIp2region(ip))
                    .ip(ip)
                    .status(Constants.YES)
                    .nickname("applet-" + getRandomString(6))
                    .avatar(avatar)
                    .build();
            userMapper.insert(user);
            //添加用户角色信息
            this.insertRole(user);
        }else {
            if (user.getStatus() == Constants.NO) {
                throw new ServiceException("账号已被禁用，请联系管理员");
            }
        }

        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);

        StpUtil.login(loginUserInfo.getId());
        loginUserInfo.setToken(StpUtil.getTokenValue());

        return loginUserInfo;
    }

    @Override
    public Captcha getCaptcha() {
        Captcha captcha = new Captcha();
        CaptchaUtil.getCaptcha(captcha);
        return captcha;
    }

    private void validateOauthLoginAvailable(String source) {
        if (!isThirdPartyLoginEnabled(source)) {
            throw new ServiceException("当前登录方式未开放");
        }
        if (!isOauthConfigComplete(source)) {
            throw new ServiceException("当前登录方式暂未配置");
        }
    }

    private void validateWechatLoginAvailable() {
        if (!isThirdPartyLoginEnabled(LoginTypeEnum.WECHAT.getType())) {
            throw new ServiceException("当前登录方式未开放");
        }
        if (!isWechatConfigComplete()) {
            throw new ServiceException("当前登录方式暂未配置");
        }
    }

    private void validateAppletLoginAvailable() {
        if (!isThirdPartyLoginEnabled(LoginTypeEnum.WECHAT.getType())) {
            throw new ServiceException("当前登录方式未开放");
        }
        if (!isAppletConfigComplete()) {
            throw new ServiceException("当前登录方式暂未配置");
        }
    }

    private boolean isThirdPartyLoginEnabled(String source) {
        return getEnabledLoginTypes().contains(normalizeLoginType(source));
    }

    private Set<String> getEnabledLoginTypes() {
        SysWebConfig config = getCurrentWebConfig();
        if (config == null || !StringUtils.hasText(config.getLoginTypeList())) {
            return Collections.emptySet();
        }
        try {
            List<String> loginTypes = JsonUtil.readValue(config.getLoginTypeList(), new TypeReference<>() {
            });
            if (loginTypes == null) {
                return Collections.emptySet();
            }
            Set<String> enabledTypes = new LinkedHashSet<>();
            for (String loginType : loginTypes) {
                if (StringUtils.hasText(loginType)) {
                    enabledTypes.add(normalizeLoginType(loginType));
                }
            }
            return enabledTypes;
        } catch (Exception e) {
            log.warn("解析登录方式配置失败: {}", config.getLoginTypeList(), e);
            return Collections.emptySet();
        }
    }

    private SysWebConfig getCurrentWebConfig() {
        return webConfigCacheService.getCurrentWebConfig();
    }

    private String normalizeLoginType(String source) {
        return source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isWechatConfigComplete() {
        return isConfiguredValue(wechatProperties.getAppId())
                && isConfiguredValue(wechatProperties.getSecret())
                && isConfiguredValue(wechatProperties.getToken())
                && isConfiguredValue(wechatProperties.getAesKey());
    }

    private boolean isAppletConfigComplete() {
        return isConfiguredValue(wechatProperties.getAppletAppId())
                && isConfiguredValue(wechatProperties.getAppletSecret());
    }

    private boolean isOauthConfigComplete(String source) {
        return switch (normalizeLoginType(source)) {
            case "gitee" -> isConfiguredValue(giteeConfigProperties.getAppId())
                    && isConfiguredValue(giteeConfigProperties.getAppSecret())
                    && isConfiguredValue(giteeConfigProperties.getRedirectUrl());
            case "qq" -> isConfiguredValue(qqConfigProperties.getAppId())
                    && isConfiguredValue(qqConfigProperties.getAppSecret())
                    && isConfiguredValue(qqConfigProperties.getRedirectUrl());
            case "weibo" -> isConfiguredValue(weiboConfigProperties.getAppId())
                    && isConfiguredValue(weiboConfigProperties.getAppSecret())
                    && isConfiguredValue(weiboConfigProperties.getRedirectUrl());
            case "github" -> isConfiguredValue(githubConfigProperties.getAppId())
                    && isConfiguredValue(githubConfigProperties.getAppSecret())
                    && isConfiguredValue(githubConfigProperties.getRedirectUrl());
            default -> false;
        };
    }

    private boolean isConfiguredValue(String value) {
        return StringUtils.hasText(value) && !value.trim().startsWith("<");
    }

    private void validateEmailCode(EmailRegisterDto dto) {
        Object code = redisUtil.get(RedisConstants.CAPTCHA_CODE_KEY + dto.getEmail());
        if (code == null || !code.equals(dto.getCode())) {
            throw new ServiceException("验证码已过期或输入错误");
        }
    }

    private LoginUserInfo wechatLogin(String openId) {

        SysUser user = userMapper.selectByUsername(openId);
        if (ObjectUtils.isEmpty(user)) {
            String ip = IpUtil.getIp();
            String ipSource = IpUtil.getIp2region(ip);

            // 保存账号信息
            user = SysUser.builder()
                    .username(openId)
                    .password(BCrypt.hashpw(openId))
                    .nickname("WECHAT-" + getRandomString(6))
                    .avatar(avatarList[(int) (Math.random() * avatarList.length)])
                    .loginType(LoginTypeEnum.WECHAT.getType())
                    .lastLoginTime(LocalDateTime.now())
                    .ip(ip)
                    .ipLocation(ipSource)
                    .status(Constants.YES)
                    .build();
            userMapper.insert(user);

            //添加用户角色信息
            this.insertRole(user);
        }

        return BeanCopyUtil.copyObj(user, LoginUserInfo.class);
    }

    /**
     * 添加用户角色信息
     */
    private void insertRole(SysUser user) {
        SysRole sysRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, Constants.USER));
        sysRoleMapper.addRoleUser(user.getId(), Collections.singletonList(sysRole.getId()));
    }

    /**
     * 随机生成6位数的字符串
     */
    public static String getRandomString(int length) {
        String str = "QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }


    private @NonNull AuthRequest getAuthRequest(String source) {
        String normalizedSource = normalizeLoginType(source);
        AuthRequest authRequest = null;
        switch (normalizedSource) {
            case "gitee":
                authRequest = new AuthGiteeRequest(AuthConfig.builder()
                        .clientId(giteeConfigProperties.getAppId())
                        .clientSecret(giteeConfigProperties.getAppSecret())
                        .redirectUri(giteeConfigProperties.getRedirectUrl())
                        .build());
                break;
            case "qq":
                authRequest = new AuthQqRequest(AuthConfig.builder()
                        .clientId(qqConfigProperties.getAppId())
                        .clientSecret(qqConfigProperties.getAppSecret())
                        .redirectUri(qqConfigProperties.getRedirectUrl())
                        .build());
                break;
            case "weibo":
                authRequest = new AuthWeiboRequest(AuthConfig.builder()
                        .clientId(weiboConfigProperties.getAppId())
                        .clientSecret(weiboConfigProperties.getAppSecret())
                        .redirectUri(weiboConfigProperties.getRedirectUrl())
                        .build());
                break;
            case "github":
                authRequest = new AuthGithubRequest(AuthConfig.builder()
                        .clientId(githubConfigProperties.getAppId())
                        .clientSecret(githubConfigProperties.getAppSecret())
                        .redirectUri(githubConfigProperties.getRedirectUrl())
                        .build());
                break;
            default:
                break;
        }
        if (null == authRequest) {
            throw new AuthException("授权地址无效");
        }
        return authRequest;
    }

}
