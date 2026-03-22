package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
public class AiQuotaSnapshotVo {

    private Long userId;

    private Boolean enabled;

    private Long minRequestTokens;

    private Long availableTokens;

    private Long totalEarnedTokens;

    private Long usedTokens;

    private Long manualBonusTokens;

    private Long signRewardTokens;

    private Long articleRewardTokens;

    private Long likeRewardTokens;

    private Long favoriteRewardTokens;

    private Long signRewardUnitTokens;

    private Long articleRewardUnitTokens;

    private Long likeRewardUnitTokens;

    private Long favoriteRewardUnitTokens;

    private Long cumulativeSignDays;

    private Long articleCount;

    private Long likedArticleCount;

    private Long favoriteArticleCount;

    private Integer likeDailyLimit;

    private Integer likeDailyPerArticleLimit;

    private Long todayLikeCount;

    private Long todayLikeRemainingCount;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime lastConsumeAt;
}
