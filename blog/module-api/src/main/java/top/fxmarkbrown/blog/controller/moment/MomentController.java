package top.fxmarkbrown.blog.controller.moment;

import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.MomentService;
import top.fxmarkbrown.blog.vo.moment.MomentPageVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/moment")
@Tag(name = "门户-说说管理")
public class MomentController {

    private final MomentService momentService;

    @GetMapping("/list")
    @Operation(description = "说说列表")
    public Result<PageResponse<MomentPageVo>> getMomentList(PageQuery pageQuery) {
        return Result.success(momentService.getMomentList(pageQuery));
    }

}
