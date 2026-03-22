package top.fxmarkbrown.blog.service.impl;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiProperties;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.vo.ai.AiChatModelOptionVo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AiChatModelServiceImpl implements AiChatModelService {

    private final AiProperties aiProperties;
    private final ToolCallingManager toolCallingManager;
    private final Map<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    @Override
    public AiResolvedChatModel getDefaultModel() {
        return requireModel(aiProperties.resolveDefaultChatModelId());
    }

    @Override
    public AiResolvedChatModel requireModel(String modelId) {
        AiProperties.ChatModelConfig modelConfig = aiProperties.requireChatModel(modelId);
        if (!aiProperties.isProviderReady(modelConfig.getProvider())) {
            throw new ServiceException("AI 模型未配置完成: " + safe(modelConfig.getLabel(), modelId));
        }
        return toResolvedModel(modelId, modelConfig);
    }

    @Override
    public AiResolvedChatModel resolveConversationModel(SysAiConversation conversation) {
        if (conversation == null) {
            return getDefaultModel();
        }
        String providerName = conversation.getModelProvider();
        String modelName = conversation.getModelName();
        if (!StringUtils.hasText(providerName) || !StringUtils.hasText(modelName)) {
            return getDefaultModel();
        }
        String modelId = resolveModelId(providerName, modelName);
        if (StringUtils.hasText(modelId)) {
            return requireModel(modelId);
        }
        return getDefaultModel();
    }

    @Override
    public ChatClient getChatClient(AiResolvedChatModel resolvedChatModel) {
        if (resolvedChatModel == null) {
            throw new ServiceException("AI 模型不存在");
        }
        String cacheKey = "%s|%s|%s".formatted(
                resolvedChatModel.providerName(),
                resolvedChatModel.modelName(),
                resolvedChatModel.temperature()
        );
        return chatClientCache.computeIfAbsent(cacheKey, ignored -> buildChatClient(resolvedChatModel));
    }

    @Override
    public List<AiChatModelOptionVo> listAvailableModels() {
        String defaultModelId = aiProperties.resolveDefaultChatModelId();
        return aiProperties.getChat().getModels().entrySet().stream()
                .filter(entry -> aiProperties.isProviderReady(entry.getValue().getProvider()))
                .sorted(Comparator.comparingInt(entry -> Objects.equals(entry.getKey(), defaultModelId) ? 0 : 1))
                .map(entry -> toOption(entry.getKey(), entry.getValue(), Objects.equals(entry.getKey(), defaultModelId)))
                .toList();
    }

    @Override
    public String resolveModelId(String providerName, String modelName) {
        return aiProperties.findChatModelId(providerName, modelName);
    }

    @Override
    public String resolveDisplayName(String providerName, String modelName) {
        String modelId = resolveModelId(providerName, modelName);
        if (StringUtils.hasText(modelId)) {
            return requireModel(modelId).displayName();
        }
        return safe(modelName, "未命名模型");
    }

    private ChatClient buildChatClient(AiResolvedChatModel resolvedChatModel) {
        AiProperties.OpenAiCompatibleProvider provider = aiProperties.requireProvider(resolvedChatModel.providerName());
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(resolvedChatModel.modelName())
                .temperature(resolvedChatModel.temperature())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(buildOpenAiApi(provider))
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiApi buildOpenAiApi(AiProperties.OpenAiCompatibleProvider provider) {
        return OpenAiApi.builder()
                .baseUrl(provider.getBaseUrl().trim())
                .apiKey(provider.getApiKey().trim())
                .restClientBuilder(org.springframework.web.client.RestClient.builder())
                .webClientBuilder(org.springframework.web.reactive.function.client.WebClient.builder())
                .build();
    }

    private AiResolvedChatModel toResolvedModel(String modelId, AiProperties.ChatModelConfig modelConfig) {
        return new AiResolvedChatModel(
                modelId,
                safe(modelConfig.getLabel(), modelId),
                modelConfig.getProvider().trim(),
                modelConfig.getModel().trim(),
                modelConfig.resolveTemperature(aiProperties.getChat().getDefaultTemperature()),
                modelConfig.resolveQuotaMultiplier()
        );
    }

    private AiChatModelOptionVo toOption(String modelId, AiProperties.ChatModelConfig modelConfig, boolean isDefault) {
        AiChatModelOptionVo vo = new AiChatModelOptionVo();
        vo.setId(modelId);
        vo.setDisplayName(safe(modelConfig.getLabel(), modelId));
        vo.setProvider(modelConfig.getProvider());
        vo.setModelName(modelConfig.getModel());
        vo.setTemperature(modelConfig.resolveTemperature(aiProperties.getChat().getDefaultTemperature()));
        vo.setQuotaMultiplier(modelConfig.resolveQuotaMultiplier());
        vo.setDefaultModel(isDefault);
        return vo;
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
