package top.fxmarkbrown.blog.service;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.entity.SysAiMessage;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.List;

public interface AiChatService {

    /**
     * 基于当前会话和历史消息提取本轮回答所需的引用片段。
     */
    List<AiRetrievedChunkVo> retrieveCitations(SysAiConversation conversation, List<SysAiMessage> historyMessages);

    /**
     * 生成一次性完整回复。
     */
    ChatResponse generateResponse(SysAiConversation conversation,
                                  List<SysAiMessage> historyMessages,
                                  List<AiRetrievedChunkVo> citations);

    /**
     * 生成流式回复。
     */
    Flux<ChatResponse> streamReply(SysAiConversation conversation,
                                   List<SysAiMessage> historyMessages,
                                   List<AiRetrievedChunkVo> citations);

    /**
     * 从模型响应中提取正文内容。
     */
    String extractText(ChatResponse chatResponse);

    /**
     * 从模型响应中提取推理内容。
     */
    String extractReasoning(ChatResponse chatResponse);

    /**
     * 从模型响应中提取 token 使用量。
     */
    Usage extractUsage(ChatResponse chatResponse);

    /**
     * 从模型响应中提取工具调用记录。
     */
    List<AiToolCallVo> extractToolCalls(ChatResponse chatResponse);
}
