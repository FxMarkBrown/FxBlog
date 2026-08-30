package top.fxmarkbrown.blog.config.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        return OpenAiEmbeddingModel.builder()
                .openAiClient(buildOpenAiClient(provider))
                .metadataMode(MetadataMode.EMBED)
                .options(options)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "blog.ai.rag", name = "enabled", havingValue = "true")
    public QdrantClient qdrantClient(AiProperties aiProperties) {
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
        return new QdrantClient(grpcBuilder.build());
    }

    private OpenAIClient buildOpenAiClient(AiProperties.OpenAiCompatibleProvider provider) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(provider.openAiBaseUrl())
                .apiKey(provider.getApiKey().trim())
                .build();
    }
}
