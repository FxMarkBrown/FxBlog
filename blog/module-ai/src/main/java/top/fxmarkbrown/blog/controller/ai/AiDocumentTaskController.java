package top.fxmarkbrown.blog.controller.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.ai.AiDocumentNodeAskDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskRenameDto;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeAnswerVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentParseResultVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskListVo;

import java.util.List;

@RestController
@RequestMapping("/api/ai/document")
@SaCheckLogin
@RequiredArgsConstructor
@Tag(name = "门户-AI 文档任务")
public class AiDocumentTaskController {

    private final AiDocumentTaskService aiDocumentTaskService;

    @GetMapping("/tasks")
    @Operation(summary = "获取文档任务列表")
    public Result<List<AiDocumentTaskListVo>> listTasks() {
        return Result.success(aiDocumentTaskService.listTasks());
    }

    @PostMapping("/tasks")
    @Operation(summary = "创建文档任务")
    public Result<AiDocumentTaskDetailVo> createTask(@RequestBody(required = false) AiDocumentTaskCreateDto createDto) {
        return Result.success(aiDocumentTaskService.createTask(createDto));
    }

    @PatchMapping("/tasks/{taskId}")
    @Operation(summary = "重命名文档任务")
    public Result<AiDocumentTaskDetailVo> renameTask(@PathVariable Long taskId,
                                                     @RequestBody AiDocumentTaskRenameDto renameDto) {
        return Result.success(aiDocumentTaskService.renameTask(taskId, renameDto));
    }

    @DeleteMapping("/tasks/{taskId}")
    @Operation(summary = "删除文档任务")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        aiDocumentTaskService.deleteTask(taskId);
        return Result.success();
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "获取文档任务详情")
    public Result<AiDocumentTaskDetailVo> getTaskDetail(@PathVariable Long taskId) {
        return Result.success(aiDocumentTaskService.getTaskDetail(taskId));
    }

    @GetMapping("/tasks/{taskId}/result")
    @Operation(summary = "获取文档结构树结果")
    public Result<AiDocumentParseResultVo> getTaskResult(@PathVariable Long taskId) {
        return Result.success(aiDocumentTaskService.getTaskResult(taskId));
    }

    @PostMapping("/tasks/{taskId}/nodes/{nodeId}/ask")
    @Operation(summary = "对文档节点发起问答")
    public Result<AiDocumentNodeAnswerVo> askNode(@PathVariable Long taskId,
                                                  @PathVariable String nodeId,
                                                  @RequestBody AiDocumentNodeAskDto askDto) {
        return Result.success(aiDocumentTaskService.askNode(taskId, nodeId, askDto));
    }
}
