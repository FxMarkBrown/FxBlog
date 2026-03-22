package top.fxmarkbrown.blog.controller.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.ai.AiConversationCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiSendMessageDto;
import top.fxmarkbrown.blog.service.AiConversationService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.vo.ai.AiChatModelOptionVo;
import top.fxmarkbrown.blog.vo.ai.AiConversationDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiConversationListVo;
import top.fxmarkbrown.blog.vo.ai.AiMessageVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaSnapshotVo;

import java.util.List;

@RestController
@RequestMapping("/api/ai/conversation")
@RequiredArgsConstructor
@SaCheckLogin
@Tag(name = "门户-AI 会话")
public class AiConversationController {

    private final AiConversationService aiConversationService;
    private final AiQuotaCoreService aiQuotaCoreService;

    @GetMapping("/models")
    @Operation(summary = "获取 AI 可选模型列表")
    public Result<List<AiChatModelOptionVo>> listChatModels() {
        return Result.success(aiConversationService.listChatModels());
    }

    @PostMapping("/global")
    @Operation(summary = "创建全局 AI 会话")
    public Result<AiConversationDetailVo> createGlobalConversation(@RequestBody(required = false) AiConversationCreateDto createDto) {
        return Result.success(aiConversationService.createGlobalConversation(createDto));
    }

    @PostMapping("/article/{articleId}")
    @Operation(summary = "创建文章 AI 会话")
    public Result<AiConversationDetailVo> createArticleConversation(@PathVariable Long articleId,
                                                                    @RequestBody(required = false) AiConversationCreateDto createDto) {
        return Result.success(aiConversationService.createArticleConversation(articleId, createDto));
    }

    @GetMapping("/detail/{conversationId}")
    @Operation(summary = "获取 AI 会话详情")
    public Result<AiConversationDetailVo> getConversationDetail(@PathVariable Long conversationId) {
        return Result.success(aiConversationService.getConversationDetail(conversationId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页获取 AI 会话列表")
    public Result<IPage<AiConversationListVo>> pageConversations(@RequestParam(required = false) String type) {
        return Result.success(aiConversationService.pageConversations(type));
    }

    @GetMapping("/messages/{conversationId}")
    @Operation(summary = "分页获取 AI 会话消息")
    public Result<IPage<AiMessageVo>> pageMessages(@PathVariable Long conversationId) {
        return Result.success(aiConversationService.pageMessages(conversationId));
    }

    @GetMapping("/quota")
    @Operation(summary = "获取当前用户 AI 额度")
    public Result<AiQuotaSnapshotVo> getQuotaSnapshot() {
        return Result.success(aiQuotaCoreService.getQuotaSnapshot(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/send/{conversationId}")
    @Operation(summary = "发送 AI 会话消息")
    public Result<List<AiMessageVo>> sendMessage(@PathVariable Long conversationId, @RequestBody AiSendMessageDto sendMessageDto) {
        return Result.success(aiConversationService.sendMessage(conversationId, sendMessageDto));
    }

    @PostMapping(value = "/stream/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式发送 AI 会话消息")
    public SseEmitter streamMessage(@PathVariable Long conversationId, @RequestBody AiSendMessageDto sendMessageDto) {
        return aiConversationService.streamMessage(conversationId, sendMessageDto);
    }

    @PutMapping("/rename/{conversationId}")
    @Operation(summary = "重命名 AI 会话")
    public Result<Void> renameConversation(@PathVariable Long conversationId, @RequestParam String title) {
        aiConversationService.renameConversation(conversationId, title);
        return Result.success();
    }

    @DeleteMapping("/delete/{conversationId}")
    @Operation(summary = "删除 AI 会话")
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        aiConversationService.deleteConversation(conversationId);
        return Result.success();
    }
}
