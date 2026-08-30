package top.fxmarkbrown.blog.controller.article;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.SeriesService;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SeriesListVo;

import java.util.List;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
@Tag(name = "门户-系列管理")
public class SeriesController {

    private final SeriesService seriesService;

    @GetMapping("/list")
    @Operation(summary = "获取全部系列")
    public Result<List<SeriesListVo>> getSeriesList() {
        return Result.success(seriesService.getSeriesList());
    }

    @GetMapping("/{id}/articles")
    @Operation(summary = "获取系列文章列表")
    public Result<List<ArticleListVo>> getSeriesArticles(@PathVariable Integer id) {
        return Result.success(seriesService.getSeriesArticles(id));
    }
}
