package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;

public interface AiModelQuotaBillingService {

    /**
     * 按指定模型的倍率把基础 token 量换算为最终计费额度。
     */
    long resolveBilledTokens(long baseTokens, AiResolvedChatModel resolvedChatModel);

    /**
     * 按会话当前绑定模型的倍率把基础 token 量换算为最终计费额度。
     */
    long resolveBilledTokens(long baseTokens, SysAiConversation conversation);
}
