package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentContextPlanVo {

    private String queryMode;

    private String currentNodeId;

    private Integer descendantDepth;

    private Integer maxBridgeNodes;

    private Integer ancestorCount;

    private Integer descendantCount;

    private Integer ancestorSiblingCount;

    private Integer selectedCount;

    private Integer semanticBridgeCount;

    private Integer totalCandidateCount;

    private Integer totalUsedCount;
}
