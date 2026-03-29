package top.fxmarkbrown.blog.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "文档结构树节点")
public class AiDocumentTreeNodeVo {

    @Schema(description = "节点 ID")
    private String id;

    @Schema(description = "父节点 ID")
    private String parentId;

    @Schema(description = "节点类型")
    private String type;

    @Schema(description = "节点标题")
    private String title;

    @Schema(description = "标题层级")
    private Integer level;

    @Schema(description = "节点摘要")
    private String summary;

    @Schema(description = "节点 Markdown 内容")
    private String markdown;

    @Schema(description = "是否可继续展开")
    private Boolean expandable;

    @Schema(description = "子节点")
    private List<AiDocumentTreeNodeVo> children = new ArrayList<>();

    @Schema(description = "原文锚点")
    private List<AiDocumentSourceAnchorVo> sourceAnchors = new ArrayList<>();
}
