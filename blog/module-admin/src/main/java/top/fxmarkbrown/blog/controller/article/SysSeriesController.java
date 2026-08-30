package top.fxmarkbrown.blog.controller.article;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysSeries;
import top.fxmarkbrown.blog.service.SysSeriesService;

import java.util.List;

/**
 * 系列表 控制器
 */
@RestController
@Tag(name = "系列管理")
@RequiredArgsConstructor
@RequestMapping("/sys/series")
public class SysSeriesController {

    private final SysSeriesService sysSeriesService;

    @GetMapping("/list")
    @Operation(summary = "系列列表")
    public Result<IPage<SysSeries>> list(SysSeries sysSeries) {
        return Result.success(sysSeriesService.selectPage(sysSeries));
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部系列")
    public Result<List<SysSeries>> selectAllList() {
        return Result.success(sysSeriesService.selectList(null));
    }

    @PostMapping
    @Operation(summary = "新增系列")
    @SaCheckPermission("sys:series:add")
    public Result<Object> add(@RequestBody SysSeries sysSeries) {
        return Result.success(sysSeriesService.insert(sysSeries));
    }

    @PutMapping
    @Operation(summary = "修改系列")
    @SaCheckPermission("sys:series:update")
    public Result<Object> edit(@RequestBody SysSeries sysSeries) {
        return Result.success(sysSeriesService.update(sysSeries));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除系列")
    @SaCheckPermission("sys:series:delete")
    public Result<Object> remove(@PathVariable List<Integer> ids) {
        return Result.success(sysSeriesService.deleteByIds(ids));
    }
}
