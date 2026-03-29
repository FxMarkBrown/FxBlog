package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.config.ai.AiProperties;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.entity.SysAiMessage;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.model.ai.*;
import top.fxmarkbrown.blog.service.*;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceSpringImpl implements AiChatService {

    /**
     * 全局模式下的 System Prompt
     */
    private static final String GLOBAL_PROMPT = """
            你是 FxMarkBrown's Blog 的站内 AI 助手。
            请始终使用简体中文回答，默认给出准确、直接、可执行的结论。
            如果信息不足，请明确说明不确定点，不要编造不存在的站内内容。
            如果问题涉及 AI token 用量、剩余额度、点赞/收藏/评论历史、当前会话绑定文章信息等实时或用户私有数据，你只能基于真实工具调用结果回答。
            如果这一轮没有拿到真实工具结果，必须直接说明“当前未获取到实时数据，无法给出准确数值”，禁止自行猜测、估算或编造任何数字。
            不要把“我将调用工具”“我正在查询”“我已调用某工具”这类计划性描述当成最终答案输出给用户，除非系统已经真的返回了工具结果。
            回答格式优先清晰、克制，可适度使用 Markdown 列表帮助阅读。
            如果要写标题，必须显式使用 Markdown 标题标记（例如 ## 或 ###），不要输出没有标记的伪标题。
            如果要写列表，每个列表项必须单独换行，不要把多个 "-" 或 "1." 串在同一行。
            当问题涉及当前文章、相关文章、用户点赞/收藏/评论历史或 AI 使用情况时，应优先调用可用工具获取站内上下文，再组织回答。
            当检索上下文中提供了“可用站内跳转”时，如需引用相关文章，必须优先使用这些已提供的 Markdown 链接，禁止自行编造文章标题、文章 ID 或站内路径。
            当检索上下文中提供了“可用媒体资源”时，如需引用文中的图片或视频链接，必须优先使用这些已提供的 Markdown 链接，禁止自行编造资源地址。
            当检索上下文中提供了“可用分类标签跳转”时，如需推荐分类页或标签页，必须优先使用这些已提供的 Markdown 链接，禁止自行编造聚合页路径。
            """;

    /**
     * 文章嵌入 (RAG) 模式下的 System Prompt
     */
    private static final String ARTICLE_PROMPT = """
            当前会话绑定了一篇博客文章。
            你应优先依据提供的文章标题、摘要和 Markdown 摘录回答。
            如果用户的问题超出文章内容，可以先说明哪些部分来自文章，哪些部分是一般性补充。
            如果用户追问的是实时额度、用户互动历史等私有动态数据，必须依赖真实工具结果，不能根据文章内容或常识自行编造。
            当你需要当前文章更完整的元信息或用户与文章的交互历史时，应优先调用工具。
            如果要写标题，必须显式使用 Markdown 标题标记（例如 ## 或 ###），不要输出没有标记的伪标题。
            如果要写列表，每个列表项必须单独换行，不要把多个 "-" 或 "1." 串在同一行。
            如果检索片段提供了“可用站内跳转”，只有这些链接可以被当作相关文章引用输出，必须保持原始 Markdown 链接格式。
            如果检索片段提供了“可用媒体资源”或“可用分类标签跳转”，只有这些链接可以被当作资源或聚合页引用输出，必须保持原始 Markdown 链接格式。
            """;

    private final AiProperties aiProperties;
    private final AiRagProperties aiRagProperties;
    private final SysArticleMapper articleMapper;
    private final AiArticleRagService aiArticleRagService;
    private final AiArticleToolService aiArticleToolService;
    private final AiChatModelService aiChatModelService;
    private final AiConversationRoutingService aiConversationRoutingService;
    private final AiToolDisplayNameService aiToolDisplayNameService;
    private final ToolCallingManager toolCallingManager;

    public List<AiRetrievedChunkVo> retrieveCitations(SysAiConversation conversation, List<SysAiMessage> historyMessages) {
        if (conversation == null) {
            return List.of();
        }
        String latestUserQuestion = extractLatestUserQuestion(historyMessages);
        AiConversationRouteDecision routeDecision = aiConversationRoutingService.determineRoute(conversation, latestUserQuestion);
        AiConversationRoute route = resolveConversationRoute(routeDecision);

        if (route == AiConversationRoute.TOOL_FIRST || route == AiConversationRoute.DIRECT_CHAT) {
            return List.of();
        }
        if (Constants.AI_CONVERSATION_TYPE_ARTICLE.equals(conversation.getType()) && conversation.getArticleId() != null) {
            return aiArticleRagService.retrieveArticleHybridChunks(conversation.getArticleId(), latestUserQuestion);
        }
        return aiArticleRagService.retrieveGlobalChunks(latestUserQuestion);
    }

    public ChatResponse generateResponse(SysAiConversation conversation,
                                         List<SysAiMessage> historyMessages,
                                         List<AiRetrievedChunkVo> citations) {
        if (!aiProperties.isEnabled()) {
            return null;
        }
        AiResolvedChatModel resolvedChatModel = aiChatModelService.resolveConversationModel(conversation);
        ChatClient chatClient = aiChatModelService.getChatClient(resolvedChatModel);
        String latestUserQuestion = extractLatestUserQuestion(historyMessages);
        AiChatInvocation invocation = buildInvocation(chatClient, conversation, historyMessages, citations, latestUserQuestion, false);

        try {
            ChatResponse chatResponse = invocation.requestSpec()
                    .call()
                    .chatResponse();
            attachExecutionMetadata(chatResponse, invocation.toolCalls());
            if (!StringUtils.hasText(extractText(chatResponse))) {
                throw new ServiceException("AI 未返回有效内容");
            }
            return chatResponse;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AI 模型调用失败, conversationId={}", conversation.getId(), ex);
            throw new ServiceException("AI 回复生成失败，请稍后重试");
        }
    }

    public Flux<ChatResponse> streamReply(SysAiConversation conversation,
                                          List<SysAiMessage> historyMessages,
                                          List<AiRetrievedChunkVo> citations) {
        if (!aiProperties.isEnabled()) {
            return Flux.error(new ServiceException(buildNotReadyMessage()));
        }
        AiResolvedChatModel resolvedChatModel = aiChatModelService.resolveConversationModel(conversation);
        ChatClient chatClient = aiChatModelService.getChatClient(resolvedChatModel);
        String latestUserQuestion = extractLatestUserQuestion(historyMessages);
        AiChatInvocation invocation = buildInvocation(chatClient, conversation, historyMessages, citations, latestUserQuestion, true);
        return invocation.requestSpec()
                .stream()
                .chatResponse()
                .map(chatResponse -> {
                    attachExecutionMetadata(chatResponse, invocation.toolCalls());
                    return chatResponse;
                });
    }

    public String extractText(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return buildNotReadyMessage();
        }
        if (chatResponse.getResult() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    public String extractReasoning(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return "";
        }
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        Object reasoning = assistantMessage.getMetadata().get("reasoningContent");
        return reasoning == null ? "" : reasoning.toString();
    }

    public Usage extractUsage(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        return chatResponse.getMetadata().getUsage();
    }

    public List<AiToolCallVo> extractToolCalls(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return List.of();
        }
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        Object executedToolCalls = assistantMessage.getMetadata().get("executedToolCalls");
        if (executedToolCalls instanceof List<?> items && !items.isEmpty()) {
            List<AiToolCallVo> result = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof AiToolCallVo toolCallVo) {
                    result.add(toolCallVo);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (!assistantMessage.hasToolCalls()) {
            return List.of();
        }
        return assistantMessage.getToolCalls().stream()
                .map(this::toToolCallVo)
                .toList();
    }

    private AiChatInvocation buildInvocation(ChatClient chatClient,
                                             SysAiConversation conversation,
                                             List<SysAiMessage> historyMessages,
                                             List<AiRetrievedChunkVo> citations,
                                             String latestUserQuestion,
                                             boolean stream) {
        AiToolBundle toolBundle = aiArticleToolService.buildToolBundle(conversation, citations);
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(buildChatMemoryAdvisor(conversation, historyMessages))
                .advisors(buildToolCallAdvisor())
                .system(buildSystemPrompt(conversation, citations))
                .user(latestUserQuestion)
                .toolCallbacks(toolBundle.toolCallbacks())
                .toolContext(toolBundle.toolContext())
                .options(OpenAiChatOptions.builder()
                        .streamUsage(stream)
                        .build());
        return new AiChatInvocation(requestSpec, toolBundle.toolCalls());
    }

    private MessageChatMemoryAdvisor buildChatMemoryAdvisor(SysAiConversation conversation, List<SysAiMessage> historyMessages) {
        String conversationId = conversation == null || conversation.getId() == null
                ? ChatMemory.DEFAULT_CONVERSATION_ID
                : String.valueOf(conversation.getId());
        ChatMemory chatMemory = buildChatMemory(conversationId, historyMessages);
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .order(BaseAdvisor.HIGHEST_PRECEDENCE + 200)
                .conversationId(conversationId)
                .build();
    }

    private ToolCallAdvisor buildToolCallAdvisor() {
        return ToolCallAdvisor.builder()
                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                .toolCallingManager(toolCallingManager)
                .disableInternalConversationHistory()
                .streamToolCallResponses(true)
                .build();
    }

    private ChatMemory buildChatMemory(String conversationId, List<SysAiMessage> historyMessages) {
        return new TransientChatMemory(
                conversationId,
                buildMemoryMessages(historyMessages),
                Math.max(aiProperties.getMaxHistoryMessages(), 1)
        );
    }

    private List<Message> buildMemoryMessages(List<SysAiMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return List.of();
        }
        List<SysAiMessage> filteredMessages = historyMessages.stream()
                .filter(item -> !Constants.AI_MESSAGE_ROLE_TOOL.equals(item.getRole()))
                .filter(item -> !Constants.AI_MESSAGE_ROLE_SYSTEM.equals(item.getRole()))
                .toList();
        if (filteredMessages.isEmpty()) {
            return List.of();
        }
        int latestUserIndex = findLatestUserMessageIndex(filteredMessages);
        int startIndex = Math.max(filteredMessages.size() - Math.max(aiProperties.getMaxHistoryMessages(), 1), 0);
        List<Message> promptMessages = new ArrayList<>();
        for (int i = startIndex; i < filteredMessages.size(); i++) {
            if (i == latestUserIndex) {
                continue;
            }
            Message message = toPromptMessage(filteredMessages.get(i));
            if (message != null) {
                promptMessages.add(message);
            }
        }
        return promptMessages;
    }

    private int findLatestUserMessageIndex(List<SysAiMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            SysAiMessage message = messages.get(i);
            if (Constants.AI_MESSAGE_ROLE_USER.equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                return i;
            }
        }
        return -1;
    }

    private Message toPromptMessage(SysAiMessage historyMessage) {
        if (Constants.AI_MESSAGE_ROLE_USER.equals(historyMessage.getRole())) {
            return new UserMessage(historyMessage.getContent());
        }
        if (Constants.AI_MESSAGE_ROLE_ASSISTANT.equals(historyMessage.getRole())) {
            return new AssistantMessage(historyMessage.getContent());
        }
        return null;
    }

    private String buildSystemPrompt(SysAiConversation conversation, List<AiRetrievedChunkVo> citations) {
        StringBuilder promptBuilder = new StringBuilder(GLOBAL_PROMPT.trim());
        List<AiRetrievedChunkVo> retrievedChunks = citations == null ? List.of() : citations;
        if (conversation == null) {
            return promptBuilder.toString().trim();
        }
        if (Constants.AI_CONVERSATION_TYPE_ARTICLE.equals(conversation.getType()) && conversation.getArticleId() != null) {
            promptBuilder.append("\n\n").append(ARTICLE_PROMPT.trim());
            SysArticle article = articleMapper.selectById(conversation.getArticleId());
            if (article != null) {
                promptBuilder.append("\n\n当前文章信息：")
                        .append("\n标题：").append(safe(article.getTitle()))
                        .append("\n摘要：").append(buildArticleSummary(article));
                if (retrievedChunks.isEmpty()) {
                    promptBuilder.append("\nMarkdown 摘录：\n").append(buildArticleExcerpt(article));
                } else {
                    promptBuilder.append("\n相关检索片段：\n").append(buildRetrievedContext(retrievedChunks));
                }
            }
        } else if (!retrievedChunks.isEmpty()) {
            promptBuilder.append("""
                    当前问题命中了站内文章知识库，请优先依据下列检索片段回答。
                    回答时可以自然归纳，不必逐段复述；如果片段不足以支持结论，请明确说明。
                    """);
            promptBuilder.append("\n相关站内片段：\n").append(buildRetrievedContext(retrievedChunks));
        }
        return promptBuilder.toString().trim();
    }

    private String buildArticleSummary(SysArticle article) {
        if (StringUtils.hasText(article.getSummary())) {
            return article.getSummary().trim();
        }
        return safe(article.getTitle());
    }

    private String buildArticleExcerpt(SysArticle article) {
        String content = StringUtils.hasText(article.getContentMd()) ? article.getContentMd() : article.getContent();
        if (!StringUtils.hasText(content)) {
            return "暂无文章正文摘录。";
        }
        String normalized = content.replace("\r\n", "\n").trim();
        int maxLength = Math.max(aiProperties.getMaxArticleContextChars(), 1000);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n\n[内容已截断]";
    }

    private String buildRetrievedContext(List<AiRetrievedChunkVo> retrievedChunks) {
        StringBuilder builder = new StringBuilder();
        int maxContextChars = Math.max(aiRagProperties.getMaxContextChars(), 1000);
        for (int i = 0; i < retrievedChunks.size(); i++) {
            AiRetrievedChunkVo chunk = retrievedChunks.get(i);
            builder.append("[来源 ").append(i + 1).append("]")
                    .append("\n文章标题：").append(safe(chunk.getArticleTitle()))
                    .append("\n来源范围：").append(formatSourceScope(chunk.getSourceScope()))
                    .append("\n标题路径：").append(safe(chunk.getSectionPath()))
                    .append("\n片段范围：").append(formatChunkRange(chunk))
                    .append("\n块类型：").append(safe(chunk.getBlockType()))
                    .append("\n可用站内跳转：").append(formatInternalLinks(chunk.getInternalLinks()))
                    .append("\n可用媒体资源：").append(formatMediaRefs(chunk.getMediaRefs()))
                    .append("\n可用分类标签跳转：").append(formatTaxonomyLinks(chunk.getTaxonomyLinks()))
                    .append("\n内容：\n").append(safe(chunk.getContent()))
                    .append("\n\n");
            if (builder.length() >= maxContextChars) {
                return builder.substring(0, maxContextChars) + "\n[检索上下文已截断]";
            }
        }
        return builder.toString().trim();
    }

    private String extractLatestUserQuestion(List<SysAiMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "";
        }
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            SysAiMessage message = historyMessages.get(i);
            if (Constants.AI_MESSAGE_ROLE_USER.equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return "";
    }

    private String buildNotReadyMessage() {
        return """
                AI 服务当前不可用，请稍后重试。
                """.trim();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未命名";
    }

    private void attachExecutionMetadata(ChatResponse chatResponse, List<AiToolCallVo> toolCalls) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return;
        }
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        assistantMessage.getMetadata().put("executedToolCalls", toolCalls);
    }

    private AiToolCallVo toToolCallVo(ToolCall toolCall) {
        AiToolCallVo vo = new AiToolCallVo();
        vo.setId(toolCall.id());
        vo.setType(toolCall.type());
        vo.setName(toolCall.name());
        vo.setDisplayName(aiToolDisplayNameService.resolveToolDisplayName(toolCall.name()));
        vo.setArguments(toolCall.arguments());
        vo.setStatus("requested");
        return vo;
    }

    private AiConversationRoute resolveConversationRoute(AiConversationRouteDecision decision) {
        if (decision == null || !StringUtils.hasText(decision.getRoute())) {
            return AiConversationRoute.DIRECT_CHAT;
        }
        try {
            return AiConversationRoute.valueOf(decision.getRoute().trim());
        } catch (IllegalArgumentException ex) {
            return AiConversationRoute.DIRECT_CHAT;
        }
    }

    private String formatChunkRange(AiRetrievedChunkVo chunk) {
        Integer startOrder = chunk.getChunkOrder();
        Integer endOrder = chunk.getEndChunkOrder();
        Integer mergedChunkCount = chunk.getMergedChunkCount();
        if (startOrder == null) {
            return "未标注";
        }
        if (endOrder != null && !startOrder.equals(endOrder)) {
            return startOrder + " - " + endOrder + "（合并 " + (mergedChunkCount == null ? 2 : mergedChunkCount) + " 段）";
        }
        return String.valueOf(startOrder);
    }

    private String formatSourceScope(String sourceScope) {
        if (!StringUtils.hasText(sourceScope)) {
            return "未标注";
        }
        return switch (sourceScope) {
            case "article" -> "当前文章";
            case "global" -> "全站补充";
            default -> sourceScope;
        };
    }

    private String formatInternalLinks(List<AiChunkInternalLink> internalLinks) {
        if (internalLinks == null || internalLinks.isEmpty()) {
            return "无";
        }
        return internalLinks.stream()
                .filter(Objects::nonNull)
                .map(link -> "[" + safe(link.targetArticleTitle()) + "](" + safe(link.targetPath()) + ")")
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
    }

    private String formatMediaRefs(List<AiChunkMediaRef> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return "无";
        }
        return mediaRefs.stream()
                .filter(Objects::nonNull)
                .map(ref -> "[" + safe(ref.mediaType()) + "：" + safe(ref.displayText()) + "](" + safe(ref.sourceUrl()) + ")")
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
    }

    private String formatTaxonomyLinks(List<AiChunkTaxonomyLink> taxonomyLinks) {
        if (taxonomyLinks == null || taxonomyLinks.isEmpty()) {
            return "无";
        }
        return taxonomyLinks.stream()
                .filter(Objects::nonNull)
                .map(link -> "[" + safe(link.displayName()) + "](" + safe(link.targetPath()) + ")")
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
    }

    /**
     * 请求期 ChatMemory：以数据库历史为种子，只在当前一次模型调用及其 tool loop 内追加消息。
     */
    private static final class TransientChatMemory implements ChatMemory {

        private final String conversationId;
        private final int maxMessages;
        private final List<Message> messages;

        private TransientChatMemory(String conversationId, List<Message> seedMessages, int maxMessages) {
            this.conversationId = conversationId;
            this.maxMessages = Math.max(maxMessages, 1);
            this.messages = new ArrayList<>(seedMessages == null ? List.of() : seedMessages);
            trimToWindow();
        }

        @Override
        public synchronized void add(@NonNull String conversationId, @NonNull List<Message> messages) {
            if (!Objects.equals(this.conversationId, conversationId) || messages.isEmpty()) {
                return;
            }
            this.messages.addAll(messages.stream().filter(Objects::nonNull).toList());
            trimToWindow();
        }

        @Override
        public synchronized @NonNull List<Message> get(@NonNull String conversationId) {
            if (!Objects.equals(this.conversationId, conversationId)) {
                return List.of();
            }
            return new ArrayList<>(this.messages);
        }

        @Override
        public synchronized void clear(@NonNull String conversationId) {
            if (Objects.equals(this.conversationId, conversationId)) {
                this.messages.clear();
            }
        }

        private void trimToWindow() {
            int overflow = this.messages.size() - this.maxMessages;
            if (overflow <= 0) {
                return;
            }
            this.messages.subList(0, overflow).clear();
        }
    }
}
