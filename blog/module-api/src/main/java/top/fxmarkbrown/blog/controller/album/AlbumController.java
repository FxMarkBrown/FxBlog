package top.fxmarkbrown.blog.controller.album;

import top.fxmarkbrown.blog.annotation.AccessLimit;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysAlbum;
import top.fxmarkbrown.blog.entity.SysPhoto;
import top.fxmarkbrown.blog.service.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/album")
@RequiredArgsConstructor
@Tag(name = "门户-相册管理")
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping("/list")
    @Operation(summary = "获取相册列表")
    public Result<List<SysAlbum>> getAlbumList() {
        return Result.success(albumService.getAlbumList());
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "获取相册详情")
    public Result<SysAlbum> detail(@PathVariable Long id) {
        return Result.success(albumService.detail(id));
    }

    @GetMapping("/photos/{albumId}")
    @Operation(summary = "获取照片列表")
    public Result<List<SysPhoto>> getPhotos(@PathVariable Long albumId, String password) {
        return Result.success(albumService.getPhotos(albumId, password));
    }

    @AccessLimit(count = 5, time = 30)
    @GetMapping("/verify/{id}")
    @Operation(summary = "验证相册的密码")
    public Result<Boolean> verify(@PathVariable Long id, String password) {
        return albumService.verify(id, password) ? Result.success(true) : Result.error("密码错误");
    }
}
