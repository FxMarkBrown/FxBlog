package top.fxmarkbrown.blog.model.ai;

public record AiResolvedChatModel(
        String modelId,
        String displayName,
        String providerName,
        String modelName,
        double temperature,
        double quotaMultiplier
) {
}
