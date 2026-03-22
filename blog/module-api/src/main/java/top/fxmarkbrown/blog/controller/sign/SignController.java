package top.fxmarkbrown.blog.controller.sign;

import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.SignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sign")
@Tag(name = "门户-签到")
public class SignController {

    private final SignService signService;

    @GetMapping("/")
    @Operation(summary = "签到")
    public Result<Void> sign() {
        signService.sign();
        return Result.success();
    }

    @GetMapping("/isSignedToday")
    @Operation(summary = "是否签到")
    public Result<Boolean> isSignedToday() {
        return Result.success(signService.isSignedToday());
    }

    @GetMapping("/getSignDays")
    @Operation(summary = "获取累计天数和连续天数")
    public Result<Map<String, Object>> getSignDays() {
        Map<String, Object> map = new HashMap<>();
        map.put("totalDays", signService.getCumulativeSignDays());
        map.put("continuousDays", signService.getConsecutiveSignDays());
        return Result.success(map);
    }

}
