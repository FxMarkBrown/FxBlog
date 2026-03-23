package top.fxmarkbrown.blog.model.ai;

public record AiChunkTaxonomyLink(
        String taxonomyType,
        String targetPath,
        Long targetId,
        String displayName
) {
}
