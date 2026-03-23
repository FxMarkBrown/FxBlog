package top.fxmarkbrown.blog.model.ai;

import java.util.List;

public record AiMarkdownChunk(
        int chunkOrder,
        String sectionPath,
        int headingLevel,
        String blockType,
        String rawMarkdownFragment,
        String retrievalText,
        String contentPreview,
        List<AiChunkInternalLink> internalLinks,
        List<AiChunkMediaRef> mediaRefs,
        List<AiChunkTaxonomyLink> taxonomyLinks,
        boolean hasMath,
        boolean hasImage,
        boolean hasVideo
) {
}
