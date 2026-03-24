package top.fxmarkbrown.blog.controller.comment;


import cn.dev33.satoken.annotation.SaCheckLogin;
import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.service.CommentService;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comment")
@Tag(name = "门户-评论管理")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/list")
    @Operation(summary = "获取文章评论列表")
    public Result<PageResponse<CommentListVo>> getComments(PageQuery pageQuery, Long articleId, String sortType) {
        return Result.success(commentService.getComments(pageQuery, articleId, sortType));
    }

    @SaCheckLogin
    @PostMapping("/add")
    @Operation(summary = "获取文章评论列表")
    public Result<Void> add(@RequestBody SysComment sysComment) {
        commentService.add(sysComment);
        return Result.success();
    }
}
