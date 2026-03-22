package top.fxmarkbrown.blog.config.ai;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class AiModelConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "blog.ai", name = "enabled", havingValue = "true")
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "blog.ai.rag", name = "enabled", havingValue = "true")
    public EmbeddingModel embeddingModel(AiProperties aiProperties) {
        AiProperties.Embedding embeddingConfig = aiProperties.getEmbedding();
        AiProperties.OpenAiCompatibleProvider provider = aiProperties.requireProvider(embeddingConfig.getProvider());
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingConfig.getModel())
                .build();
        return new OpenAiEmbeddingModel(buildOpenAiApi(provider), MetadataMode.EMBED, options);
    }

    @Bean
    @ConditionalOnProperty(prefix = "blog.ai.rag", name = "enabled", havingValue = "true")
    public VectorStore vectorStore(AiProperties aiProperties, EmbeddingModel embeddingModel) {
        AiProperties.Qdrant qdrantConfig = aiProperties.getVectorStore().getQdrant();
        QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(
                qdrantConfig.getHost(),
                qdrantConfig.getPort(),
                qdrantConfig.isUseTls()
        );
        if (StringUtils.hasText(qdrantConfig.getApiKey())) {
            grpcBuilder.withApiKey(qdrantConfig.getApiKey().trim());
        }
        grpcBuilder.withTimeout(Duration.ofSeconds(10));
        QdrantClient qdrantClient = new QdrantClient(grpcBuilder.build());
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(qdrantConfig.getCollectionName())
                .initializeSchema(qdrantConfig.isInitializeSchema())
                .build();
    }

    private OpenAiApi buildOpenAiApi(AiProperties.OpenAiCompatibleProvider provider) {
        return OpenAiApi.builder()
                .baseUrl(provider.getBaseUrl().trim())
                .apiKey(provider.getApiKey().trim())
                .restClientBuilder(org.springframework.web.client.RestClient.builder())
                .webClientBuilder(org.springframework.web.reactive.function.client.WebClient.builder())
                .build();
    }
}
