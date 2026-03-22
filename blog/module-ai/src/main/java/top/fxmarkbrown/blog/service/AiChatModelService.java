package top.fxmarkbrown.blog.service;

import org.springframework.ai.chat.client.ChatClient;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.vo.ai.AiChatModelOptionVo;

import java.util.List;

public interface AiChatModelService {

    AiResolvedChatModel getDefaultModel();

    AiResolvedChatModel requireModel(String modelId);

    AiResolvedChatModel resolveConversationModel(SysAiConversation conversation);

    ChatClient getChatClient(AiResolvedChatModel resolvedChatModel);

    List<AiChatModelOptionVo> listAvailableModels();

    String resolveModelId(String providerName, String modelName);

    String resolveDisplayName(String providerName, String modelName);
}
