package top.fxmarkbrown.blog.controller.article;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysTag;
import top.fxmarkbrown.blog.service.SysTagService;

import java.util.List;

/**
 * 标签表 控制器
 */
@RestController
@RequestMapping("/sys/tag")
@RequiredArgsConstructor
@Tag(name = "标签管理")
public class SysTagController {

    private final SysTagService sysTagService;

    @GetMapping("/list")
    @Operation(summary = "标签列表")
    public Result<IPage<SysTag>> list(SysTag sysTag) {
        return Result.success(sysTagService.selectPage(sysTag));
    }

    @PostMapping
    @Operation(summary = "新增标签")
    @SaCheckPermission("sys:tag:add")
    public Result<Object> add(@RequestBody SysTag sysTag) {
        return Result.success(sysTagService.insert(sysTag));
    }

    @PutMapping
    @Operation(summary = "修改标签")
    @SaCheckPermission("sys:tag:update")
    public Result<Object> edit(@RequestBody SysTag sysTag) {
        return Result.success(sysTagService.update(sysTag));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除标签")
    @SaCheckPermission("sys:tag:delete")
    public Result<Object> remove(@PathVariable List<Integer> ids) {
        return Result.success(sysTagService.deleteByIds(ids));
    }
}
