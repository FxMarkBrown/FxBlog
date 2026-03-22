package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
public class AiConversationListVo {

    private Long id;

    private String type;

    private Long articleId;

    private String title;

    private String summary;

    private String modelProvider;

    private String modelName;

    private String modelId;

    private String modelDisplayName;

    @Schema(description = "最后消息时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastMessageAt;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
