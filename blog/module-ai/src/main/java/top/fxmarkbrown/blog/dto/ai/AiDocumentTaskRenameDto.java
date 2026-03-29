package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "重命名文档任务请求")
public class AiDocumentTaskRenameDto {

    @Schema(description = "任务标题")
    private String title;
}
