package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
public class AiQuotaLogVo {

    private Long id;

    private String bizType;

    private Long tokenDelta;

    private Long articleId;

    private Long conversationId;

    private String sourceTitle;

    private String remark;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;
}
