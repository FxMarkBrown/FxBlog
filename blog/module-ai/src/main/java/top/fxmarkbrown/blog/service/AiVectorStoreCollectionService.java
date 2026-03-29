package top.fxmarkbrown.blog.service;

import org.springframework.ai.vectorstore.VectorStore;

public interface AiVectorStoreCollectionService {

    /**
     * 当前向量能力是否已完成装配并可实际访问。
     */
    boolean isReady();

    /**
     * 获取站内通用 RAG collection 名称。
     */
    String getSiteCollectionName();

    /**
     * 获取站内通用 RAG 使用的向量库。
     */
    VectorStore getSiteVectorStore();

    /**
     * 根据文档任务 ID 生成专属 collection 名称。
     */
    String getDocumentTaskCollectionName(Long taskId);

    /**
     * 获取指定文档任务独占使用的向量库。
     */
    VectorStore getDocumentTaskVectorStore(Long taskId);

    /**
     * 删除指定文档任务对应的 collection。
     */
    boolean deleteDocumentTaskCollection(Long taskId);

    /**
     * 按 collection 名称直接删除底层向量库。
     */
    boolean deleteCollectionIfExists(String collectionName);
}
