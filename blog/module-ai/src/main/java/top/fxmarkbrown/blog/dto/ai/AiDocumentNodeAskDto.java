package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 文档节点问答请求")
public class AiDocumentNodeAskDto {

    @Schema(description = "用户问题")
    private String question;

    @Schema(description = "额外显式选中的节点 ID 集合")
    private List<String> selectedNodeIds;
}
