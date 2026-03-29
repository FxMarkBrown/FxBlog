package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_feedback")
@Schema(description = "反馈表对象")
public class SysFeedback implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "反馈人id")
    private Long userId;

    @Schema(description = "反馈类型")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "联系邮箱")
    private String email;

    @Schema(description = "回复内容")
    private String replyContent;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;
}
