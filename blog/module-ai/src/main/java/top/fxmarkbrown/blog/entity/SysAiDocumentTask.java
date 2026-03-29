package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_ai_document_task")
@Schema(description = "AI 文档任务")
public class SysAiDocumentTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "源文件 ID")
    private String sourceFileId;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "供应方")
    private String provider;

    @Schema(description = "远端任务 ID")
    private String remoteTaskId;

    @Schema(description = "源文件名")
    private String fileName;

    @Schema(description = "源文件 URL")
    private String sourceUrl;

    @Schema(description = "Markdown 结果 URL")
    private String markdownUrl;

    @Schema(description = "页数")
    private Integer pageCount;

    @Schema(description = "根节点 ID")
    private String rootNodeId;

    @Schema(description = "结果过期时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime expireAt;

    @Schema(description = "最近轮询时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastPolledAt;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
