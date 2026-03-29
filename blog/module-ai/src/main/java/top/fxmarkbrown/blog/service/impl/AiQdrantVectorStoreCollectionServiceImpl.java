package top.fxmarkbrown.blog.service.impl;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiProperties;
import top.fxmarkbrown.blog.service.AiVectorStoreCollectionService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "blog.ai.rag", name = "enabled", havingValue = "true")
public class AiQdrantVectorStoreCollectionServiceImpl implements AiVectorStoreCollectionService {

    private final AiProperties aiProperties;
    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    public AiQdrantVectorStoreCollectionServiceImpl(AiProperties aiProperties,
                                                    QdrantClient qdrantClient,
                                                    EmbeddingModel embeddingModel) {
        this.aiProperties = aiProperties;
        this.qdrantClient = qdrantClient;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public boolean isReady() {
        return qdrantClient != null && embeddingModel != null;
    }

    @Override
    public String getSiteCollectionName() {
        return requireQdrantConfig().getSiteCollectionName().trim();
    }

    @Override
    public VectorStore getSiteVectorStore() {
        return getOrCreateVectorStore(getSiteCollectionName(), requireQdrantConfig().isInitializeSchema());
    }

    @Override
    public String getDocumentTaskCollectionName(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalStateException("文档任务 ID 非法，无法生成 collection 名称");
        }
        AiProperties.Qdrant qdrant = requireQdrantConfig();
        return qdrant.getDocumentTaskCollectionPrefix().trim() + taskId;
    }

    @Override
    public VectorStore getDocumentTaskVectorStore(Long taskId) {
        return getOrCreateVectorStore(getDocumentTaskCollectionName(taskId), true);
    }

    @Override
    public boolean deleteDocumentTaskCollection(Long taskId) {
        return deleteCollectionIfExists(getDocumentTaskCollectionName(taskId));
    }

    @Override
    public boolean deleteCollectionIfExists(String collectionName) {
        if (!StringUtils.hasText(collectionName)) {
            return false;
        }
        String normalizedCollectionName = collectionName.trim();
        try {
            boolean exists = qdrantClient.collectionExistsAsync(normalizedCollectionName).get();
            if (!exists) {
                vectorStoreCache.remove(normalizedCollectionName);
                return false;
            }
            qdrantClient.deleteCollectionAsync(normalizedCollectionName).get();
            vectorStoreCache.remove(normalizedCollectionName);
            log.info("Qdrant collection 已删除, collection={}", normalizedCollectionName);
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("删除 Qdrant collection 失败: " + normalizedCollectionName, ex);
        }
    }

    private VectorStore getOrCreateVectorStore(String collectionName, boolean initializeSchema) {
        if (!isReady()) {
            throw new IllegalStateException("Qdrant 向量能力未就绪");
        }
        if (initializeSchema) {
            ensureCollectionExists(collectionName);
        }
        return vectorStoreCache.computeIfAbsent(collectionName, key -> QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(key)
                .initializeSchema(false)
                .build());
    }

    private void ensureCollectionExists(String collectionName) {
        if (!StringUtils.hasText(collectionName)) {
            throw new IllegalStateException("Qdrant collection 名称不能为空");
        }
        String normalizedCollectionName = collectionName.trim();
        try {
            boolean exists = qdrantClient.collectionExistsAsync(normalizedCollectionName).get();
            if (exists) {
                return;
            }
            Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                    .setDistance(Collections.Distance.Cosine)
                    .setSize(embeddingModel.dimensions())
                    .build();
            qdrantClient.createCollectionAsync(normalizedCollectionName, vectorParams).get();
            log.info("Qdrant collection 已创建, collection={}, dimensions={}",
                    normalizedCollectionName, embeddingModel.dimensions());
        } catch (Exception ex) {
            throw new IllegalStateException("创建 Qdrant collection 失败: " + normalizedCollectionName, ex);
        }
    }

    private AiProperties.Qdrant requireQdrantConfig() {
        AiProperties.Qdrant qdrant = aiProperties.getVectorStore().getQdrant();
        if (qdrant == null) {
            throw new IllegalStateException("Qdrant 配置缺失");
        }
        if (!StringUtils.hasText(qdrant.getSiteCollectionName())) {
            throw new IllegalStateException("Qdrant site collection 未配置");
        }
        if (!StringUtils.hasText(qdrant.getDocumentTaskCollectionPrefix())) {
            throw new IllegalStateException("Qdrant document task collection prefix 未配置");
        }
        return qdrant;
    }
}
