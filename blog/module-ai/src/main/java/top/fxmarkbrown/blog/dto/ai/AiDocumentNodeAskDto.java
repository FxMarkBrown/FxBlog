package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 文档节点问答请求")
public class AiDocumentNodeAskDto {

    @Schema(description = "用户问题")
    private String question;

    @Schema(description = "本轮显式选择的模型 ID，为空时使用默认模型")
    private String modelId;

    @Schema(description = "问答模式，可选 explain / compare / locate / reason / summarize")
    private String queryMode;

    @Schema(description = "额外显式选中的节点 ID 集合")
    private List<String> selectedNodeIds;

    @Schema(description = "子树展开深度，默认按 queryMode 自动决定")
    private Integer descendantDepth;

    @Schema(description = "是否补充主召回节点的同层上下文")
    private Boolean includePeerContext;

    @Schema(description = "是否启用文档内 RAG 召回")
    private Boolean enableRetrieval;

    @Schema(description = "RAG 召回节点数量上限，默认按 queryMode 自动决定")
    private Integer maxRetrievedNodes;
}
