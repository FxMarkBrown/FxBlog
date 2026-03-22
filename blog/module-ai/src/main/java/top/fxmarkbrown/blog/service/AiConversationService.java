package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.dto.ai.AiConversationCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiSendMessageDto;
import top.fxmarkbrown.blog.vo.ai.AiConversationDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiConversationListVo;
import top.fxmarkbrown.blog.vo.ai.AiChatModelOptionVo;
import top.fxmarkbrown.blog.vo.ai.AiMessageVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiConversationService {

    /**
     * 创建一条新的全局 AI 会话。
     */
    AiConversationDetailVo createGlobalConversation(AiConversationCreateDto createDto);

    /**
     * 为指定文章创建一条 AI 会话。
     */
    AiConversationDetailVo createArticleConversation(Long articleId, AiConversationCreateDto createDto);

    /**
     * 获取当前可用聊天模型列表。
     */
    List<AiChatModelOptionVo> listChatModels();

    /**
     * 获取当前用户拥有的会话详情。
     */
    AiConversationDetailVo getConversationDetail(Long conversationId);

    /**
     * 分页查询当前用户的 AI 会话列表。
     */
    IPage<AiConversationListVo> pageConversations(String type);

    /**
     * 分页查询指定会话的消息记录。
     */
    IPage<AiMessageVo> pageMessages(Long conversationId);

    /**
     * 以普通请求方式发送一条消息并返回完整结果。
     */
    List<AiMessageVo> sendMessage(Long conversationId, AiSendMessageDto sendMessageDto);

    /**
     * 以流式方式发送一条消息并返回 SSE 通道。
     */
    SseEmitter streamMessage(Long conversationId, AiSendMessageDto sendMessageDto);

    /**
     * 重命名指定会话。
     */
    void renameConversation(Long conversationId, String title);

    /**
     * 删除指定会话及其消息。
     */
    void deleteConversation(Long conversationId);
}
