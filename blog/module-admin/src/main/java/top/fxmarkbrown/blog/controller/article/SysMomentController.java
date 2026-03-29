package top.fxmarkbrown.blog.controller.article;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysMoment;
import top.fxmarkbrown.blog.service.SysMomentService;

import java.util.List;

/**
 * 说说 控制器
 */
@RestController
@RequestMapping("/sys/moment")
@RequiredArgsConstructor
@Tag(name = "说说管理")
public class SysMomentController {

    private final SysMomentService sysMomentService;

    @GetMapping("/list")
    @Operation(summary = "获取说说列表")
    public Result<IPage<SysMoment>> list(SysMoment sysMoment) {
        return Result.success(sysMomentService.selectPage(sysMoment));
    }

    @PostMapping("/add")
    @SaCheckPermission("sys:moment:add")
    @Operation(summary = "添加说说")
    public Result<Object> add(@RequestBody SysMoment sysMoment) {
        return Result.success(sysMomentService.add(sysMoment));
    }

    @PutMapping("/update")
    @SaCheckPermission("sys:moment:update")
    @Operation(summary = "修改说说")
    public Result<Object> edit(@RequestBody SysMoment sysMoment) {
        return Result.success(sysMomentService.updateById(sysMoment));
    }

    @DeleteMapping("/delete/{ids}")
    @SaCheckPermission("sys:moment:delete")
    @Operation(summary = "删除说说")
    public Result<Object> remove(@PathVariable List<Long> ids) {
        return Result.success(sysMomentService.removeByIds(ids));
    }
}
