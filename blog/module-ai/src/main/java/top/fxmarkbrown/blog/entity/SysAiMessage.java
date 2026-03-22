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
@TableName("sys_ai_message")
@Schema(description = "AI 会话消息")
public class SysAiMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "会话 id")
    private Long conversationId;

    @Schema(description = "消息角色")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "输入 token 数")
    private Integer tokensIn;

    @Schema(description = "输出 token 数")
    private Integer tokensOut;

    @Schema(description = "引用片段 JSON")
    private String quotePayload;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
