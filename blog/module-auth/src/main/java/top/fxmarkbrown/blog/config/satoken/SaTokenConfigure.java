package top.fxmarkbrown.blog.config.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.fxmarkbrown.blog.entity.SysFileOss;
import top.fxmarkbrown.blog.enums.FileOssEnum;
import top.fxmarkbrown.blog.mapper.SysFileOssMapper;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfigure implements WebMvcConfigurer {

    private final SysFileOssMapper sysFileOssMapper;

    // 注册拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> excludes = new ArrayList<>(List.of(
                "/auth/login",
                "/auth/logout",
                "/auth/verify",
                "/swagger-ui/**",
                "/webjars/**",
                "/v3/api-docs/**",
                "/doc.html",
                "/favicon.ico",
                "/swagger-resources",
                "/api/**",
                "/wechat/**",
                "/static/**"
        ));
        SysFileOss localConfig = sysFileOssMapper.selectOne(new LambdaQueryWrapper<SysFileOss>()
                .eq(SysFileOss::getPlatform, FileOssEnum.LOCAL.getValue()));
        if (localConfig != null && StringUtils.hasText(localConfig.getPathPatterns())) {
            excludes.add(localConfig.getPathPatterns());
        }
        // 注册 Sa-Token 拦截器，定义详细的拦截路由
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(excludes);
    }
}
