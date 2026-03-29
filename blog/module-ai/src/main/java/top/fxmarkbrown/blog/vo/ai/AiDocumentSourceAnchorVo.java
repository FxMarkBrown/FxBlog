package top.fxmarkbrown.blog.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "文档节点原文锚点")
public class AiDocumentSourceAnchorVo {

    @Schema(description = "页码，从 1 开始")
    private Integer page;

    @Schema(description = "归一化包围盒，[x1, y1, x2, y2]")
    private List<Double> bbox;

    @Schema(description = "原文文本片段")
    private String textSnippet;
}
