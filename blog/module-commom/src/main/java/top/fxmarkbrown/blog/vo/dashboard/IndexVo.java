package top.fxmarkbrown.blog.vo.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IndexVo {

    @Schema(description = "文章数量")
    private Long articleCount;

    @Schema(description = "用户数量")
    private Long userCount;

    @Schema(description = "留言数量")
    private Long messageCount ;

    @Schema(description = "访问量")
    private Long visitCount;

    @Schema(description = "贡献度")
    private List<ContributionData> contributionData;


}
