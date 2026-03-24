package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.dto.ai.AiConversationCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiSendMessageDto;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.entity.SysAiMessage;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysAiConversationMapper;
import top.fxmarkbrown.blog.mapper.SysAiMessageMapper;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.service.AiConversationService;
import top.fxmarkbrown.blog.service.AiChatService;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.model.ai.AiChunkInternalLink;
import top.fxmarkbrown.blog.model.ai.AiChunkMediaRef;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.model.ai.AiChunkTaxonomyLink;
import top.fxmarkbrown.blog.vo.ai.AiChatModelOptionVo;
import top.fxmarkbrown.blog.vo.ai.AiConversationDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiConversationListVo;
import top.fxmarkbrown.blog.vo.ai.AiMessageVo;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;
import top.fxmarkbrown.blog.vo.ai.AiStreamEventVo;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiConversationServiceImpl implements AiConversationService {

    private static final String GLOBAL_TITLE = "新的全局对话";
    private static final String GLOBAL_SUMMARY = "站内 AI 对话支持全站问答、文章检索与引用片段展示。";
    private static final String GLOBAL_SYSTEM_MESSAGE = "当前会话为全局对话，你可以直接追问站内文章、技术问题或功能设计。";
    private static final String GLOBAL_ASSISTANT_MESSAGE = "全局会话已创建成功。我会结合当前模型配置与站内知识继续回答你的问题。";
    private static final String ARTICLE_SUMMARY_PREFIX = "围绕文章继续追问：";
    private static final String ARTICLE_SYSTEM_TEMPLATE = "当前会话已绑定文章《%s》，后续回答会优先结合文章 Markdown 内容。";
    private static final String ARTICLE_ASSISTANT_TEMPLATE = "文章会话已创建成功。我会优先基于《%s》的内容继续回答你的问题。";

    private final SysAiConversationMapper conversationMapper;
    private final SysAiMessageMapper messageMapper;
    private final SysArticleMapper articleMapper;
    private final AiChatService aiChatService;
    private final AiChatModelService aiChatModelService;
    private final AiQuotaCoreService aiQuotaService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationDetailVo createGlobalConversation(AiConversationCreateDto createDto) {
        Long userId = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();
        AiResolvedChatModel selectedModel = resolveSelectedModel(createDto);
        SysAiConversation conversation = SysAiConversation.builder()
                .userId(userId)
                .type(Constants.AI_CONVERSATION_TYPE_GLOBAL)
                .title(GLOBAL_TITLE)
                .summary(GLOBAL_SUMMARY)
                .modelProvider(selectedModel.providerName())
                .modelName(selectedModel.modelName())
                .lastMessageAt(now)
                .build();
        conversationMapper.insert(conversation);
        insertSeedMessages(conversation.getId(), GLOBAL_SYSTEM_MESSAGE, GLOBAL_ASSISTANT_MESSAGE);
        SysAiConversation savedConversation = getOwnedConversation(conversation.getId());
        return toDetailVo(savedConversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationDetailVo createArticleConversation(Long articleId, AiConversationCreateDto createDto) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ServiceException("文章不存在");
        }
        String articleTitle = article.getTitle();
        AiResolvedChatModel selectedModel = resolveSelectedModel(createDto);
        SysAiConversation conversation = SysAiConversation.builder()
                .userId(userId)
                .type(Constants.AI_CONVERSATION_TYPE_ARTICLE)
                .articleId(articleId)
                .title("聊聊：《" + articleTitle + "》")
                .summary(ARTICLE_SUMMARY_PREFIX + buildArticleSummary(article))
                .modelProvider(selectedModel.providerName())
                .modelName(selectedModel.modelName())
                .lastMessageAt(LocalDateTime.now())
                .build();
        conversationMapper.insert(conversation);
        insertSeedMessages(
                conversation.getId(),
                ARTICLE_SYSTEM_TEMPLATE.formatted(articleTitle),
                ARTICLE_ASSISTANT_TEMPLATE.formatted(articleTitle)
        );
        SysAiConversation savedConversation = getOwnedConversation(conversation.getId());
        return toDetailVo(savedConversation);
    }

    @Override
    public List<AiChatModelOptionVo> listChatModels() {
        return aiChatModelService.listAvailableModels();
    }

    @Override
    public AiConversationDetailVo getConversationDetail(Long conversationId) {
        return toDetailVo(getOwnedConversation(conversationId));
    }

    @Override
    public IPage<AiConversationListVo> pageConversations(String type) {
        LambdaQueryWrapper<SysAiConversation> wrapper = new LambdaQueryWrapper<SysAiConversation>()
                .eq(SysAiConversation::getUserId, StpUtil.getLoginIdAsLong())
                .orderByDesc(SysAiConversation::getLastMessageAt)
                .orderByDesc(SysAiConversation::getId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(SysAiConversation::getType, type);
        }
        Page<SysAiConversation> page = conversationMapper.selectPage(PageUtil.getPage(), wrapper);
        Page<AiConversationListVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toListVo).toList());
        return result;
    }

    @Override
    public IPage<AiMessageVo> pageMessages(Long conversationId) {
        getOwnedConversation(conversationId);
        Page<SysAiMessage> page = messageMapper.selectPage(
                PageUtil.getPage(),
                new LambdaQueryWrapper<SysAiMessage>()
                        .eq(SysAiMessage::getConversationId, conversationId)
                        .orderByAsc(SysAiMessage::getCreateTime)
                        .orderByAsc(SysAiMessage::getId)
        );
        Page<AiMessageVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toMessageVo).toList());
        return result;
    }

    @Override
    public SseEmitter streamMessage(Long conversationId, AiSendMessageDto sendMessageDto) {
        SysAiConversation conversation = getOwnedConversation(conversationId);
        String content = normalizeMessageContent(sendMessageDto);
        aiQuotaService.assertRequestQuota(conversation.getUserId());

        SysAiMessage userMessage = SysAiMessage.builder()
                .conversationId(conversationId)
                .role(Constants.AI_MESSAGE_ROLE_USER)
                .content(content)
                .build();
        messageMapper.insert(userMessage);

        List<SysAiMessage> historyMessages = listConversationMessages(conversationId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());
        AtomicReference<StringBuilder> reasoningBuilder = new AtomicReference<>(new StringBuilder());
        AtomicReference<List<AiRetrievedChunkVo>> citationsRef = new AtomicReference<>(aiChatService.retrieveCitations(conversation, historyMessages));
        AtomicReference<List<AiToolCallVo>> toolCallsRef = new AtomicReference<>(List.of());
        AtomicInteger tokensIn = new AtomicInteger(0);
        AtomicInteger tokensOut = new AtomicInteger(0);
        AtomicInteger totalTokens = new AtomicInteger(0);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        emitter.onCompletion(() -> closeStream(emitterClosed, disposableRef));
        emitter.onTimeout(() -> closeStream(emitterClosed, disposableRef));
        emitter.onError(error -> closeStream(emitterClosed, disposableRef));

        sendStreamEvent(emitter, emitterClosed, disposableRef, "user", eventWithMessage("user", toMessageVo(userMessage)));

        Disposable disposable = aiChatService.streamReply(conversation, historyMessages, citationsRef.get()).subscribe(
                chatResponse -> handleStreamChunk(emitter, emitterClosed, chatResponse, answerBuilder, reasoningBuilder,
                        toolCallsRef, tokensIn, tokensOut, totalTokens, disposableRef),
                error -> {
                    log.error("AI 流式回复失败, conversationId={}", conversationId, error);
                    sendStreamEvent(emitter, emitterClosed, disposableRef, "error", eventWithError(error.getMessage()));
                    emitter.complete();
                },
                () -> completeStreamConversation(emitter, emitterClosed, conversation, content, answerBuilder, reasoningBuilder,
                        citationsRef, toolCallsRef, tokensIn, tokensOut, totalTokens, disposableRef)
        );
        disposableRef.set(disposable);
        return emitter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(Long conversationId, String title) {
        SysAiConversation conversation = getOwnedConversation(conversationId);
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty()) {
            throw new ServiceException("会话标题不能为空");
        }
        if (normalizedTitle.length() > 60) {
            throw new ServiceException("会话标题不能超过 60 个字符");
        }
        conversation.setTitle(normalizedTitle);
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        getOwnedConversation(conversationId);
        messageMapper.delete(new LambdaQueryWrapper<SysAiMessage>()
                .eq(SysAiMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    private SysAiConversation getOwnedConversation(Long conversationId) {
        SysAiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ServiceException("AI 会话不存在");
        }
        long currentUserId = StpUtil.getLoginIdAsLong();
        if (!Objects.equals(conversation.getUserId(), currentUserId)) {
            throw new ServiceException("无权访问该 AI 会话");
        }
        return conversation;
    }

    private void handleStreamChunk(SseEmitter emitter, AtomicBoolean emitterClosed, ChatResponse chatResponse,
                                   AtomicReference<StringBuilder> answerBuilder, AtomicReference<StringBuilder> reasoningBuilder,
                                   AtomicReference<List<AiToolCallVo>> toolCallsRef,
                                   AtomicInteger tokensIn, AtomicInteger tokensOut, AtomicInteger totalTokens,
                                   AtomicReference<Disposable> disposableRef) {
        String answerDelta = aiChatService.extractText(chatResponse);
        String reasoningDelta = aiChatService.extractReasoning(chatResponse);
        List<AiToolCallVo> toolCalls = aiChatService.extractToolCalls(chatResponse);
        if (StringUtils.hasText(answerDelta)) {
            answerBuilder.get().append(answerDelta);
        }
        if (StringUtils.hasText(reasoningDelta)) {
            reasoningBuilder.get().append(reasoningDelta);
        }
        if (!toolCalls.isEmpty()) {
            toolCallsRef.set(toolCalls);
        }
        Usage usage = aiChatService.extractUsage(chatResponse);
        if (usage != null) {
            tokensIn.set(defaultInt(usage.getPromptTokens()));
            tokensOut.set(defaultInt(usage.getCompletionTokens()));
            totalTokens.set(defaultInt(usage.getTotalTokens()));
        }
        if (!StringUtils.hasText(answerDelta) && !StringUtils.hasText(reasoningDelta) && toolCalls.isEmpty()) {
            return;
        }
        AiStreamEventVo event = new AiStreamEventVo();
        event.setType("delta");
        event.setContent(answerDelta);
        event.setReasoningContent(reasoningDelta);
        event.setToolCalls(toolCalls);
        sendStreamEvent(emitter, emitterClosed, disposableRef, "delta", event);
    }

    private void completeStreamConversation(SseEmitter emitter, AtomicBoolean emitterClosed, SysAiConversation conversation,
                                            String question, AtomicReference<StringBuilder> answerBuilder,
                                            AtomicReference<StringBuilder> reasoningBuilder,
                                            AtomicReference<List<AiRetrievedChunkVo>> citationsRef,
                                            AtomicReference<List<AiToolCallVo>> toolCallsRef,
                                            AtomicInteger tokensIn, AtomicInteger tokensOut, AtomicInteger totalTokens,
                                            AtomicReference<Disposable> disposableRef) {
        String answer = answerBuilder.get().toString().trim();
        if (!StringUtils.hasText(answer)) {
            answer = "模型未返回有效内容";
        }
        String reasoningContent = reasoningBuilder.get().toString().trim();
        List<AiToolCallVo> toolCalls = toolCallsRef.get();
        SysAiMessage assistantMessage = SysAiMessage.builder()
                .conversationId(conversation.getId())
                .role(Constants.AI_MESSAGE_ROLE_ASSISTANT)
                .content(answer)
                .quotePayload(buildQuotePayload(reasoningContent, citationsRef.get(), toolCalls))
                .tokensIn(zeroToNull(tokensIn.get()))
                .tokensOut(zeroToNull(tokensOut.get()))
                .build();
        messageMapper.insert(assistantMessage);
        aiQuotaService.consumeTokens(
                conversation.getUserId(),
                applyModelQuotaMultiplier(resolveConsumedTokens(question, answer, reasoningContent, tokensIn.get(), tokensOut.get(), totalTokens.get()), conversation),
                conversation.getId(),
                conversation.getTitle()
        );

        conversationMapper.updateById(SysAiConversation.builder()
                .id(conversation.getId())
                .summary(buildConversationSummary(question))
                .modelProvider(conversation.getModelProvider())
                .modelName(conversation.getModelName())
                .lastMessageAt(LocalDateTime.now())
                .build());

        AiStreamEventVo event = new AiStreamEventVo();
        event.setType("done");
        event.setMessage(toMessageVo(assistantMessage));
        event.setToolCalls(toolCalls);
        event.setTokensIn(zeroToNull(tokensIn.get()));
        event.setTokensOut(zeroToNull(tokensOut.get()));
        event.setTotalTokens(zeroToNull(totalTokens.get()));
        sendStreamEvent(emitter, emitterClosed, disposableRef, "done", event);
        emitter.complete();
    }

    private void sendStreamEvent(SseEmitter emitter, AtomicBoolean emitterClosed,
                                 AtomicReference<Disposable> disposableRef,
                                 String eventName, AiStreamEventVo event) {
        if (emitterClosed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            closeStream(emitterClosed, disposableRef);
            log.warn("AI 流事件发送失败, event={}", eventName, ex);
        }
    }

    private void closeStream(AtomicBoolean emitterClosed, AtomicReference<Disposable> disposableRef) {
        emitterClosed.set(true);
        Disposable disposable = disposableRef.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private AiStreamEventVo eventWithMessage(String type, AiMessageVo message) {
        AiStreamEventVo event = new AiStreamEventVo();
        event.setType(type);
        event.setMessage(message);
        return event;
    }

    private AiStreamEventVo eventWithError(String message) {
        AiStreamEventVo event = new AiStreamEventVo();
        event.setType("error");
        event.setErrorMessage(message == null ? "AI 回复生成失败，请稍后重试" : message);
        return event;
    }

    private String normalizeMessageContent(AiSendMessageDto sendMessageDto) {
        String content = sendMessageDto == null ? "" : sendMessageDto.getContent();
        content = content == null ? "" : content.trim();
        if (!StringUtils.hasText(content)) {
            throw new ServiceException("消息内容不能为空");
        }
        if (content.length() > 4000) {
            throw new ServiceException("消息内容不能超过 4000 个字符");
        }
        return content;
    }

    private List<SysAiMessage> listConversationMessages(Long conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<SysAiMessage>()
                        .eq(SysAiMessage::getConversationId, conversationId)
                        .orderByAsc(SysAiMessage::getCreateTime)
                        .orderByAsc(SysAiMessage::getId)
        );
    }

    private String buildQuotePayload(String reasoningContent,
                                     List<AiRetrievedChunkVo> citations,
                                     List<AiToolCallVo> toolCalls) {
        boolean hasReasoning = StringUtils.hasText(reasoningContent);
        boolean hasCitations = citations != null && !citations.isEmpty();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (!hasReasoning && !hasCitations && !hasToolCalls) {
            return null;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        if (hasReasoning) {
            payload.put("reasoningContent", reasoningContent);
        }
        if (hasCitations) {
            payload.put("citations", citations.stream().map(this::toCitationPayload).toList());
        }
        if (hasToolCalls) {
            payload.put("toolCalls", toolCalls);
        }
        return JsonUtil.toJsonString(payload);
    }

    private Integer zeroToNull(int value) {
        return value > 0 ? value : null;
    }

    private long resolveConsumedTokens(String question, String answer, String reasoningContent, Usage usage) {
        return resolveConsumedTokens(
                question,
                answer,
                reasoningContent,
                usage == null ? 0 : defaultInt(usage.getPromptTokens()),
                usage == null ? 0 : defaultInt(usage.getCompletionTokens()),
                usage == null ? 0 : defaultInt(usage.getTotalTokens())
        );
    }

    private long resolveConsumedTokens(String question, String answer, String reasoningContent,
                                       int tokensIn, int tokensOut, int totalTokens) {
        if (totalTokens > 0) {
            return totalTokens;
        }
        int combined = tokensIn + tokensOut;
        if (combined > 0) {
            return combined;
        }
        return estimateTokens(question) + estimateTokens(answer) + estimateTokens(reasoningContent);
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (content.trim().length() + 3) / 4);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String buildConversationSummary(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "新会话";
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) + "..." : normalized;
    }

    private void insertSeedMessages(Long conversationId, String systemContent, String assistantContent) {
        messageMapper.insert(SysAiMessage.builder()
                .conversationId(conversationId)
                .role(Constants.AI_MESSAGE_ROLE_SYSTEM)
                .content(systemContent)
                .build());
        messageMapper.insert(SysAiMessage.builder()
                .conversationId(conversationId)
                .role(Constants.AI_MESSAGE_ROLE_ASSISTANT)
                .content(assistantContent)
                .build());
        SysAiConversation update = SysAiConversation.builder()
                .id(conversationId)
                .lastMessageAt(LocalDateTime.now())
                .build();
        conversationMapper.updateById(update);
    }

    private String buildArticleSummary(SysArticle article) {
        if (article.getSummary() != null && !article.getSummary().isBlank()) {
            return article.getSummary().trim();
        }
        String content = article.getContentMd();
        if (content == null || content.isBlank()) {
            return article.getTitle();
        }
        String plainText = content
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]+\\)", " ")
                .replaceAll("\\[[^]]*]\\([^)]+\\)", " ")
                .replaceAll("[>#*_`~-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plainText.isEmpty()) {
            return article.getTitle();
        }
        return plainText.length() > 120 ? plainText.substring(0, 120) + "..." : plainText;
    }

    private AiResolvedChatModel resolveSelectedModel(AiConversationCreateDto createDto) {
        String modelId = createDto == null ? null : createDto.getModelId();
        if (!StringUtils.hasText(modelId)) {
            return aiChatModelService.getDefaultModel();
        }
        return aiChatModelService.requireModel(modelId.trim());
    }

    private long applyModelQuotaMultiplier(long baseTokens, SysAiConversation conversation) {
        if (baseTokens <= 0) {
            return 0L;
        }
        double multiplier = aiChatModelService.resolveConversationModel(conversation).quotaMultiplier();
        double normalizedMultiplier = multiplier > 0 ? multiplier : 1D;
        return Math.max(1L, (long) Math.ceil(baseTokens * normalizedMultiplier));
    }

    private AiConversationListVo toListVo(SysAiConversation conversation) {
        AiConversationListVo vo = new AiConversationListVo();
        vo.setId(conversation.getId());
        vo.setType(conversation.getType());
        vo.setArticleId(conversation.getArticleId());
        vo.setTitle(conversation.getTitle());
        vo.setSummary(conversation.getSummary());
        vo.setModelProvider(conversation.getModelProvider());
        vo.setModelName(conversation.getModelName());
        vo.setModelId(aiChatModelService.resolveModelId(conversation.getModelProvider(), conversation.getModelName()));
        vo.setModelDisplayName(aiChatModelService.resolveDisplayName(conversation.getModelProvider(), conversation.getModelName()));
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setCreateTime(conversation.getCreateTime());
        return vo;
    }

    private AiConversationDetailVo toDetailVo(SysAiConversation conversation) {
        AiConversationDetailVo vo = new AiConversationDetailVo();
        vo.setId(conversation.getId());
        vo.setUserId(conversation.getUserId());
        vo.setType(conversation.getType());
        vo.setArticleId(conversation.getArticleId());
        vo.setTitle(conversation.getTitle());
        vo.setSummary(conversation.getSummary());
        vo.setModelProvider(conversation.getModelProvider());
        vo.setModelName(conversation.getModelName());
        vo.setModelId(aiChatModelService.resolveModelId(conversation.getModelProvider(), conversation.getModelName()));
        vo.setModelDisplayName(aiChatModelService.resolveDisplayName(conversation.getModelProvider(), conversation.getModelName()));
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setCreateTime(conversation.getCreateTime());
        vo.setUpdateTime(conversation.getUpdateTime());
        return vo;
    }

    private AiMessageVo toMessageVo(SysAiMessage message) {
        AiMessageVo vo = new AiMessageVo();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setReasoningContent(parseReasoningContent(message.getQuotePayload()));
        vo.setTokensIn(message.getTokensIn());
        vo.setTokensOut(message.getTokensOut());
        vo.setQuotePayload(message.getQuotePayload());
        vo.setToolCalls(parseToolCalls(message.getQuotePayload()));
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private String parseReasoningContent(String quotePayload) {
        if (!StringUtils.hasText(quotePayload)) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.readTree(quotePayload);
            if (root == null || !root.hasNonNull("reasoningContent")) {
                return null;
            }
            return root.get("reasoningContent").asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> toCitationPayload(AiRetrievedChunkVo chunk) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("articleId", chunk.getArticleId());
        payload.put("articleTitle", chunk.getArticleTitle());
        payload.put("chunkId", chunk.getChunkId());
        payload.put("chunkOrder", chunk.getChunkOrder());
        payload.put("endChunkOrder", chunk.getEndChunkOrder());
        payload.put("mergedChunkCount", chunk.getMergedChunkCount());
        payload.put("sectionPath", chunk.getSectionPath());
        payload.put("headingLevel", chunk.getHeadingLevel());
        payload.put("blockType", chunk.getBlockType());
        payload.put("sourceScope", chunk.getSourceScope());
        payload.put("content", chunk.getContent());
        payload.put("contentPreview", chunk.getContentPreview());
        payload.put("internalLinks", chunk.getInternalLinks() == null ? List.of() : chunk.getInternalLinks().stream()
                .map(this::toInternalLinkPayload)
                .toList());
        payload.put("mediaRefs", chunk.getMediaRefs() == null ? List.of() : chunk.getMediaRefs().stream()
                .map(this::toMediaRefPayload)
                .toList());
        payload.put("taxonomyLinks", chunk.getTaxonomyLinks() == null ? List.of() : chunk.getTaxonomyLinks().stream()
                .map(this::toTaxonomyLinkPayload)
                .toList());
        return payload;
    }

    private Map<String, Object> toInternalLinkPayload(AiChunkInternalLink link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anchorText", link.anchorText());
        payload.put("targetPath", link.targetPath());
        payload.put("targetArticleId", link.targetArticleId());
        payload.put("targetArticleTitle", link.targetArticleTitle());
        return payload;
    }

    private Map<String, Object> toMediaRefPayload(AiChunkMediaRef mediaRef) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mediaType", mediaRef.mediaType());
        payload.put("sourceUrl", mediaRef.sourceUrl());
        payload.put("displayText", mediaRef.displayText());
        payload.put("localResource", mediaRef.localResource());
        return payload;
    }

    private Map<String, Object> toTaxonomyLinkPayload(AiChunkTaxonomyLink link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taxonomyType", link.taxonomyType());
        payload.put("targetPath", link.targetPath());
        payload.put("targetId", link.targetId());
        payload.put("displayName", link.displayName());
        return payload;
    }

    private List<AiToolCallVo> parseToolCalls(String quotePayload) {
        if (!StringUtils.hasText(quotePayload)) {
            return List.of();
        }
        try {
            JsonNode root = JsonUtil.readTree(quotePayload);
            JsonNode toolCallsNode = root == null ? null : root.get("toolCalls");
            if (toolCallsNode == null || !toolCallsNode.isArray()) {
                return List.of();
            }
            List<AiToolCallVo> toolCalls = JsonUtil.convertValue(toolCallsNode, new TypeReference<>() {
            });
            return toolCalls == null ? List.of() : toolCalls;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
