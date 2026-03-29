package top.fxmarkbrown.blog.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "blog.ai")
public class AiProperties {

    private boolean enabled = false;

    private int maxHistoryMessages = 12;

    private int maxArticleContextChars = 6000;

    private Chat chat = new Chat();

    private Embedding embedding = new Embedding();

    private Rerank rerank = new Rerank();

    private VectorStore vectorStore = new VectorStore();

    private Document document = new Document();

    private Map<String, OpenAiCompatibleProvider> providers = new LinkedHashMap<>();

    public OpenAiCompatibleProvider requireProvider(String providerName) {
        if (!StringUtils.hasText(providerName)) {
            throw new IllegalStateException("AI provider 未配置");
        }
        OpenAiCompatibleProvider provider = providers.get(providerName.trim());
        if (provider == null) {
            throw new IllegalStateException("未找到 AI provider 配置: " + providerName);
        }
        if (!StringUtils.hasText(provider.getBaseUrl()) || !StringUtils.hasText(provider.getApiKey())) {
            throw new IllegalStateException("AI provider 缺少 baseUrl 或 apiKey: " + providerName);
        }
        return provider;
    }

    @Data
    public static class Chat {

        private String defaultModelId = "glm5";

        private Double defaultTemperature = 0.4D;

        private Map<String, ChatModelConfig> models = new LinkedHashMap<>();
    }

    @Data
    public static class ChatModelConfig {

        private String label;

        private String provider;

        private String model;

        private Double temperature;

        private Double quotaMultiplier = 1D;

        public double resolveTemperature(double fallback) {
            return temperature == null ? fallback : temperature;
        }

        public double resolveQuotaMultiplier() {
            return quotaMultiplier == null || quotaMultiplier <= 0 ? 1D : quotaMultiplier;
        }
    }

    @Data
    public static class Embedding {

        private String provider = "siliconflow";

        private String model = "Qwen/Qwen3-Embedding-8B";
    }

    @Data
    public static class Rerank {

        private boolean enabled = true;

        private String provider = "siliconflow";

        private String model = "Qwen/Qwen3-Reranker-8B";

        private int timeoutMillis = 15000;
    }

    @Data
    public static class VectorStore {

        private String provider = "qdrant";

        private Qdrant qdrant = new Qdrant();
    }

    @Data
    public static class Qdrant {

        private String host = "127.0.0.1";

        private int port = 6334;

        private boolean useTls = false;

        private String apiKey;

        private String siteCollectionName = "blog_markdown_chunk";

        private String documentTaskCollectionPrefix = "blog_document_task_";

        private boolean initializeSchema = true;
    }

    @Data
    public static class OpenAiCompatibleProvider {

        private String baseUrl;

        private String apiKey;
    }

    @Data
    public static class Document {

        private boolean enabled = true;

        private int retentionDays = 1;

        private Mineru mineru = new Mineru();
    }

    @Data
    public static class Mineru {

        private boolean enabled = false;

        private boolean mockMode = true;

        private String baseUrl;

        private String apiKey;

        private String language;

        private Boolean ocr;

        private Boolean enableFormula = true;

        private Boolean enableTable = true;

        private String callbackUrl;

        private String callbackSeed;

        private String callbackUid;

        private String submitPath = "/api/v4/extract/task";

        private String taskDetailPath = "/api/v4/extract/task/{taskId}";

        private int timeoutMillis = 30000;
    }

    public ChatModelConfig requireChatModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            throw new IllegalStateException("AI chat model 未配置");
        }
        ChatModelConfig modelConfig = chat.getModels().get(modelId.trim());
        if (modelConfig == null) {
            throw new IllegalStateException("未找到 AI chat model 配置: " + modelId);
        }
        if (!StringUtils.hasText(modelConfig.getProvider()) || !StringUtils.hasText(modelConfig.getModel())) {
            throw new IllegalStateException("AI chat model 缺少 provider 或 model: " + modelId);
        }
        return modelConfig;
    }

    public String resolveDefaultChatModelId() {
        if (StringUtils.hasText(chat.getDefaultModelId())) {
            return chat.getDefaultModelId().trim();
        }
        return chat.getModels().keySet().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("未配置任何 AI chat model"));
    }

    public boolean isProviderReady(String providerName) {
        if (!StringUtils.hasText(providerName)) {
            return false;
        }
        OpenAiCompatibleProvider provider = providers.get(providerName.trim());
        return provider != null
                && StringUtils.hasText(provider.getBaseUrl())
                && StringUtils.hasText(provider.getApiKey());
    }

    public String findChatModelId(String providerName, String modelName) {
        if (!StringUtils.hasText(providerName) || !StringUtils.hasText(modelName)) {
            return null;
        }
        String normalizedProviderName = providerName.trim();
        String normalizedModelName = modelName.trim();
        return chat.getModels().entrySet().stream()
                .filter(entry -> normalizedProviderName.equals(entry.getValue().getProvider()))
                .filter(entry -> normalizedModelName.equals(entry.getValue().getModel()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
