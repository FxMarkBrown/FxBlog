package top.fxmarkbrown.blog.controller.site;

import cn.dev33.satoken.annotation.SaCheckPermission;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.service.SysWebConfigService;
import top.fxmarkbrown.blog.service.WebConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "网站配置管理")
@RequestMapping("/sys/web")
@RequiredArgsConstructor
public class SysWebConfigController {

    private final SysWebConfigService sysWebConfigService;
    private final WebConfigCacheService webConfigCacheService;

    @GetMapping("/config")
    @Operation(summary = "获取网站配置")
    public Result<SysWebConfig> getWebConfig() {
        return Result.success(webConfigCacheService.getCurrentWebConfig());
    }

    @PutMapping("/update")
    @Operation(summary = "修改网站配置")
    @SaCheckPermission("sys:web:update")
    public Result<Void> update(@RequestBody SysWebConfig sysWebConfig) {
        sysWebConfigService.update(sysWebConfig);
        return Result.success();
    }
}
