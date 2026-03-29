package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.model.ai.AiDocumentChunk;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTreeNodeVo;

import java.util.List;

/**
 * 将文档结构树转换为适合向量检索的 chunk 列表。
 */
public interface AiDocumentChunkService {

    /**
     * 按文档树结构切分当前任务的检索 chunk。
     */
    List<AiDocumentChunk> split(AiDocumentTaskDetailVo detail, AiDocumentTreeNodeVo root);
}
