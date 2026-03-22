package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.model.ai.AiConversationRouteDecision;

public interface AiConversationRoutingService {

    /**
     * 为当前问题选择本轮对话链路，决定优先走工具、文章检索、全局检索或直接回答。
     */
    AiConversationRouteDecision determineRoute(SysAiConversation conversation, String latestUserQuestion);
}
