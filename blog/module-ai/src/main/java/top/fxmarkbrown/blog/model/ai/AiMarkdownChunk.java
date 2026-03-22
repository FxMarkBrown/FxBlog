package top.fxmarkbrown.blog.model.ai;

public record AiMarkdownChunk(
        int chunkOrder,
        String sectionPath,
        int headingLevel,
        String blockType,
        String rawMarkdownFragment,
        String retrievalText,
        String contentPreview,
        boolean hasMath,
        boolean hasImage,
        boolean hasVideo
) {
}
