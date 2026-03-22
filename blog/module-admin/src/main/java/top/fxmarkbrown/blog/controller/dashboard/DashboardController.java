package top.fxmarkbrown.blog.controller.dashboard;


import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.IndexService;
import top.fxmarkbrown.blog.vo.dashboard.IndexVo;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/sys/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IndexService indexService;

    @GetMapping
    @Operation(summary = "首页")
    public Result<IndexVo> index() {
        return Result.success(indexService.index());
    }


    @GetMapping("/bottom")
    @Operation(summary = "首页底部分类")
    public Result<List<Map<String, Integer>>> getCategories() {
        return Result.success(indexService.getCategories());
    }

}
