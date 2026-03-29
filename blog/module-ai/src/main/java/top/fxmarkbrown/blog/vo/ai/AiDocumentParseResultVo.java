package top.fxmarkbrown.blog.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档解析结果")
public class AiDocumentParseResultVo {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "完整 Markdown")
    private String markdown;

    @Schema(description = "结构树根节点")
    private AiDocumentTreeNodeVo root;
}
