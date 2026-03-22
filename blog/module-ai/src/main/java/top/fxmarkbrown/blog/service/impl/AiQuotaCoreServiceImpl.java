package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.dto.ai.AiQuotaRuleUpdateDto;
import top.fxmarkbrown.blog.entity.SysAiQuota;
import top.fxmarkbrown.blog.entity.SysAiQuotaLog;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysConfig;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysAiQuotaMapper;
import top.fxmarkbrown.blog.mapper.SysAiQuotaLogMapper;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysConfigMapper;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.utils.RedisUtil;
import top.fxmarkbrown.blog.vo.ai.AiQuotaLogVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaRuleVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaSnapshotVo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiQuotaCoreServiceImpl implements AiQuotaCoreService {

    private static final String CONFIG_TYPE_SYSTEM = "Y";
    private static final String BIZ_TYPE_SIGN = "sign";
    private static final String BIZ_TYPE_ARTICLE = "article";
    private static final String BIZ_TYPE_LIKE = "like";
    private static final String BIZ_TYPE_FAVORITE = "favorite";
    private static final String BIZ_TYPE_CONSUME = "consume";
    private static final String BIZ_TYPE_MANUAL = "manual";
    private static final ConfigDefinition ENABLED = new ConfigDefinition("AI 额度开关", "ai_quota_enabled", "Y", "站内 AI 额度体系开关，Y/N");
    private static final ConfigDefinition MIN_REQUEST_TOKENS = new ConfigDefinition("AI 起聊门槛", "ai_quota_min_request_tokens", "800", "用户发起一次 AI 对话前至少需要保留的 token 数");
    private static final ConfigDefinition SIGN_REWARD_TOKENS = new ConfigDefinition("AI 签到奖励", "ai_quota_sign_reward_tokens", "30000", "每累计签到 1 天折算的 token 奖励");
    private static final ConfigDefinition ARTICLE_REWARD_TOKENS = new ConfigDefinition("AI 发文奖励", "ai_quota_article_reward_tokens", "120000", "每发布 1 篇已发布文章折算的 token 奖励");
    private static final ConfigDefinition LIKE_REWARD_TOKENS = new ConfigDefinition("AI 点赞奖励", "ai_quota_like_reward_tokens", "6000", "每次点赞 1 篇他人已发布文章折算的 token 奖励");
    private static final ConfigDefinition FAVORITE_REWARD_TOKENS = new ConfigDefinition("AI 收藏奖励", "ai_quota_favorite_reward_tokens", "18000", "每收藏 1 篇他人已发布文章折算的 token 奖励");
    private static final ConfigDefinition LIKE_DAILY_LIMIT = new ConfigDefinition("AI 点赞每日总上限", "ai_quota_like_daily_limit", "30", "单个用户每天可触发的点赞次数上限，0 表示不限制");
    private static final ConfigDefinition LIKE_DAILY_PER_ARTICLE_LIMIT = new ConfigDefinition("AI 点赞单篇上限", "ai_quota_like_daily_per_article_limit", "4", "单个用户每天对同一篇文章可触发的点赞次数上限，0 表示不限制");
    private static final List<ConfigDefinition> RULE_DEFINITIONS = List.of(
            ENABLED,
            MIN_REQUEST_TOKENS,
            SIGN_REWARD_TOKENS,
            ARTICLE_REWARD_TOKENS,
            LIKE_REWARD_TOKENS,
            FAVORITE_REWARD_TOKENS,
            LIKE_DAILY_LIMIT,
            LIKE_DAILY_PER_ARTICLE_LIMIT
    );

    private final SysAiQuotaMapper aiQuotaMapper;
    private final SysAiQuotaLogMapper aiQuotaLogMapper;
    private final SysArticleMapper articleMapper;
    private final SysConfigMapper configMapper;
    private final RedisUtil redisUtil;

    public AiQuotaRuleVo getRule() {
        Map<String, SysConfig> configMap = getOrCreateRuleConfigMap();
        AiQuotaRuleVo rule = new AiQuotaRuleVo();
        rule.setEnabled(parseBoolean(configMap.get(ENABLED.key()), true));
        rule.setMinRequestTokens(parseLong(configMap.get(MIN_REQUEST_TOKENS.key()), MIN_REQUEST_TOKENS.defaultLongValue()));
        rule.setSignRewardTokens(parseLong(configMap.get(SIGN_REWARD_TOKENS.key()), SIGN_REWARD_TOKENS.defaultLongValue()));
        rule.setArticleRewardTokens(parseLong(configMap.get(ARTICLE_REWARD_TOKENS.key()), ARTICLE_REWARD_TOKENS.defaultLongValue()));
        rule.setLikeRewardTokens(parseLong(configMap.get(LIKE_REWARD_TOKENS.key()), LIKE_REWARD_TOKENS.defaultLongValue()));
        rule.setFavoriteRewardTokens(parseLong(configMap.get(FAVORITE_REWARD_TOKENS.key()), FAVORITE_REWARD_TOKENS.defaultLongValue()));
        rule.setLikeDailyLimit(parseInt(configMap.get(LIKE_DAILY_LIMIT.key()), LIKE_DAILY_LIMIT.defaultIntValue()));
        rule.setLikeDailyPerArticleLimit(parseInt(configMap.get(LIKE_DAILY_PER_ARTICLE_LIMIT.key()), LIKE_DAILY_PER_ARTICLE_LIMIT.defaultIntValue()));
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiQuotaRuleVo saveRule(AiQuotaRuleUpdateDto updateDto) {
        if (updateDto == null) {
            throw new ServiceException("额度规则不能为空");
        }
        boolean enabled = updateDto.getEnabled() == null || updateDto.getEnabled();
        long minRequestTokens = defaultLong(updateDto.getMinRequestTokens(), MIN_REQUEST_TOKENS.defaultLongValue());
        long signRewardTokens = defaultLong(updateDto.getSignRewardTokens(), SIGN_REWARD_TOKENS.defaultLongValue());
        long articleRewardTokens = defaultLong(updateDto.getArticleRewardTokens(), ARTICLE_REWARD_TOKENS.defaultLongValue());
        long likeRewardTokens = defaultLong(updateDto.getLikeRewardTokens(), LIKE_REWARD_TOKENS.defaultLongValue());
        long favoriteRewardTokens = defaultLong(updateDto.getFavoriteRewardTokens(), FAVORITE_REWARD_TOKENS.defaultLongValue());
        int likeDailyLimit = defaultInt(updateDto.getLikeDailyLimit(), LIKE_DAILY_LIMIT.defaultIntValue());
        int likeDailyPerArticleLimit = defaultInt(updateDto.getLikeDailyPerArticleLimit(), LIKE_DAILY_PER_ARTICLE_LIMIT.defaultIntValue());

        if (minRequestTokens <= 0) {
            throw new ServiceException("起聊门槛必须大于 0");
        }
        if (signRewardTokens < 0 || articleRewardTokens < 0 || likeRewardTokens < 0 || favoriteRewardTokens < 0) {
            throw new ServiceException("奖励额度不能小于 0");
        }
        if (likeDailyLimit < 0 || likeDailyPerArticleLimit < 0) {
            throw new ServiceException("点赞限额不能小于 0");
        }

        upsertConfig(ENABLED, enabled ? "Y" : "N");
        upsertConfig(MIN_REQUEST_TOKENS, String.valueOf(minRequestTokens));
        upsertConfig(SIGN_REWARD_TOKENS, String.valueOf(signRewardTokens));
        upsertConfig(ARTICLE_REWARD_TOKENS, String.valueOf(articleRewardTokens));
        upsertConfig(LIKE_REWARD_TOKENS, String.valueOf(likeRewardTokens));
        upsertConfig(FAVORITE_REWARD_TOKENS, String.valueOf(favoriteRewardTokens));
        upsertConfig(LIKE_DAILY_LIMIT, String.valueOf(likeDailyLimit));
        upsertConfig(LIKE_DAILY_PER_ARTICLE_LIMIT, String.valueOf(likeDailyPerArticleLimit));
        return getRule();
    }

    public AiQuotaSnapshotVo getQuotaSnapshot(Long userId) {
        return getQuotaSnapshot(userId, getRule());
    }

    public AiQuotaSnapshotVo getQuotaSnapshot(Long userId, AiQuotaRuleVo rule) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("用户不存在");
        }
        SysAiQuota quota = findQuota(userId);
        long signDays = getCumulativeSignDays(userId);
        long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<SysArticle>()
                .eq(SysArticle::getUserId, userId)
                .eq(SysArticle::getStatus, Constants.YES));
        long likedArticleCount = defaultLong(articleMapper.selectGivenLikeCount(userId));
        long favoriteArticleCount = defaultLong(articleMapper.selectGivenFavoriteCount(userId));
        long signRewardTokens = signDays * defaultLong(rule.getSignRewardTokens());
        long articleRewardTokens = articleCount * defaultLong(rule.getArticleRewardTokens());
        long likeRewardTokens = likedArticleCount * defaultLong(rule.getLikeRewardTokens());
        long favoriteRewardTokens = favoriteArticleCount * defaultLong(rule.getFavoriteRewardTokens());
        long manualBonusTokens = quota == null ? 0L : defaultLong(quota.getManualBonusTokens());
        long usedTokens = quota == null ? 0L : defaultLong(quota.getUsedTokens());
        long totalEarnedTokens = signRewardTokens + articleRewardTokens + likeRewardTokens + favoriteRewardTokens + manualBonusTokens;
        long todayLikeCount = getTodayLikeCount(userId);
        int likeDailyLimit = defaultInt(rule.getLikeDailyLimit());

        AiQuotaSnapshotVo snapshot = new AiQuotaSnapshotVo();
        snapshot.setUserId(userId);
        snapshot.setEnabled(Boolean.TRUE.equals(rule.getEnabled()));
        snapshot.setMinRequestTokens(defaultLong(rule.getMinRequestTokens()));
        snapshot.setAvailableTokens(Math.max(0L, totalEarnedTokens - usedTokens));
        snapshot.setTotalEarnedTokens(totalEarnedTokens);
        snapshot.setUsedTokens(usedTokens);
        snapshot.setManualBonusTokens(manualBonusTokens);
        snapshot.setSignRewardTokens(signRewardTokens);
        snapshot.setArticleRewardTokens(articleRewardTokens);
        snapshot.setLikeRewardTokens(likeRewardTokens);
        snapshot.setFavoriteRewardTokens(favoriteRewardTokens);
        snapshot.setSignRewardUnitTokens(defaultLong(rule.getSignRewardTokens()));
        snapshot.setArticleRewardUnitTokens(defaultLong(rule.getArticleRewardTokens()));
        snapshot.setLikeRewardUnitTokens(defaultLong(rule.getLikeRewardTokens()));
        snapshot.setFavoriteRewardUnitTokens(defaultLong(rule.getFavoriteRewardTokens()));
        snapshot.setCumulativeSignDays(signDays);
        snapshot.setArticleCount(articleCount);
        snapshot.setLikedArticleCount(likedArticleCount);
        snapshot.setFavoriteArticleCount(favoriteArticleCount);
        snapshot.setLikeDailyLimit(likeDailyLimit);
        snapshot.setLikeDailyPerArticleLimit(defaultInt(rule.getLikeDailyPerArticleLimit()));
        snapshot.setTodayLikeCount(todayLikeCount);
        snapshot.setTodayLikeRemainingCount(resolveLikeRemainingCount(todayLikeCount, likeDailyLimit));
        snapshot.setLastConsumeAt(quota == null ? null : quota.getLastConsumeAt());
        return snapshot;
    }

    public IPage<AiQuotaLogVo> pageUserLogs(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("用户不存在");
        }
        Page<SysAiQuotaLog> page = aiQuotaLogMapper.selectPage(
                PageUtil.getPage(),
                new LambdaQueryWrapper<SysAiQuotaLog>()
                        .eq(SysAiQuotaLog::getUserId, userId)
                        .orderByDesc(SysAiQuotaLog::getCreateTime)
                        .orderByDesc(SysAiQuotaLog::getId)
        );
        Page<AiQuotaLogVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toQuotaLogVo).toList());
        return result;
    }

    public void assertAndRecordLikeAction(Long userId, Long articleId) {
        if (userId == null || userId <= 0 || articleId == null || articleId <= 0) {
            throw new ServiceException("点赞参数不合法");
        }

        AiQuotaRuleVo rule = getRule();
        int likeDailyLimit = defaultInt(rule.getLikeDailyLimit());
        int likeDailyPerArticleLimit = defaultInt(rule.getLikeDailyPerArticleLimit());
        LocalDate today = LocalDate.now();
        String dailyKey = buildLikeDailyKey(userId, today);
        String articleDailyKey = buildLikeArticleDailyKey(userId, articleId, today);
        long expireSeconds = secondsUntilTomorrow();

        Long todayLikeCount = redisUtil.increment(dailyKey, 1);
        if (todayLikeCount != null && todayLikeCount == 1L) {
            redisUtil.expire(dailyKey, expireSeconds, java.util.concurrent.TimeUnit.SECONDS);
        }
        if (likeDailyLimit > 0 && defaultLong(todayLikeCount) > likeDailyLimit) {
            redisUtil.decrement(dailyKey, 1);
            throw new ServiceException("今日点赞次数已达上限，请明天再来");
        }

        Long articleTodayCount = redisUtil.increment(articleDailyKey, 1);
        if (articleTodayCount != null && articleTodayCount == 1L) {
            redisUtil.expire(articleDailyKey, expireSeconds, java.util.concurrent.TimeUnit.SECONDS);
        }
        if (likeDailyPerArticleLimit > 0 && defaultLong(articleTodayCount) > likeDailyPerArticleLimit) {
            redisUtil.decrement(articleDailyKey, 1);
            redisUtil.decrement(dailyKey, 1);
            throw new ServiceException("这篇文章今天已经点得够多了，换一篇看看吧");
        }
    }

    public void assertRequestQuota(Long userId) {
        AiQuotaRuleVo rule = getRule();
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            return;
        }
        AiQuotaSnapshotVo snapshot = getQuotaSnapshot(userId, rule);
        if (defaultLong(snapshot.getAvailableTokens()) < defaultLong(rule.getMinRequestTokens())) {
            throw new ServiceException("AI 额度不足，请先签到、点赞收藏文章或发布文章后再试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void consumeTokens(Long userId, long tokens) {
        consumeTokens(userId, tokens, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void consumeTokens(Long userId, long tokens, Long conversationId, String conversationTitle) {
        if (tokens <= 0) {
            return;
        }
        AiQuotaRuleVo rule = getRule();
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            return;
        }
        SysAiQuota quota = getOrCreateQuota(userId);
        quota.setUsedTokens(defaultLong(quota.getUsedTokens()) + tokens);
        quota.setLastConsumeAt(LocalDateTime.now());
        aiQuotaMapper.updateById(quota);
        saveQuotaLog(userId, BIZ_TYPE_CONSUME, -tokens, null, conversationId, safeTitle(conversationTitle, "AI 对话"), "AI 对话消耗");
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateManualBonusTokens(Long userId, Long manualBonusTokens) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("用户不存在");
        }
        long normalizedManualBonusTokens = defaultLong(manualBonusTokens);
        if (normalizedManualBonusTokens < 0) {
            throw new ServiceException("手动额度不能小于 0");
        }
        SysAiQuota quota = getOrCreateQuota(userId);
        long previousManualBonusTokens = defaultLong(quota.getManualBonusTokens());
        quota.setManualBonusTokens(normalizedManualBonusTokens);
        aiQuotaMapper.updateById(quota);
        saveQuotaLog(userId, BIZ_TYPE_MANUAL, normalizedManualBonusTokens - previousManualBonusTokens, null, null, "后台调整", "后台调整 AI 手动额度");
    }

    public void recordSignReward(Long userId, long cumulativeSignDays) {
        saveQuotaLog(
                userId,
                BIZ_TYPE_SIGN,
                defaultLong(getRule().getSignRewardTokens()),
                null,
                null,
                "每日签到",
                "完成第 " + Math.max(1L, cumulativeSignDays) + " 天签到"
        );
    }

    public void recordArticleReward(Long userId, Long articleId, String articleTitle, boolean increase) {
        long reward = defaultLong(getRule().getArticleRewardTokens());
        saveQuotaLog(
                userId,
                BIZ_TYPE_ARTICLE,
                increase ? reward : -reward,
                articleId,
                null,
                safeTitle(articleTitle, "文章"),
                increase ? "文章发布获得 AI 额度" : "文章下架或删除，回收发文额度"
        );
    }

    public void recordLikeReward(Long userId, Long articleId, String articleTitle, long count, boolean increase) {
        if (count <= 0) {
            return;
        }
        long reward = defaultLong(getRule().getLikeRewardTokens()) * count;
        saveQuotaLog(
                userId,
                BIZ_TYPE_LIKE,
                increase ? reward : -reward,
                articleId,
                null,
                safeTitle(articleTitle, "文章"),
                increase ? "点赞文章获得 AI 额度" : "取消点赞，回收点赞额度"
        );
    }

    public void recordFavoriteReward(Long userId, Long articleId, String articleTitle, boolean increase) {
        long reward = defaultLong(getRule().getFavoriteRewardTokens());
        saveQuotaLog(
                userId,
                BIZ_TYPE_FAVORITE,
                increase ? reward : -reward,
                articleId,
                null,
                safeTitle(articleTitle, "文章"),
                increase ? "收藏文章获得 AI 额度" : "取消收藏，回收藏额度"
        );
    }

    private SysAiQuota findQuota(Long userId) {
        return aiQuotaMapper.selectOne(new LambdaQueryWrapper<SysAiQuota>()
                .eq(SysAiQuota::getUserId, userId)
                .last("limit 1"));
    }

    private SysAiQuota getOrCreateQuota(Long userId) {
        SysAiQuota quota = findQuota(userId);
        if (quota != null) {
            return quota;
        }
        SysAiQuota created = SysAiQuota.builder()
                .userId(userId)
                .manualBonusTokens(0L)
                .usedTokens(0L)
                .build();
        aiQuotaMapper.insert(created);
        return created;
    }

    private Map<String, SysConfig> getOrCreateRuleConfigMap() {
        List<String> configKeys = RULE_DEFINITIONS.stream().map(ConfigDefinition::key).toList();
        Map<String, SysConfig> configMap = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                        .in(SysConfig::getConfigKey, configKeys))
                .stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (ConfigDefinition definition : RULE_DEFINITIONS) {
            if (configMap.containsKey(definition.key())) {
                continue;
            }
            SysConfig created = new SysConfig();
            created.setConfigName(definition.name());
            created.setConfigKey(definition.key());
            created.setConfigValue(definition.defaultValue());
            created.setConfigType(CONFIG_TYPE_SYSTEM);
            created.setRemark(definition.remark());
            configMapper.insert(created);
            configMap.put(definition.key(), created);
        }
        return configMap;
    }

    private void upsertConfig(ConfigDefinition definition, String value) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, definition.key())
                .last("limit 1"));
        if (config == null) {
            config = new SysConfig();
            config.setConfigName(definition.name());
            config.setConfigKey(definition.key());
            config.setConfigType(CONFIG_TYPE_SYSTEM);
            config.setRemark(definition.remark());
            configMapper.insert(config);
        }
        config.setConfigName(definition.name());
        config.setConfigValue(value);
        config.setConfigType(CONFIG_TYPE_SYSTEM);
        config.setRemark(definition.remark());
        configMapper.updateById(config);
    }

    @SuppressWarnings("SameParameterValue")
    private boolean parseBoolean(SysConfig config, boolean defaultValue) {
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return defaultValue;
        }
        String normalized = config.getConfigValue().trim();
        return "Y".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "true".equalsIgnoreCase(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized);
    }

    private long parseLong(SysConfig config, long defaultValue) {
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0L, Long.parseLong(config.getConfigValue().trim()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long getCumulativeSignDays(Long userId) {
        Long bitCount = redisUtil.bitCount(RedisConstants.USER_SIGN + userId, 0, getCurrentSignOffset());
        return defaultLong(bitCount);
    }

    private long getTodayLikeCount(Long userId) {
        Object count = redisUtil.get(buildLikeDailyKey(userId, LocalDate.now()));
        if (count == null) {
            return 0L;
        }
        if (count instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        try {
            return Math.max(0L, Long.parseLong(count.toString()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long resolveLikeRemainingCount(long todayLikeCount, int likeDailyLimit) {
        if (likeDailyLimit <= 0) {
            return -1L;
        }
        return Math.max(0L, likeDailyLimit - todayLikeCount);
    }

    private String buildLikeDailyKey(Long userId, LocalDate date) {
        return RedisConstants.AI_LIKE_DAILY_COUNT + userId + ":" + date;
    }

    private String buildLikeArticleDailyKey(Long userId, Long articleId, LocalDate date) {
        return RedisConstants.AI_LIKE_ARTICLE_DAILY_COUNT + userId + ":" + articleId + ":" + date;
    }

    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atStartOfDay().plusHours(2);
        return Math.max(1L, ChronoUnit.SECONDS.between(now, tomorrow));
    }

    private long getCurrentSignOffset() {
        LocalDate today = LocalDate.now();
        return Math.max(0L, ChronoUnit.DAYS.between(Constants.USER_SIGN_START_DATE, today));
    }

    private AiQuotaLogVo toQuotaLogVo(SysAiQuotaLog log) {
        AiQuotaLogVo vo = new AiQuotaLogVo();
        vo.setId(log.getId());
        vo.setBizType(log.getBizType());
        vo.setTokenDelta(log.getTokenDelta());
        vo.setArticleId(log.getArticleId());
        vo.setConversationId(log.getConversationId());
        vo.setSourceTitle(log.getSourceTitle());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private void saveQuotaLog(Long userId, String bizType, long tokenDelta, Long articleId, Long conversationId, String sourceTitle, String remark) {
        if (userId == null || userId <= 0 || tokenDelta == 0) {
            return;
        }
        aiQuotaLogMapper.insert(SysAiQuotaLog.builder()
                .userId(userId)
                .bizType(bizType)
                .tokenDelta(tokenDelta)
                .articleId(articleId)
                .conversationId(conversationId)
                .sourceTitle(sourceTitle)
                .remark(remark)
                .build());
    }

    private String safeTitle(String title, String fallback) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return fallback;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private long defaultLong(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int parseInt(SysConfig config, int defaultValue) {
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(config.getConfigValue().trim()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : Math.max(0, value);
    }

    private record ConfigDefinition(String name, String key, String defaultValue, String remark) {
        private long defaultLongValue() {
            return Long.parseLong(defaultValue);
        }

        private int defaultIntValue() {
            return Integer.parseInt(defaultValue);
        }
    }
}
