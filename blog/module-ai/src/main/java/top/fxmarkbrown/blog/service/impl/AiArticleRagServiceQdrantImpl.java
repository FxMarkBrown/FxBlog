package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.model.ai.AiMarkdownChunk;
import top.fxmarkbrown.blog.service.AiArticleRagService;
import top.fxmarkbrown.blog.service.AiMarkdownChunkService;
import top.fxmarkbrown.blog.service.AiRerankService;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiArticleRagServiceQdrantImpl implements AiArticleRagService {

    private static final String META_ARTICLE_ID = "articleId";
    private static final String META_ARTICLE_TITLE = "articleTitle";
    private static final String META_CHUNK_ORDER = "chunkOrder";
    private static final String META_SECTION_PATH = "sectionPath";
    private static final String META_HEADING_LEVEL = "headingLevel";
    private static final String META_BLOCK_TYPE = "blockType";
    private static final String META_CONTENT_PREVIEW = "contentPreview";
    private static final String META_RAW_MARKDOWN = "rawMarkdownFragment";
    private static final String META_PUBLISHED = "published";
    private static final int PUBLISHED_FLAG = 1;
    private static final int UNPUBLISHED_FLAG = 0;

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final AiRagProperties aiRagProperties;
    private final AiMarkdownChunkService aiMarkdownChunkService;
    private final AiRerankService aiRerankService;

    @Override
    public boolean isReady() {
        return aiRagProperties.isEnabled() && vectorStoreProvider.getIfAvailable() != null;
    }

    @Override
    public void syncArticleIndex(SysArticle article) {
        if (article == null || article.getId() == null || !isReady()) {
            return;
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        try {
            removeByArticleId(vectorStore, article.getId());
            if (aiRagProperties.isIndexPublishedOnly() && !Integer.valueOf(Constants.YES).equals(article.getStatus())) {
                return;
            }
            List<AiMarkdownChunk> chunks = aiMarkdownChunkService.split(article);
            if (chunks.isEmpty()) {
                return;
            }
            vectorStore.add(chunks.stream().map(chunk -> toDocument(article, chunk)).toList());
        } catch (Exception ex) {
            log.warn("文章 RAG 索引同步失败, articleId={}", article.getId(), ex);
        }
    }

    @Override
    public void removeArticleIndex(Long articleId) {
        if (articleId == null || !isReady()) {
            return;
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        try {
            removeByArticleId(vectorStore, articleId);
        } catch (Exception ex) {
            log.warn("文章 RAG 索引删除失败, articleId={}", articleId, ex);
        }
    }

    @Override
    public List<AiRetrievedChunkVo> retrieveArticleChunks(Long articleId, String query) {
        if (articleId == null || !StringUtils.hasText(query) || !isReady()) {
            return List.of();
        }
        return search(
                query,
                buildArticleFilter(articleId),
                resolveArticleSearchTopK(),
                aiRagProperties.getArticleRecallTopK(),
                resolveArticleSimilarityThreshold()
        ).stream()
                .map(chunk -> withSourceScope(chunk, "article"))
                .toList();
    }

    @Override
    public List<AiRetrievedChunkVo> retrieveArticleHybridChunks(Long articleId, String query) {
        if (articleId == null || !StringUtils.hasText(query) || !isReady()) {
            return List.of();
        }
        int totalTopK = Math.max(aiRagProperties.getTopK(), 1);
        List<AiRetrievedChunkVo> articleChunks = search(
                query,
                buildArticleFilter(articleId),
                totalTopK,
                aiRagProperties.getArticleRecallTopK(),
                resolveArticleSimilarityThreshold()
        ).stream().map(chunk -> withSourceScope(chunk, "article")).toList();
        if (!aiRagProperties.isArticleSupplementEnabled()) {
            return articleChunks.stream().limit(totalTopK).toList();
        }
        int articleReserve = Math.min(
                Math.max(aiRagProperties.getArticleOwnMinTopK(), 0),
                Math.max(totalTopK - Math.max(aiRagProperties.getArticleSupplementTopK(), 0), 0)
        );
        int articleKeepCount = Math.min(articleChunks.size(), articleReserve);
        int supplementSlots = totalTopK - articleKeepCount;
        if (supplementSlots <= 0) {
            return articleChunks.stream().limit(totalTopK).toList();
        }
        List<AiRetrievedChunkVo> supplementChunks = search(
                query,
                buildGlobalSupplementFilter(articleId),
                supplementSlots,
                aiRagProperties.getGlobalRecallTopK(),
                resolveGlobalSimilarityThreshold()
        ).stream().map(chunk -> withSourceScope(chunk, "global")).toList();
        List<AiRetrievedChunkVo> merged = new ArrayList<>();
        merged.addAll(articleChunks.stream().limit(articleKeepCount).toList());
        merged.addAll(supplementChunks.stream().limit(supplementSlots).toList());
        if (merged.size() < totalTopK && articleChunks.size() > articleKeepCount) {
            merged.addAll(articleChunks.stream()
                    .skip(articleKeepCount)
                    .limit(totalTopK - merged.size())
                    .toList());
        }
        return merged.stream().limit(totalTopK).toList();
    }

    @Override
    public List<AiRetrievedChunkVo> retrieveGlobalChunks(String query) {
        if (!StringUtils.hasText(query) || !isReady()) {
            return List.of();
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        int topK = Math.max(aiRagProperties.getTopK(), 1);
        List<AiRetrievedChunkVo> chunks = search(
                query,
                builder.eq(META_PUBLISHED, PUBLISHED_FLAG).build(),
                topK,
                aiRagProperties.getGlobalRecallTopK(),
                resolveGlobalSimilarityThreshold()
        ).stream().map(chunk -> withSourceScope(chunk, "global")).toList();
        return limitChunksPerArticle(chunks, aiRagProperties.getGlobalMaxChunksPerArticle(), topK);
    }

    private List<AiRetrievedChunkVo> search(String query,
                                            Filter.Expression filterExpression,
                                            int targetTopK,
                                            int configuredRecallTopK,
                                            double similarityThreshold) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return List.of();
        }
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(resolveRecallTopK(query, targetTopK, configuredRecallTopK))
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(filterExpression)
                    .build();
            List<Document> documents = vectorStore.similaritySearch(request);
            if (documents.isEmpty()) {
                return List.of();
            }
            List<Document> rerankedDocuments = aiRerankService.rerank(query, documents, targetTopK);
            return postProcessRetrievedChunks(rerankedDocuments.stream()
                    .map(this::toRetrievedChunkVo)
                    .toList(), targetTopK);
        } catch (Exception ex) {
            if (isCollectionMissing(ex)) {
                log.info("文章 RAG 检索跳过：collection 尚未初始化");
                return List.of();
            }
            log.warn("文章 RAG 检索失败, query={}", query, ex);
            return List.of();
        }
    }

    private void removeByArticleId(VectorStore vectorStore, Long articleId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        try {
            vectorStore.delete(builder.eq(META_ARTICLE_ID, articleId).build());
        } catch (Exception ex) {
            if (isCollectionMissing(ex)) {
                log.info("文章 RAG 索引删除跳过：collection 尚未初始化, articleId={}", articleId);
                return;
            }
            throw ex;
        }
    }

    private Document toDocument(SysArticle article, AiMarkdownChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_ARTICLE_ID, article.getId());
        metadata.put(META_ARTICLE_TITLE, safe(article.getTitle()));
        metadata.put(META_CHUNK_ORDER, chunk.chunkOrder());
        metadata.put(META_SECTION_PATH, chunk.sectionPath());
        metadata.put(META_HEADING_LEVEL, chunk.headingLevel());
        metadata.put(META_BLOCK_TYPE, chunk.blockType());
        metadata.put(META_CONTENT_PREVIEW, chunk.contentPreview());
        metadata.put(META_RAW_MARKDOWN, chunk.rawMarkdownFragment());
        metadata.put(META_PUBLISHED, resolvePublishedFlag(article));
        return new Document(buildDocumentId(article.getId(), chunk.chunkOrder()), chunk.retrievalText(), metadata);
    }

    private AiRetrievedChunkVo toRetrievedChunkVo(Document document) {
        AiRetrievedChunkVo vo = new AiRetrievedChunkVo();
        vo.setChunkId(document.getId());
        Object rawMarkdown = document.getMetadata().get(META_RAW_MARKDOWN);
        vo.setContent(stringValue(rawMarkdown != null ? rawMarkdown : document.getText()));
        vo.setContentPreview(stringValue(document.getMetadata().get(META_CONTENT_PREVIEW)));
        vo.setArticleTitle(stringValue(document.getMetadata().get(META_ARTICLE_TITLE)));
        vo.setSectionPath(stringValue(document.getMetadata().get(META_SECTION_PATH)));
        vo.setBlockType(stringValue(document.getMetadata().get(META_BLOCK_TYPE)));
        vo.setArticleId(longValue(document.getMetadata().get(META_ARTICLE_ID)));
        vo.setChunkOrder(intValue(document.getMetadata().get(META_CHUNK_ORDER)));
        vo.setEndChunkOrder(vo.getChunkOrder());
        vo.setMergedChunkCount(1);
        vo.setHeadingLevel(intValue(document.getMetadata().get(META_HEADING_LEVEL)));
        return vo;
    }

    private List<AiRetrievedChunkVo> postProcessRetrievedChunks(List<AiRetrievedChunkVo> chunks, int targetTopK) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<AiRetrievedChunkVo> deduplicated = deduplicateChunks(chunks);
        List<AiRetrievedChunkVo> processed = aiRagProperties.isMergeAdjacentChunks()
                ? mergeAdjacentChunks(deduplicated)
                : deduplicated;
        return processed.stream()
                .limit(Math.max(targetTopK, 1))
                .toList();
    }

    private List<AiRetrievedChunkVo> limitChunksPerArticle(List<AiRetrievedChunkVo> chunks, int maxPerArticle, int targetTopK) {
        if (chunks.isEmpty() || maxPerArticle <= 0) {
            return chunks.stream().limit(Math.max(targetTopK, 1)).toList();
        }
        Map<Long, Integer> articleHitCount = new HashMap<>();
        List<AiRetrievedChunkVo> selected = new ArrayList<>();
        for (AiRetrievedChunkVo chunk : chunks) {
            Long articleId = chunk.getArticleId();
            if (articleId == null) {
                selected.add(chunk);
            } else {
                int currentCount = articleHitCount.getOrDefault(articleId, 0);
                if (currentCount >= maxPerArticle) {
                    continue;
                }
                articleHitCount.put(articleId, currentCount + 1);
                selected.add(chunk);
            }
            if (selected.size() >= Math.max(targetTopK, 1)) {
                break;
            }
        }
        return selected;
    }

    private List<AiRetrievedChunkVo> deduplicateChunks(List<AiRetrievedChunkVo> chunks) {
        Map<String, AiRetrievedChunkVo> uniqueChunks = new LinkedHashMap<>();
        for (AiRetrievedChunkVo chunk : chunks) {
            uniqueChunks.putIfAbsent(buildChunkKey(chunk), copyChunk(chunk));
        }
        return new ArrayList<>(uniqueChunks.values());
    }

    private List<AiRetrievedChunkVo> mergeAdjacentChunks(List<AiRetrievedChunkVo> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        List<AiRetrievedChunkVo> mergedChunks = new ArrayList<>();
        boolean[] consumed = new boolean[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            consumed[i] = true;
            List<AiRetrievedChunkVo> mergeGroup = new ArrayList<>();
            mergeGroup.add(copyChunk(chunks.get(i)));
            boolean expanded;
            do {
                expanded = false;
                for (int j = i + 1; j < chunks.size(); j++) {
                    if (consumed[j]) {
                        continue;
                    }
                    AiRetrievedChunkVo candidate = chunks.get(j);
                    if (canMergeIntoGroup(mergeGroup, candidate)) {
                        mergeGroup.add(copyChunk(candidate));
                        consumed[j] = true;
                        expanded = true;
                    }
                }
            } while (expanded);
            mergedChunks.add(mergeGroup.size() == 1 ? mergeGroup.getFirst() : mergeGroup(mergeGroup));
        }
        return mergedChunks;
    }

    private boolean canMergeIntoGroup(List<AiRetrievedChunkVo> mergeGroup, AiRetrievedChunkVo candidate) {
        if (candidate == null || mergeGroup.isEmpty()) {
            return false;
        }
        AiRetrievedChunkVo anchor = mergeGroup.getFirst();
        if (!Objects.equals(anchor.getArticleId(), candidate.getArticleId())) {
            return false;
        }
        if (!Objects.equals(normalizeSectionPath(anchor.getSectionPath()), normalizeSectionPath(candidate.getSectionPath()))) {
            return false;
        }
        if (!isMergeFriendly(anchor.getBlockType(), candidate.getBlockType())) {
            return false;
        }
        Integer candidateOrder = candidate.getChunkOrder();
        if (candidateOrder == null) {
            return false;
        }
        int minOrder = mergeGroup.stream()
                .map(AiRetrievedChunkVo::getChunkOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(candidateOrder);
        int maxOrder = mergeGroup.stream()
                .map(AiRetrievedChunkVo::getEndChunkOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(candidateOrder);
        if (candidateOrder < minOrder - aiRagProperties.getMergeMaxGap()
                || candidateOrder > maxOrder + aiRagProperties.getMergeMaxGap()) {
            return false;
        }
        int mergedLength = mergeGroup.stream()
                .map(AiRetrievedChunkVo::getContent)
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .sum();
        return mergedLength + safeContent(candidate).length() <= Math.max(aiRagProperties.getMergeMaxChars(), 400);
    }

    private AiRetrievedChunkVo mergeGroup(List<AiRetrievedChunkVo> mergeGroup) {
        List<AiRetrievedChunkVo> orderedGroup = mergeGroup.stream()
                .sorted((left, right) -> Integer.compare(
                        left.getChunkOrder() == null ? Integer.MAX_VALUE : left.getChunkOrder(),
                        right.getChunkOrder() == null ? Integer.MAX_VALUE : right.getChunkOrder()))
                .toList();
        AiRetrievedChunkVo merged = copyChunk(orderedGroup.getFirst());
        String mergedContent = orderedGroup.stream()
                .map(this::safeContent)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("\n\n"));
        merged.setContent(mergedContent);
        merged.setContentPreview(buildPreview(mergedContent));
        merged.setChunkOrder(orderedGroup.stream()
                .map(AiRetrievedChunkVo::getChunkOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(merged.getChunkOrder()));
        merged.setEndChunkOrder(orderedGroup.stream()
                .map(AiRetrievedChunkVo::getEndChunkOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(merged.getChunkOrder()));
        merged.setMergedChunkCount(orderedGroup.size());
        merged.setHeadingLevel(orderedGroup.stream()
                .map(AiRetrievedChunkVo::getHeadingLevel)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(merged.getHeadingLevel()));
        merged.setBlockType(orderedGroup.stream()
                .map(AiRetrievedChunkVo::getBlockType)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(" / ")));
        return merged;
    }

    private AiRetrievedChunkVo copyChunk(AiRetrievedChunkVo source) {
        AiRetrievedChunkVo target = new AiRetrievedChunkVo();
        target.setArticleId(source.getArticleId());
        target.setArticleTitle(source.getArticleTitle());
        target.setChunkId(source.getChunkId());
        target.setChunkOrder(source.getChunkOrder());
        target.setEndChunkOrder(source.getEndChunkOrder());
        target.setMergedChunkCount(source.getMergedChunkCount());
        target.setSectionPath(source.getSectionPath());
        target.setHeadingLevel(source.getHeadingLevel());
        target.setBlockType(source.getBlockType());
        target.setSourceScope(source.getSourceScope());
        target.setContent(source.getContent());
        target.setContentPreview(source.getContentPreview());
        return target;
    }

    private AiRetrievedChunkVo withSourceScope(AiRetrievedChunkVo source, String sourceScope) {
        AiRetrievedChunkVo target = copyChunk(source);
        target.setSourceScope(sourceScope);
        return target;
    }

    private String buildChunkKey(AiRetrievedChunkVo chunk) {
        if (chunk.getArticleId() != null && chunk.getChunkOrder() != null) {
            return chunk.getArticleId() + ":" + chunk.getChunkOrder();
        }
        return String.valueOf(chunk.getChunkId());
    }

    private String normalizeSectionPath(String sectionPath) {
        return StringUtils.hasText(sectionPath) ? sectionPath.trim() : "未分节";
    }

    private boolean isMergeFriendly(String leftBlockType, String rightBlockType) {
        if (!StringUtils.hasText(leftBlockType) || !StringUtils.hasText(rightBlockType)) {
            return false;
        }
        if (leftBlockType.equals(rightBlockType)) {
            return true;
        }
        return isNarrativeBlock(leftBlockType) && isNarrativeBlock(rightBlockType);
    }

    private boolean isNarrativeBlock(String blockType) {
        return "paragraph".equals(blockType)
                || "list".equals(blockType)
                || "quote".equals(blockType)
                || "math".equals(blockType);
    }

    private String safeContent(AiRetrievedChunkVo chunk) {
        return StringUtils.hasText(chunk.getContent()) ? chunk.getContent().trim() : "";
    }

    private String buildPreview(String content) {
        String preview = content.replace('\n', ' ').trim();
        if (preview.length() > 120) {
            return preview.substring(0, 120) + "...";
        }
        return preview;
    }

    private String buildDocumentId(Long articleId, int chunkOrder) {
        String rawId = articleId + ":" + chunkOrder;
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未命名文章";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(value.toString());
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.parseInt(value.toString());
    }

    private int resolvePublishedFlag(SysArticle article) {
        return Integer.valueOf(Constants.YES).equals(article.getStatus()) ? PUBLISHED_FLAG : UNPUBLISHED_FLAG;
    }

    private Filter.Expression buildArticleFilter(Long articleId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.and(
                builder.eq(META_ARTICLE_ID, articleId),
                builder.eq(META_PUBLISHED, PUBLISHED_FLAG)
        ).build();
    }

    private Filter.Expression buildGlobalSupplementFilter(Long articleId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.and(
                builder.eq(META_PUBLISHED, PUBLISHED_FLAG),
                builder.ne(META_ARTICLE_ID, articleId)
        ).build();
    }

    private int resolveArticleSearchTopK() {
        return Math.max(aiRagProperties.getTopK(), 1);
    }

    private int resolveRecallTopK(String query, int targetTopK, int configuredRecallTopK) {
        int fallbackRecallTopK = configuredRecallTopK > 0 ? configuredRecallTopK : aiRagProperties.getRerankFetchTopK();
        int recallTopK = Math.max(Math.max(targetTopK, 1), fallbackRecallTopK);
        if (isShortQuery(query)) {
            recallTopK += Math.max(aiRagProperties.getShortQueryRecallBoost(), 0);
        }
        return recallTopK;
    }

    private boolean isShortQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        String normalized = query.trim();
        if (normalized.length() <= Math.max(aiRagProperties.getShortQueryThreshold(), 1)) {
            return true;
        }
        String[] terms = normalized.split("[\\s,，。！？；、/]+");
        int termCount = 0;
        for (String term : terms) {
            if (StringUtils.hasText(term)) {
                termCount++;
            }
        }
        return termCount > 0 && termCount <= 6;
    }

    private double resolveArticleSimilarityThreshold() {
        return aiRagProperties.getArticleSimilarityThreshold() > 0
                ? aiRagProperties.getArticleSimilarityThreshold()
                : aiRagProperties.getSimilarityThreshold();
    }

    private double resolveGlobalSimilarityThreshold() {
        return aiRagProperties.getGlobalSimilarityThreshold() > 0
                ? aiRagProperties.getGlobalSimilarityThreshold()
                : aiRagProperties.getSimilarityThreshold();
    }

    private boolean isCollectionMissing(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("doesn't exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
