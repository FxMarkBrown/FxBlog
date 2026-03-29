package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
@Schema(description = "文档任务详情")
public class AiDocumentTaskDetailVo {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "源文件 ID")
    private String sourceFileId;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "远端任务 ID")
    private String remoteTaskId;

    @Schema(description = "源文件名")
    private String fileName;

    @Schema(description = "原始文档地址")
    private String sourceUrl;

    @Schema(description = "解析后的 Markdown 地址")
    private String markdownUrl;

    @Schema(description = "页数")
    private Integer pageCount;

    @Schema(description = "根节点 ID")
    private String rootNodeId;

    @Schema(description = "结果过期时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime expireAt;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
