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
@TableName("sys_ai_document_result")
@Schema(description = "AI 文档任务结果")
public class SysAiDocumentResult implements Serializable {

    @TableId(value = "task_id", type = IdType.INPUT)
    private Long taskId;

    @Schema(description = "完整 Markdown")
    private String markdown;

    @Schema(description = "content_list 原始 JSON")
    private String contentListJson;

    @Schema(description = "结构树 JSON")
    private String rootJson;

    @Schema(description = "远端原始结果 JSON")
    private String rawPayloadJson;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
