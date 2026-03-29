package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.service.AiModelQuotaBillingService;

@Service
@RequiredArgsConstructor
public class AiModelQuotaBillingServiceImpl implements AiModelQuotaBillingService {

    private final AiChatModelService aiChatModelService;

    @Override
    public long resolveBilledTokens(long baseTokens, AiResolvedChatModel resolvedChatModel) {
        if (baseTokens <= 0) {
            return 0L;
        }
        double multiplier = resolvedChatModel == null ? 1D : resolvedChatModel.quotaMultiplier();
        double normalizedMultiplier = multiplier > 0 ? multiplier : 1D;
        return Math.max(1L, (long) Math.ceil(baseTokens * normalizedMultiplier));
    }

    @Override
    public long resolveBilledTokens(long baseTokens, SysAiConversation conversation) {
        return resolveBilledTokens(baseTokens, aiChatModelService.resolveConversationModel(conversation));
    }
}
