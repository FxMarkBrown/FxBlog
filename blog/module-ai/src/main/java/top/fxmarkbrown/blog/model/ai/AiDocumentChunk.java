package top.fxmarkbrown.blog.model.ai;

import top.fxmarkbrown.blog.vo.ai.AiDocumentSourceAnchorVo;

import java.util.List;

public record AiDocumentChunk(
        String chunkId,
        Long taskId,
        String nodeId,
        String parentNodeId,
        String nodeType,
        String nodeTitle,
        String titlePath,
        int level,
        int depth,
        int chunkIndex,
        String chunkType,
        String rawMarkdownFragment,
        String retrievalText,
        String contentPreview,
        Integer pageStart,
        Integer pageEnd,
        List<AiDocumentSourceAnchorVo> sourceAnchors
) {
}
