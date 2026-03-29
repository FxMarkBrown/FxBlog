package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiDocumentNodeAnswerVo {

    private Long taskId;

    private String nodeId;

    private String question;

    private String answer;

    private String modelId;

    private List<String> contextNodeIds;

    private List<AiDocumentNodeCitationVo> citations;

    private AiDocumentContextPlanVo contextPlan;

    private AiDocumentContextBudgetVo budgetReport;

    private List<AiDocumentContextNodeVo> usedNodes;

    private List<AiDocumentContextNodeVo> candidateNodes;

    private List<AiDocumentKnowledgeFlowEdgeVo> knowledgeFlowEdges;
}
