package top.fxmarkbrown.blog.controller.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.SysCommentService;
import top.fxmarkbrown.blog.vo.comment.SysCommentVO;

import java.util.List;

@RestController
@Tag(name = "评论管理")
@RequestMapping("/sys/comment")
@RequiredArgsConstructor
public class SysCommentController {

    private final SysCommentService sysCommentService;

    @GetMapping("/list")
    @Operation(summary = "获取评论列表")
    public Result<Page<SysCommentVO>> list() {
        return Result.success(sysCommentService.selectList());
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除评论")
    @SaCheckPermission("sys:comment:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysCommentService.removeBatchByIds(ids);
        return Result.success();
    }
}
