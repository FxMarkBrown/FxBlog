package top.fxmarkbrown.blog.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.Captcha;
import top.fxmarkbrown.blog.dto.EmailRegisterDto;
import top.fxmarkbrown.blog.dto.LoginDTO;
import top.fxmarkbrown.blog.dto.user.LoginUserInfo;
import top.fxmarkbrown.blog.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.model.AuthCallback;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Tag(name = "认证管理")
public class AuthController {

    private final AuthService authService;

    @RequestMapping("/api/auth/render/{source}")
    @Operation(summary = "获取第三方授权地址")
    public Result<String> renderAuth(HttpServletResponse response, @PathVariable String source) {
        return Result.success(authService.renderAuth(source));
    }

    @RequestMapping("/api/auth/callback/{source}")
    public void login(AuthCallback callback, @PathVariable String source, HttpServletResponse httpServletResponse) throws IOException {
        authService.authLogin(callback, source, httpServletResponse);
    }


    @Operation(summary = "用户登录")
    @PostMapping("/auth/login")
    public Result<LoginUserInfo> login(@Validated @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    @SaIgnore
    @Operation(summary = "获取滑块验证码")
    @GetMapping("/auth/getCaptcha")
    public Result<Captcha> getCaptcha() {
        return Result.success(authService.getCaptcha());
    }

    @Operation(summary = "用户登出")
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success(null);
    }

    @Operation(summary = "发送注册邮箱验证码")
    @GetMapping("/api/sendEmailCode")
    public Result<Boolean> sendEmailCode(String email) throws MessagingException {
        return Result.success(authService.sendEmailCode(email));
    }

    @Operation(summary = "邮箱账号注册")
    @PostMapping("/api/email/register")
    public Result<Boolean> register(@RequestBody EmailRegisterDto dto) {
        return Result.success(authService.register(dto));
    }

    @Operation(summary = "根据邮箱修改密码")
    @PostMapping("/api/email/forgot")
    public Result<Boolean> forgot(@RequestBody EmailRegisterDto dto) {
        return Result.success(authService.forgot(dto));
    }

    @Operation(summary = "获取微信扫码登录验证码")
    @GetMapping("/api/wechat/getCode")
    public Result<String> getWechatLoginCode() {
        return Result.success(authService.getWechatLoginCode());
    }

    @Operation(summary = "获取微信扫码登录验证码")
    @GetMapping("/api/wechat/isLogin/{loginCode}")
    public Result<LoginUserInfo> getWechatIsLogin(@PathVariable String loginCode) {
        return Result.success(authService.getWechatIsLogin(loginCode));
    }

    @Operation(summary = "微信小程序登录")
    @GetMapping("/api/wechat/appletLogin/{code}")
    public Result<LoginUserInfo> appletLogin(@PathVariable String code) {
        return Result.success(authService.appletLogin(code));
    }

    @GetMapping("/auth/info")
    public Result<LoginUserInfo> getUserInfo(@RequestParam(defaultValue = "admin") String source) {
        return Result.success(authService.getLoginUserInfo(source));
    }

}
