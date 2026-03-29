package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建文档任务请求")
public class AiDocumentTaskCreateDto {

    @Schema(description = "源文件 ID")
    private String sourceFileId;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "源文件名")
    private String fileName;

    @Schema(description = "已上传文件地址")
    private String sourceUrl;
}
