package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.dto.ai.AiQuotaRuleUpdateDto;
import top.fxmarkbrown.blog.vo.ai.AiQuotaLogVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaRuleVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaSnapshotVo;

public interface AiQuotaCoreService {

    /**
     * 读取当前生效的 AI 额度规则。
     */
    AiQuotaRuleVo getRule();

    /**
     * 保存并返回最新的 AI 额度规则。
     */
    AiQuotaRuleVo saveRule(AiQuotaRuleUpdateDto updateDto);

    /**
     * 查询指定用户的额度快照。
     */
    AiQuotaSnapshotVo getQuotaSnapshot(Long userId);

    /**
     * 在指定规则快照下查询用户额度，避免重复读取规则。
     */
    AiQuotaSnapshotVo getQuotaSnapshot(Long userId, AiQuotaRuleVo rule);

    /**
     * 分页查询指定用户的额度流水。
     */
    IPage<AiQuotaLogVo> pageUserLogs(Long userId);

    /**
     * 校验点赞奖励限额并记录本次点赞次数。
     */
    void assertAndRecordLikeAction(Long userId, Long articleId);

    /**
     * 在发起 AI 请求前校验用户额度是否足够。
     */
    void assertRequestQuota(Long userId);

    /**
     * 按 token 数消耗用户额度。
     */
    void consumeTokens(Long userId, long tokens);

    /**
     * 按 token 数消耗用户额度，并记录会话来源信息。
     */
    void consumeTokens(Long userId, long tokens, Long conversationId, String conversationTitle);

    /**
     * 按 token 数消耗用户额度，并记录通用来源信息。
     */
    void consumeTokens(Long userId, long tokens, String sourceTitle, String remark);

    /**
     * 更新用户的后台手动额度。
     */
    void updateManualBonusTokens(Long userId, Long manualBonusTokens);

    /**
     * 记录签到奖励流水。
     */
    void recordSignReward(Long userId, long cumulativeSignDays);

    /**
     * 记录发文奖励或回收流水。
     */
    void recordArticleReward(Long userId, Long articleId, String articleTitle, boolean increase);

    /**
     * 记录点赞奖励或回收流水。
     */
    void recordLikeReward(Long userId, Long articleId, String articleTitle, long count, boolean increase);

    /**
     * 记录收藏奖励或回收流水。
     */
    void recordFavoriteReward(Long userId, Long articleId, String articleTitle, boolean increase);
}
