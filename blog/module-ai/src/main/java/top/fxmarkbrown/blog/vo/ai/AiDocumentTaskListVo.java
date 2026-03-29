package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
@Schema(description = "文档任务列表项")
public class AiDocumentTaskListVo {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "源文件 ID")
    private String sourceFileId;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文档页数")
    private Integer pageCount;

    @Schema(description = "是否已完成解析")
    private Boolean parsed;

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
