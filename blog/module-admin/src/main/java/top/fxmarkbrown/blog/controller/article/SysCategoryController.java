package top.fxmarkbrown.blog.controller.article;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.service.SysCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类表 控制器
 */
@RestController
@Tag(name = "分类管理")
@RequiredArgsConstructor
@RequestMapping("/sys/category")
public class SysCategoryController {

    private final SysCategoryService sysCategoryService;

    @GetMapping("/list")
    @Operation(summary = "分类列表")
    public Result<IPage<SysCategory>> list(SysCategory sysCategory) {
        return Result.success(sysCategoryService.selectPage(sysCategory));
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部分类")
    public Result<List<SysCategory>> selectAllList() {
        return Result.success(sysCategoryService.selectList(null));
    }

    @PostMapping
    @Operation(summary = "新增分类")
    @SaCheckPermission("sys:category:add")
    public Result<Object> add(@RequestBody SysCategory sysCategory) {
        return Result.success(sysCategoryService.insert(sysCategory));
    }

    @PutMapping
    @Operation(summary = "修改分类")
    @SaCheckPermission("sys:category:update")
    public Result<Object> edit(@RequestBody SysCategory sysCategory) {
        return Result.success(sysCategoryService.update(sysCategory));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除分类")
    @SaCheckPermission("sys:category:delete")
    public Result<Object> remove(@PathVariable List<Integer> ids) {
        return Result.success(sysCategoryService.deleteByIds(ids));
    }
}
