package top.fxmarkbrown.blog.controller.message;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.SysAiConversationService;
import top.fxmarkbrown.blog.vo.ai.AiConversationAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiMessageVo;

import java.util.List;

@RestController
@Tag(name = "AI 对话管理")
@RequestMapping("/sys/ai/conversation")
@RequiredArgsConstructor
public class SysAiConversationController {

    private final SysAiConversationService sysAiConversationService;

    @GetMapping("/list")
    @Operation(summary = "获取 AI 会话列表")
    public Result<IPage<AiConversationAdminVo>> list(String type, String keyword, String userKeyword) {
        return Result.success(sysAiConversationService.pageConversations(type, keyword, userKeyword));
    }

    @GetMapping("/messages/{conversationId}")
    @Operation(summary = "获取 AI 会话消息")
    public Result<IPage<AiMessageVo>> messages(@PathVariable Long conversationId) {
        return Result.success(sysAiConversationService.pageMessages(conversationId));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除 AI 会话")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysAiConversationService.deleteConversations(ids);
        return Result.success();
    }
}
