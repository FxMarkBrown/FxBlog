package top.fxmarkbrown.blog.controller.site;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysAlbum;
import top.fxmarkbrown.blog.service.SysAlbumService;

import java.util.List;

/**
 * 相册 控制器
 */
@RestController
@RequestMapping("/sys/album")
@RequiredArgsConstructor
@Tag(name = "相册管理")
public class SysAlbumController {

    private final SysAlbumService sysAlbumService;

    @GetMapping("/list")
    @Operation(summary = "获取相册列表")
    public Result<IPage<SysAlbum>> selectPage(SysAlbum sysAlbum) {
        return Result.success(sysAlbumService.selectPage(sysAlbum));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有相册列表")
    public Result<List<SysAlbum>> selectList(SysAlbum sysAlbum) {
        return Result.success(sysAlbumService.selectList(sysAlbum));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取相册详情")
    public Result<SysAlbum> getInfo(@PathVariable("id") Long id) {
        return Result.success(sysAlbumService.getById(id));
    }

    @PostMapping("/add")
    @SaCheckPermission("sys:album:add")
    @Operation(summary = "添加相册")
    public Result<Object> add(@RequestBody SysAlbum sysAlbum) {
        return Result.success(sysAlbumService.insert(sysAlbum));
    }

    @PutMapping("/update")
    @SaCheckPermission("sys:album:update")
    @Operation(summary = "修改相册")
    public Result<Object> edit(@RequestBody SysAlbum sysAlbum) {
        return Result.success(sysAlbumService.update(sysAlbum));
    }

    @DeleteMapping("/delete/{ids}")
    @SaCheckPermission("sys:album:delete")
    @Operation(summary = "删除相册")
    public Result<Object> remove(@PathVariable List<Long> ids) {
        return Result.success(sysAlbumService.deleteByIds(ids));
    }
}
