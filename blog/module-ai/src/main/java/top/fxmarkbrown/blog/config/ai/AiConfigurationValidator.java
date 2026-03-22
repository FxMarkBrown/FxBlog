package top.fxmarkbrown.blog.config.ai;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiConfigurationValidator {

    private static final String VECTOR_STORE_PROVIDER_QDRANT = "qdrant";

    public AiConfigurationValidator(AiProperties aiProperties, AiRagProperties aiRagProperties) {
        if (aiProperties.isEnabled()) {
            validateChat(aiProperties);
        }
        if (aiRagProperties.isEnabled()) {
            validateRag(aiProperties);
        }
    }

    private void validateChat(AiProperties aiProperties) {
        AiProperties.Chat chat = aiProperties.getChat();
        requireText(chat.getDefaultModelId(), "blog.ai.chat.default-model-id 未配置");
        if (chat.getModels() == null || chat.getModels().isEmpty()) {
            throw new IllegalStateException("blog.ai.chat.models 未配置");
        }
        AiProperties.ChatModelConfig defaultModel = aiProperties.requireChatModel(chat.getDefaultModelId());
        if (!aiProperties.isProviderReady(defaultModel.getProvider())) {
            throw new IllegalStateException("默认 AI chat model 的 provider 未配置完成: " + chat.getDefaultModelId());
        }
        chat.getModels().forEach((modelId, modelConfig) -> {
            requireText(modelConfig.getProvider(), "blog.ai.chat.models." + modelId + ".provider 未配置");
            requireText(modelConfig.getModel(), "blog.ai.chat.models." + modelId + ".model 未配置");
        });
    }

    private void validateRag(AiProperties aiProperties) {
        AiProperties.Embedding embedding = aiProperties.getEmbedding();
        requireText(embedding.getProvider(), "blog.ai.embedding.provider 未配置");
        requireText(embedding.getModel(), "blog.ai.embedding.model 未配置");
        aiProperties.requireProvider(embedding.getProvider());

        AiProperties.Rerank rerank = aiProperties.getRerank();
        if (rerank.isEnabled()) {
            requireText(rerank.getProvider(), "blog.ai.rerank.provider 未配置");
            requireText(rerank.getModel(), "blog.ai.rerank.model 未配置");
            if (rerank.getTimeoutMillis() <= 0) {
                throw new IllegalStateException("blog.ai.rerank.timeout-millis 必须大于 0");
            }
            aiProperties.requireProvider(rerank.getProvider());
        }

        AiProperties.VectorStore vectorStore = aiProperties.getVectorStore();
        requireText(vectorStore.getProvider(), "blog.ai.vector-store.provider 未配置");
        if (!VECTOR_STORE_PROVIDER_QDRANT.equalsIgnoreCase(vectorStore.getProvider().trim())) {
            throw new IllegalStateException("当前仅支持 blog.ai.vector-store.provider=qdrant");
        }

        AiProperties.Qdrant qdrant = vectorStore.getQdrant();
        requireText(qdrant.getHost(), "blog.ai.vector-store.qdrant.host 未配置");
        if (qdrant.getPort() <= 0) {
            throw new IllegalStateException("blog.ai.vector-store.qdrant.port 必须大于 0");
        }
        requireText(qdrant.getCollectionName(), "blog.ai.vector-store.qdrant.collection-name 未配置");
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
