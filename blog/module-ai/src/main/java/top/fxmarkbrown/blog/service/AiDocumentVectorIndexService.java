package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.model.ai.AiDocumentChunkHit;
import top.fxmarkbrown.blog.vo.ai.AiDocumentParseResultVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;

import java.util.List;

/**
 * 管理文档任务专属向量索引的构建、删除与检索。
 */
public interface AiDocumentVectorIndexService {

    /**
     * 当前文档向量索引能力是否可用。
     */
    boolean isReady();

    /**
     * 为指定文档任务重建专属向量索引。
     */
    void syncTaskIndex(AiDocumentTaskDetailVo detail, AiDocumentParseResultVo result);

    /**
     * 删除指定文档任务对应的专属向量索引。
     */
    void deleteTaskIndex(Long taskId);

    /**
     * 在指定文档任务的专属向量索引内检索相关节点。
     */
    List<AiDocumentChunkHit> searchRelevantChunks(Long taskId, String query, int topK);
}
