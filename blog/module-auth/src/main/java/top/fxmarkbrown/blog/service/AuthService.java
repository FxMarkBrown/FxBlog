package top.fxmarkbrown.blog.service;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.zhyd.oauth.model.AuthCallback;
import top.fxmarkbrown.blog.dto.Captcha;
import top.fxmarkbrown.blog.dto.EmailRegisterDto;
import top.fxmarkbrown.blog.dto.LoginDTO;
import top.fxmarkbrown.blog.dto.user.LoginUserInfo;

import java.io.IOException;

public interface AuthService {

    /**
     * 用户登录
     */
    LoginUserInfo login(LoginDTO loginDTO);

    /**
     * 获取当前登录用户信息
     */
    LoginUserInfo getLoginUserInfo(String source);

    /**
     * 发送注册邮箱验证码
     */
    Boolean sendEmailCode(String email) throws MessagingException;

    /**
     * 邮箱账号注册
     *
     */
    Boolean register(EmailRegisterDto dto);

    /**
     * 邮箱账号重置密码
     *
     */
    Boolean forgot(EmailRegisterDto dto);

    /**
     * 获取微信扫码登录验证码
     *
     */
    String getWechatLoginCode();


    /**
     * 验证微信是否扫码登录
     *
     */
    LoginUserInfo getWechatIsLogin(String loginCode);

    /**
     * 微信公众号登录
     *
     */
    String wechatLogin(WxMpXmlMessage message);

    /**
     * 获取第三方授权地址
     *
     */
    String renderAuth(String source);

    /**
     * 第三方授权登录
     *
     */
    void authLogin(AuthCallback callback, String source, HttpServletResponse httpServletResponse) throws IOException;

    /**
     * 小程序登录
     *
     */
    LoginUserInfo appletLogin(String code);

    /**
     * 获取滑块验证码
     *
     */
    Captcha getCaptcha();
}
