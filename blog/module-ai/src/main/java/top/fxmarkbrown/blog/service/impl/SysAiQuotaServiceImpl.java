package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.entity.SysAiQuotaLog;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.mapper.SysAiQuotaLogMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.SysAiQuotaService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.ai.AiQuotaAdminLogVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaRuleVo;
import top.fxmarkbrown.blog.vo.ai.AiQuotaSnapshotVo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysAiQuotaServiceImpl implements SysAiQuotaService {

    private final SysUserMapper userMapper;
    private final SysAiQuotaLogMapper aiQuotaLogMapper;
    private final AiQuotaCoreService aiQuotaCoreService;

    @Override
    public IPage<AiQuotaAdminVo> pageQuota(String userKeyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(userKeyword)) {
            String normalizedKeyword = userKeyword.trim();
            wrapper.and(q -> q.like(SysUser::getNickname, normalizedKeyword)
                    .or()
                    .like(SysUser::getUsername, normalizedKeyword));
        }
        Page<SysUser> page = userMapper.selectPage(PageUtil.getPage(), wrapper);
        AiQuotaRuleVo rule = aiQuotaCoreService.getRule();
        Page<AiQuotaAdminVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(user -> toQuotaVo(user, rule)).toList());
        return result;
    }

    @Override
    public IPage<AiQuotaAdminLogVo> pageQuotaLogs(String bizType, String userKeyword) {
        Page<SysAiQuotaLog> pageRequest = PageUtil.getPage();
        LambdaQueryWrapper<SysAiQuotaLog> wrapper = new LambdaQueryWrapper<SysAiQuotaLog>()
                .orderByDesc(SysAiQuotaLog::getCreateTime)
                .orderByDesc(SysAiQuotaLog::getId);
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(SysAiQuotaLog::getBizType, bizType.trim());
        }
        if (StringUtils.hasText(userKeyword)) {
            String normalizedKeyword = userKeyword.trim();
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getNickname, normalizedKeyword)
                            .or()
                            .like(SysUser::getUsername, normalizedKeyword))
                    .stream()
                    .map(SysUser::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (matchedUserIds.isEmpty()) {
                return emptyLogPage(pageRequest);
            }
            wrapper.in(SysAiQuotaLog::getUserId, matchedUserIds);
        }
        Page<SysAiQuotaLog> page = aiQuotaLogMapper.selectPage(pageRequest, wrapper);
        if (page.getRecords().isEmpty()) {
            return emptyLogPage(page);
        }
        List<Long> userIds = page.getRecords().stream()
                .map(SysAiQuotaLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Page<AiQuotaAdminLogVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(item -> toQuotaLogVo(item, userMap)).toList());
        return result;
    }

    private AiQuotaAdminVo toQuotaVo(SysUser user, AiQuotaRuleVo rule) {
        AiQuotaSnapshotVo snapshot = aiQuotaCoreService.getQuotaSnapshot(user.getId(), rule);
        AiQuotaAdminVo vo = new AiQuotaAdminVo();
        vo.setUserId(snapshot.getUserId());
        vo.setEnabled(snapshot.getEnabled());
        vo.setMinRequestTokens(snapshot.getMinRequestTokens());
        vo.setAvailableTokens(snapshot.getAvailableTokens());
        vo.setTotalEarnedTokens(snapshot.getTotalEarnedTokens());
        vo.setUsedTokens(snapshot.getUsedTokens());
        vo.setManualBonusTokens(snapshot.getManualBonusTokens());
        vo.setSignRewardTokens(snapshot.getSignRewardTokens());
        vo.setArticleRewardTokens(snapshot.getArticleRewardTokens());
        vo.setLikeRewardTokens(snapshot.getLikeRewardTokens());
        vo.setFavoriteRewardTokens(snapshot.getFavoriteRewardTokens());
        vo.setSignRewardUnitTokens(snapshot.getSignRewardUnitTokens());
        vo.setArticleRewardUnitTokens(snapshot.getArticleRewardUnitTokens());
        vo.setLikeRewardUnitTokens(snapshot.getLikeRewardUnitTokens());
        vo.setFavoriteRewardUnitTokens(snapshot.getFavoriteRewardUnitTokens());
        vo.setCumulativeSignDays(snapshot.getCumulativeSignDays());
        vo.setArticleCount(snapshot.getArticleCount());
        vo.setLikedArticleCount(snapshot.getLikedArticleCount());
        vo.setFavoriteArticleCount(snapshot.getFavoriteArticleCount());
        vo.setLikeDailyLimit(snapshot.getLikeDailyLimit());
        vo.setLikeDailyPerArticleLimit(snapshot.getLikeDailyPerArticleLimit());
        vo.setTodayLikeCount(snapshot.getTodayLikeCount());
        vo.setTodayLikeRemainingCount(snapshot.getTodayLikeRemainingCount());
        vo.setLastConsumeAt(snapshot.getLastConsumeAt());
        vo.setUsername(user.getUsername());
        vo.setUserNickname(user.getNickname());
        vo.setUserAvatar(user.getAvatar());
        return vo;
    }

    private Page<AiQuotaAdminLogVo> emptyLogPage(Page<?> sourcePage) {
        Page<AiQuotaAdminLogVo> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), 0);
        result.setRecords(Collections.emptyList());
        return result;
    }

    private AiQuotaAdminLogVo toQuotaLogVo(SysAiQuotaLog log, Map<Long, SysUser> userMap) {
        SysUser user = userMap.get(log.getUserId());
        AiQuotaAdminLogVo vo = new AiQuotaAdminLogVo();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setBizType(log.getBizType());
        vo.setTokenDelta(log.getTokenDelta());
        vo.setArticleId(log.getArticleId());
        vo.setConversationId(log.getConversationId());
        vo.setSourceTitle(log.getSourceTitle());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setUserNickname(user == null ? "-" : user.getNickname());
        vo.setUserAvatar(user == null ? null : user.getAvatar());
        return vo;
    }
}
