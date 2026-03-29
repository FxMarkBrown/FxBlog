package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentContextPlanVo {

    private String queryMode;

    private String currentNodeId;

    private Integer descendantDepth;

    private Integer maxRetrievedNodes;

    private Integer ancestorCount;

    private Integer descendantCount;

    private Integer peerContextCount;

    private Integer selectedCount;

    private Integer retrievedCount;

    private Integer totalCandidateCount;

    private Integer totalUsedCount;
}
