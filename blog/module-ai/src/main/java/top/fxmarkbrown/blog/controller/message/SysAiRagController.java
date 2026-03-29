package top.fxmarkbrown.blog.controller.message;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.service.AiArticleRagRebuildService;
import top.fxmarkbrown.blog.vo.ai.AiRagRebuildSubmitVo;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "AI RAG 管理")
@RequestMapping("/sys/ai/rag")
@RequiredArgsConstructor
public class SysAiRagController {

    private final AiArticleRagRebuildService aiArticleRagRebuildService;
    private final AiRagProperties aiRagProperties;

    @SaCheckLogin
    @GetMapping("/status")
    @Operation(summary = "获取文章 RAG 重建状态")
    public Result<Map<String, Object>> status() {
        if (!StpUtil.hasRole(Constants.ADMIN)) {
            return Result.error(403, "无权限");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", aiRagProperties.isEnabled());
        data.put("syncOnStartup", aiRagProperties.isSyncOnStartup());
        data.put("indexPublishedOnly", aiRagProperties.isIndexPublishedOnly());
        data.put("running", aiArticleRagRebuildService.isRunning());
        return Result.success(data);
    }

    @SaCheckLogin
    @PostMapping("/rebuild")
    @Operation(summary = "手动提交文章 RAG 全量重建")
    public Result<AiRagRebuildSubmitVo> rebuild(@RequestParam(required = false) Boolean publishedOnly) {
        if (!StpUtil.hasRole(Constants.ADMIN)) {
            return Result.error(403, "无权限");
        }
        boolean effectivePublishedOnly = publishedOnly != null ? publishedOnly : aiRagProperties.isIndexPublishedOnly();
        return Result.success(aiArticleRagRebuildService.submitAsync(effectivePublishedOnly, "manual"));
    }
}
