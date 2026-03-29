package top.fxmarkbrown.blog.controller.message;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.SysAiDocumentTaskService;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeMessageVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeThreadAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskAdminVo;

import java.util.List;

@RestController
@Tag(name = "AI 文档任务管理")
@RequestMapping("/sys/ai/document-task")
@RequiredArgsConstructor
public class SysAiDocumentTaskController {

    private final SysAiDocumentTaskService sysAiDocumentTaskService;

    @GetMapping("/list")
    @Operation(summary = "获取文档任务列表")
    public Result<IPage<AiDocumentTaskAdminVo>> list(String status, String provider, String keyword, String userKeyword) {
        return Result.success(sysAiDocumentTaskService.pageTasks(status, provider, keyword, userKeyword));
    }

    @GetMapping("/threads/{taskId}")
    @Operation(summary = "获取文档节点线程列表")
    public Result<IPage<AiDocumentNodeThreadAdminVo>> threads(@PathVariable Long taskId, String keyword) {
        return Result.success(sysAiDocumentTaskService.pageThreads(taskId, keyword));
    }

    @GetMapping("/messages/{threadId}")
    @Operation(summary = "获取文档节点线程消息")
    public Result<IPage<AiDocumentNodeMessageVo>> messages(@PathVariable Long threadId) {
        return Result.success(sysAiDocumentTaskService.pageMessages(threadId));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除文档任务")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysAiDocumentTaskService.deleteTasks(ids);
        return Result.success();
    }
}
