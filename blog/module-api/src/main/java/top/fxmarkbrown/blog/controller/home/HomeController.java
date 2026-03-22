package top.fxmarkbrown.blog.controller.home;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysNotice;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.service.HomeService;
import top.fxmarkbrown.blog.vo.home.WeatherEffectVo;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/webConfig")
    @Operation(summary = "获取网站配置")
    public Result<SysWebConfig> getWebConfig() {
        return homeService.getWebConfig();
    }

    @GetMapping("/weather/effect")
    @Operation(summary = "获取天气氛围")
    public Result<WeatherEffectVo> getWeatherEffect() {
        return Result.success(homeService.getWeatherEffect());
    }

    @GetMapping("/getNotice")
    @Operation(summary = "获取公告")
    public Result<Map<String, List<SysNotice>>> getNotice() {
        return Result.success(homeService.getNotice());
    }

    @GetMapping("/report")
    @Operation(summary = "添加访问量")
    public Result<Void> report() {
        homeService.report();
        return Result.success();
    }
}
