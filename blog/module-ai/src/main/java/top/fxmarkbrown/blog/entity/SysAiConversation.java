package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("sys_ai_conversation")
@Schema(description = "AI 会话")
public class SysAiConversation implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "会话类型")
    private String type;

    @Schema(description = "文章 id")
    private Long articleId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话摘要")
    private String summary;

    @Schema(description = "模型提供商")
    private String modelProvider;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "最后消息时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastMessageAt;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
