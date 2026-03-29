package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentContextBudgetVo {

    private Integer maxChars;

    private Integer candidateChars;

    private Integer usedChars;

    private Integer remainingChars;

    private Integer truncatedNodeCount;
}
