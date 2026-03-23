package top.fxmarkbrown.blog.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.service.UserService;
import top.fxmarkbrown.blog.vo.ai.AiQuotaLogVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/portal/user")
@RequiredArgsConstructor
@Tag(name = "门户-个人中心")
public class UserController {

    private final UserService userService;

    @PutMapping("/updateProfile")
    @Operation(summary = "修改我的资料")
    public Result<Void> updateProfile(@RequestBody SysUser user) {
        userService.updateProfile(user);
        return Result.success();
    }

    @GetMapping("/comment")
    @Operation(summary = "获取我的评论")
    public Result<IPage<CommentListVo>> selectMyComment() {
        return Result.success(userService.selectMyComment());
    }

    @DeleteMapping("/delMyComment/{ids}")
    @Operation(summary = "删除我的评论")
    public Result<Void> delMyComment(@PathVariable List<Long> ids) {
        return Result.success(userService.delMyComment(ids));
    }

    @GetMapping("/myReply")
    @Operation(description = "获取我的回复")
    public Result<IPage<CommentListVo>> getMyReply() {
        return Result.success(userService.getMyReply());
    }

    @GetMapping("/myLike")
    @Operation(summary = "获取我的点赞")
    public Result<IPage<ArticleListVo>> selectMyLike() {
        return Result.success(userService.selectMyLike());
    }

    @GetMapping("/myFavorite")
    @Operation(summary = "获取我的收藏")
    public Result<IPage<ArticleListVo>> selectMyFavorite() {
        return Result.success(userService.selectMyFavorite());
    }

    @GetMapping("/aiQuotaLog")
    @Operation(summary = "获取我的 AI 额度流水")
    public Result<IPage<AiQuotaLogVo>> getMyAiQuotaLogs() {
        return Result.success(userService.getMyAiQuotaLogs());
    }

    @GetMapping("/myArticle")
    @Operation(summary = "获取我的文章")
    public Result<IPage<ArticleListVo>> selectMyArticle(SysArticle article) {
        return Result.success(userService.selectMyArticle(article));
    }
}
