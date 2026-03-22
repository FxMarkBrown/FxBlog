package top.fxmarkbrown.blog.dto.ai;

import lombok.Data;

@Data
public class AiQuotaRuleUpdateDto {

    private Boolean enabled;

    private Long minRequestTokens;

    private Long signRewardTokens;

    private Long articleRewardTokens;

    private Long likeRewardTokens;

    private Long favoriteRewardTokens;

    private Integer likeDailyLimit;

    private Integer likeDailyPerArticleLimit;
}
