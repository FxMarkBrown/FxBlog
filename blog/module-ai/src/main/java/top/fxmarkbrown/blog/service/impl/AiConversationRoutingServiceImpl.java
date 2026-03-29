package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.model.ai.AiConversationRoute;
import top.fxmarkbrown.blog.model.ai.AiConversationRouteDecision;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.service.AiConversationRoutingService;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationRoutingServiceImpl implements AiConversationRoutingService {

    private static final String ROUTER_SYSTEM_PROMPT = """
            你是 FxMarkBrown's Blog AI 编排层的语义路由器。
            你的任务不是回答用户问题，而是只判断这一轮对话应该走哪条链路。

            可选 route 只有四个：
            1. TOOL_FIRST: 问题主要依赖站内实时数据、用户个人状态、会话绑定状态或交互历史，例如 AI token 用量、点赞、收藏、评论、当前会话绑定文章信息。
            2. ARTICLE_RAG: 问题主要围绕当前绑定文章本身的内容、结构、论点、术语或上下文，需要优先检索当前文章。
            3. GLOBAL_RAG: 问题主要是在询问站内知识库/博客文章中的主题知识、概念解释、总结比较，适合检索全站文章片段。
            4. DIRECT_CHAT: 问题不依赖站内文章或实时工具数据，直接普通回答即可。

            判断要求：
            - 优先考虑真正需要的数据来源，而不是表面关键词。
            - 只有在问题明显需要站内文章知识时，才选择 GLOBAL_RAG 或 ARTICLE_RAG。
            - 只有在问题明显需要用户数据、会话上下文或实时统计时，才选择 TOOL_FIRST。
            - 无法确认需要站内知识时，优先选择 DIRECT_CHAT，不要过度触发 RAG。

            你必须返回结构化 JSON，字段只有：
            - route: 四选一
            - reason: 一句话说明判断依据
            - confidence: 0-100 的整数
            """;

    private final AiChatModelService aiChatModelService;

    @Override
    public AiConversationRouteDecision determineRoute(SysAiConversation conversation, String latestUserQuestion) {
        if (!StringUtils.hasText(latestUserQuestion)) {
            return fallbackDecision(conversation, "问题为空，默认直接回答。");
        }
        AiResolvedChatModel resolvedChatModel = aiChatModelService.resolveConversationModel(conversation);
        ChatClient chatClient = aiChatModelService.getChatClient(resolvedChatModel);
        try {
            AiConversationRouteDecision decision = chatClient.prompt()
                    .system(ROUTER_SYSTEM_PROMPT)
                    .user(buildRouterUserPrompt(conversation, latestUserQuestion))
                    .options(OpenAiChatOptions.builder()
                            .model(resolvedChatModel.modelName())
                            .temperature(0D)
                            .build())
                    .call()
                    .entity(AiConversationRouteDecision.class);
            return normalizeDecision(conversation, decision);
        } catch (Exception ex) {
            log.warn("AI 对话路由判断失败, conversationId={}", conversation == null ? null : conversation.getId(), ex);
            return fallbackDecision(conversation, "路由模型调用失败，使用保守回退策略。");
        }
    }

    private String buildRouterUserPrompt(SysAiConversation conversation, String latestUserQuestion) {
        String conversationType = conversation == null ? "unknown" : safe(conversation.getType());
        boolean hasBoundArticle = conversation != null
                && Constants.AI_CONVERSATION_TYPE_ARTICLE.equals(conversation.getType())
                && conversation.getArticleId() != null;
        return """
                当前会话信息：
                - conversationType: %s
                - hasBoundArticle: %s

                用户最新问题：
                %s
                """.formatted(conversationType, hasBoundArticle, latestUserQuestion.trim());
    }

    private AiConversationRouteDecision normalizeDecision(SysAiConversation conversation, AiConversationRouteDecision rawDecision) {
        AiConversationRouteDecision decision = rawDecision == null ? new AiConversationRouteDecision() : rawDecision;
        AiConversationRoute normalizedRoute = resolveRoute(conversation, decision.getRoute());
        decision.setRoute(normalizedRoute.name());
        if (!StringUtils.hasText(decision.getReason())) {
            decision.setReason("未提供原因。");
        }
        if (decision.getConfidence() == null) {
            decision.setConfidence(0);
        }
        return decision;
    }

    private AiConversationRoute resolveRoute(SysAiConversation conversation, String rawRoute) {
        AiConversationRoute fallbackRoute = fallbackRoute(conversation);
        if (!StringUtils.hasText(rawRoute)) {
            return fallbackRoute;
        }
        try {
            AiConversationRoute route = AiConversationRoute.valueOf(rawRoute.trim().toUpperCase(Locale.ROOT));
            Set<AiConversationRoute> allowedRoutes = allowedRoutes(conversation);
            return allowedRoutes.contains(route) ? route : fallbackRoute;
        } catch (IllegalArgumentException ex) {
            return fallbackRoute;
        }
    }

    private Set<AiConversationRoute> allowedRoutes(SysAiConversation conversation) {
        if (conversation != null
                && Constants.AI_CONVERSATION_TYPE_ARTICLE.equals(conversation.getType())
                && conversation.getArticleId() != null) {
            return EnumSet.of(AiConversationRoute.ARTICLE_RAG, AiConversationRoute.TOOL_FIRST, AiConversationRoute.DIRECT_CHAT);
        }
        return EnumSet.of(AiConversationRoute.GLOBAL_RAG, AiConversationRoute.TOOL_FIRST, AiConversationRoute.DIRECT_CHAT);
    }

    private AiConversationRouteDecision fallbackDecision(SysAiConversation conversation, String reason) {
        AiConversationRouteDecision decision = new AiConversationRouteDecision();
        decision.setRoute(fallbackRoute(conversation).name());
        decision.setReason(reason);
        decision.setConfidence(0);
        return decision;
    }

    private AiConversationRoute fallbackRoute(SysAiConversation conversation) {
        if (conversation != null
                && Constants.AI_CONVERSATION_TYPE_ARTICLE.equals(conversation.getType())
                && conversation.getArticleId() != null) {
            return AiConversationRoute.ARTICLE_RAG;
        }
        return AiConversationRoute.DIRECT_CHAT;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
