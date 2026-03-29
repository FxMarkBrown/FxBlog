package top.fxmarkbrown.blog.model.ai;

public record AiDocumentChunkHit(
        String nodeId,
        String nodeTitle,
        String titlePath,
        String chunkType,
        String contentPreview,
        Integer pageStart,
        Integer pageEnd,
        int rank
) {
}
