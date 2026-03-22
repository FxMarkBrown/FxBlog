package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;

import java.util.List;

public interface AiArticleRagService {

    /**
     * 判断当前 RAG 服务是否具备可用的向量检索能力。
     */
    boolean isReady();

    /**
     * 为指定文章重建或刷新向量索引。
     */
    void syncArticleIndex(SysArticle article);

    /**
     * 删除指定文章对应的向量索引。
     */
    void removeArticleIndex(Long articleId);

    /**
     * 在当前文章范围内检索与问题最相关的片段。
     */
    List<AiRetrievedChunkVo> retrieveArticleChunks(Long articleId, String query);

    /**
     * 在文章会话中优先检索当前文章，并按需补充全站相关片段。
     */
    List<AiRetrievedChunkVo> retrieveArticleHybridChunks(Long articleId, String query);

    /**
     * 在全站文章范围内检索与问题最相关的片段。
     */
    List<AiRetrievedChunkVo> retrieveGlobalChunks(String query);
}
