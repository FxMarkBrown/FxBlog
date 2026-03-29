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
@TableName("sys_ai_document_node_thread")
@Schema(description = "AI 文档节点线程")
public class SysAiDocumentNodeThread implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "文档任务 id")
    private Long taskId;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "文档节点 id")
    private String nodeId;

    @Schema(description = "线程标题")
    private String title;

    @Schema(description = "线程摘要")
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
