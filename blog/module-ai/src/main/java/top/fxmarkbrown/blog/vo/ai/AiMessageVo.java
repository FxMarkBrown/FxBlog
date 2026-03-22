package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiMessageVo {

    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String reasoningContent;

    private Integer tokensIn;

    private Integer tokensOut;

    private String quotePayload;

    private List<AiToolCallVo> toolCalls;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
