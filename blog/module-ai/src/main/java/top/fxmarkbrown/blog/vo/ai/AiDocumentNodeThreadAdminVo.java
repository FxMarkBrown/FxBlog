package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
@Schema(description = "后台文档节点线程管理项")
public class AiDocumentNodeThreadAdminVo {

    @Schema(description = "线程 ID")
    private Long threadId;

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "节点 ID")
    private String nodeId;

    @Schema(description = "节点标题")
    private String nodeTitle;

    @Schema(description = "线程标题")
    private String title;

    @Schema(description = "线程摘要")
    private String summary;

    @Schema(description = "模型提供商")
    private String modelProvider;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "模型 ID")
    private String modelId;

    @Schema(description = "模型显示名")
    private String modelDisplayName;

    @Schema(description = "消息数")
    private Long messageCount;

    @Schema(description = "最后消息时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastMessageAt;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
