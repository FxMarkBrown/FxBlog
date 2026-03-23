package top.fxmarkbrown.blog.model.ai;

public record AiChunkInternalLink(
        String anchorText,
        String targetPath,
        Long targetArticleId,
        String targetArticleTitle
) {
}
