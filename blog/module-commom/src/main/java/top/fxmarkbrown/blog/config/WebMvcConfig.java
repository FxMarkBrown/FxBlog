package top.fxmarkbrown.blog.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.fxmarkbrown.blog.config.interceptor.ApiAccessLogInterceptor;
import top.fxmarkbrown.blog.entity.SysFileOss;
import top.fxmarkbrown.blog.enums.FileOssEnum;
import top.fxmarkbrown.blog.mapper.SysFileOssMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableWebMvc
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SysFileOssMapper sysFileOssMapper;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        SysFileOss sysFileOss = sysFileOssMapper.selectOne(new LambdaQueryWrapper<SysFileOss>()
                .eq(SysFileOss::getPlatform, FileOssEnum.LOCAL.getValue()));

        if (sysFileOss != null) {
            registry.addResourceHandler(sysFileOss.getPathPatterns())
                    .addResourceLocations("file:" + ensureDirectoryPath(sysFileOss.getStoragePath()));
        }
    }

    private String ensureDirectoryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim().replace("\\", "/");
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiAccessLogInterceptor());
    }

    /**
     * 注册跨域信息
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // 允许所有跨域地址
                .allowedHeaders("*")
                .allowedMethods("*")
                .maxAge(3600);
    }

}
