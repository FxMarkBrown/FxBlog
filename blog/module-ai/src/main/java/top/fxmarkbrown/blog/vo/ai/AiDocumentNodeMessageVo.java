package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiDocumentNodeMessageVo {

    private Long id;

    private Long threadId;

    private String role;

    private String content;

    private String modelId;

    private Integer tokensIn;

    private Integer tokensOut;

    private String quotePayload;

    private List<String> selectedNodeIds;

    private List<AiDocumentNodeCitationVo> citations;

    private AiDocumentContextPlanVo contextPlan;

    private AiDocumentContextBudgetVo budgetReport;

    private List<AiDocumentContextNodeVo> usedNodes;

    private List<AiDocumentContextNodeVo> candidateNodes;

    private List<AiDocumentKnowledgeFlowEdgeVo> knowledgeFlowEdges;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
