package top.fxmarkbrown.blog.model.ai;

public record AiChunkMediaRef(
        String mediaType,
        String sourceUrl,
        String displayText,
        boolean localResource
) {
}
