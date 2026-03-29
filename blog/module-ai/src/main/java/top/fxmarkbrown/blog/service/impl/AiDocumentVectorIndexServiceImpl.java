package top.fxmarkbrown.blog.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.model.ai.AiDocumentChunk;
import top.fxmarkbrown.blog.model.ai.AiDocumentChunkHit;
import top.fxmarkbrown.blog.service.AiDocumentChunkService;
import top.fxmarkbrown.blog.service.AiDocumentVectorIndexService;
import top.fxmarkbrown.blog.service.AiVectorStoreCollectionService;
import top.fxmarkbrown.blog.vo.ai.AiDocumentParseResultVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "blog.ai.rag", name = "enabled", havingValue = "true")
public class AiDocumentVectorIndexServiceImpl implements AiDocumentVectorIndexService {

    private static final String META_TASK_ID = "taskId";
    private static final String META_NODE_ID = "nodeId";
    private static final String META_PARENT_NODE_ID = "parentNodeId";
    private static final String META_NODE_TYPE = "nodeType";
    private static final String META_NODE_TITLE = "nodeTitle";
    private static final String META_TITLE_PATH = "titlePath";
    private static final String META_LEVEL = "level";
    private static final String META_DEPTH = "depth";
    private static final String META_CHUNK_INDEX = "chunkIndex";
    private static final String META_CHUNK_TYPE = "chunkType";
    private static final String META_CONTENT_PREVIEW = "contentPreview";
    private static final String META_RAW_MARKDOWN = "rawMarkdownFragment";
    private static final String META_PAGE_START = "pageStart";
    private static final String META_PAGE_END = "pageEnd";

    private final AiRagProperties aiRagProperties;
    private final AiDocumentChunkService aiDocumentChunkService;
    private final AiVectorStoreCollectionService aiVectorStoreCollectionService;

    public AiDocumentVectorIndexServiceImpl(AiRagProperties aiRagProperties,
                                            AiDocumentChunkService aiDocumentChunkService,
                                            AiVectorStoreCollectionService aiVectorStoreCollectionService) {
        this.aiRagProperties = aiRagProperties;
        this.aiDocumentChunkService = aiDocumentChunkService;
        this.aiVectorStoreCollectionService = aiVectorStoreCollectionService;
    }

    @Override
    public boolean isReady() {
        return aiVectorStoreCollectionService.isReady();
    }

    @Override
    public void syncTaskIndex(AiDocumentTaskDetailVo detail, AiDocumentParseResultVo result) {
        if (!isReady() || detail == null || detail.getTaskId() == null || result == null || result.getRoot() == null) {
            return;
        }
        List<AiDocumentChunk> chunks = aiDocumentChunkService.split(detail, result.getRoot());
        deleteTaskIndex(detail.getTaskId());
        if (chunks.isEmpty()) {
            return;
        }
        VectorStore vectorStore = aiVectorStoreCollectionService.getDocumentTaskVectorStore(detail.getTaskId());
        vectorStore.add(chunks.stream().map(this::toDocument).toList());
        log.info("文档任务向量索引已同步, taskId={}, chunks={}", detail.getTaskId(), chunks.size());
    }

    @Override
    public void deleteTaskIndex(Long taskId) {
        if (taskId == null || !isReady()) {
            return;
        }
        aiVectorStoreCollectionService.deleteDocumentTaskCollection(taskId);
    }

    @Override
    public List<AiDocumentChunkHit> searchRelevantChunks(Long taskId, String query, int topK) {
        if (taskId == null || !StringUtils.hasText(query) || topK <= 0 || !isReady()) {
            return List.of();
        }
        try {
            VectorStore vectorStore = aiVectorStoreCollectionService.getDocumentTaskVectorStore(taskId);
            SearchRequest request = SearchRequest.builder()
                    .query(query.trim())
                    .topK(Math.max(topK, 1))
                    .similarityThreshold(aiRagProperties.getSimilarityThreshold())
                    .build();
            List<Document> documents = vectorStore.similaritySearch(request);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }
            Map<String, AiDocumentChunkHit> deduplicated = new LinkedHashMap<>();
            int rank = 0;
            for (Document document : documents) {
                rank += 1;
                AiDocumentChunkHit hit = toChunkHit(document, rank);
                if (hit == null || !StringUtils.hasText(hit.nodeId())) {
                    continue;
                }
                deduplicated.putIfAbsent(hit.nodeId(), hit);
            }
            return new ArrayList<>(deduplicated.values());
        } catch (Exception ex) {
            if (isCollectionMissing(ex)) {
                return List.of();
            }
            log.warn("文档任务向量检索失败, taskId={}, query={}", taskId, query, ex);
            return List.of();
        }
    }

    private Document toDocument(AiDocumentChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(META_TASK_ID, chunk.taskId());
        metadata.put(META_NODE_ID, chunk.nodeId());
        metadata.put(META_PARENT_NODE_ID, chunk.parentNodeId());
        metadata.put(META_NODE_TYPE, chunk.nodeType());
        metadata.put(META_NODE_TITLE, chunk.nodeTitle());
        metadata.put(META_TITLE_PATH, chunk.titlePath());
        metadata.put(META_LEVEL, chunk.level());
        metadata.put(META_DEPTH, chunk.depth());
        metadata.put(META_CHUNK_INDEX, chunk.chunkIndex());
        metadata.put(META_CHUNK_TYPE, chunk.chunkType());
        metadata.put(META_CONTENT_PREVIEW, chunk.contentPreview());
        metadata.put(META_RAW_MARKDOWN, chunk.rawMarkdownFragment());
        if (chunk.pageStart() != null) {
            metadata.put(META_PAGE_START, chunk.pageStart());
        }
        if (chunk.pageEnd() != null) {
            metadata.put(META_PAGE_END, chunk.pageEnd());
        }
        return new Document(chunk.chunkId(), chunk.retrievalText(), metadata);
    }

    private AiDocumentChunkHit toChunkHit(Document document, int rank) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        return new AiDocumentChunkHit(
                text(document.getMetadata().get(META_NODE_ID)),
                text(document.getMetadata().get(META_NODE_TITLE)),
                text(document.getMetadata().get(META_TITLE_PATH)),
                text(document.getMetadata().get(META_CHUNK_TYPE)),
                text(document.getMetadata().get(META_CONTENT_PREVIEW)),
                intValue(document.getMetadata().get(META_PAGE_START)),
                intValue(document.getMetadata().get(META_PAGE_END)),
                rank
        );
    }

    private boolean isCollectionMissing(Exception ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("not found");
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
