package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentKnowledgeFlowEdgeVo {

    private String fromNodeId;

    private String toNodeId;

    private String edgeType;

    private Double weight;

    private String reason;
}
