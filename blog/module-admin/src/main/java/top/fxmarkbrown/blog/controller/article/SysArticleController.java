package top.fxmarkbrown.blog.controller.article;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.article.ArticleQueryDto;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.service.SysArticleService;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SysArticleDetailVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "文章管理")
@RequestMapping("/sys/article")
@RequiredArgsConstructor
public class SysArticleController {

    private final SysArticleService sysArticleService;

    @GetMapping("/list")
    @Operation(summary = "文章列表")
    @SaCheckPermission("sys:article:list")
    public Result<IPage<ArticleListVo>> list(ArticleQueryDto articleQueryDto) {
        return Result.success(sysArticleService.selectPage(articleQueryDto));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "文章详情")
    public Result<SysArticleDetailVo> detail(@PathVariable Long id) {
        return Result.success(sysArticleService.detail(id));
    }

    @PostMapping("/add")
    @Operation(summary = "新增文章")
    @SaCheckPermission("sys:article:add")
    public Result<Boolean> add(@RequestBody SysArticleDetailVo sysArticle) {
        return Result.success(sysArticleService.add(sysArticle));
    }

    @PutMapping("/update")
    @Operation(summary = "修改文章")
    @SaCheckPermission("sys:article:update")
    public Result<Boolean> update(@RequestBody SysArticleDetailVo sysArticle) {
        return Result.success(sysArticleService.update(sysArticle));
    }

    @PutMapping("/updateStatus")
    @Operation(summary = "修改状态")
    @SaCheckPermission("sys:article:updateStatus")
    public Result<Boolean> updateStatus(@RequestBody SysArticle sysArticle) {
        return Result.success(sysArticleService.updateById(sysArticle));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除文章")
    @SaCheckPermission("sys:article:delete")
    public Result<Boolean> delete(@PathVariable List<Long> ids) {
        return Result.success(sysArticleService.delete(ids));
    }
}
