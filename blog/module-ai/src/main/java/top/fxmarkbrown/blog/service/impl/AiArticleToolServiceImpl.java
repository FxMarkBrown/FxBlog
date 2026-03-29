package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysCommentMapper;
import top.fxmarkbrown.blog.model.ai.AiCallDisplayNameResolver;
import top.fxmarkbrown.blog.model.ai.AiArticleHistoryToolInput;
import top.fxmarkbrown.blog.model.ai.AiArticleSearchToolInput;
import top.fxmarkbrown.blog.model.ai.AiToolBundle;
import top.fxmarkbrown.blog.service.AiArticleRagService;
import top.fxmarkbrown.blog.service.AiArticleToolService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.vo.ai.AiQuotaSnapshotVo;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;
import top.fxmarkbrown.blog.vo.article.ArticleDetailVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiArticleToolServiceImpl implements AiArticleToolService {

    private static final String TOOL_GET_CURRENT_ARTICLE_CONTEXT = "getCurrentArticleContext";
    private static final String TOOL_SEARCH_RELEVANT_ARTICLES = "searchRelevantArticles";
    private static final String TOOL_GET_MY_LIKED_ARTICLES = "getMyLikedArticles";
    private static final String TOOL_GET_MY_FAVORITE_ARTICLES = "getMyFavoriteArticles";
    private static final String TOOL_GET_MY_COMMENTED_ARTICLES = "getMyCommentedArticles";
    private static final String TOOL_GET_MY_AI_USAGE_SNAPSHOT = "getMyAiUsageSnapshot";
    private static final int DEFAULT_HISTORY_LIMIT = 5;
    private static final String TOOL_CONTEXT_CONVERSATION = "conversation";
    private static final String TOOL_CONTEXT_CITATIONS = "citations";
    private static final String TOOL_CONTEXT_RECORDER = "toolRecorder";

    private final SysArticleMapper articleMapper;
    private final SysCommentMapper commentMapper;
    private final AiArticleRagService aiArticleRagService;
    private final AiQuotaCoreService aiQuotaCoreService;

    @Override
    public AiToolBundle buildToolBundle(SysAiConversation conversation, List<AiRetrievedChunkVo> citations) {
        List<AiToolCallVo> toolCalls = new ArrayList<>();
        Map<String, Object> toolContext = new LinkedHashMap<>();
        toolContext.put(TOOL_CONTEXT_CONVERSATION, conversation);
        toolContext.put(TOOL_CONTEXT_CITATIONS, citations == null ? List.of() : citations);
        toolContext.put(TOOL_CONTEXT_RECORDER, toolCalls);
        putIfNotNull(toolContext, "conversationId", conversation == null ? null : conversation.getId());
        putIfNotNull(toolContext, "conversationType", conversation == null ? null : conversation.getType());
        putIfNotNull(toolContext, "articleId", conversation == null ? null : conversation.getArticleId());
        putIfNotNull(toolContext, "userId", conversation == null ? null : conversation.getUserId());
        toolContext.put("citationCount", citations == null ? 0 : citations.size());
        List<ToolCallback> callbacks = Arrays.stream(ToolCallbacks.from(new RequestScopedAiArticleTools())).toList();
        return new AiToolBundle(List.copyOf(callbacks), toolContext, toolCalls);
    }

    private Map<String, Object> getCurrentArticleContext(SysAiConversation conversation, List<AiRetrievedChunkVo> citations) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (conversation == null) {
            result.put("mode", "unknown");
            result.put("hasBoundArticle", false);
            result.put("message", "当前没有可用会话上下文。");
            return result;
        }
        if (Constants.AI_CONVERSATION_TYPE_ARTICLE.equals(conversation.getType()) && conversation.getArticleId() != null) {
            ArticleDetailVo detail = articleMapper.getArticleDetail(conversation.getArticleId());
            result.put("mode", "article");
            result.put("hasBoundArticle", detail != null);
            if (detail == null) {
                result.put("message", "当前会话绑定文章不存在或不可访问。");
                return result;
            }
            result.put("article", buildArticleContext(detail));
            return result;
        }
        result.put("mode", "global");
        result.put("hasBoundArticle", false);
        result.put("message", "当前是全局会话，没有直接绑定文章。");
        result.put("retrievedArticles", summarizeRetrievedArticles(citations, 5));
        return result;
    }

    private Map<String, Object> searchRelevantArticles(AiArticleSearchToolInput input) {
        String query = input == null ? "" : safe(input.query());
        int limit = normalizeLimit(input == null ? null : input.limit());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        if (!StringUtils.hasText(query)) {
            result.put("articles", List.of());
            result.put("message", "查询关键词为空，未执行文章检索。");
            return result;
        }
        List<AiRetrievedChunkVo> chunks = aiArticleRagService.retrieveGlobalChunks(query);
        result.put("articles", summarizeRetrievedArticles(chunks, limit));
        result.put("hitCount", chunks.size());
        return result;
    }

    private Map<String, Object> getMyLikedArticles(SysAiConversation conversation, AiArticleHistoryToolInput input) {
        return buildUserArticleHistoryPayload("liked", queryArticlePage(conversation, input, articleMapper::selectMyLike));
    }

    private Map<String, Object> getMyFavoriteArticles(SysAiConversation conversation, AiArticleHistoryToolInput input) {
        return buildUserArticleHistoryPayload("favorited", queryArticlePage(conversation, input, articleMapper::selectMyFavorite));
    }

    private Map<String, Object> getMyCommentedArticles(SysAiConversation conversation, AiArticleHistoryToolInput input) {
        long userId = resolveUserId(conversation);
        int limit = normalizeLimit(input == null ? null : input.limit());
        IPage<CommentListVo> page = commentMapper.selectMyComment(new Page<>(1, limit), userId);
        LinkedHashSet<Long> seenArticleIds = new LinkedHashSet<>();
        List<Map<String, Object>> articles = new ArrayList<>();
        for (CommentListVo item : page.getRecords()) {
            if (item == null || item.getArticleId() == null) {
                continue;
            }
            Long articleId = item.getArticleId();
            if (!seenArticleIds.add(articleId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("articleId", articleId);
            row.put("title", safe(item.getArticleTitle()));
            row.put("commentSnippet", truncate(item.getContent(), 120));
            row.put("commentTime", item.getCreateTime());
            articles.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "commented");
        result.put("count", articles.size());
        result.put("articles", articles);
        return result;
    }

    private Map<String, Object> getMyAiUsageSnapshot(SysAiConversation conversation) {
        AiQuotaSnapshotVo snapshot = aiQuotaCoreService.getQuotaSnapshot(resolveUserId(conversation));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", snapshot.getEnabled());
        result.put("availableTokens", snapshot.getAvailableTokens());
        result.put("usedTokens", snapshot.getUsedTokens());
        result.put("totalEarnedTokens", snapshot.getTotalEarnedTokens());
        result.put("likedArticleCount", snapshot.getLikedArticleCount());
        result.put("favoriteArticleCount", snapshot.getFavoriteArticleCount());
        result.put("articleCount", snapshot.getArticleCount());
        result.put("cumulativeSignDays", snapshot.getCumulativeSignDays());
        result.put("lastConsumeAt", snapshot.getLastConsumeAt());
        return result;
    }

    private IPage<ArticleListVo> queryArticlePage(SysAiConversation conversation,
                                                  AiArticleHistoryToolInput input,
                                                  ArticlePageFetcher fetcher) {
        return fetcher.fetch(new Page<>(1, normalizeLimit(input == null ? null : input.limit())), resolveUserId(conversation));
    }

    private Map<String, Object> buildUserArticleHistoryPayload(String type, IPage<ArticleListVo> page) {
        List<Map<String, Object>> articles = page.getRecords().stream()
                .filter(Objects::nonNull)
                .map(this::toArticleHistoryRow)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("count", articles.size());
        result.put("articles", articles);
        return result;
    }

    private List<Map<String, Object>> summarizeRetrievedArticles(List<AiRetrievedChunkVo> citations, int limit) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (AiRetrievedChunkVo item : citations) {
            if (item == null || item.getArticleId() == null) {
                continue;
            }
            grouped.computeIfAbsent(item.getArticleId(), articleId -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("articleId", articleId);
                row.put("title", safe(item.getArticleTitle()));
                row.put("sectionPath", safe(item.getSectionPath()));
                row.put("preview", safe(item.getContentPreview()));
                row.put("sourceScope", safe(item.getSourceScope()));
                return row;
            });
            if (grouped.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private Map<String, Object> buildArticleContext(ArticleDetailVo detail) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("articleId", detail.getId());
        article.put("title", safe(detail.getTitle()));
        article.put("summary", truncate(firstNotBlank(detail.getContentMd(), detail.getContent()), 280));
        article.put("categoryName", detail.getCategory() == null ? "" : safe(detail.getCategory().getName()));
        article.put("commentNum", detail.getCommentNum());
        article.put("likeNum", detail.getLikeNum());
        article.put("favoriteNum", detail.getFavoriteNum());
        article.put("createTime", detail.getCreateTime());
        return article;
    }

    private Map<String, Object> toArticleHistoryRow(ArticleListVo article) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("articleId", article.getId());
        row.put("title", safe(article.getTitle()));
        row.put("summary", truncate(article.getSummary(), 120));
        row.put("categoryName", safe(article.getCategoryName()));
        row.put("createTime", article.getCreateTime());
        return row;
    }

    private Map<String, Object> executeTool(String toolName,
                                            String arguments,
                                            List<AiToolCallVo> recorder,
                                            ToolExecutor executor) {
        AiToolCallVo trace = new AiToolCallVo();
        trace.setType("function");
        trace.setName(toolName);
        trace.setDisplayName(AiCallDisplayNameResolver.resolveToolDisplayName(toolName));
        trace.setArguments(arguments);
        trace.setStatus("running");
        recorder.add(trace);
        try {
            Map<String, Object> result = executor.execute();
            trace.setStatus("completed");
            trace.setResult(toJsonArguments(result));
            return result;
        } catch (Exception ex) {
            trace.setStatus("failed");
            trace.setErrorMessage(ex.getMessage());
            throw ex;
        }
    }

    private long resolveUserId(SysAiConversation conversation) {
        return conversation == null || conversation.getUserId() == null ? 0L : conversation.getUserId();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, 10);
    }

    private String toJsonArguments(Object value) {
        return JsonUtil.toJsonString(value == null ? Map.of() : value);
    }

    private String truncate(String content, int maxLength) {
        String normalized = safe(content);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String firstNotBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return safe(second);
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private SysAiConversation resolveConversation(ToolContext toolContext) {
        Object conversation = toolContext.getContext().get(TOOL_CONTEXT_CONVERSATION);
        return conversation instanceof SysAiConversation sysAiConversation ? sysAiConversation : null;
    }

    private List<AiRetrievedChunkVo> resolveCitations(ToolContext toolContext) {
        Object citations = toolContext.getContext().get(TOOL_CONTEXT_CITATIONS);
        if (citations instanceof List<?> items) {
            return items.stream()
                    .filter(AiRetrievedChunkVo.class::isInstance)
                    .map(AiRetrievedChunkVo.class::cast)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<AiToolCallVo> resolveRecorder(ToolContext toolContext) {
        Object recorder = toolContext.getContext().get(TOOL_CONTEXT_RECORDER);
        if (recorder instanceof List<?> items) {
            return (List<AiToolCallVo>) items;
        }
        return new ArrayList<>();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @FunctionalInterface
    private interface ToolExecutor {
        Map<String, Object> execute();
    }

    @FunctionalInterface
    private interface ArticlePageFetcher {
        IPage<ArticleListVo> fetch(Page<Object> page, long userId);
    }

    @SuppressWarnings("unused")
    private final class RequestScopedAiArticleTools {

        @Tool(name = TOOL_GET_CURRENT_ARTICLE_CONTEXT, description = "获取当前对话绑定文章的上下文信息；如果当前是全局会话且没有绑定文章，会返回无绑定文章。")
        Map<String, Object> getCurrentArticleContextTool(ToolContext toolContext) {
            return executeTool(
                    TOOL_GET_CURRENT_ARTICLE_CONTEXT,
                    "{}",
                    resolveRecorder(toolContext),
                    () -> getCurrentArticleContext(resolveConversation(toolContext), resolveCitations(toolContext))
            );
        }

        @Tool(name = TOOL_SEARCH_RELEVANT_ARTICLES, description = "按用户问题检索最相关的站内文章，适合全局模式下先判断问题主要在问哪篇文章。")
        Map<String, Object> searchRelevantArticlesTool(
                @ToolParam(description = "用户问题或文章主题关键词") String query,
                @ToolParam(description = "返回文章数上限，默认 5，最大 10", required = false) Integer limit,
                ToolContext toolContext) {
            AiArticleSearchToolInput input = new AiArticleSearchToolInput(query, limit);
            return executeTool(
                    TOOL_SEARCH_RELEVANT_ARTICLES,
                    toJsonArguments(input),
                    resolveRecorder(toolContext),
                    () -> searchRelevantArticles(input)
            );
        }

        @Tool(name = TOOL_GET_MY_LIKED_ARTICLES, description = "获取当前用户点赞过的文章列表，适合围绕用户阅读偏好和历史行为继续对话。")
        Map<String, Object> getMyLikedArticlesTool(
                @ToolParam(description = "返回文章数上限，默认 5，最大 10", required = false) Integer limit,
                ToolContext toolContext) {
            AiArticleHistoryToolInput input = new AiArticleHistoryToolInput(limit);
            return executeTool(
                    TOOL_GET_MY_LIKED_ARTICLES,
                    toJsonArguments(input),
                    resolveRecorder(toolContext),
                    () -> getMyLikedArticles(resolveConversation(toolContext), input)
            );
        }

        @Tool(name = TOOL_GET_MY_FAVORITE_ARTICLES, description = "获取当前用户收藏过的文章列表，适合基于长期兴趣做推荐与追问。")
        Map<String, Object> getMyFavoriteArticlesTool(
                @ToolParam(description = "返回文章数上限，默认 5，最大 10", required = false) Integer limit,
                ToolContext toolContext) {
            AiArticleHistoryToolInput input = new AiArticleHistoryToolInput(limit);
            return executeTool(
                    TOOL_GET_MY_FAVORITE_ARTICLES,
                    toJsonArguments(input),
                    resolveRecorder(toolContext),
                    () -> getMyFavoriteArticles(resolveConversation(toolContext), input)
            );
        }

        @Tool(name = TOOL_GET_MY_COMMENTED_ARTICLES, description = "获取当前用户评论过的文章列表，适合围绕用户已参与讨论的文章继续对话。")
        Map<String, Object> getMyCommentedArticlesTool(
                @ToolParam(description = "返回文章数上限，默认 5，最大 10", required = false) Integer limit,
                ToolContext toolContext) {
            AiArticleHistoryToolInput input = new AiArticleHistoryToolInput(limit);
            return executeTool(
                    TOOL_GET_MY_COMMENTED_ARTICLES,
                    toJsonArguments(input),
                    resolveRecorder(toolContext),
                    () -> getMyCommentedArticles(resolveConversation(toolContext), input)
            );
        }

        @Tool(name = TOOL_GET_MY_AI_USAGE_SNAPSHOT, description = "获取当前用户的 AI 使用概览，包括已用 token、可用 token 和互动奖励统计。")
        Map<String, Object> getMyAiUsageSnapshotTool(ToolContext toolContext) {
            return executeTool(
                    TOOL_GET_MY_AI_USAGE_SNAPSHOT,
                    "{}",
                    resolveRecorder(toolContext),
                    () -> getMyAiUsageSnapshot(resolveConversation(toolContext))
            );
        }
    }
}
