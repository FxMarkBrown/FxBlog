package top.fxmarkbrown.blog.controller.message;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.ai.AiQuotaManualAdjustDto;
import top.fxmarkbrown.blog.dto.ai.AiQuotaRuleUpdateDto;
import top.fxmarkbrown.blog.service.SysAiQuotaService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.vo.ai.AiQuotaAdminLogVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaRuleVo;

@RestController
@Tag(name = "AI 额度管理")
@RequestMapping("/sys/ai/quota")
@RequiredArgsConstructor
public class SysAiQuotaController {

    private final SysAiQuotaService sysAiQuotaService;
    private final AiQuotaCoreService aiQuotaCoreService;

    @GetMapping("/list")
    @Operation(summary = "获取 AI 额度列表")
    public Result<IPage<AiQuotaAdminVo>> list(String userKeyword) {
        return Result.success(sysAiQuotaService.pageQuota(userKeyword));
    }

    @GetMapping("/log/list")
    @Operation(summary = "获取 AI 额度流水列表")
    public Result<IPage<AiQuotaAdminLogVo>> logList(String bizType, String userKeyword) {
        return Result.success(sysAiQuotaService.pageQuotaLogs(bizType, userKeyword));
    }

    @GetMapping("/rule")
    @Operation(summary = "获取 AI 额度规则")
    public Result<AiQuotaRuleVo> getRule() {
        return Result.success(aiQuotaCoreService.getRule());
    }

    @PutMapping("/rule")
    @Operation(summary = "更新 AI 额度规则")
    public Result<AiQuotaRuleVo> updateRule(@RequestBody AiQuotaRuleUpdateDto updateDto) {
        return Result.success(aiQuotaCoreService.saveRule(updateDto));
    }

    @PutMapping("/manual")
    @Operation(summary = "更新用户手动额度")
    public Result<Void> updateManualBonus(@RequestBody AiQuotaManualAdjustDto adjustDto) {
        aiQuotaCoreService.updateManualBonusTokens(adjustDto.getUserId(), adjustDto.getManualBonusTokens());
        return Result.success();
    }
}
