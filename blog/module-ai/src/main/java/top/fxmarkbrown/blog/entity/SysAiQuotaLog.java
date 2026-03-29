package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_ai_quota_log")
@Schema(description = "AI 额度流水")
public class SysAiQuotaLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "额度变化值，正数为增加，负数为扣减")
    private Long tokenDelta;

    @Schema(description = "关联文章 id")
    private Long articleId;

    @Schema(description = "关联会话 id")
    private Long conversationId;

    @Schema(description = "来源标题")
    private String sourceTitle;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
